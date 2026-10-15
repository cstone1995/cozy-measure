package org.remmeasure.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.remmeasure.core.math.MeasurementMath
import org.remmeasure.core.model.MeasurementUnit
import org.remmeasure.core.model.TrackingStatus
import org.remmeasure.ui.theme.MeasureTapeYellow
import org.remmeasure.ui.theme.SurfaceDark
import org.remmeasure.ui.theme.TextLight

@Composable
fun StatusBanner(
    status: TrackingStatus,
    isMeasuring: Boolean,
    liveDistanceMeters: Float,
    unit: MeasurementUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDark)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isMeasuring && liveDistanceMeters > 0f) {
                    Text(
                        text = MeasurementMath.formatDistance(liveDistanceMeters, unit),
                        color = MeasureTapeYellow,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                } else {
                    Text(
                        text = status.message,
                        color = TextLight,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
