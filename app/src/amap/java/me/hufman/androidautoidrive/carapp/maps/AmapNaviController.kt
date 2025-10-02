package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.hardware.display.VirtualDisplay
import android.util.Log
import me.hufman.androidautoidrive.AppSettingsObserver
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong

class AmapNaviController(
    private val context: Context,
    private val carLocationProvider: CarLocationProvider,
    private val virtualDisplay: VirtualDisplay,
    private val appSettings: AppSettingsObserver,
    private val mapAppMode: MapAppMode
) : MapInteractionController {

    companion object {
        private const val TAG = "AmapNaviController"
    }

    private val SHUTDOWN_WAIT_INTERVAL = 120000L   // milliseconds of inactivity before shutting down map

    var projection: AmapNaviProjection? = null
    var currentLocation: android.location.Location? = null

    init {
        carLocationProvider.callback = { location ->
            currentLocation = location
            projection?.applySettings(AmapSettings.build(appSettings, location.toLatLong()))
        }
    }

    override fun showMap() {
        Log.i(TAG, "Showing navigation map")

        if (projection == null) {
            Log.i(TAG, "First showing of the navigation map")
            this.projection = AmapNaviProjection(context, virtualDisplay.display, appSettings, carLocationProvider)
        }

        if (projection?.isShowing == false) {
            projection?.show()
        }

        // register for location updates
        carLocationProvider.start()

        // watch for map settings
        appSettings.callback = { applySettings() }
        applySettings(force = true)
    }

    override fun pauseMap() {
        carLocationProvider.stop()
        projection?.hide()
    }

    private fun applySettings(force: Boolean = false) {
        val newSettings = AmapSettings.build(appSettings, currentLocation?.toLatLong())
        projection?.applySettings(newSettings)
    }

    override fun zoomIn(steps: Int) {
        mapAppMode.startInteraction()
        // AMapNaviView handles zoom internally
    }

    override fun zoomOut(steps: Int) {
        mapAppMode.startInteraction()
        // AMapNaviView handles zoom internally
    }

    override fun navigateTo(dest: LatLong) {
        mapAppMode.startInteraction()
        projection?.navigateTo(dest)
    }

    override fun recalcNavigation() {
        // AMapNaviView handles recalculation internally
    }

    override fun stopNavigation() {
        projection?.stopNavigation()
    }
}
