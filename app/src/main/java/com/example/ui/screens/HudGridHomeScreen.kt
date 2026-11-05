package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.IdentityEntity
import com.example.data.SearchStage
import com.example.data.SearchTelemetry
import com.example.engine.BackupOperationType
import com.example.engine.BackupProgressState
import com.example.engine.BackupStatus
import com.example.model.HudModule
import com.example.ui.components.DeveloperAttributionCard
import com.example.ui.components.HudChamferedPanel
import com.example.ui.components.IdentityRecordDetailCardDialog
import com.example.ui.components.IdentityRecordHudRow
import com.example.ui.components.MulticolorHudBackdrop
import com.example.ui.components.MulticolorSpectrumBar
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBackgroundDeep
import com.example.ui.theme.HudBackgroundElevated
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudLime
import com.example.ui.theme.HudMagenta
import com.example.ui.theme.HudSurfaceCard
import com.example.ui.theme.HudTextSecondary
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HudGridHomeScreen(
    moduleCounts: Map<String, Long>,
    searchTelemetry: SearchTelemetry,
    selectedFilterModuleId: String?,
    backupState: BackupProgressState,
    suggestedBackupFileName: String,
    onExportBackup: (Uri) -> Unit,
    onFastRestoreBackup: (Uri, Boolean) -> Unit,
    onCancelBackupOperation: () -> Unit,
    onDismissBackupCompleted: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterModuleChange: (String?) -> Unit,
    onClearSearch: () -> Unit,
    onSelectModule: (HudModule) -> Unit,
    onLockHud: () -> Unit
) {
    val totalRecords = moduleCounts.values.sum()
    val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

    var selectedRecord by remember { mutableStateOf<IdentityEntity?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val isSearchActive = searchTelemetry.query.isNotBlank()

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            onExportBackup(uri)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    MulticolorHudBackdrop(accentPrimary = HudCyan, accentSecondary = HudMagenta) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val columns = when {
                maxWidth >= 840.dp -> 4
                maxWidth >= 560.dp -> 3
                else -> 2
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("hud_module_grid")
            ) {
                // Top Hero Header
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HudHeroHeader(
                        totalRecords = totalRecords,
                        cpuCores = cpuCores,
                        onLockHud = onLockHud
                    )
                }

                // Universal Multi-Database Search Panel
                item(span = { GridItemSpan(maxLineSpan) }) {
                    UniversalSearchPanel(
                        searchTelemetry = searchTelemetry,
                        selectedFilterModuleId = selectedFilterModuleId,
                        moduleCounts = moduleCounts,
                        totalRecords = totalRecords,
                        onSearchQueryChange = onSearchQueryChange,
                        onFilterModuleChange = onFilterModuleChange,
                        onClearSearch = onClearSearch
                    )
                }

                // If Universal Search is active: Display matching search results or empty indicator
                if (isSearchActive) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "UNIVERSAL SEARCH RESULTS // ${searchTelemetry.results.size} MATCHES",
                                style = MaterialTheme.typography.titleSmall,
                                color = HudCyan
                            )

                            OutlinedButton(
                                onClick = onClearSearch,
                                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                border = BorderStroke(1.dp, HudAmber),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("return_to_grid_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GridView,
                                    contentDescription = stringResource(R.string.btn_return_to_grid),
                                    tint = HudAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_return_to_grid),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudAmber
                                )
                            }
                        }
                    }

                    if (searchTelemetry.results.isEmpty() && !searchTelemetry.isSearching) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            UniversalSearchEmptyCard(onReset = onClearSearch)
                        }
                    } else {
                        items(
                            items = searchTelemetry.results,
                            key = { it.id },
                            span = { GridItemSpan(maxLineSpan) }
                        ) { record ->
                            val recordModule = HudModule.fromId(record.moduleId)
                            IdentityRecordHudRow(
                                record = record,
                                module = recordModule,
                                accentColor = recordModule.primaryColor,
                                showDatabaseBadge = true,
                                onClick = { activeRecord ->
                                    selectedRecord = activeRecord
                                }
                            )
                        }
                    }
                } else {
                    // Default State: Database Nodes Section Header + 6 Database Cards Grid
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.databases_grid_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = HudCyan
                        )
                    }

                    items(items = HudModule.entries, key = { it.id }) { module ->
                        val count = moduleCounts[module.id] ?: 0L
                        HudModuleCard(
                            module = module,
                            recordCount = count,
                            onClick = { onSelectModule(module) }
                        )
                    }
                }

                // Database Backup & Fast Restore Matrix
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DatabaseBackupRestorePanel(
                        backupState = backupState,
                        totalIndexedRecords = totalRecords,
                        onExportBackupClick = {
                            exportBackupLauncher.launch(suggestedBackupFileName)
                        },
                        onRestoreBackupClick = {
                            restoreBackupLauncher.launch(arrayOf("*/*"))
                        },
                        onAbortClick = onCancelBackupOperation,
                        onDismissClick = onDismissBackupCompleted
                    )
                }

                // Footer Developer Attribution
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 12.dp)
                    ) {
                        DeveloperAttributionCard()
                    }
                }
            }
        }

        // Fast Restore Mode Confirmation Dialog
        pendingRestoreUri?.let { uri ->
            FastRestoreConfirmationDialog(
                uri = uri,
                onConfirm = { cleanRestore ->
                    onFastRestoreBackup(uri, cleanRestore)
                    pendingRestoreUri = null
                },
                onDismiss = { pendingRestoreUri = null }
            )
        }

        // Tap-to-open Record Information Card dialog with Copy, Share, and Jump to Database
        selectedRecord?.let { activeRecord ->
            val recordModule = HudModule.fromId(activeRecord.moduleId)
            IdentityRecordDetailCardDialog(
                record = activeRecord,
                module = recordModule,
                onDismiss = { selectedRecord = null },
                onOpenModule = { targetModule ->
                    selectedRecord = null
                    onSelectModule(targetModule)
                }
            )
        }
    }
}

