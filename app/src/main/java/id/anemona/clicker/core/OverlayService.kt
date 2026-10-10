package id.anemona.clicker.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import id.anemona.clicker.MainActivity
import id.anemona.clicker.R
import id.anemona.clicker.data.ClickerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Foreground service hosting the floating clicker overlay and the [ClickEngine].
 *
 * Two presentations:
 * - Minimized: pill with a live clock (HH:mm:ss.SSS) and the last measured
 *   network RTT. The whole pill is draggable; a chevron button expands it.
 * - Expanded: drag-handle strip plus icon-only buttons — add touch point,
 *   remove last point, set execution start time (clock time, not countdown),
 *   start, stop, collapse — and a close button that shuts everything down.
 *
 * "Add point" enters placement mode: a draggable marker appears anywhere on
 * screen; confirm (check) drops a [TouchPoint] there, cancel (X) aborts.
 * While expanded, existing points are shown as numbered markers.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var repo: ClickerRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clockHandler = Handler(Looper.getMainLooper())

    private var controlView: View? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var markerLayer: FrameLayout? = null
    private var markerParams: WindowManager.LayoutParams? = null
    private var placementBar: View? = null

    private var expanded = false
    private var placing = false
    private var lastX = 0
    private var lastY = 220

    private var clockText: TextView? = null
    private var rttText: TextView? = null
    private var startBtn: ImageButton? = null
    private var stopBtn: ImageButton? = null
    private var markerView: View? = null
    private var markerX = 0
    private var markerY = 0

    @Volatile
    private var lastRttMs: Long? = null

    private val clockTick = object : Runnable {
        private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        override fun run() {
            clockText?.text = fmt.format(Date())
            rttText?.text = "rtt " + (lastRttMs?.let { "${it}ms" } ?: "—")
            clockHandler.postDelayed(this, 47)
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        repo = ClickerRepository.get(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        showControl()
        clockHandler.post(clockTick)
        scope.launch { rttLoop() }
        scope.launch {
            ClickEngine.state.collect { updateEngineButtons() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showControl()
            ACTION_HIDE -> hideControl()
            ACTION_START_ENGINE -> {
                ClickEngine.configure(repo.points.value, repo.settings.value)
                ClickEngine.start()
            }
            ACTION_STOP_ENGINE -> scope.launch { ClickEngine.stop() }
            ACTION_REFRESH -> refreshAll()
            ACTION_CLOSE -> {
                scope.launch { ClickEngine.stop() }
                teardown()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        teardown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun teardown() {
        clockHandler.removeCallbacks(clockTick)
        scope.cancel()
        try {
            controlView?.let { wm.removeView(it) }
            markerLayer?.let { wm.removeView(it) }
            placementBar?.let { wm.removeView(it) }
        } catch (_: Exception) {
        }
        controlView = null
        markerLayer = null
        placementBar = null
    }

    // ------------------------------------------------------------------ control view

    private fun showControl() {
        if (controlView != null) return
        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastX
            y = lastY
        }
        controlView = if (expanded) buildExpandedView() else buildCollapsedView()
        applyScale()
        wm.addView(controlView, controlParams)
        updateEngineButtons()
    }

    private fun hideControl() {
        controlView?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        controlView = null
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        exitPlacement()
        hideControl()
        showControl()
        refreshMarkers()
    }

    private fun applyScale() {
        val s = repo.settings.value.overlayScale
        controlView?.scaleX = s
        controlView?.scaleY = s
    }

    private fun buildCollapsedView(): View {
        val ctx = this
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(0xCC1E1E1E.toInt(), 22f)
            val p = dp(10)
            setPadding(p, dp(8), p, dp(8))
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        clockText = TextView(ctx).apply {
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.MONOSPACE
            text = "--:--:--.---"
        }
        rttText = TextView(ctx).apply {
            textSize = 10f
            setTextColor(0xFFAAAAAA.toInt())
            typeface = Typeface.MONOSPACE
            text = "rtt —"
        }
        texts.addView(clockText)
        texts.addView(rttText)
        pill.addView(texts)
        pill.addView(iconButton(R.drawable.ic_expand, "Expand") {
            setExpanded(true)
        })
        // Whole pill (except the button) drags the overlay.
        pill.setOnTouchListener(dragControlListener())
        return pill
    }

    private fun buildExpandedView(): View {
        val ctx = this
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(0xCC1E1E1E.toInt(), 18f)
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }

        // Drag-handle strip with close button.
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val grip = View(ctx).apply {
            background = roundedBg(0xFF666666.toInt(), 2f)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(5)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val close = iconButton(R.drawable.ic_close, "Close overlay") {
            onStartCommand(Intent(ACTION_CLOSE).setClass(ctx, OverlayService::class.java), 0, 0)
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)) }
        header.addView(grip)
        header.addView(spacer)
        header.addView(close)
        header.setOnTouchListener(dragControlListener())
        panel.addView(header)

        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val row2 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row1.addView(iconButton(R.drawable.ic_add, "Add touch point") { enterPlacement() })
        row1.addView(iconButton(R.drawable.ic_remove, "Remove last point") {
            repo.removeLastPoint()
            refreshMarkers()
        })
        row1.addView(iconButton(R.drawable.ic_schedule, "Set start time") { showScheduleDialog() })
        startBtn = iconButton(R.drawable.ic_play, "Start") {
            ClickEngine.configure(repo.points.value, repo.settings.value)
            ClickEngine.start()
        }
        stopBtn = iconButton(R.drawable.ic_stop, "Stop") {
            scope.launch { ClickEngine.stop() }
        }
        val collapse = iconButton(R.drawable.ic_collapse, "Minimize") { setExpanded(false) }
        row2.addView(startBtn)
        row2.addView(stopBtn)
        row2.addView(collapse)
        panel.addView(row1)
        panel.addView(row2)
        return panel
    }

    private fun iconButton(drawableRes: Int, contentDesc: String, onClick: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(drawableRes)
            contentDescription = contentDesc
            background = roundedBg(0xFF333333.toInt(), 12f)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun updateEngineButtons() {
        val running = ClickEngine.state.value == EngineState.RUNNING ||
            ClickEngine.state.value == EngineState.WAITING ||
            ClickEngine.state.value == EngineState.SYNCING
        startBtn?.apply {
            alpha = if (running) 0.35f else 1f
            isEnabled = !running
        }
        stopBtn?.apply {
            alpha = if (running) 1f else 0.35f
            isEnabled = running
        }
    }

    // ------------------------------------------------------------------ dragging

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f

    private fun dragControlListener(): View.OnTouchListener =
        View.OnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawX = e.rawX
                    dragStartRawY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - dragStartRawX).toInt()
                    val dy = (e.rawY - dragStartRawY).toInt()
                    dragStartRawX = e.rawX
                    dragStartRawY = e.rawY
                    controlParams?.let {
                        it.x += dx
                        it.y += dy
                        lastX = it.x
                        lastY = it.y
                        try {
                            wm.updateViewLayout(controlView, it)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                else -> false
            }
        }

    // ------------------------------------------------------------------ markers + placement

    private fun ensureMarkerLayer(touchable: Boolean) {
        if (markerLayer == null) {
            markerParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            markerLayer = FrameLayout(this)
            wm.addView(markerLayer, markerParams)
        }
        markerParams?.let {
            it.flags = if (touchable) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }
            try {
                wm.updateViewLayout(markerLayer, it)
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshMarkers() {
        if (!expanded && !placing) {
            markerLayer?.let {
                try {
                    wm.removeView(it)
                } catch (_: Exception) {
                }
            }
            markerLayer = null
            markerParams = null
            return
        }
        ensureMarkerLayer(placing)
        val layer = markerLayer ?: return
        layer.removeAllViews()
        val metrics = resources.displayMetrics
        repo.points.value.forEachIndexed { index, p ->
            val dot = TextView(this).apply {
                text = (index + 1).toString()
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = roundedBg(0xFF2F6BFF.toInt(), 14f)
            }
            val size = dp(28)
            val lp = FrameLayout.LayoutParams(size, size).apply {
                leftMargin = (p.xFrac * metrics.widthPixels - size / 2).roundToInt()
                topMargin = (p.yFrac * metrics.heightPixels - size / 2).roundToInt()
            }
            layer.addView(dot, lp)
        }
        if (placing) addPlacingMarker(layer, metrics.widthPixels, metrics.heightPixels)
    }

    private fun enterPlacement() {
        if (placing) return
        placing = true
        val metrics = resources.displayMetrics
        markerX = metrics.widthPixels / 2
        markerY = metrics.heightPixels / 2
        refreshMarkers()
        showPlacementBar()
    }

    private fun exitPlacement() {
        if (!placing) return
        placing = false
        markerView = null
        placementBar?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        placementBar = null
        refreshMarkers()
    }

    private fun addPlacingMarker(layer: FrameLayout, w: Int, h: Int) {
        val size = dp(56)
        markerView = FrameLayout(this).apply {
            background = roundedBg(0x00000000, 28f).also {
                // dashed-look ring via stroke
                it.setStroke(dp(3), 0xFFFFAA00.toInt())
            }
            val plus = android.widget.ImageView(this@OverlayService).apply {
                setImageResource(R.drawable.ic_add)
            }
            addView(plus, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
        }
        positionMarker(w, h)
        markerView?.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawX = e.rawX
                    dragStartRawY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    markerX += (e.rawX - dragStartRawX).toInt()
                    markerY += (e.rawY - dragStartRawY).toInt()
                    dragStartRawX = e.rawX
                    dragStartRawY = e.rawY
                    positionMarker(w, h)
                    true
                }
                else -> false
            }
        }
        layer.addView(markerView)
    }

    private fun positionMarker(w: Int, h: Int) {
        val size = dp(56)
        markerX = markerX.coerceIn(0, w)
        markerY = markerY.coerceIn(0, h)
        (markerView?.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.leftMargin = markerX - size / 2
            it.topMargin = markerY - size / 2
            it.width = size
            it.height = size
            markerView?.layoutParams = it
        } ?: run {
            markerView?.layoutParams = FrameLayout.LayoutParams(size, size).apply {
                leftMargin = markerX - size / 2
                topMargin = markerY - size / 2
            }
        }
    }

    private fun showPlacementBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBg(0xCC1E1E1E.toInt(), 18f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        bar.addView(iconButton(R.drawable.ic_check, "Confirm point") {
            val metrics = resources.displayMetrics
            repo.addPoint(
                TouchPoint(
                    xFrac = (markerX.toFloat() / metrics.widthPixels).coerceIn(0f, 1f),
                    yFrac = (markerY.toFloat() / metrics.heightPixels).coerceIn(0f, 1f),
                )
            )
            exitPlacement()
        })
        bar.addView(iconButton(R.drawable.ic_close, "Cancel") { exitPlacement() })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(90)
        }
        placementBar = bar
        wm.addView(bar, params)
    }

    // ------------------------------------------------------------------ schedule dialog

    private fun showScheduleDialog() {
        val s = repo.settings.value
        val themed = ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog)
        val dialog = TimePickerDialog(
            themed,
            { _, hour, minute ->
                repo.updateSettings(
                    s.copy(scheduleEnabled = true, startHour = hour, startMinute = minute)
                )
                ClickEngine.configure(repo.points.value, repo.settings.value)
            },
            s.startHour,
            s.startMinute,
            true,
        )
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    // ------------------------------------------------------------------ misc

    private fun refreshAll() {
        applyScale()
        refreshMarkers()
        updateEngineButtons()
    }

    private suspend fun rttLoop() {
        // Initial quick sample so the minimized pill shows RTT promptly.
        sampleRtt()
        while (isActive) {
            delay(30_000)
            sampleRtt()
        }
    }

    private suspend fun sampleRtt() {
        try {
            val r = NtpClient.sync(repo.settings.value.ntpHost)
            lastRttMs = r.rttMs
            val cur = repo.settings.value
            repo.updateSettings(cur.copy(lastOffsetMs = r.offsetMs, lastRttMs = r.rttMs))
        } catch (_: Exception) {
            // Keep the last known RTT; overlay keeps ticking.
        }
    }

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).roundToInt()

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply { description = getString(R.string.overlay_channel_description) }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(pending)
            .build()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_SHOW = "id.anemona.clicker.action.SHOW"
        const val ACTION_HIDE = "id.anemona.clicker.action.HIDE"
        const val ACTION_START_ENGINE = "id.anemona.clicker.action.START_ENGINE"
        const val ACTION_STOP_ENGINE = "id.anemona.clicker.action.STOP_ENGINE"
        const val ACTION_REFRESH = "id.anemona.clicker.action.REFRESH"
        const val ACTION_CLOSE = "id.anemona.clicker.action.CLOSE"

        private const val CHANNEL_ID = "clicker_fg"
        private const val NOTIF_ID = 1001

        fun intent(ctx: Context, action: String): Intent =
            Intent(ctx, OverlayService::class.java).setAction(action)
    }
}
