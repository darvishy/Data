package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBackgroundDeep
import com.example.ui.theme.HudBackgroundElevated
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudLime
import com.example.ui.theme.HudMagenta
import com.example.ui.theme.HudSurfaceCard
import com.example.ui.theme.HudTeal
import com.example.ui.theme.HudViolet

@Composable
fun MulticolorHudBackdrop(
    modifier: Modifier = Modifier,
    accentPrimary: Color = HudCyan,
    accentSecondary: Color = HudMagenta,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hudBackdrop")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HudBackgroundDeep)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle radial neon glows
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentPrimary.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(w * 0.15f, h * 0.12f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.15f, h * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentSecondary.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(w * 0.88f, h * 0.78f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.88f, h * 0.78f)
            )

            // HUD Coordinate Grid
            val gridStep = 44.dp.toPx()
            val gridColor = accentPrimary.copy(alpha = 0.045f)
            var x = 0f
            while (x <= w) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                x += gridStep
            }
            var y = 0f
            while (y <= h) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }

            // Moving multicolor laser scanline
            val scanY = h * scanProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        HudCyan.copy(alpha = 0.35f),
                        HudMagenta.copy(alpha = 0.35f),
                        HudLime.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, scanY),
                end = Offset(w, scanY),
                strokeWidth = 2.dp.toPx()
            )
        }
        content()
    }
}

@Composable
fun HudChamferedPanel(
    modifier: Modifier = Modifier,
    primaryAccent: Color = HudCyan,
    secondaryAccent: Color = HudMagenta,
    containerColor: Color = HudSurfaceCard.copy(alpha = 0.88f),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            primaryAccent.copy(alpha = 0.85f),
            secondaryAccent.copy(alpha = 0.55f),
            primaryAccent.copy(alpha = 0.35f)
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(width = 1.2.dp, brush = borderBrush, shape = shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
fun MulticolorSpectrumBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        HudCyan,
                        HudMagenta,
                        HudAmber,
                        HudLime,
                        HudViolet,
                        HudTeal
                    )
                )
            )
    )
}

@Composable
fun DeveloperAttributionCard(
    modifier: Modifier = Modifier,
    showTelegramJoinButton: Boolean = false
) {
    val context = LocalContext.current
    val telegramUrl = stringResource(R.string.telegram_url)
    val shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("developer_attribution_card"),
        shape = shape,
        color = HudBackgroundElevated.copy(alpha = 0.94f),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(listOf(HudCyan, HudMagenta, HudLime, HudAmber))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Terminal,
                    contentDescription = "Developer Credit Icon",
                    tint = HudCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DEVELOPER SIGNATURE // NULLEMPEROR",
                    style = MaterialTheme.typography.labelMedium,
                    color = HudAmber
                )
            }

            Text(
                text = stringResource(R.string.developer_credit),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("developer_credit_text")
            )

            if (showTelegramJoinButton) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Safe no-op if no browser installed in minimal container
                        }
                    },
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudTeal,
                        contentColor = HudBackgroundDeep
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("join_telegram_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.join_telegram),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.join_telegram),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedBiometricReticle(
    modifier: Modifier = Modifier,
    primaryColor: Color = HudCyan,
    secondaryColor: Color = HudMagenta
) {
    val transition = rememberInfiniteTransition(label = "reticle")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reticleAngle"
    )

    Canvas(modifier = modifier.size(124.dp)) {
        val radius = size.minDimension / 2f
        drawCircle(
            color = primaryColor.copy(alpha = 0.16f),
            radius = radius * 0.95f,
            style = Stroke(width = 2.dp.toPx())
        )
        drawArc(
            color = primaryColor,
            startAngle = angle,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = secondaryColor,
            startAngle = angle + 180f,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = HudLime.copy(alpha = 0.25f),
            radius = radius * 0.72f,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
