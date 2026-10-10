package id.anemona.clicker.ui.screens

import android.util.DisplayMetrics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.anemona.clicker.core.TouchPoint
import id.anemona.clicker.data.ClickerRepository
import kotlin.math.roundToInt

@Composable
fun PointsScreen() {
    val ctx = LocalContext.current
    val repo = remember { ClickerRepository.get(ctx) }
    val points by repo.points.collectAsState()
    val metrics = ctx.resources.displayMetrics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${points.size} touch point(s)",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "Tip: place points precisely from the floating overlay — tap + " +
                "there, drag the marker, confirm with the check icon.",
            style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(points, key = { _, p -> p.id }) { index, p ->
                PointEditor(index, p, repo, metrics)
            }
        }
        Button(
            onClick = { repo.addPoint(TouchPoint(xFrac = 0.5f, yFrac = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add point at screen center") }
    }
}

@Composable
private fun PointEditor(
    index: Int,
    point: TouchPoint,
    repo: ClickerRepository,
    metrics: DisplayMetrics,
) {
    var open by remember { mutableStateOf(false) }
    val xPx = (point.xFrac * metrics.widthPixels).roundToInt()
    val yPx = (point.yFrac * metrics.heightPixels).roundToInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { open = !open },
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "#${index + 1}  ($xPx, $yPx px)",
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = { repo.removePoint(point.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete point")
                }
            }
            Text(
                if (point.repeatCount == 0) {
                    "Loops forever · every ${point.intervalMs} ms"
                } else {
                    "${point.repeatCount} tap(s) · every ${point.intervalMs} ms" +
                        " · +${point.delayAfterMs} ms after"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (open) {
                NumberField(
                    label = "Repeat count (0 = loop forever)",
                    value = point.repeatCount.toString(),
                ) { v ->
                    v.toIntOrNull()?.let {
                        repo.updatePoint(point.copy(repeatCount = it.coerceAtLeast(0)))
                    }
                }
                NumberField(
                    label = "Interval between taps (ms)",
                    value = point.intervalMs.toString(),
                ) { v ->
                    v.toLongOrNull()?.let {
                        repo.updatePoint(point.copy(intervalMs = it.coerceAtLeast(0)))
                    }
                }
                NumberField(
                    label = "Delay after point finishes (ms)",
                    value = point.delayAfterMs.toString(),
                ) { v ->
                    v.toLongOrNull()?.let {
                        repo.updatePoint(point.copy(delayAfterMs = it.coerceAtLeast(0)))
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onCommit(it)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
