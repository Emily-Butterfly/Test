package com.dojolog.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Everything the app stores, as written to an export file. The format is versioned so a
 * newer app can still read older files; references between records use the ids from the
 * exporting app, which an import maps to fresh ones.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** ISO-8601 instant. */
    val exportedAt: String = "",
    val appVersion: String = "",
    val techniques: List<BackupTechnique> = emptyList(),
    val opponents: List<BackupOpponent> = emptyList(),
    /** Oldest first. */
    val sessions: List<BackupSession> = emptyList(),
    /** Colour slot per martial art (by its lower-case name), so arts keep their colours. */
    val colors: Map<String, Int> = emptyMap(),
) {
    companion object {
        const val FORMAT = "dojo-log-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupTechnique(
    val id: Long,
    val name: String,
    val category: String = "other",
    val notes: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class BackupOpponent(
    val id: Long,
    val name: String,
    val club: String = "",
    val grade: String = "",
    val weight: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class BackupSession(
    /** ISO-8601 date, e.g. 2026-10-02. */
    val date: String,
    val durationMinutes: Int,
    val discipline: String = "",
    val type: String = "class",
    val notes: String = "",
    val overall: Float = 0f,
    val overallAuto: Boolean = true,
    val ratings: BackupRatings = BackupRatings(),
    val techniques: List<BackupTechniqueEntry> = emptyList(),
    val matchups: List<BackupMatchup> = emptyList(),
    /** When the session was first logged (epoch millis); identifies it across devices. */
    val createdAt: Long = 0,
)

@Serializable
data class BackupRatings(
    val technique: Int = 0,
    val conditioning: Int = 0,
    val sparring: Int = 0,
    val focus: Int = 0,
    val effort: Int = 0,
)

@Serializable
data class BackupTechniqueEntry(
    val techniqueId: Long,
    val reps: Int = 0,
    val quality: Int = 0,
    val notes: String = "",
)

@Serializable
data class BackupMatchup(
    val opponentId: Long,
    val result: String = "none",
    val rating: Int = 0,
    val notes: String = "",
)

/** A file that can't be imported; [message] says why, in words for the user. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    /**
     * Largest file an import reads: room for tens of thousands of sessions, while a wrong
     * file (a video, say) is turned down before it fills the memory.
     */
    const val MAX_FILE_BYTES = 32 * 1024 * 1024

    const val NOT_A_BACKUP = "This file isn't a Dojo Log backup."

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val compactJson = Json(from = json) { prettyPrint = false }

    /**
     * Indented so a person can read it; a very large log is written without indentation so
     * its file stays well inside [MAX_FILE_BYTES].
     */
    fun encode(backup: BackupFile): String {
        val pretty = json.encodeToString(BackupFile.serializer(), backup)
        return if (pretty.length > MAX_FILE_BYTES / 4) compactJson.encodeToString(BackupFile.serializer(), backup) else pretty
    }

    /** Reads an export file, or throws [BackupException] when it isn't one this app can read. */
    fun decode(text: String): BackupFile {
        val root: JsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: SerializationException) {
            throw BackupException(NOT_A_BACKUP, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(NOT_A_BACKUP, e)
        }
        val format = (root["format"] as? JsonPrimitive)?.contentOrNull
        if (format != BackupFile.FORMAT) throw BackupException(NOT_A_BACKUP)
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: throw BackupException(NOT_A_BACKUP)
        if (version > BackupFile.VERSION) {
            throw BackupException("This backup was made by a newer version of Dojo Log. Update the app to import it.")
        }
        return try {
            json.decodeFromJsonElement(BackupFile.serializer(), root)
        } catch (e: SerializationException) {
            throw BackupException("This backup file is damaged and can't be read.", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("This backup file is damaged and can't be read.", e)
        }
    }
}
