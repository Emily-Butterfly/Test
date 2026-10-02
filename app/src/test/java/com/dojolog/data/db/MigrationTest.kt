package com.dojolog.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.dojolog.domain.Ratings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun version1DataSurvivesAndTheNewTablesMatchTheEntities() = runBlocking {
        // Version 1 had exactly today's tables minus the two new ones, so build it that way.
        AppDatabase.create(context, name).apply {
            val techniqueId = techniqueDao().insert(TechniqueEntity(name = "Armbar", category = "submission", notes = "", createdAt = 1))
            val sessionId = sessionDao().insert(
                SessionEntity(
                    date = LocalDate.of(2026, 9, 1),
                    durationMinutes = 60,
                    discipline = "BJJ",
                    sessionType = "sparring",
                    notes = "",
                    overallRating = 7f,
                    overallAuto = true,
                    ratings = Ratings(technique = 7),
                    createdAt = 2,
                ),
            )
            sessionDao().insertEntries(
                listOf(SessionTechniqueEntity(sessionId = sessionId, techniqueId = techniqueId, reps = 5, quality = 3, notes = "", position = 0)),
            )
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP TABLE session_matchups")
            db.execSQL("DROP TABLE opponents")
            db.version = 1
        }

        // Opening runs MIGRATION_1_2, then Room checks every table against the entities and
        // throws if the new ones don't match.
        val db = AppDatabase.create(context, name)
        try {
            assertEquals(2, db.openHelper.writableDatabase.version)
            val session = db.sessionDao().getAll().single()
            assertEquals("BJJ", session.session.discipline)
            assertEquals("Armbar", session.entries.single().technique.name)
            assertTrue(session.matchups.isEmpty())

            val opponentId = db.opponentDao().insert(
                OpponentEntity(name = "Alex", club = "", grade = "", weight = "", notes = "", createdAt = 3),
            )
            db.sessionDao().insertMatchups(
                listOf(
                    SessionMatchupEntity(
                        sessionId = session.session.id,
                        opponentId = opponentId,
                        result = "win",
                        rating = 8,
                        notes = "",
                        position = 0,
                    ),
                ),
            )
            assertEquals("Alex", db.sessionDao().getAll().single().matchups.single().opponent.name)
            // The unique, case-insensitive name index came with the migration.
            assertEquals("Alex", db.opponentDao().findByName("alex")?.name)
            // Deleting the session takes its matchups with it.
            db.sessionDao().delete(session.session.id)
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM session_matchups").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            db.close()
        }
    }
}
