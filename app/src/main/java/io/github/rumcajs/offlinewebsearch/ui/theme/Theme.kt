package io.github.rumcajs.offlinewebsearch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.Purple80,
    secondary = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.PurpleGrey80,
    tertiary = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.Purple40,
    secondary = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.PurpleGrey40,
    tertiary = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun OfflineWebSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.DarkColorScheme
        else -> _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.theme.Typography,
        content = content
    )
}