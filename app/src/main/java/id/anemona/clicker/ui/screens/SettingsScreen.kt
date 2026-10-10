package id.anemona.clicker.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import id.anemona.clicker.core.NtpClient
import id.anemona.clicker.core.OverlayService
import id.anemona.clicker.data.ClickerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val repo = remember { ClickerRepository.get(ctx) }
    val settings by repo.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var measuring by remember { mutableStateOf(false) }
    var hostText by remember(settings.ntpHost) { mutableStateOf(settings.ntpHost) }
    var ntpClock by remember { mutableStateOf("--:--:--.---") }

    // Live network clock, ticking ~10x per second.
    LaunchedEffect(settings.lastOffsetMs) {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        while (true) {
            ntpClock = fmt.format(Date(NtpClient.nowWithOffset(settings.lastOffsetMs)))
            delay(97)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // ---- Scheduled start ----
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Execution start", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Wall-clock time (not a countdown). The engine syncs NTP " +
                        "first, then waits for the next occurrence of this time.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Scheduled start")
                    Switch(
                        checked = settings.scheduleEnabled,
                        onCheckedChange = {
                            repo.updateSettings(settings.copy(scheduleEnabled = it))
                        },
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "%02d:%02d".format(settings.startHour, settings.startMinute),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    TextButton(onClick = {
                        TimePickerDialog(
                            ctx,
                            { _, h, m ->
                                repo.updateSettings(
                                    settings.copy(
                                        scheduleEnabled = true,
                                        startHour = h,
                                        startMinute = m,
                                    )
                                )
                            },
                            settings.startHour,
                            settings.startMinute,
                            true,
                        ).show()
                    }) { Text("Set time") }
                }
            }
        }

        // ---- Network time ----
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Network time", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = hostText,
                    onValueChange = { hostText = it },
                    label = { Text("NTP server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val host = hostText.ifBlank { "pool.ntp.org" }
                            repo.updateSettings(settings.copy(ntpHost = host))
                            scope.launch {
                                measuring = true
                                try {
                                    val r = NtpClient.sync(host)
                                    repo.updateSettings(
                                        repo.settings.value.copy(
                                            lastOffsetMs = r.offsetMs,
                                            lastRttMs = r.rttMs,
                                        )
                                    )
                                } catch (_: Exception) {
                                    // keep previous values; UI shows them as-is
                                } finally {
                                    measuring = false
                                }
                            }
                        },
                        enabled = !measuring,
                    ) { Text(if (measuring) "Measuring…" else "Measure network delay") }
                }
                Text(
                    "Clock offset: ${settings.lastOffsetMs} ms\n" +
                        "Last RTT: " + if (settings.lastRttMs >= 0) "${settings.lastRttMs} ms" else "—",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Network clock: $ntpClock",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // ---- Overlay ----
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Floating overlay", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Overlay size: ${(settings.overlayScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = settings.overlayScale,
                    onValueChange = {
                        repo.updateSettings(settings.copy(overlayScale = it))
                    },
                    onValueChangeFinished = {
                        if (OverlayService.isRunning) {
                            ctx.startService(
                                OverlayService.intent(ctx, OverlayService.ACTION_REFRESH)
                            )
                        }
                    },
                    valueRange = 0.5f..2.0f,
                )
            }
        }
    }
}
