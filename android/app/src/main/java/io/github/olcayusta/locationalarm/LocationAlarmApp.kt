package io.github.olcayusta.locationalarm

import android.app.Application
import org.osmdroid.config.Configuration

class LocationAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName
    }
}
