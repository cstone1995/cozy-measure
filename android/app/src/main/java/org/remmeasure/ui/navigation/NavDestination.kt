// Modified for Cozy Flooring: carpet-size navigation.
package org.remmeasure.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations for ReMeasure application navigation.
 */
sealed class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Measure : NavDestination(
        route = "measure",
        label = "Measure",
        icon = Icons.Rounded.Straighten
    )

    data object Level : NavDestination(
        route = "level",
        label = "Carpet size",
        icon = Icons.Rounded.Architecture
    )

    data object History : NavDestination(
        route = "history",
        label = "History",
        icon = Icons.Rounded.History
    )

    data object Settings : NavDestination(
        route = "settings",
        label = "Settings",
        icon = Icons.Rounded.Settings
    )

    companion object {
        val topLevelDestinations: List<NavDestination>
            get() = listOf(
                Measure,
                Level,
                History,
                Settings
            )
    }
}
