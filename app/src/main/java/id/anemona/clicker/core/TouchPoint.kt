package id.anemona.clicker.core

import org.json.JSONObject
import java.util.UUID

/**
 * A single auto-click target.
 *
 * Coordinates are stored as fractions of the screen size (0..1) so points
 * survive resolution changes reasonably well.
 *
 * @param repeatCount how many taps to perform on this point; 0 = loop forever
 *                    on this point until the engine is stopped.
 * @param intervalMs delay between consecutive taps on this point.
 * @param delayAfterMs pause after this point's taps finish, before the next point.
 */
data class TouchPoint(
    val id: String = UUID.randomUUID().toString(),
    val xFrac: Float,
    val yFrac: Float,
    val label: String = "",
    val repeatCount: Int = 1,
    val intervalMs: Long = 500,
    val delayAfterMs: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("x", xFrac.toDouble())
        .put("y", yFrac.toDouble())
        .put("label", label)
        .put("repeat", repeatCount)
        .put("intervalMs", intervalMs)
        .put("delayAfterMs", delayAfterMs)

    companion object {
        fun fromJson(o: JSONObject): TouchPoint = TouchPoint(
            id = o.optString("id", UUID.randomUUID().toString()),
            xFrac = o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
            yFrac = o.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
            label = o.optString("label", ""),
            repeatCount = o.optInt("repeat", 1).coerceAtLeast(0),
            intervalMs = o.optLong("intervalMs", 500).coerceAtLeast(0),
            delayAfterMs = o.optLong("delayAfterMs", 0).coerceAtLeast(0),
        )
    }
}
