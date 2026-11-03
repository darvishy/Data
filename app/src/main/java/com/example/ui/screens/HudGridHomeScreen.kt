package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HudModule
import com.example.ui.components.DeveloperAttributionCard
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
    onSelectModule: (HudModule) -> Unit,
    onLockHud: () -> Unit
) {
    val totalRecords = moduleCounts.values.sum()
    val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HudHeroHeader(
                        totalRecords = totalRecords,
                        cpuCores = cpuCores,
                        onLockHud = onLockHud
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
