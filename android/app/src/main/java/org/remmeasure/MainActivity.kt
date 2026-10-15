// Modified for Cozy Flooring: carpet calculator and brand palette.
package org.remmeasure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.remmeasure.core.model.AppThemeMode
import org.remmeasure.ui.components.ExpressiveFloatingBottomBar
import org.remmeasure.ui.navigation.NavDestination
import org.remmeasure.ui.screens.CarpetScreen
import org.remmeasure.ui.screens.HistoryScreen
import org.remmeasure.ui.screens.LevelScreen
import org.remmeasure.ui.screens.MeasureScreen
import org.remmeasure.ui.screens.SettingsScreen
import org.remmeasure.ui.theme.ReMeasureTheme
import org.remmeasure.ui.viewmodel.MeasureViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val measureViewModel: MeasureViewModel = viewModel()
            val uiState by measureViewModel.uiState.collectAsState()

            val isDarkTheme = when (uiState.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
            }

            ReMeasureTheme(
                darkTheme = isDarkTheme,
                dynamicColor = false
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScaffold(measureViewModel = measureViewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    measureViewModel: MeasureViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavDestination.Measure.route

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = NavDestination.Measure.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(NavDestination.Measure.route) {
                MeasureScreen(
                    viewModel = measureViewModel,
                    onReviewRoom = { navController.navigate(NavDestination.Level.route) }
                )
            }

            composable(NavDestination.Level.route) {
                CarpetScreen(viewModel = measureViewModel)
            }

            composable(NavDestination.History.route) {
                HistoryScreen(viewModel = measureViewModel)
            }

            composable(NavDestination.Settings.route) {
                SettingsScreen(viewModel = measureViewModel)
            }
        }

        // Floating Bottom Navigation Bar
        ExpressiveFloatingBottomBar(
            destinations = NavDestination.topLevelDestinations,
            currentRoute = currentRoute,
            onDestinationSelected = { destination ->
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
