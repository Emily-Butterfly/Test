package com.dojolog.ui.backup

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.database.SQLException
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dojolog.BuildConfig
import com.dojolog.data.ImportResult
import com.dojolog.data.TrainingRepository
import com.dojolog.data.backup.BackupCodec
import com.dojolog.data.backup.BackupException
import com.dojolog.data.backup.BackupFile
import com.dojolog.ui.Fmt
import com.dojolog.ui.repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Writes exports to and reads imports from files the user picks with the system file picker. */
class BackupViewModel(
    private val repository: TrainingRepository,
    private val resolver: ContentResolver,
    private val prefs: SharedPreferences,
) : ViewModel() {
    private class Pending(val backup: BackupFile, val preview: PendingImport)

    private val task = MutableStateFlow<BackupTask?>(null)
    private val pending = MutableStateFlow<Pending?>(null)
    private val message = MutableStateFlow<BackupMessage?>(null)
    private val lastExport = MutableStateFlow(
        prefs.getLong(KEY_LAST_EXPORT, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay),
    )

    private val counts = combine(
        repository.observeSessions(),
        repository.observeTechniques(),
        repository.observeOpponents(),
    ) { sessions, techniques, opponents -> DataCounts(sessions.size, techniques.size, opponents.size) }

    val state: StateFlow<BackupUiState> =
        combine(counts, lastExport, task, pending, message) { counts, lastExport, task, pending, message ->
            BackupUiState(counts, lastExport, task, pending?.preview, message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    /** Writes everything to [uri]. Finishes even if the screen is left meanwhile. */
    fun export(uri: Uri) {
        if (task.value != null) return
        task.value = BackupTask.EXPORTING
        message.value = null
        viewModelScope.launch {
            withContext(NonCancellable) {
                message.value = try {
                    val backup = repository.exportBackup(BuildConfig.VERSION_NAME)
                    val bytes = withContext(Dispatchers.Default) { BackupCodec.encode(backup).encodeToByteArray() }
                    withContext(Dispatchers.IO) { openForWriting(uri).use { it.write(bytes) } }
                    val today = LocalDate.now()
                    prefs.edit { putLong(KEY_LAST_EXPORT, today.toEpochDay()) }
                    lastExport.value = today
                    BackupMessage(
                        "Exported ${Fmt.count(backup.sessions.size, "session")}, " +
                            "${Fmt.count(backup.techniques.size, "technique")} and " +
                            "${Fmt.count(backup.opponents.size, "opponent")}. Keep the file somewhere safe.",
                    )
                } catch (e: IOException) {
                    BackupMessage("The file couldn't be saved. Try again or pick another place.", isError = true)
                } catch (e: SecurityException) {
                    BackupMessage("The file couldn't be saved. Try again or pick another place.", isError = true)
                } finally {
                    task.value = null
                }
            }
        }
    }

    /** Reads and checks the file at [uri], then asks for confirmation (see [confirmImport]). */
    fun read(uri: Uri) {
        if (task.value != null) return
        task.value = BackupTask.READING
        message.value = null
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readText(uri) }
                val backup = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
                val plan = repository.previewImport(backup)
                pending.value = Pending(
                    backup = backup,
                    preview = PendingImport(
                        exportedOn = runCatching {
                            Instant.parse(backup.exportedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        }.getOrNull(),
                        sessions = backup.sessions.size,
                        newSessions = plan.sessions.size,
                        techniques = backup.techniques.size,
                        opponents = backup.opponents.size,
                        problems = plan.invalidSessions + plan.droppedReferences,
                    ),
                )
            } catch (e: BackupException) {
                message.value = BackupMessage(e.message ?: "This file can't be imported.", isError = true)
            } catch (e: IOException) {
                message.value = BackupMessage("The file couldn't be opened. Try picking it again.", isError = true)
            } catch (e: SecurityException) {
                message.value = BackupMessage("The file couldn't be opened. Try picking it again.", isError = true)
            } finally {
                task.value = null
            }
        }
    }

    /** Imports the file read last; with [replace], deletes everything in the app first. */
    fun confirmImport(replace: Boolean) {
        val current = pending.value ?: return
        if (task.value != null) return
        pending.value = null
        task.value = BackupTask.IMPORTING
        viewModelScope.launch {
            withContext(NonCancellable) {
                message.value = try {
                    BackupMessage(summary(repository.importBackup(current.backup, replace), replace))
                } catch (e: SQLException) {
                    BackupMessage("The import failed, so nothing was changed.", isError = true)
                } catch (e: IllegalStateException) {
                    BackupMessage("The import failed, so nothing was changed.", isError = true)
                } finally {
                    task.value = null
                }
            }
        }
    }

    fun cancelImport() {
        pending.value = null
    }

    fun dismissMessage() {
        message.value = null
    }

    fun showError(text: String) {
        message.value = BackupMessage(text, isError = true)
    }

    private fun openForWriting(uri: Uri): OutputStream {
        // "wt" truncates an existing file; not every provider supports it, so fall back to "w".
        val truncating = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        return truncating ?: resolver.openOutputStream(uri, "w") ?: throw IOException("No stream for $uri")
    }

    private fun readText(uri: Uri): String {
        val input = resolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_FILE_BYTES) throw BackupException("This file is too big to be a Dojo Log backup.")
            }
            out.toByteArray()
        }
        // A text editor may have added a byte order mark.
        return bytes.decodeToString().removePrefix("\uFEFF")
    }

    private fun summary(result: ImportResult, replace: Boolean): String {
        val added = "${Fmt.count(result.sessionsAdded, "session")}, ${Fmt.count(result.techniquesAdded, "technique")} " +
            "and ${Fmt.count(result.opponentsAdded, "opponent")}"
        return buildString {
            append(if (replace) "Your data was replaced by the backup: $added." else "Imported $added.")
            if (result.sessionsSkipped > 0) {
                append(" ${Fmt.count(result.sessionsSkipped, "session")} ")
                append(if (result.sessionsSkipped == 1) "was" else "were")
                append(" already here and skipped.")
            }
            if (result.problems > 0) append(" ${Fmt.count(result.problems, "record")} couldn't be read and were left out.")
        }
    }

    companion object {
        private const val KEY_LAST_EXPORT = "last_export_day"
        private const val MAX_FILE_BYTES = 64 * 1024 * 1024

        /** Suggested name for an export made on [date]. */
        fun fileName(date: LocalDate): String = "dojo-log-$date.json"

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                BackupViewModel(repository, app.contentResolver, app.getSharedPreferences("backup", Context.MODE_PRIVATE))
            }
        }
    }
}
