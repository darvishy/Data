package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.IdentityEntity
import com.example.data.SearchStage
import com.example.data.SearchTelemetry
import com.example.engine.ImportProgressState
import com.example.engine.ImportStatus
import com.example.model.HudModule
import com.example.ui.components.DeveloperAttributionCard
import com.example.ui.components.HudChamferedPanel
import com.example.ui.components.IdentityRecordDetailCardDialog
import com.example.ui.components.IdentityRecordHudRow
import com.example.ui.components.MulticolorHudBackdrop
import com.example.ui.components.MulticolorSpectrumBar
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBackgroundDeep
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudError
import com.example.ui.theme.HudLime
import com.example.ui.theme.HudMagenta
import com.example.ui.theme.HudTextSecondary

@Composable
fun ModuleDetailScreen(
    module: HudModule,
    moduleRecordCount: Long,
    importState: ImportProgressState,
    searchTelemetry: SearchTelemetry,
    onBack: () -> Unit,
    onImportCsvUri: (Uri) -> Unit,
    onRunBenchmarkStream: (Int) -> Unit,
    onPauseImport: () -> Unit,
    onResumeImport: () -> Unit,
    onCancelImport: () -> Unit,
    onClearModule: () -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedRecord by remember(module.id) { mutableStateOf<IdentityEntity?>(null) }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportCsvUri(uri)
        }
    }

    val isThisModuleImporting = importState.activeModuleId == module.id &&
        (importState.status == ImportStatus.RUNNING || importState.status == ImportStatus.PAUSED)

    MulticolorHudBackdrop(
        accentPrimary = module.primaryColor,
        accentSecondary = module.secondaryColor
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("module_detail_screen")
        ) {
            // Top Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MulticolorSpectrumBar()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("back_to_grid_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back_to_grid),
                                    tint = module.primaryColor
                                )
                            }
                            Icon(
                                imageVector = module.icon,
                                contentDescription = stringResource(module.labelRes),
                                tint = module.primaryColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(module.labelRes),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = module.primaryColor
                                )
                                Text(
                                    text = "${module.sectorTag} // ${formatNum(moduleRecordCount)} RECORDS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudTextSecondary
                                )
                            }
                        }

                        if (moduleRecordCount > 0L && !isThisModuleImporting) {
                            IconButton(
                                onClick = onClearModule,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("purge_module_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = stringResource(R.string.btn_clear_node),
                                    tint = HudError
                                )
                            }
                        }
                    }
                }
            }

            // Dedicated CSV Import & CPU+GPU Engine Telemetry Panel
            item {
                CsvImportTelemetryPanel(
                    module = module,
                    importState = importState,
                    onPickCsv = {
                        csvPickerLauncher.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    onRunBenchmark = { onRunBenchmarkStream(100_000) },
                    onPause = onPauseImport,
                    onResume = onResumeImport,
                    onCancel = onCancelImport
                )
            }

            // High-Speed FTS4 Search Bar & Live Search Process Telemetry
            item {
                VisibleSearchPipelinePanel(
                    module = module,
                    searchTelemetry = searchTelemetry,
                    onSearchQueryChange = onSearchQueryChange
                )
            }

            // Search / Indexed Identity Records List
            if (searchTelemetry.results.isEmpty()) {
                item {
                    EmptyModuleRecordsCard(
                        module = module,
                        hasActiveQuery = searchTelemetry.query.isNotBlank()
                    )
                }
            } else {
                items(
                    items = searchTelemetry.results,
                    key = { it.id }
                ) { record ->
                    IdentityRecordHudRow(
                        record = record,
                        module = module,
                        accentColor = module.primaryColor,
                        onClick = { clickedRecord ->
                            selectedRecord = clickedRecord
                        }
                    )
                }
            }

            // Footer Developer Attribution
            item {
                Spacer(modifier = Modifier.height(4.dp))
                DeveloperAttributionCard()
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        selectedRecord?.let { activeRecord ->
            IdentityRecordDetailCardDialog(
                record = activeRecord,
                module = module,
                onDismiss = { selectedRecord = null }
            )
        }
    }
}

