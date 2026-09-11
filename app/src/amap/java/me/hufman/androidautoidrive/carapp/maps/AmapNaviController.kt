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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    private val handler = Handler(Looper.getMainLooper())

    var projection: AmapNaviProjection? = null
    var currentLocation: Location? = null

    private fun onMain(block: () -> Unit) {
        // Always post so RHMI / broadcast handlers can return before AMap work.
        handler.post(block)
    }

    fun onCarLocationUpdate(location: Location) {
        currentLocation = location
        projection?.onCarLocationUpdate(location)
        projection?.applySettings(AmapSettings.build(appSettings, location.toLatLong()))
    }

    override fun showMap() {
        Log.i(TAG, "Showing navigation map")
        onMain { showMapOnMain() }
    }

    private fun showMapOnMain() {
        if (projection == null) {
            Log.i(TAG, "First showing of the navigation map")
            this.projection = AmapNaviProjection(context, virtualDisplay.display, appSettings, carLocationProvider, mapAppMode)
        }

        try {
            if (projection?.isShowing == false) {
                projection?.show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show navi projection", e)
        }

        // register for location updates
        carLocationProvider.start()
        currentLocation?.let { projection?.onCarLocationUpdate(it) }

        // watch for map settings
        appSettings.callback = { applySettings() }
        applySettings(force = true)
    }

    override fun pauseMap() {
        onMain {
            // Route picker keeps HMI off the map page; do not tear down the Presentation or
            // CDS while routes are still being calculated / chosen.
            if (mapAppMode.isRouteSelectionPending) {
                return@onMain
            }
            projection?.hide()
            // Keep CDS flowing while navigating so extra GPS continues with the phone locked
            if (projection?.isNavigating != true) {
                carLocationProvider.stop()
            }
        }
    }

    private fun applySettings(force: Boolean = false) {
        val newSettings = AmapSettings.build(appSettings, currentLocation?.toLatLong())
        projection?.applySettings(newSettings)
    }

    override fun zoomIn(steps: Int) {
        mapAppMode.startInteraction()
        onMain { projection?.zoomIn(steps) }
    }

    override fun zoomOut(steps: Int) {
        mapAppMode.startInteraction()
        onMain { projection?.zoomOut(steps) }
    }

    override fun navigateTo(dest: LatLong) {
        mapAppMode.startInteraction()
        mapAppMode.currentNavDestination = dest
        onMain {
            showMapOnMain()
            projection?.navigateTo(dest)
        }
    }

    override fun selectRoute(routeId: Int) {
        mapAppMode.startInteraction()
        mapAppMode.finishRouteSelection()
        onMain {
            showMapOnMain()
            projection?.selectRoute(routeId)
        }
    }

    override fun recalcNavigation() {
        mapAppMode.startInteraction()
        onMain { projection?.recalcNavigation() }
    }

    override fun stopNavigation() {
        mapAppMode.currentNavDestination = null
        mapAppMode.finishRouteSelection()
        onMain { projection?.stopNavigation() }
    }

    fun destroy() {
        val teardown = {
            appSettings.callback = null
            val p = projection
            projection = null
            if (p != null) {
                try {
                    if (p.isShowing) {
                        p.hide()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to hide navi projection", e)
                }
                p.destroy()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            teardown()
            return
        }
        val done = CountDownLatch(1)
        handler.post {
            try {
                teardown()
            } finally {
                done.countDown()
            }
        }
        try {
            if (!done.await(5, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timed out waiting to destroy navi projection")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
