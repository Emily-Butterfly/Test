package com.dojolog.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dojolog.data.DisciplineSlotStore
import com.dojolog.data.TrainingRepository
import com.dojolog.data.backup.BackupCodec
import com.dojolog.domain.MatchResult
import com.dojolog.domain.Matchup
import com.dojolog.domain.Opponent
import com.dojolog.domain.Ratings
import com.dojolog.domain.SessionType
import com.dojolog.domain.TechniqueCategory
import com.dojolog.domain.TechniqueEntry
import com.dojolog.domain.TrainingSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<AppDatabase>()

    private fun repository(name: String): TrainingRepository {
        val db = AppDatabase.create(context, name)
        databases += db
        return TrainingRepository(db, DisciplineSlotStore(context))
    }

    @After
    fun cleanUp() {
        databases.forEach { it.close() }
        listOf("source.db", "target.db").forEach { context.deleteDatabase(it) }
    }

    /** Sessions without the ids, which an import assigns afresh. */
    private suspend fun TrainingRepository.content() = observeSessions().first().map { session ->
        session.copy(
            id = 0,
            techniques = session.techniques.map { it.copy(techniqueId = 0) },
            matchups = session.matchups.map { it.copy(opponentId = 0) },
        )
    }

    @Test
    fun anExportedBackupRestoresEverythingIntoANewApp() = runBlocking {
        val source = repository("source.db")
        val armbar = source.createTechnique("Armbar", TechniqueCategory.SUBMISSION, "Hips high")
        val jab = source.createTechnique("Jab", TechniqueCategory.STRIKE)
        source.createTechnique("Never practised", TechniqueCategory.OTHER)
        val alex = source.createOpponent(Opponent(0, "Alex", club = "Gracie Barra", grade = "Blue belt", weight = "-76 kg", notes = "Loves leg locks"))
        val sam = source.createOpponent(Opponent(0, "Sam"))
        source.saveSession(
            TrainingSession(
                date = LocalDate.of(2026, 9, 30),
                durationMinutes = 75,
                discipline = "BJJ",
                type = SessionType.SPARRING,
                notes = "Five rounds",
                overall = 7.5f,
                ratings = Ratings(technique = 7, sparring = 8),
                techniques = listOf(TechniqueEntry(armbar.id, armbar.name, armbar.category, reps = 5, quality = 4, notes = "Tight")),
                matchups = listOf(
                    Matchup(alex.id, alex.name, MatchResult.WIN, 8, "Armbar from guard"),
                    Matchup(sam.id, sam.name, MatchResult.LOSS, 4, "Too passive"),
                    Matchup(alex.id, alex.name, MatchResult.NONE),
                ),
                createdAt = 1_000,
            ),
        )
        source.saveSession(
            TrainingSession(
                date = LocalDate.of(2026, 10, 1),
                durationMinutes = 45,
                discipline = "Boxing",
                type = SessionType.CLASS,
                techniques = listOf(TechniqueEntry(jab.id, jab.name, jab.category, reps = 100)),
                createdAt = 2_000,
            ),
        )

        val file = BackupCodec.encode(source.exportBackup("test"))
        val target = repository("target.db")
        val result = target.importBackup(BackupCodec.decode(file), replace = false)

        assertEquals(2, result.sessionsAdded)
        assertEquals(3, result.techniquesAdded)
        assertEquals(2, result.opponentsAdded)
        assertEquals(0, result.problems)
        assertEquals(source.content(), target.content())
        assertEquals(source.observeTechniques().first().map { it.copy(id = 0) }, target.observeTechniques().first().map { it.copy(id = 0) })
        assertEquals(source.observeOpponents().first().map { it.copy(id = 0) }, target.observeOpponents().first().map { it.copy(id = 0) })

        // Importing the same file again adds nothing.
        val again = target.importBackup(BackupCodec.decode(file), replace = false)
        assertEquals(0, again.sessionsAdded)
        assertEquals(2, again.sessionsSkipped)
        assertEquals(2, target.observeSessions().first().size)

        // Replacing wipes what was logged since and leaves exactly the backup.
        target.saveSession(TrainingSession(date = LocalDate.of(2026, 10, 2), durationMinutes = 30, discipline = "Judo", type = SessionType.CLASS))
        target.createOpponent(Opponent(0, "Kim"))
        target.importBackup(BackupCodec.decode(file), replace = true)
        assertEquals(source.content(), target.content())
        assertEquals(listOf("Alex", "Sam"), target.observeOpponents().first().map { it.name })
    }
}
