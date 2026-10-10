package id.anemona.clicker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * App theme backed by the MIUIX UI library: follows the system light/dark
 * mode and gives the whole app the MIUI/HyperOS look.
 */
@Composable
fun ClickerTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(
        controller = controller,
        content = content,
    )
}
