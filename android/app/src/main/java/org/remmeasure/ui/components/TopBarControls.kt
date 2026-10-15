package org.remmeasure.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.remmeasure.core.model.MeasurementUnit

@Composable
fun TopBarControls(
    hasMeasurements: Boolean,
    selectedUnit: MeasurementUnit,
    onClearAll: () -> Unit,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
    val contentColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clear all button (only shown when measurements exist)
        if (hasMeasurements) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(pillBg)
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear all",
                    tint = contentColor
                )
                Text(
                    text = "Clear all",
                    color = contentColor,
                    fontSize = 13.sp,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        } else {
            Box(modifier = Modifier.padding(1.dp))
        }

        // Unit Switcher Pill (settings button removed as settings is now in the floating bottom bar)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(pillBg)
                .clickable(onClick = onToggleUnit)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = selectedUnit.shortSymbol,
                color = contentColor,
                fontSize = 13.sp,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
