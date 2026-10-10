package id.anemona.clicker.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.anemona.clicker.core.ClickAccessibilityService
import id.anemona.clicker.core.ClickEngine
import id.anemona.clicker.core.EngineState
import id.anemona.clicker.core.OverlayService
import id.anemona.clicker.data.ClickerRepository

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo = remember { ClickerRepository.get(ctx) }
    val engineState by ClickEngine.state.collectAsState()
    val engineStatus by ClickEngine.status.collectAsState()
    val points by repo.points.collectAsState()

    var a11yOk by remember { mutableStateOf(ClickAccessibilityService.isEnabled) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yOk = ClickAccessibilityService.isEnabled
                overlayOk = Settings.canDrawOverlays(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val running = engineState == EngineState.RUNNING ||
        engineState == EngineState.WAITING ||
        engineState == EngineState.SYNCING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Clicker", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Engine: " + engineState.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (engineStatus.isNotBlank()) {
                    Text(engineStatus, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${points.size} touch point(s) configured",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            ContextCompat.startForegroundService(
                                ctx,
                                OverlayService.intent(ctx, OverlayService.ACTION_START_ENGINE),
                            )
                        },
                        enabled = !running && a11yOk,
                    ) { Text("Start") }
                    OutlinedButton(
                        onClick = {
                            ctx.startService(
                                OverlayService.intent(ctx, OverlayService.ACTION_STOP_ENGINE)
                            )
                        },
                        enabled = running,
                    ) { Text("Stop") }
                }
                if (!a11yOk) {
                    Text(
                        "Enable the accessibility service first.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Accessibility: " + if (a11yOk) "on" else "off")
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Open") }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Overlay: " + if (overlayOk) "granted" else "missing")
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}"),
                            )
                        )
                    }) { Text("Grant") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Floating overlay", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            ContextCompat.startForegroundService(
                                ctx,
                                OverlayService.intent(ctx, OverlayService.ACTION_SHOW),
                            )
                        },
                        enabled = overlayOk,
                    ) { Text("Show") }
                    OutlinedButton(onClick = {
                        ctx.startService(OverlayService.intent(ctx, OverlayService.ACTION_HIDE))
                    }) { Text("Hide") }
                }
            }
        }
    }
}