@Composable
private fun CsvImportTelemetryPanel(
    module: HudModule,
    importState: ImportProgressState,
    onPickCsv: () -> Unit,
    onRunBenchmark: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val showTelemetryForModule =
        importState.activeModuleId == null || importState.activeModuleId == module.id
    val isRunning = importState.activeModuleId == module.id && importState.status == ImportStatus.RUNNING
    val isPaused = importState.activeModuleId == module.id && importState.status == ImportStatus.PAUSED

    HudChamferedPanel(
        modifier = Modifier.fillMaxWidth(),
        primaryAccent = module.primaryColor,
        secondaryAccent = HudLime
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Memory,
                        contentDescription = "CPU GPU Engine",
                        tint = module.primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CPU (${importState.cpuWorkerCount}T) + GPU GLES INGESTION ENGINE",
                        style = MaterialTheme.typography.labelMedium,
                        color = module.primaryColor
                    )
                }

                val badgeColor = when {
                    isRunning -> HudLime
                    isPaused -> HudAmber
                    else -> HudCyan
                }
                Text(
                    text = if (importState.activeModuleId == module.id) importState.status.name else "READY",
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor
                )
            }

            // Expected CSV Schema Specification Strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                    .background(HudBackgroundDeep.copy(alpha = 0.65f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "CSV FORMAT SPECIFICATION // ${stringResource(module.labelRes).uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudAmber
                )
                Text(
                    text = module.expectedCsvSchema,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudCyan,
                    modifier = Modifier.testTag("csv_schema_spec_text")
                )
            }

            // Dedicated CSV Import Button + Optional High-Speed Stream Benchmark Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickCsv,
                    enabled = !isRunning && !isPaused,
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = module.primaryColor,
                        contentColor = HudBackgroundDeep
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1.45f)
                        .height(50.dp)
                        .testTag("import_csv_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = stringResource(R.string.btn_import_csv),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_import_csv),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onRunBenchmark,
                    enabled = !isRunning && !isPaused,
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    border = BorderStroke(1.dp, HudAmber),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("benchmark_stream_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "100K CSV Stream",
                        tint = HudAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "100K STREAM",
                        style = MaterialTheme.typography.labelMedium,
                        color = HudAmber,
                        maxLines = 1
                    )
                }
            }

            // Pause / Resume / Abort Controls when active
            AnimatedVisibility(visible = isRunning || isPaused) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isRunning) {
                        Button(
                            onClick = onPause,
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HudAmber,
                                contentColor = HudBackgroundDeep
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pause_import_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.btn_pause_import)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_pause_import),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else if (isPaused) {
                        Button(
                            onClick = onResume,
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HudLime,
                                contentColor = HudBackgroundDeep
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("resume_import_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.btn_resume_import)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_resume_import),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        border = BorderStroke(1.dp, HudError),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.btn_cancel_import),
                            tint = HudError
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_cancel_import),
                            style = MaterialTheme.typography.labelLarge,
                            color = HudError
                        )
                    }
                }
            }

            if (showTelemetryForModule && importState.activeModuleId == module.id) {
                val pct = (importState.progressFraction * 100f).toInt().coerceIn(0, 100)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PROGRESS: $pct% (${formatNum(importState.processedRecords)} ROWS)",
                            style = MaterialTheme.typography.labelMedium,
                            color = HudCyan,
                            modifier = Modifier.testTag("import_progress_text")
                        )
                        Text(
                            text = "${formatNum(importState.recordsPerSecond)} rec/s • ${"%.1f".format(importState.mbPerSecond)} MB/s",
                            style = MaterialTheme.typography.labelMedium,
                            color = HudLime,
                            modifier = Modifier.testTag("import_speed_text")
                        )
                    }

                    LinearProgressIndicator(
                        progress = { importState.progressFraction.coerceIn(0f, 1f) },
                        color = module.primaryColor,
                        trackColor = module.primaryColor.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CutCornerShape(4.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AcceleratorLoadMeter(
                            title = "CPU ${importState.cpuWorkerCount}-CORE POOL",
                            percent = importState.cpuLoadPercent,
                            color = HudCyan,
                            modifier = Modifier.weight(1f)
                        )
                        AcceleratorLoadMeter(
                            title = "GPU GLES SHADER",
                            percent = importState.gpuLoadPercent,
                            color = HudMagenta,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = importState.statusLog,
                        style = MaterialTheme.typography.labelSmall,
                        color = HudTextSecondary
                    )
                }
            } else {
                Text(
                    text = "MULTI-THREADED PIPELINE STANDBY // PARALLEL CPU CORES + OPENGL ES PBUFFER COMPUTE",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary
                )
            }
        }
    }
}

