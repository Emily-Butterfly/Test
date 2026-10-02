package com.dojolog.data

import androidx.room.withTransaction
import com.dojolog.data.backup.BackupFile
import com.dojolog.data.backup.BackupImport
import com.dojolog.data.backup.BackupMatchup
import com.dojolog.data.backup.BackupOpponent
import com.dojolog.data.backup.BackupRatings
import com.dojolog.data.backup.BackupSession
import com.dojolog.data.backup.BackupTechnique
import com.dojolog.data.backup.BackupTechniqueEntry
import com.dojolog.data.backup.ExistingData
import com.dojolog.data.backup.ExistingOpponent
import com.dojolog.data.backup.ImportPlan
import com.dojolog.data.backup.nameKey
import com.dojolog.data.db.AppDatabase
import com.dojolog.data.db.OpponentEntity
import com.dojolog.data.db.SessionEntity
import com.dojolog.data.db.SessionMatchupEntity
import com.dojolog.data.db.SessionTechniqueEntity
import com.dojolog.data.db.SessionWithEntries
import com.dojolog.data.db.TechniqueEntity
import com.dojolog.domain.MatchResult
import com.dojolog.domain.Matchup
import com.dojolog.domain.Opponent
import com.dojolog.domain.Ratings
import com.dojolog.domain.SessionType
import com.dojolog.domain.Stats
import com.dojolog.domain.Technique
import com.dojolog.domain.TechniqueCategory
import com.dojolog.domain.TechniqueEntry
import com.dojolog.domain.TrainingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

/** What an import added. */
data class ImportResult(
    val sessionsAdded: Int,
    val sessionsSkipped: Int,
    val techniquesAdded: Int,
    val opponentsAdded: Int,
    /** Sessions or references the file had but that couldn't be read. */
    val problems: Int,
)

/** Single source of truth for the app; everything is stored in the local Room database. */
class TrainingRepository(private val db: AppDatabase, private val slotStore: DisciplineSlotStore) {
    private val sessionDao = db.sessionDao()
    private val techniqueDao = db.techniqueDao()
    private val opponentDao = db.opponentDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Held while the colour slots are updated, so an import and the update don't interleave. */
    private val slotLock = Mutex()

    /** All sessions, newest first. */
    fun observeSessions(): Flow<List<TrainingSession>> =
        sessionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeSession(id: Long): Flow<TrainingSession?> =
        sessionDao.observe(id).map { it?.toDomain() }

    suspend fun getSession(id: Long): TrainingSession? = sessionDao.get(id)?.toDomain()

    fun observeRecentDisciplines(): Flow<List<String>> = sessionDao.observeRecentDisciplines()

