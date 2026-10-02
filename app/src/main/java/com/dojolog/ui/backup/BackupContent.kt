package com.dojolog.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.SectionCard
import com.dojolog.ui.theme.DojoColors
import java.time.LocalDate

/** How much the app holds. */
data class DataCounts(val sessions: Int, val techniques: Int, val opponents: Int) {
    val isEmpty: Boolean get() = sessions == 0 && techniques == 0 && opponents == 0
}

enum class BackupTask(val label: String) {
    EXPORTING("Exporting…"),
    READING("Reading the file…"),
    IMPORTING("Importing…"),
}

/** A file that was read and checked, waiting for the user to confirm the import. */
data class PendingImport(
    val exportedOn: LocalDate?,
    val sessions: Int,
    /** Sessions in the file that the app doesn't have yet. */
    val newSessions: Int,
    val techniques: Int,
    val opponents: Int,
    /** Records in the file that can't be read and will be left out. */
    val problems: Int,
)

/** The outcome of the last export or import, shown until dismissed. */
data class BackupMessage(val text: String, val isError: Boolean = false)

data class BackupUiState(
    val counts: DataCounts? = null,
    val lastExport: LocalDate? = null,
    val task: BackupTask? = null,
    val pending: PendingImport? = null,
    val message: BackupMessage? = null,
)

/** The export and import screen's content, free of Android file handling so it can be previewed. */
@Composable
fun BackupContent(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: (replace: Boolean) -> Unit,
    onCancelImport: () -> Unit,
    onDismissMessage: () -> Unit,
    contentPadding: PaddingValues,
) {
    val busy = state.task != null
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Everything stays on this phone. Export it to a file to keep a copy, move it to a new phone, " +
                "or bring it back after reinstalling the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = DojoColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (state.task != null) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(state.task.label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        state.message?.let { MessageCard(it, onDismissMessage) }

        SectionCard(title = "Export", subtitle = state.counts?.let { describe(it) }) {
            Text(
                "Saves your sessions, ratings, techniques, opponents and colours to one file. Keep it somewhere " +
                    "safe, such as Google Drive or your Downloads folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = DojoColors.TextSecondary,
            )
            Text(
                state.lastExport?.let { "Last exported ${Fmt.mediumDate(it)}" } ?: "Not exported yet",
                style = MaterialTheme.typography.bodySmall,
                color = DojoColors.TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onExport, enabled = !busy && state.counts?.isEmpty == false, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export to a file")
            }
        }

        SectionCard(title = "Import") {
            Text(
                "Loads a file exported from Dojo Log. What's in the file is added to the app; sessions you " +
                    "already have are skipped, so importing the same file twice is safe.",
                style = MaterialTheme.typography.bodyMedium,
                color = DojoColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose a backup file")
            }
        }
    }

    state.pending?.let { pending ->
        ImportDialog(
            pending = pending,
            current = state.counts,
            onConfirm = onConfirmImport,
            onCancel = onCancelImport,
        )
    }
}

@Composable
private fun MessageCard(message: BackupMessage, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (message.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.TaskAlt,
                contentDescription = null,
                tint = if (message.isError) MaterialTheme.colorScheme.error else DojoColors.TextPrimary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("OK") }
            }
        }
    }
}

/** Confirms an import; when the app already holds data, also offers to replace it (asked twice). */
@Composable
private fun ImportDialog(
    pending: PendingImport,
    current: DataCounts?,
    onConfirm: (replace: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val hasData = current != null && !current.isEmpty
    if (confirmReplace && current != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace all your data?") },
            text = {
                Text(
                    "This deletes the ${describe(current)} in the app and puts the backup in their place. " +
                        "It can't be undone.",
                )
            },
            confirmButton = { TextButton(onClick = { onConfirm(true) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Back") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (pending.exportedOn?.let { "Exported ${Fmt.mediumDate(it)}: " } ?: "") +
                        describe(DataCounts(pending.sessions, pending.techniques, pending.opponents)) + ".",
                )
                if (hasData) {
                    val skipped = pending.sessions - pending.newSessions
                    Text(
                        when {
                            pending.newSessions == 0 -> "You already have every session in it."
                            skipped > 0 -> "${Fmt.count(pending.newSessions, "session")} will be added; " +
                                "$skipped ${if (skipped == 1) "is" else "are"} already in the app and will be skipped."
                            else -> "All ${Fmt.count(pending.newSessions, "session")} will be added to yours."
                        },
                    )
                }
                if (pending.problems > 0) {
                    Text(
                        "${Fmt.count(pending.problems, "record")} in the file can't be read and will be left out.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (hasData) TextButton(onClick = { confirmReplace = true }) { Text("Replace…") }
                TextButton(onClick = { onConfirm(false) }) { Text(if (hasData) "Add" else "Import") }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** "34 sessions, 12 techniques and 5 opponents" */
private fun describe(counts: DataCounts): String =
    "${Fmt.count(counts.sessions, "session")}, ${Fmt.count(counts.techniques, "technique")} and " +
        Fmt.count(counts.opponents, "opponent")
