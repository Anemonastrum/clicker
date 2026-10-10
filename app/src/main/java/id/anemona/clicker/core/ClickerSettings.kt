package id.anemona.clicker.core

import org.json.JSONObject

/**
 * App-wide clicker settings, persisted by [id.anemona.clicker.data.ClickerRepository].
 *
 * The execution start time is a wall-clock time (hour/minute), not a countdown:
 * the engine syncs with NTP, converts the target to the device clock and waits.
 */
data class ClickerSettings(
    val scheduleEnabled: Boolean = false,
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val ntpHost: String = "pool.ntp.org",
    val lastOffsetMs: Long = 0L,
    val lastRttMs: Long = -1L,
    /** Overlay UI scale factor, 0.5f..2.0f. */
    val overlayScale: Float = 1.0f,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("scheduleEnabled", scheduleEnabled)
        .put("startHour", startHour)
        .put("startMinute", startMinute)
        .put("ntpHost", ntpHost)
        .put("lastOffsetMs", lastOffsetMs)
        .put("lastRttMs", lastRttMs)
        .put("overlayScale", overlayScale.toDouble())

    companion object {
        fun fromJson(o: JSONObject): ClickerSettings = ClickerSettings(
            scheduleEnabled = o.optBoolean("scheduleEnabled", false),
            startHour = o.optInt("startHour", 8).coerceIn(0, 23),
            startMinute = o.optInt("startMinute", 0).coerceIn(0, 59),
            ntpHost = o.optString("ntpHost", "pool.ntp.org").ifBlank { "pool.ntp.org" },
            lastOffsetMs = o.optLong("lastOffsetMs", 0),
            lastRttMs = o.optLong("lastRttMs", -1),
            overlayScale = o.optDouble("overlayScale", 1.0).toFloat().coerceIn(0.5f, 2.0f),
        )

        fun default() = ClickerSettings()
    }
}
