package id.anemona.clicker.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Accessibility service whose only job is performing taps via
 * [dispatchGesture]. It consumes no accessibility events.
 *
 * The user must enable it once in system Settings → Accessibility.
 * [instance] is the bridge used by [ClickEngine].
 */
class ClickAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — gesture-only service.
    }

    override fun onInterrupt() {
        // Nothing to clean up.
    }

    /**
     * Performs one tap at absolute screen coordinates.
     * @return true if the gesture completed.
     */
    suspend fun tap(x: Float, y: Float, pressMs: Long = 80): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, pressMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return suspendCancellableCoroutine { cont ->
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            if (!dispatched && cont.isActive) cont.resume(false)
        }
    }

    companion object {
        @Volatile
        var instance: ClickAccessibilityService? = null
            private set

        val isEnabled: Boolean
            get() = instance != null
    }
}
