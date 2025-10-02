package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import me.hufman.androidautoidrive.AppSettingsObserver
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun Location.toLatLong(): LatLong = LatLong(this.latitude, this.longitude)
class AmapController(private val context: Context,
                     private val carLocationProvider: CarLocationProvider,
                     private val virtualDisplay: VirtualDisplay,
                     private val appSettings: AppSettingsObserver,
                     private val mapAppMode: MapAppMode): MapInteractionController {

	private val SHUTDOWN_WAIT_INTERVAL = 120000L   // milliseconds of inactivity before shutting down map

	var handler = Handler(Looper.getMainLooper())
	var projection: AmapProjection? = null

	val navController = AmapNaviController(context, carLocationProvider, virtualDisplay, appSettings, mapAppMode)
	private val amapLocationSource = AmapLocationSource()
	var currentLocation: Location? = null

	var currentSettings: AmapSettings = AmapSettings.build(appSettings, currentLocation?.toLatLong())

	var animatingCamera = false
	private var startZoom = 6f  // what zoom level we start the projection with
	private var currentZoom = 15f

	init {
		carLocationProvider.callback = { location ->
			handler.post {
				onLocationUpdate(location)
			}
		}
	}

	private fun onLocationUpdate(location: Location) {
		val firstView = currentLocation == null
		currentLocation = location
		// move the map dot to the new location
		amapLocationSource.onLocationUpdate(location)

		if (firstView) {  // first view
			applySettings(true)
			initCamera()
		} else {
			updateCamera()
		}
	}

	override fun showMap() {
		// cancel a shutdown timer
		handler.removeCallbacks(shutdownMapRunnable)

		// Use the new navigation controller
		navController.showMap()
	}

	override fun pauseMap() {
		navController.pauseMap()
	}
	private val shutdownMapRunnable = Runnable {
		Log.i(TAG, "Shutting down AmapProjection due to inactivity of ${SHUTDOWN_WAIT_INTERVAL}ms")
		projection?.hide()
		projection = null
	}

	fun applySettings(force: Boolean = false) {
		// AppSettings updates every ~10s from cachedCds updates
		// so check if anything relevant has changed before redrawing map
		val newSettings = AmapSettings.build(appSettings, currentLocation?.toLatLong())
		if (currentSettings != newSettings || force) {
			projection?.applySettings(newSettings)

			// these functions read from the currentSettings
			currentSettings = newSettings
			updateCamera()      // apply tilt settings
		}
	}

	override fun zoomIn(steps: Int) {
		mapAppMode.startInteraction()
		currentZoom = min(18f, currentZoom + steps)
		updateCamera()
	}

	override fun zoomOut(steps: Int) {
		mapAppMode.startInteraction()
		currentZoom = max(0f, currentZoom - steps)
		updateCamera()
	}

	private fun initCamera() {
		// set the camera to the starting position
		mapAppMode.startInteraction()
		val location = currentLocation
		if (location != null) {
			val cameraPosition = CameraPosition.Builder()
					.target(LatLng(location.latitude, location.longitude))
					.zoom(startZoom)
					.build()
			projection?.map?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
		}
	}

	private fun updateCamera() {
		if (animatingCamera) {
			return
		}
		val location = currentLocation ?: return
		val cameraPosition = CameraPosition.Builder()
				.target(LatLng(location.latitude, location.longitude))
				.zoom(currentZoom)
		if (location.hasBearing() && currentSettings.mapTilt) {
			cameraPosition
					.tilt(60f)
					.bearing(location.bearing)
		} else {
			cameraPosition
					.tilt(0f)
					.bearing(0f)
		}
		try {
			projection?.map?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition.build()))
		} catch (e: Exception) {
			// sometimes AMap crashes here?
		}
	}

	override fun navigateTo(dest: LatLong) {
		mapAppMode.startInteraction(NAVIGATION_MAP_STARTZOOM_TIME + 4000)
		navController.navigateTo(dest)
	}

	override fun recalcNavigation() {
		navController.recalcNavigation()
	}

	override fun stopNavigation() {
		navController.stopNavigation()
	}

	// Navigation is now handled by AMapNaviView, so these methods are no longer needed
}
