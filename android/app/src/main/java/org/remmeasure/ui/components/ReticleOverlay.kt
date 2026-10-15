package org.remmeasure.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Screen-space reticle overlay.
 * When searching for surfaces (no plane detected), displays a subtle aiming ring.
 * Once a surface is detected, the authentic 3D surface-projected reticle on the plane
 * takes over, displaying the physical plane orientation in AR space.
 */
@Composable
fun ReticleOverlay(
    isSurfaceDetected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = !isSurfaceDetected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Canvas(modifier = Modifier.size(32.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)

                // Subtle seeking ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = 8.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.8.dp.toPx())
                )

                // Subtle center dot
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = 2.5.dp.toPx(),
                    center = center
                )
            }
        }
    }
}
