package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import me.hufman.androidautoidrive.AppSettingsObserver
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong

fun Location.toLatLong(): LatLong = LatLong(this.latitude, this.longitude)

class AmapController(
    private val context: Context,
    private val carLocationProvider: CarLocationProvider,
    private val virtualDisplay: VirtualDisplay,
    private val appSettings: AppSettingsObserver,
    private val mapAppMode: MapAppMode
) : MapInteractionController {

    private val TAG = "AmapController"
    private val handler = Handler(Looper.getMainLooper())
    
    // 使用新的导航控制器
    val navController = AmapNaviController(context, carLocationProvider, virtualDisplay, appSettings, mapAppMode)
    
    var currentLocation: Location? = null

    init {
        carLocationProvider.callback = { location ->
            handler.post {
                onLocationUpdate(location)
            }
        }
    }

    private fun onLocationUpdate(location: Location) {
        currentLocation = location
        Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
    }

    override fun showMap() {
        Log.i(TAG, "Showing map using AMapNaviView")
        navController.showMap()
    }

    override fun pauseMap() {
        Log.i(TAG, "Pausing map")
        navController.pauseMap()
    }

    override fun zoomIn(steps: Int) {
        Log.i(TAG, "Zoom in by $steps steps (handled by AMapNaviView)")
        mapAppMode.startInteraction()
        // AMapNaviView handles its own zoom controls
    }

    override fun zoomOut(steps: Int) {
        Log.i(TAG, "Zoom out by $steps steps (handled by AMapNaviView)")
        mapAppMode.startInteraction()
        // AMapNaviView handles its own zoom controls
    }

    override fun navigateTo(dest: LatLong) {
        Log.i(TAG, "Starting navigation to $dest")
        mapAppMode.startInteraction()
        navController.navigateTo(dest)
    }

    override fun recalcNavigation() {
        Log.i(TAG, "Recalculating navigation")
        navController.recalcNavigation()
    }

    override fun stopNavigation() {
        Log.i(TAG, "Stopping navigation")
        navController.stopNavigation()
    }

    fun destroy() {
        Log.i(TAG, "Destroying AmapController")
        // AmapNaviController handles its own cleanup
    }
}