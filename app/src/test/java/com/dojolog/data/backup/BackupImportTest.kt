package com.dojolog.data.backup

import com.dojolog.domain.MatchResult
import com.dojolog.domain.Ratings
import com.dojolog.domain.SessionType
import com.dojolog.domain.nameKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupImportTest {
    private val armbar = BackupTechnique(id = 10, name = "Armbar", category = "submission", createdAt = 1)
    private val jab = BackupTechnique(id = 11, name = "Jab", category = "strike", createdAt = 2)
    private val alex = BackupOpponent(id = 20, name = "Alex", club = "Gracie Barra", grade = "Blue belt")
    private val sam = BackupOpponent(id = 21, name = "Sam")

    private val sparring = BackupSession(
        date = "2026-09-30",
        durationMinutes = 75,
        discipline = "BJJ",
        type = "sparring",
        notes = "Good rolls",
        overall = 7.5f,
        overallAuto = true,
        ratings = BackupRatings(technique = 7, sparring = 8),
        techniques = listOf(BackupTechniqueEntry(techniqueId = 10, reps = 5, quality = 4, notes = "Tight")),
        matchups = listOf(
            BackupMatchup(opponentId = 20, result = "win", rating = 8, notes = "Armbar from guard"),
            BackupMatchup(opponentId = 21, result = "loss", rating = 4),
            BackupMatchup(opponentId = 20, result = "none"),
        ),
        createdAt = 1_000,
    )
    private val boxing = BackupSession(
        date = "2026-10-01",
        durationMinutes = 60,
        discipline = "Boxing",
        type = "class",
        techniques = listOf(BackupTechniqueEntry(techniqueId = 11, reps = 100)),
        createdAt = 2_000,
    )
    private val backup = BackupFile(
        exportedAt = "2026-10-02T10:00:00Z",
        techniques = listOf(armbar, jab),
        opponents = listOf(alex, sam),
        sessions = listOf(sparring, boxing),
        colors = mapOf("bjj" to 3, "boxing" to 0),
    )

    @Test
    fun encodedBackupsReadBackUnchanged() {
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test
    fun filesFromOlderVersionsWithMissingFieldsStillRead() {
        val text = """{"format":"dojo-log-backup","version":1,"sessions":[{"date":"2026-01-05","durationMinutes":45,"createdAt":5,"extra":true}]}"""
        val decoded = BackupCodec.decode(text)
        assertEquals(1, decoded.sessions.size)
        assertEquals("class", decoded.sessions.single().type)
        assertEquals(emptyList<BackupMatchup>(), decoded.sessions.single().matchups)
    }

    @Test
    fun otherFilesAreRefusedWithAReason() {
        assertThrows(BackupException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupException::class.java) { BackupCodec.decode("""{"hello":"world"}""") }
        assertThrows(BackupException::class.java) { BackupCodec.decode("[1,2,3]") }
        val newer = assertThrows(BackupException::class.java) {
            BackupCodec.decode("""{"format":"dojo-log-backup","version":99}""")
        }
        assertTrue(newer.message!!.contains("newer version"))
        assertThrows(BackupException::class.java) {
            BackupCodec.decode("""{"format":"dojo-log-backup","version":1,"sessions":[{"date":"2026-01-05"}]}""")
        }
    }

    @Test
    fun intoAnEmptyAppEverythingIsAdded() {
        val plan = BackupImport.plan(backup, ExistingData())
        assertEquals(listOf("Armbar", "Jab"), plan.newTechniques.map { it.name })
        assertEquals(listOf("Alex", "Sam"), plan.newOpponents.map { it.name })
        assertEquals(0, plan.skippedSessions)
        assertEquals(0, plan.droppedReferences)

        val session = plan.sessions.first()
        assertEquals(LocalDate.of(2026, 9, 30), session.date)
        assertEquals(SessionType.SPARRING, session.type)
        assertEquals(Ratings(technique = 7, sparring = 8), session.ratings)
        assertEquals(listOf(PlannedEntry("armbar", 5, 4, "Tight")), session.techniques)
        assertEquals(
            listOf(
                PlannedMatchup("alex", MatchResult.WIN, 8, "Armbar from guard"),
                PlannedMatchup("sam", MatchResult.LOSS, 4, ""),
                PlannedMatchup("alex", MatchResult.NONE, 0, ""),
            ),
            session.matchups,
        )
        assertEquals(mapOf("bjj" to 3, "boxing" to 0), plan.colors)
    }

    @Test
    fun importingIntoAnAppThatHasTheDataAddsNothing() {
        val existing = ExistingData(
            techniques = setOf("armbar", "jab"),
            opponents = mapOf(
                "alex" to ExistingOpponent(1, "Gracie Barra", "Blue belt", "", ""),
                "sam" to ExistingOpponent(2, "", "", "", ""),
            ),
            sessionCreatedAt = setOf(1_000, 2_000),
        )
        val plan = BackupImport.plan(backup, existing)
        assertEquals(emptyList<BackupTechnique>(), plan.newTechniques)
        assertEquals(emptyList<BackupOpponent>(), plan.newOpponents)
        assertEquals(emptyList<OpponentFill>(), plan.opponentFills)
        assertEquals(emptyList<PlannedSession>(), plan.sessions)
        assertEquals(2, plan.skippedSessions)
    }

    @Test
    fun namesMatchIgnoringCaseAndOnlyEmptyDetailsAreFilledIn() {
        val existing = ExistingData(
            techniques = setOf("armbar"),
            opponents = mapOf("alex" to ExistingOpponent(7, "", "Purple belt", "", "Lefty")),
            sessionCreatedAt = setOf(2_000),
        )
        val renamed = backup.copy(
            techniques = listOf(armbar.copy(name = " ARMBAR "), jab),
            opponents = listOf(alex.copy(name = "alex"), sam),
        )
        val plan = BackupImport.plan(renamed, existing)
        assertEquals(listOf("Jab"), plan.newTechniques.map { it.name })
        assertEquals(listOf("Sam"), plan.newOpponents.map { it.name })
        // The app's grade and notes stay; the club it didn't have comes from the file.
        assertEquals(listOf(OpponentFill(7, "Gracie Barra", "Purple belt", "", "Lefty")), plan.opponentFills)
        assertEquals(listOf(1_000L), plan.sessions.map { it.createdAt })
        assertEquals("armbar", plan.sessions.single().techniques.single().techniqueKey)
    }

    @Test
    fun brokenRecordsAreSkippedAndValuesKeptInRange() {
        val damaged = backup.copy(
            techniques = listOf(armbar, armbar.copy(name = "armbar"), BackupTechnique(id = 12, name = "  ")),
            sessions = listOf(
                sparring.copy(
                    durationMinutes = 0,
                    overall = 42f,
                    type = "unknown",
                    ratings = BackupRatings(technique = 15, focus = -3),
                    techniques = listOf(BackupTechniqueEntry(10, reps = -5, quality = 9), BackupTechniqueEntry(99)),
                    matchups = listOf(BackupMatchup(20, result = "forfeit", rating = 12), BackupMatchup(98)),
                ),
                boxing.copy(date = "30/09/2026"),
                boxing.copy(createdAt = 0, techniques = emptyList()),
                boxing.copy(createdAt = 0, techniques = emptyList()),
            ),
            colors = mapOf("bjj" to 1, " " to 2, "judo" to -1, "karate" to 1000),
        )
        val plan = BackupImport.plan(damaged, ExistingData())
        assertEquals(listOf("Armbar"), plan.newTechniques.map { it.name })
        assertEquals(1, plan.invalidSessions)
        assertEquals(2, plan.droppedReferences)
        val session = plan.sessions.first()
        assertEquals(1, session.durationMinutes)
        assertEquals(10f, session.overall)
        assertEquals(SessionType.CLASS, session.type)
        assertEquals(Ratings(technique = 10), session.ratings)
        assertEquals(listOf(PlannedEntry("armbar", 0, 5, "")), session.techniques)
        assertEquals(listOf(PlannedMatchup("alex", MatchResult.NONE, 10, "")), session.matchups)
        // Sessions without a creation time get distinct ones instead of being taken as duplicates.
        assertEquals(3, plan.sessions.size)
        assertEquals(3, plan.sessions.map { it.createdAt }.toSet().size)
        assertEquals(mapOf("bjj" to 1), plan.colors)
    }

    @Test
    fun namesThatTheDatabaseKeepsApartStayApart() {
        // The database ignores the case of A–Z only, so these are four different records.
        val file = backup.copy(
            techniques = listOf(BackupTechnique(1, "Ō-goshi", notes = "A"), BackupTechnique(2, "ō-goshi", notes = "B"), armbar),
            opponents = listOf(BackupOpponent(1, "Zoë", club = "Club A"), BackupOpponent(2, "ZOË", club = "Club B")),
            sessions = listOf(
                sparring.copy(
                    techniques = listOf(BackupTechniqueEntry(1), BackupTechniqueEntry(2)),
                    matchups = listOf(BackupMatchup(1, "win"), BackupMatchup(2, "loss")),
                ),
            ),
        )
        val plan = BackupImport.plan(file, ExistingData(techniques = setOf("armbar")))
        assertEquals(listOf("Ō-goshi" to "A", "ō-goshi" to "B"), plan.newTechniques.map { it.name to it.notes })
        assertEquals(listOf("Zoë" to "Club A", "ZOË" to "Club B"), plan.newOpponents.map { it.name to it.club })
        val session = plan.sessions.single()
        assertEquals(listOf("Ō-goshi", "ō-goshi").map(::nameKey), session.techniques.map { it.techniqueKey })
        assertEquals(listOf("Zoë", "ZOË").map(::nameKey), session.matchups.map { it.opponentKey })
        // A–Z case still matches, as in the database.
        assertEquals("armbar", nameKey(" ARMBAR "))
    }

    @Test
    fun sessionsWithImpossibleDatesAreLeftOut() {
        val file = backup.copy(
            sessions = listOf(
                sparring.copy(date = "+300000000-01-01", createdAt = 0),
                sparring.copy(date = "1850-06-01"),
                boxing,
            ),
        )
        val plan = BackupImport.plan(file, ExistingData())
        assertEquals(2, plan.invalidSessions)
        assertEquals(listOf(boxing.createdAt), plan.sessions.map { it.createdAt })
    }
}