@Composable
private fun AcceleratorLoadMeter(
    title: String,
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
            .background(HudBackgroundDeep.copy(alpha = 0.65f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = HudTextSecondary)
            Text(text = "$percent%", style = MaterialTheme.typography.labelSmall, color = color)
        }
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        )
    }
}

@Composable
private fun VisibleSearchPipelinePanel(
    module: HudModule,
    searchTelemetry: SearchTelemetry,
    onSearchQueryChange: (String) -> Unit
) {
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var localQuery by androidx.compose.runtime.saveable.rememberSaveable(module.id) {
        androidx.compose.runtime.mutableStateOf(searchTelemetry.query)
    }

    HudChamferedPanel(
        modifier = Modifier.fillMaxWidth(),
        primaryAccent = HudCyan,
        secondaryAccent = module.primaryColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = localQuery,
                    onValueChange = { updated ->
                        localQuery = updated
                        onSearchQueryChange(updated)
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search Records",
                            tint = module.primaryColor
                        )
                    },
                    trailingIcon = {
                        if (localQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    localQuery = ""
                                    onSearchQueryChange("")
                                },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear Search Query",
                                    tint = HudTextSecondary
                                )
                            }
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            onSearchQueryChange(localQuery)
                            keyboardController?.hide()
                        }
                    ),
                    singleLine = true,
                    colors = hudTextFieldColors(module.primaryColor),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("identity_search_input")
                )

                Button(
                    onClick = {
                        onSearchQueryChange(localQuery)
                        keyboardController?.hide()
                    },
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = module.primaryColor,
                        contentColor = HudBackgroundDeep
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("execute_search_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_execute_search),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Visible Search Execution Pipeline Bar & Stage Indicators
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                    .background(HudBackgroundDeep.copy(alpha = 0.72f))
                    .padding(10.dp)
                    .testTag("search_process_visualizer"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SEARCH PIPELINE // ${searchTelemetry.stage.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (searchTelemetry.isSearching) HudAmber else HudLime
                    )
                    Text(
                        text = if (searchTelemetry.query.isBlank()) {
                            "PARTITIONS READY [8/8]"
                        } else {
                            "LATENCY: ${"%.2f".format(searchTelemetry.elapsedMillis)} ms • HITS: ${formatNum(searchTelemetry.matchCount)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = HudCyan
                    )
                }

                // 8-Partition Visual Scanner Blocks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (idx in 1..searchTelemetry.totalPartitions) {
                        val active = when {
                            searchTelemetry.stage == SearchStage.IDLE -> false
                            idx <= searchTelemetry.scannedPartitions -> true
                            else -> false
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    when {
                                        searchTelemetry.isSearching && active -> HudAmber
                                        active -> HudLime
                                        else -> module.primaryColor.copy(alpha = 0.22f)
                                    }
                                )
                        )
                    }
                }

                Text(
                    text = searchTelemetry.executionTrace,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary,
                    modifier = Modifier.testTag("search_trace_text")
                )
            }
        }
    }
}



@Composable
private fun EmptyModuleRecordsCard(
    module: HudModule,
    hasActiveQuery: Boolean
) {
    HudChamferedPanel(
        modifier = Modifier.fillMaxWidth(),
        primaryAccent = module.primaryColor.copy(alpha = 0.55f),
        secondaryAccent = HudMagenta.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = module.icon,
                contentDescription = null,
                tint = module.primaryColor,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (hasActiveQuery) "ZERO RECORDS MATCHED QUERY" else stringResource(R.string.empty_node_title),
                style = MaterialTheme.typography.titleMedium,
                color = module.primaryColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasActiveQuery) {
                    "Try searching by prefix, name, national code, phone number, or account handle."
                } else {
                    stringResource(R.string.empty_node_tip)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = HudTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatNum(value: Long): String {
    val raw = value.toString()
    val start = if (value < 0L) 1 else 0
    val digits = raw.length - start
    if (digits <= 3) return raw
    val sb = StringBuilder(raw.length + (digits - 1) / 3)
    if (start == 1) sb.append('-')
    for (i in start until raw.length) {
        val posFromStart = i - start
        if (posFromStart > 0 && (digits - posFromStart) % 3 == 0) {
            sb.append(',')
        }
        sb.append(raw[i])
    }
    return sb.toString()
}
