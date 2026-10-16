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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import org.remmeasure.ui.screens.PlannerStartScreen
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
                darkTheme = false,
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
    var screen by rememberSaveable { mutableStateOf("home") }
    BackHandler(enabled=screen!="home") { screen="home" }
    when(screen) {
        "home" -> PlannerStartScreen(
            onScan={name, ceiling -> measureViewModel.setCeilingMode(ceiling); measureViewModel.clearAllMeasurements();measureViewModel.setRoomName(name);screen="scan"},
            onRooms={screen="saved"},onManual={screen="manual"}
        )
        "scan" -> MeasureScreen(viewModel=measureViewModel,onReviewRoom={screen="review"})
        else -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TextButton(onClick={screen="home"},modifier=Modifier.padding(horizontal=12.dp)){Text("← My rooms & new scan")}
            CarpetScreen(viewModel=measureViewModel,importScan=screen=="review")
        }
    }
}
