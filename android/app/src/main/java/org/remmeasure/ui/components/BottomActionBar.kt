package org.remmeasure.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.remmeasure.ui.theme.ReticleLocked

@Composable
fun BottomActionBar(
    isMeasuring: Boolean,
    isSurfaceDetected: Boolean,
    onPrimaryAction: () -> Unit,
    onCancelMeasurement: () -> Unit,
    onCaptureSnapshot: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // "Start a new measurement" Tooltip bubble with downward arrow
        AnimatedVisibility(
            visible = !isMeasuring && isSurfaceDetected,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(TooltipBubbleShape())
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .padding(bottom = 6.dp), // extra padding for downward arrow
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start a new measurement",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons Row (Cancel, +, Camera)
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel Button (appears when actively measuring)
            AnimatedVisibility(
                visible = isMeasuring,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                val cancelBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                val cancelTint = MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(cancelBg)
                        .clickable(onClick = onCancelMeasurement),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel measurement",
                        tint = cancelTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Primary Measure Action Button (+) or (✓)
            val primaryColor = Color.White
            val buttonColor = when {
                isMeasuring -> Color.White
                isSurfaceDetected -> primaryColor
                else -> primaryColor.copy(alpha = 0.5f)
            }
            val iconTint = Color.Black

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .clickable(
                        enabled = isSurfaceDetected || isMeasuring,
                        onClick = onPrimaryAction
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMeasuring) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (isMeasuring) "Lock end point" else "Start measurement",
                    tint = iconTint,
                    modifier = Modifier.size(38.dp)
                )
            }

            // Camera Snapshot Shutter Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onCaptureSnapshot),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = "Take photo",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Bubble shape with rounded corners and a centered downward triangular pointer.
 */
private class TooltipBubbleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val arrowW = with(density) { 14.dp.toPx() }
        val arrowH = with(density) { 7.dp.toPx() }
        val cornerRadius = with(density) { 6.dp.toPx() }

        val bodyHeight = size.height - arrowH
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = bodyHeight,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            )
            // Downward arrow centered at bottom
            val midX = size.width / 2f
            moveTo(midX - arrowW / 2f, bodyHeight)
            lineTo(midX, size.height)
            lineTo(midX + arrowW / 2f, bodyHeight)
            close()
        }
        return Outline.Generic(path)
    }
}
