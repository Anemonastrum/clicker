package id.anemona.clicker

import android.app.Application
import id.anemona.clicker.data.ClickerRepository

class ClickerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Eager init so StateFlows are warm for the UI and the overlay service.
        ClickerRepository.get(this)
    }
}