@Composable
private fun UniversalSearchPanel(
    searchTelemetry: SearchTelemetry,
    selectedFilterModuleId: String?,
    moduleCounts: Map<String, Long>,
    totalRecords: Long,
    onSearchQueryChange: (String) -> Unit,
    onFilterModuleChange: (String?) -> Unit,
    onClearSearch: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var localQuery by rememberSaveable(searchTelemetry.query) {
        mutableStateOf(searchTelemetry.query)
    }

    HudChamferedPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("universal_search_panel"),
        primaryAccent = HudCyan,
        secondaryAccent = HudMagenta
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Title and Node Matrix Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.TravelExplore,
                        contentDescription = "Universal Search",
                        tint = HudCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.universal_search_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = HudCyan
                    )
                }

                Text(
                    text = "6 DATABASE NODES ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudLime
                )
            }

            // Universal Search Input Row
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
                            text = stringResource(R.string.universal_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Universal Search Input",
                            tint = HudCyan
                        )
                    },
                    trailingIcon = {
                        if (localQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    localQuery = ""
                                    onClearSearch()
                                },
                                modifier = Modifier.testTag("clear_universal_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear Universal Search",
                                    tint = HudTextSecondary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearchQueryChange(localQuery)
                            keyboardController?.hide()
                        }
                    ),
                    singleLine = true,
                    colors = hudTextFieldColors(HudCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("universal_search_input")
                )

                Button(
                    onClick = {
                        onSearchQueryChange(localQuery)
                        keyboardController?.hide()
                    },
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudCyan,
                        contentColor = HudBackgroundDeep
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("execute_universal_search_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_execute_search),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Horizontally Scrollable Filter Chips (ALL vs Specific Database)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAllSelected = selectedFilterModuleId == null
                FilterChip(
                    selected = isAllSelected,
                    onClick = { onFilterModuleChange(null) },
                    label = {
                        Text(
                            text = "${stringResource(R.string.universal_filter_all)} (${formatNum(totalRecords)})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HudCyan,
                        selectedLabelColor = HudBackgroundDeep,
                        containerColor = HudSurfaceCard.copy(alpha = 0.7f),
                        labelColor = HudTextSecondary
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isAllSelected) HudCyan else HudCyan.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.testTag("filter_chip_all")
                )

                HudModule.entries.forEach { module ->
                    val isModuleSelected = selectedFilterModuleId == module.id
                    val count = moduleCounts[module.id] ?: 0L
                    FilterChip(
                        selected = isModuleSelected,
                        onClick = {
                            if (isModuleSelected) {
                                onFilterModuleChange(null)
                            } else {
                                onFilterModuleChange(module.id)
                            }
                        },
                        label = {
                            Text(
                                text = "${module.displayLabel} (${formatNum(count)})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = module.primaryColor,
                            selectedLabelColor = HudBackgroundDeep,
                            containerColor = HudSurfaceCard.copy(alpha = 0.7f),
                            labelColor = HudTextSecondary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isModuleSelected) module.primaryColor else module.primaryColor.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.testTag("filter_chip_${module.id}")
                    )
                }
            }

            // Real-Time Search Telemetry & Stage Visualizer Strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                    .background(HudBackgroundDeep.copy(alpha = 0.72f))
                    .padding(10.dp)
                    .testTag("universal_search_visualizer"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UNIVERSAL PIPELINE // ${searchTelemetry.stage.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (searchTelemetry.isSearching) HudAmber else HudLime
                    )
                    Text(
                        text = if (searchTelemetry.query.isBlank()) {
                            "ALL 6 NODES ONLINE"
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
                                .height(5.dp)
                                .background(
                                    when {
                                        searchTelemetry.isSearching && active -> HudAmber
                                        active -> HudLime
                                        else -> HudCyan.copy(alpha = 0.22f)
                                    }
                                )
                        )
                    }
                }

                Text(
                    text = searchTelemetry.executionTrace,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary,
                    modifier = Modifier.testTag("universal_search_trace_text")
                )
            }
        }
    }
}

