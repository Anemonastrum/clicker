package id.anemona.clicker.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class EngineState { IDLE, SYNCING, WAITING, RUNNING }

/**
 * Runs the configured [TouchPoint] sequence through
 * [ClickAccessibilityService.tap].
 *
 * Lifecycle: [configure] with the latest points/settings, then [start].
 * If a scheduled start time is enabled, the engine syncs NTP first so the
 * wait is anchored to network time, not the (possibly wrong) device clock.
 * The whole point sequence loops until [stop] is called; a point with
 * repeatCount == 0 loops on that single point until stopped.
 */
object ClickEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    @Volatile
    var points: List<TouchPoint> = emptyList()

    @Volatile
    var settings: ClickerSettings = ClickerSettings.default()

    fun configure(points: List<TouchPoint>, settings: ClickerSettings) {
        this.points = points
        this.settings = settings
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val svc = ClickAccessibilityService.instance
            if (svc == null) {
                _status.value = "Accessibility service is not enabled"
                _state.value = EngineState.IDLE
                return@launch
            }
            if (points.isEmpty()) {
                _status.value = "No touch points configured"
                _state.value = EngineState.IDLE
                return@launch
            }

            if (settings.scheduleEnabled) {
                _state.value = EngineState.SYNCING
                _status.value = "Syncing network time…"
                val offset = try {
                    val r = NtpClient.sync(settings.ntpHost)
                    settings = settings.copy(lastOffsetMs = r.offsetMs, lastRttMs = r.rttMs)
                    r.offsetMs
                } catch (e: Exception) {
                    _status.value = "NTP sync failed (${e.message}); using device clock"
                    0L
                }
                val waitMs = millisUntil(settings.startHour, settings.startMinute, offset)
                if (waitMs > 1000) {
                    _state.value = EngineState.WAITING
                    _status.value = "Armed for %02d:%02d (network time)".format(
                        settings.startHour, settings.startMinute
                    )
                    delay(waitMs)
                }
            }

            _state.value = EngineState.RUNNING
            runLoop()
            _state.value = EngineState.IDLE
            if (_status.value.isBlank() || _status.value.startsWith("Point")) {
                _status.value = "Stopped"
            }
        }
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        _state.value = EngineState.IDLE
        _status.value = "Stopped"
    }

    private suspend fun runLoop() {
        val svc = ClickAccessibilityService.instance ?: return
        val metrics = svc.resources.displayMetrics
        while (coroutineContext.isActive) {
            val current = points
            if (current.isEmpty()) {
                _status.value = "No touch points configured"
                return
            }
            for ((index, p) in current.withIndex()) {
                if (!coroutineContext.isActive) return
                val x = p.xFrac * metrics.widthPixels
                val y = p.yFrac * metrics.heightPixels
                if (p.repeatCount <= 0) {
                    // Infinite loop on this single point until stopped.
                    var n = 0
                    while (coroutineContext.isActive) {
                        svc.tap(x, y)
                        n++
                        _status.value = "Point ${index + 1}: tap $n (looping)…"
                        delay(p.intervalMs)
                    }
                    return
                }
                repeat(p.repeatCount) { i ->
                    if (!coroutineContext.isActive) return
                    svc.tap(x, y)
                    _status.value = "Point ${index + 1}: ${i + 1}/${p.repeatCount}"
                    if (i < p.repeatCount - 1) delay(p.intervalMs)
                }
                if (p.delayAfterMs > 0) delay(p.delayAfterMs)
            }
        }
    }

    /**
     * Milliseconds from now until the next occurrence of [hour]:[minute],
     * measured in NTP time so the returned delay can be applied directly to
     * the device clock (the offset cancels out).
     */
    private fun millisUntil(hour: Int, minute: Int, offsetMs: Long): Long {
        val nowNtp = System.currentTimeMillis() + offsetMs
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowNtp
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var target = cal.timeInMillis
        if (target <= nowNtp + 1000) {
            target += 24 * 60 * 60 * 1000L // next day
        }
        return target - nowNtp
    }
}
