package com.dojolog.ui.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

/** Export everything to a file the user picks, or import such a file (e.g. on a new install). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.read(uri)
    }
    val noPicker = "No file manager was found to pick the file with."

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export & import") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        BackupContent(
            state = state,
            onExport = {
                try {
                    exportLauncher.launch(BackupViewModel.fileName(LocalDate.now()))
                } catch (e: ActivityNotFoundException) {
                    viewModel.showError(noPicker)
                }
            },
            onImport = {
                // Any type: depending on where it is stored, a .json file may not be labelled as JSON.
                try {
                    importLauncher.launch(arrayOf("*/*"))
                } catch (e: ActivityNotFoundException) {
                    viewModel.showError(noPicker)
                }
            },
            onConfirmImport = viewModel::confirmImport,
            onCancelImport = viewModel::cancelImport,
            onDismissMessage = viewModel::dismissMessage,
            contentPadding = padding,
        )
    }
}