@Composable
private fun UniversalSearchEmptyCard(onReset: () -> Unit) {
    HudChamferedPanel(
        modifier = Modifier.fillMaxWidth(),
        primaryAccent = HudAmber.copy(alpha = 0.65f),
        secondaryAccent = HudMagenta.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = HudAmber,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.universal_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = HudAmber,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.universal_empty_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = HudTextSecondary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onReset,
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudAmber,
                    contentColor = HudBackgroundDeep
                ),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_return_to_grid),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun HudHeroHeader(
    totalRecords: Long,
    cpuCores: Int,
    onLockHud: () -> Unit
) {
    val bannerShape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
    val formattedTotal = NumberFormat.getNumberInstance(Locale.US).format(totalRecords)

    Surface(
        shape = bannerShape,
        color = HudBackgroundElevated,
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.horizontalGradient(listOf(HudCyan, HudMagenta, HudLime, HudAmber))
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hud_banner_1793736965070),
                contentDescription = "Multicolor HUD Telemetry Banner",
                contentScale = ContentScale.Crop,
                alpha = 0.34f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(178.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(178.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                HudBackgroundDeep.copy(alpha = 0.55f),
                                HudBackgroundDeep.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MulticolorSpectrumBar()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = HudCyan
                        )
                        Text(
                            text = stringResource(R.string.hud_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = HudTextSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = onLockHud,
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        border = BorderStroke(1.dp, HudMagenta),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("lock_hud_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.lock_hud_button),
                            tint = HudMagenta,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.lock_hud_button),
                            style = MaterialTheme.typography.labelMedium,
                            color = HudMagenta
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TelemetryMiniBadge(
                        icon = Icons.Filled.Speed,
                        label = stringResource(R.string.total_records_label),
                        value = formattedTotal,
                        accent = HudLime,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMiniBadge(
                        icon = Icons.Filled.Memory,
                        label = "COMPUTE MATRIX",
                        value = "${cpuCores}C CPU + GPU GLES",
                        accent = HudAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(HudSurfaceCard.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = HudTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HudModuleCard(
    module: HudModule,
    recordCount: Long,
    onClick: () -> Unit
) {
    val shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
    val formattedCount = NumberFormat.getNumberInstance(Locale.US).format(recordCount)

    Surface(
        shape = shape,
        color = HudSurfaceCard.copy(alpha = 0.90f),
        border = BorderStroke(
            width = 1.3.dp,
            brush = Brush.linearGradient(
                colors = listOf(module.primaryColor, module.secondaryColor.copy(alpha = 0.55f))
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag("module_card_${module.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            module.primaryColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                        .background(module.primaryColor.copy(alpha = 0.18f))
                ) {
                    Icon(
                        imageVector = module.icon,
                        contentDescription = stringResource(module.labelRes),
                        tint = module.primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = module.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = module.primaryColor
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(module.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = module.sectorTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ROWS",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary
                )
                Text(
                    text = formattedCount,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (recordCount > 0L) module.primaryColor else HudTextSecondary
                )
            }
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

@Composable
fun DatabaseBackupRestorePanel(
    backupState: BackupProgressState,
    totalIndexedRecords: Long,
    onExportBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onAbortClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HudChamferedPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("database_backup_restore_panel"),
        primaryAccent = HudLime,
        secondaryAccent = HudCyan
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = HudLime,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.backup_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = HudLime
                        )
                        Text(
                            text = stringResource(R.string.backup_section_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = HudTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            MulticolorSpectrumBar(
                modifier = Modifier.fillMaxWidth()
            )

            // Progress / Status Display if Active, Completed, or Error
            when (backupState.status) {
                BackupStatus.RUNNING -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = HudBackgroundElevated,
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        border = BorderStroke(1.dp, HudCyan)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val opTitle = if (backupState.operation == BackupOperationType.EXPORT) {
                                    "EXPORTING TO UNIFIED ARCHIVE // .NEXUSBAK"
                                } else {
                                    "FAST RESTORING DATABASE FROM ARCHIVE"
                                }
                                Text(
                                    text = opTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HudCyan
                                )
                                OutlinedButton(
                                    onClick = onAbortClick,
                                    shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                    border = BorderStroke(1.dp, HudAmber),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp).testTag("abort_backup_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_abort_backup),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HudAmber
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { backupState.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CutCornerShape(2.dp)),
                                color = HudLime,
                                trackColor = HudBackgroundDeep
                            )

                            // Telemetry Readouts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.backup_records_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HudTextSecondary
                                    )
                                    Text(
                                        text = "${NumberFormat.getNumberInstance(Locale.US).format(backupState.processedRecords)}" +
                                            if (backupState.totalRecords > 0) " / ${NumberFormat.getNumberInstance(Locale.US).format(backupState.totalRecords)}" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HudLime
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.backup_speed_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HudTextSecondary
                                    )
                                    Text(
                                        text = "${NumberFormat.getNumberInstance(Locale.US).format(backupState.recordsPerSecond)} REC/S",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HudCyan
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(R.string.backup_elapsed_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HudTextSecondary
                                    )
                                    Text(
                                        text = "${backupState.activeElapsedMs / 1000}s",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HudMagenta
                                    )
                                }
                            }

                            Text(
                                text = "> ${backupState.statusLog}",
                                style = MaterialTheme.typography.bodySmall,
                                color = HudLime,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                BackupStatus.COMPLETED -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = HudBackgroundElevated,
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        border = BorderStroke(1.dp, HudLime)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = HudLime,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = backupState.statusLog,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HudLime
                                    )
                                    if (backupState.recordsPerSecond > 0) {
                                        Text(
                                            text = "AVERAGE THROUGHPUT: ${NumberFormat.getNumberInstance(Locale.US).format(backupState.recordsPerSecond)} REC/SEC",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HudCyan
                                        )
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = onDismissClick,
                                shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                border = BorderStroke(1.dp, HudLime),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_dismiss_backup),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudLime
                                )
                            }
                        }
                    }
                }

                BackupStatus.ERROR -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = HudBackgroundElevated,
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        border = BorderStroke(1.dp, HudAmber)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = HudAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = backupState.statusLog,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HudAmber
                                    )
                                    backupState.errorMessage?.let { err ->
                                        Text(
                                            text = err,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HudTextSecondary
                                        )
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = onDismissClick,
                                shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                border = BorderStroke(1.dp, HudAmber),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_dismiss_backup),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudAmber
                                )
                            }
                        }
                    }
                }

                BackupStatus.CANCELLED -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = HudBackgroundElevated,
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        border = BorderStroke(1.dp, HudAmber)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = backupState.statusLog,
                                style = MaterialTheme.typography.bodySmall,
                                color = HudAmber,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = onDismissClick,
                                shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                border = BorderStroke(1.dp, HudAmber),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_dismiss_backup),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudAmber
                                )
                            }
                        }
                    }
                }

                BackupStatus.IDLE -> { /* Show standard action buttons */ }
            }

            // Dual Action Buttons: EXPORT ALL BACKUP & FAST RESTORE BACKUP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Export Button
                Button(
                    onClick = onExportBackupClick,
                    enabled = backupState.status != BackupStatus.RUNNING,
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudCyan.copy(alpha = 0.15f),
                        contentColor = HudCyan
                    ),
                    border = BorderStroke(1.dp, HudCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_all_backup_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = null,
                            tint = HudCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = stringResource(R.string.btn_export_all_backup),
                                style = MaterialTheme.typography.labelMedium,
                                color = HudCyan
                            )
                            Text(
                                text = "Export $totalIndexedRecords records to .nexusbak",
                                style = MaterialTheme.typography.labelSmall,
                                color = HudTextSecondary
                            )
                        }
                    }
                }

                // Restore Button
                Button(
                    onClick = onRestoreBackupClick,
                    enabled = backupState.status != BackupStatus.RUNNING,
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudLime.copy(alpha = 0.15f),
                        contentColor = HudLime
                    ),
                    border = BorderStroke(1.dp, HudLime),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("fast_restore_backup_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = HudLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = stringResource(R.string.btn_fast_restore_backup),
                                style = MaterialTheme.typography.labelMedium,
                                color = HudLime
                            )
                            Text(
                                text = "High-speed WAL stream into Room",
                                style = MaterialTheme.typography.labelSmall,
                                color = HudTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FastRestoreConfirmationDialog(
    uri: Uri,
    onConfirm: (cleanRestore: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = HudLime
                )
                Text(
                    text = stringResource(R.string.restore_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = HudLime
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.restore_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Surface(
                    color = HudBackgroundElevated,
                    shape = CutCornerShape(4.dp),
                    border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "TARGET FILE: ${uri.lastPathSegment ?: uri.path ?: "Selected Archive"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HudCyan,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onConfirm(true) },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudAmber.copy(alpha = 0.2f),
                        contentColor = HudAmber
                    ),
                    border = BorderStroke(1.dp, HudAmber),
                    modifier = Modifier.fillMaxWidth().testTag("clean_restore_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_restore_clean),
                        style = MaterialTheme.typography.labelMedium,
                        color = HudAmber
                    )
                }

                Button(
                    onClick = { onConfirm(false) },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudLime.copy(alpha = 0.2f),
                        contentColor = HudLime
                    ),
                    border = BorderStroke(1.dp, HudLime),
                    modifier = Modifier.fillMaxWidth().testTag("merge_restore_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_restore_merge),
                        style = MaterialTheme.typography.labelMedium,
                        color = HudLime
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                border = BorderStroke(1.dp, HudTextSecondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.btn_dialog_cancel),
                    style = MaterialTheme.typography.labelMedium,
                    color = HudTextSecondary
                )
            }
        },
        containerColor = HudSurfaceCard,
        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    )
}
