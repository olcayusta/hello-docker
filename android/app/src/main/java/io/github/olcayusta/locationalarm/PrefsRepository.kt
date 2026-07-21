package io.github.olcayusta.locationalarm

import android.content.Context

data class AlarmTarget(val latitude: Double, val longitude: Double, val radiusMeters: Float)

class PrefsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("location_alarm_prefs", Context.MODE_PRIVATE)

    var isTracking: Boolean
        get() = prefs.getBoolean(KEY_TRACKING, false)
        set(value) = prefs.edit().putBoolean(KEY_TRACKING, value).apply()

    fun saveTarget(target: AlarmTarget) {
        prefs.edit()
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(target.latitude))
            .putLong(KEY_LNG, java.lang.Double.doubleToRawLongBits(target.longitude))
            .putFloat(KEY_RADIUS, target.radiusMeters)
            .apply()
    }

    fun loadTarget(): AlarmTarget? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LNG)) return null
        return AlarmTarget(
            latitude = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0L)),
            longitude = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LNG, 0L)),
            radiusMeters = prefs.getFloat(KEY_RADIUS, 200f)
        )
    }

    companion object {
        private const val KEY_LAT = "target_lat"
        private const val KEY_LNG = "target_lng"
        private const val KEY_RADIUS = "target_radius"
        private const val KEY_TRACKING = "is_tracking"
    }
}
