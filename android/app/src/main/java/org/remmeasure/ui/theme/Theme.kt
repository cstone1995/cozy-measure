// Modified for Cozy Flooring: brand colours.
package org.remmeasure.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B7566),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFF80CBC4),
    secondary = Color(0xFFA68875),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00382E),
    onSecondaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF181818),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color(0xFF49454F)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF735D4D), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DDD4), onPrimaryContainer = Color(0xFF493A2E),
    secondary = Color(0xFF6B6154), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE4DA), onSecondaryContainer = Color(0xFF493A2E),
    background = Color(0xFFF5F1EB), onBackground = Color(0xFF303A34),
    surface = Color(0xFFFFFCF7), onSurface = Color(0xFF303A34),
    surfaceVariant = Color(0xFFEDE4DA), onSurfaceVariant = Color(0xFF62594F),
    surfaceContainer = Color(0xFFF0E8DF), surfaceContainerHigh = Color(0xFFE8DDD4),
    surfaceContainerHighest = Color(0xFFDFD1C5), outline = Color(0xFF8B7566)
)

@Composable
fun ReMeasureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
