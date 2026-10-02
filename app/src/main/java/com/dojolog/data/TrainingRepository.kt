package com.dojolog.data

import androidx.room.withTransaction
import com.dojolog.data.db.AppDatabase
import com.dojolog.data.db.SessionEntity
import com.dojolog.data.db.SessionTechniqueEntity
import com.dojolog.data.db.SessionWithEntries
import com.dojolog.data.db.TechniqueEntity
import com.dojolog.domain.SessionType
import com.dojolog.domain.Stats
import com.dojolog.domain.Technique
import com.dojolog.domain.TechniqueCategory
import com.dojolog.domain.TechniqueEntry
import com.dojolog.domain.TrainingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Single source of truth for the app; everything is stored in the local Room database. */
class TrainingRepository(private val db: AppDatabase) {
    private val sessionDao = db.sessionDao()
    private val techniqueDao = db.techniqueDao()

    /** All sessions, newest first. */
    fun observeSessions(): Flow<List<TrainingSession>> =
        sessionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeSession(id: Long): Flow<TrainingSession?> =
        sessionDao.observe(id).map { it?.toDomain() }

    suspend fun getSession(id: Long): TrainingSession? = sessionDao.get(id)?.toDomain()

    fun observeRecentDisciplines(): Flow<List<String>> = sessionDao.observeRecentDisciplines()

    /** Colour slot per martial art; see [Stats.disciplineSlots]. */
    fun observeDisciplineSlots(): Flow<Map<String, Int>> =
        observeSessions().map { Stats.disciplineSlots(it) }.distinctUntilChanged()

    fun observeTechniques(): Flow<List<Technique>> =
        techniqueDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeTechnique(id: Long): Flow<Technique?> = techniqueDao.observe(id).map { it?.toDomain() }

    /** Inserts or updates the session and replaces its technique entries. Returns the session id. */
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
}

private fun TechniqueEntity.toDomain() = Technique(
    id = id,
    name = name,
    category = TechniqueCategory.fromKey(category),
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
    createdAt = session.createdAt,
)
