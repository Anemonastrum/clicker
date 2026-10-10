package id.anemona.clicker.data

import android.content.Context
import id.anemona.clicker.core.ClickerSettings
import id.anemona.clicker.core.TouchPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists touch points and settings in SharedPreferences as JSON.
 * Exposes them as StateFlows so the UI and the overlay stay in sync.
 */
class ClickerRepository private constructor(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _points = MutableStateFlow(loadPoints())
    val points: StateFlow<List<TouchPoint>> = _points.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ClickerSettings> = _settings.asStateFlow()

    fun addPoint(point: TouchPoint) {
        setPoints(_points.value + point)
    }

    fun removeLastPoint() {
        if (_points.value.isNotEmpty()) setPoints(_points.value.dropLast(1))
    }

    fun removePoint(id: String) {
        setPoints(_points.value.filterNot { it.id == id })
    }

    fun updatePoint(point: TouchPoint) {
        setPoints(_points.value.map { if (it.id == point.id) point else it })
    }

    fun clearPoints() {
        setPoints(emptyList())
    }

    fun updateSettings(settings: ClickerSettings) {
        _settings.value = settings
        prefs.edit().putString(KEY_SETTINGS, settings.toJson().toString()).apply()
    }

    private fun setPoints(list: List<TouchPoint>) {
        _points.value = list
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_POINTS, arr.toString()).apply()
    }

    private fun loadPoints(): List<TouchPoint> {
        return try {
            val raw = prefs.getString(KEY_POINTS, null) ?: return emptyList()
            val arr = JSONArray(raw)
            List(arr.length()) { i -> TouchPoint.fromJson(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadSettings(): ClickerSettings {
        return try {
            val raw = prefs.getString(KEY_SETTINGS, null)
                ?: return ClickerSettings.default()
            ClickerSettings.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            ClickerSettings.default()
        }
    }

    companion object {
        private const val PREFS_NAME = "clicker_prefs"
        private const val KEY_POINTS = "points_json"
        private const val KEY_SETTINGS = "settings_json"

        @Volatile
        private var instance: ClickerRepository? = null

        fun get(context: Context): ClickerRepository =
            instance ?: synchronized(this) {
                instance ?: ClickerRepository(context).also { instance = it }
            }
    }
}