    /**
     * Colour slot per martial art, kept in [slotStore] and updated as sessions change (see
     * [Stats.reconcileSlots]); null until first loaded. It outlives the screens, so a screen
     * recreated on rotation gets the colours at once. One collector updates the store.
     */
    val disciplineSlots: StateFlow<Map<String, Int>?> = observeSessions()
        .map { sessions ->
            slotLock.withLock {
                val stored = slotStore.read()
                val preferred = slotStore.readPreferred()
                val slots = Stats.reconcileSlots(stored, sessions, preferred)
                if (slots != stored) slotStore.write(slots)
                if (preferred.keys.any { it in slots }) slotStore.writePreferred(preferred - slots.keys)
                slots
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun observeTechniques(): Flow<List<Technique>> =
        techniqueDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeTechnique(id: Long): Flow<Technique?> = techniqueDao.observe(id).map { it?.toDomain() }

    fun observeOpponents(): Flow<List<Opponent>> =
        opponentDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeOpponent(id: Long): Flow<Opponent?> = opponentDao.observe(id).map { it?.toDomain() }

    /** Inserts or updates the session and replaces its technique entries and matchups. Returns the session id. */
    suspend fun saveSession(session: TrainingSession): Long = db.withTransaction {
        val entity = SessionEntity(
            id = session.id,
            date = session.date,
            durationMinutes = session.durationMinutes,
            discipline = session.discipline.trim(),
            sessionType = session.type.key,
            notes = session.notes.trim(),
            overallRating = session.overall,
            overallAuto = session.overallAuto,
            ratings = session.ratings,
            createdAt = if (session.createdAt > 0) session.createdAt else System.currentTimeMillis(),
        )
        val id = if (session.id == 0L) {
            sessionDao.insert(entity)
        } else {
            sessionDao.update(entity)
            session.id
        }
        sessionDao.deleteEntries(id)
        sessionDao.insertEntries(
            session.techniques.mapIndexed { index, entry ->
                SessionTechniqueEntity(
                    sessionId = id,
                    techniqueId = entry.techniqueId,
                    reps = entry.reps,
                    quality = entry.quality,
                    notes = entry.notes.trim(),
                    position = index,
                )
            },
        )
        sessionDao.deleteMatchups(id)
        sessionDao.insertMatchups(
            session.matchups.mapIndexed { index, matchup ->
                SessionMatchupEntity(
                    sessionId = id,
                    opponentId = matchup.opponentId,
                    result = matchup.result.key,
                    rating = matchup.rating,
                    notes = matchup.notes.trim(),
                    position = index,
                )
            },
        )
        id
    }

    suspend fun deleteSession(id: Long) = sessionDao.delete(id)

    suspend fun isTechniqueNameTaken(name: String): Boolean = techniqueDao.findByName(name.trim()) != null

    /** Returns the existing technique when one with the same name (ignoring case) exists. */
    suspend fun createTechnique(name: String, category: TechniqueCategory, notes: String = ""): Technique {
        val trimmed = name.trim()
        techniqueDao.findByName(trimmed)?.let { return it.toDomain() }
        val entity = TechniqueEntity(
            name = trimmed,
            category = category.key,
            notes = notes.trim(),
            createdAt = System.currentTimeMillis(),
        )
        return entity.copy(id = techniqueDao.insert(entity)).toDomain()
    }

    /** Returns false when another technique already uses the new name. */
    suspend fun updateTechnique(technique: Technique): Boolean {
        val name = technique.name.trim()
        val clash = techniqueDao.findByName(name)
        if (clash != null && clash.id != technique.id) return false
        techniqueDao.update(technique.id, name, technique.category.key, technique.notes.trim())
        return true
    }

    suspend fun deleteTechnique(id: Long) = techniqueDao.delete(id)

    suspend fun isOpponentNameTaken(name: String): Boolean = opponentDao.findByName(name.trim()) != null

    /** Returns the existing opponent when one with the same name (ignoring case) exists. */
    suspend fun createOpponent(opponent: Opponent): Opponent {
        val name = opponent.name.trim()
        opponentDao.findByName(name)?.let { return it.toDomain() }
        val entity = OpponentEntity(
            name = name,
            club = opponent.club.trim(),
            grade = opponent.grade.trim(),
            weight = opponent.weight.trim(),
            notes = opponent.notes.trim(),
            createdAt = System.currentTimeMillis(),
        )
        return entity.copy(id = opponentDao.insert(entity)).toDomain()
    }

    /** Returns false when another opponent already uses the new name. */
    suspend fun updateOpponent(opponent: Opponent): Boolean {
        val name = opponent.name.trim()
        val clash = opponentDao.findByName(name)
        if (clash != null && clash.id != opponent.id) return false
        opponentDao.update(
            id = opponent.id,
            name = name,
            club = opponent.club.trim(),
            grade = opponent.grade.trim(),
            weight = opponent.weight.trim(),
            notes = opponent.notes.trim(),
        )
        return true
    }

    /** Also removes every matchup against them. */
    suspend fun deleteOpponent(id: Long) = opponentDao.delete(id)

    /** Everything in the app, ready to be written to an export file. */
    suspend fun exportBackup(appVersion: String): BackupFile = withContext(Dispatchers.IO) {
        val (sessions, techniques, opponents) = db.withTransaction {
            Triple(sessionDao.getAll(), techniqueDao.getAll(), opponentDao.getAll())
        }
        BackupFile(
            exportedAt = Instant.now().toString(),
            appVersion = appVersion,
            techniques = techniques.map {
                BackupTechnique(id = it.id, name = it.name, category = it.category, notes = it.notes, createdAt = it.createdAt)
            },
            opponents = opponents.map {
                BackupOpponent(
                    id = it.id,
                    name = it.name,
                    club = it.club,
                    grade = it.grade,
                    weight = it.weight,
                    notes = it.notes,
                    createdAt = it.createdAt,
                )
            },
            sessions = sessions.map { it.toBackup() },
            colors = slotLock.withLock { slotStore.read() },
        )
    }

    /** What importing [backup] would add, without changing anything. */
    suspend fun previewImport(backup: BackupFile): ImportPlan = withContext(Dispatchers.Default) {
        BackupImport.plan(backup, existingData())
    }

    /**
     * Adds what [backup] holds that the app doesn't have yet (see [BackupImport]). With
     * [replace], everything in the app is deleted first, so it ends up exactly as the backup.
     */
    suspend fun importBackup(backup: BackupFile, replace: Boolean): ImportResult = slotLock.withLock {
        val plan = db.withTransaction {
            if (replace) {
                sessionDao.deleteAll()
                techniqueDao.deleteAll()
                opponentDao.deleteAll()
            }
            val plan = BackupImport.plan(backup, existingData())
            val techniqueIds = techniqueDao.getAll().associateTo(HashMap()) { nameKey(it.name) to it.id }
            for (technique in plan.newTechniques) {
                techniqueIds[nameKey(technique.name)] = techniqueDao.insert(
                    TechniqueEntity(
                        name = technique.name,
                        category = technique.category,
                        notes = technique.notes,
                        createdAt = technique.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    ),
                )
            }
            val opponentIds = opponentDao.getAll().associateTo(HashMap()) { nameKey(it.name) to it.id }
            for (opponent in plan.newOpponents) {
                opponentIds[nameKey(opponent.name)] = opponentDao.insert(
                    OpponentEntity(
                        name = opponent.name,
                        club = opponent.club,
                        grade = opponent.grade,
                        weight = opponent.weight,
                        notes = opponent.notes,
                        createdAt = opponent.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    ),
                )
            }
            for (fill in plan.opponentFills) {
                val current = opponentDao.get(fill.id) ?: continue
                opponentDao.update(fill.id, current.name, fill.club, fill.grade, fill.weight, fill.notes)
            }
            for (session in plan.sessions) {
                val id = sessionDao.insert(
                    SessionEntity(
                        date = session.date,
                        durationMinutes = session.durationMinutes,
                        discipline = session.discipline,
                        sessionType = session.type.key,
                        notes = session.notes,
                        overallRating = session.overall,
                        overallAuto = session.overallAuto,
                        ratings = session.ratings,
                        createdAt = session.createdAt,
                    ),
                )
                sessionDao.insertEntries(
                    session.techniques.mapIndexedNotNull { index, entry ->
                        val techniqueId = techniqueIds[entry.techniqueKey] ?: return@mapIndexedNotNull null
                        SessionTechniqueEntity(
                            sessionId = id,
                            techniqueId = techniqueId,
                            reps = entry.reps,
                            quality = entry.quality,
                            notes = entry.notes,
                            position = index,
                        )
                    },
                )
                sessionDao.insertMatchups(
                    session.matchups.mapIndexedNotNull { index, matchup ->
                        val opponentId = opponentIds[matchup.opponentKey] ?: return@mapIndexedNotNull null
                        SessionMatchupEntity(
                            sessionId = id,
                            opponentId = opponentId,
                            result = matchup.result.key,
                            rating = matchup.rating,
                            notes = matchup.notes,
                            position = index,
                        )
                    },
                )
            }
            plan
        }
        // The colours travel with the backup: its arts take their old slots where free. The
        // slot update for the new sessions waits for this lock, so it sees them.
        if (replace) slotStore.write(emptyMap())
        slotStore.writePreferred(if (replace) plan.colors else slotStore.readPreferred() + plan.colors)
        ImportResult(
            sessionsAdded = plan.sessions.size,
            sessionsSkipped = plan.skippedSessions,
            techniquesAdded = plan.newTechniques.size,
            opponentsAdded = plan.newOpponents.size,
            problems = plan.invalidSessions + plan.droppedReferences,
        )
    }

    private suspend fun existingData() = ExistingData(
        techniques = techniqueDao.getAll().map { nameKey(it.name) }.toSet(),
        opponents = opponentDao.getAll().associate {
            nameKey(it.name) to ExistingOpponent(it.id, it.club, it.grade, it.weight, it.notes)
        },
        sessionCreatedAt = sessionDao.getAllCreatedAt().toSet(),
    )
}

private fun TechniqueEntity.toDomain() = Technique(
    id = id,
    name = name,
    category = TechniqueCategory.fromKey(category),
    notes = notes,
)

private fun OpponentEntity.toDomain() = Opponent(
    id = id,
    name = name,
    club = club,
    grade = grade,
    weight = weight,
    notes = notes,
)

private fun SessionWithEntries.toDomain() = TrainingSession(
    id = session.id,
    date = session.date,
    durationMinutes = session.durationMinutes,
    discipline = session.discipline,
    type = SessionType.fromKey(session.sessionType),
    notes = session.notes,
    overall = session.overallRating,
    overallAuto = session.overallAuto,
    ratings = session.ratings,
    techniques = entries
        .sortedBy { it.entry.position }
        .map {
            TechniqueEntry(
                techniqueId = it.entry.techniqueId,
                techniqueName = it.technique.name,
                category = TechniqueCategory.fromKey(it.technique.category),
                reps = it.entry.reps,
                quality = it.entry.quality,
                notes = it.entry.notes,
            )
        },
    matchups = matchups
        .sortedBy { it.matchup.position }
        .map {
            Matchup(
                opponentId = it.matchup.opponentId,
                opponentName = it.opponent.name,
                result = MatchResult.fromKey(it.matchup.result),
                rating = it.matchup.rating,
                notes = it.matchup.notes,
            )
        },
    createdAt = session.createdAt,
)

private fun SessionWithEntries.toBackup() = BackupSession(
    date = session.date.toString(),
    durationMinutes = session.durationMinutes,
    discipline = session.discipline,
    type = session.sessionType,
    notes = session.notes,
    overall = session.overallRating,
    overallAuto = session.overallAuto,
    ratings = session.ratings.toBackup(),
    techniques = entries.sortedBy { it.entry.position }.map {
        BackupTechniqueEntry(techniqueId = it.entry.techniqueId, reps = it.entry.reps, quality = it.entry.quality, notes = it.entry.notes)
    },
    matchups = matchups.sortedBy { it.matchup.position }.map {
        BackupMatchup(opponentId = it.matchup.opponentId, result = it.matchup.result, rating = it.matchup.rating, notes = it.matchup.notes)
    },
    createdAt = session.createdAt,
)

private fun Ratings.toBackup() = BackupRatings(
    technique = technique,
    conditioning = conditioning,
    sparring = sparring,
    focus = focus,
    effort = effort,
)
