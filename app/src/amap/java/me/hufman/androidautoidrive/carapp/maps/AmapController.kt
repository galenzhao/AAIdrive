package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.model.CameraPosition
import com.amap.api.maps2d.model.LatLng
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

	val navController = AmapNavController.getInstance(context, carLocationProvider) {
		drawNavigation()
		mapAppMode.currentNavDestination = it.currentNavDestination
	}
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

		if (projection == null) {
			Log.i(TAG, "First showing of the map")
			this.projection = AmapProjection(context, virtualDisplay.display, appSettings, amapLocationSource).apply {
				mapListener = Runnable {
					drawNavigation()
				}
			}
		}

		if (projection?.isShowing == false) {
			projection?.show()
		}

		drawNavigation()

		// nudge the camera to trigger a redraw, in case we changed windows
		if (!animatingCamera) {
			projection?.map?.animateCamera(CameraUpdateFactory.scrollBy(1f, 1f))
		}
		// register for location updates
		carLocationProvider.start()

		// watch for map settings
		appSettings.callback = {applySettings()}
		applySettings(force = true) // which also updates the settings in the projection for first draw
	}

	override fun pauseMap() {
		carLocationProvider.stop()

		handler.postDelayed(shutdownMapRunnable, SHUTDOWN_WAIT_INTERVAL)

		appSettings.callback = null
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
		animateNavigation()
	}

	override fun recalcNavigation() {
		navController.currentNavDestination?.let {
			navController.navigateTo(it)
		}
	}

	override fun stopNavigation() {
		navController.stopNavigation()
	}

	private fun animateNavigation() {
		// show a camera animation to zoom out to the whole navigation route
		val dest = navController.currentNavDestination ?: return
		val startLocation = currentLocation ?: return
		// zoom out to the full view
		val startPoint = LatLng(startLocation.latitude, startLocation.longitude)
		val destPoint = LatLng(dest.latitude, dest.longitude)

		val currentCamera = projection?.map?.cameraPosition
		if (currentCamera == null) return

		// Calculate bounds to fit both points
		val bounds = com.amap.api.maps2d.model.LatLngBounds.Builder()
				.include(startPoint)
				.include(destPoint)
				.build()

		animatingCamera = true
		handler.postDelayed({
			val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 100)
			try {
				projection?.map?.animateCamera(cameraUpdate)
			} catch (e: Exception) {
				// sometimes AMap crashes here?
			}
		}, 100)

		// then zoom back in to the user's chosen zoom
		handler.postDelayed({
			animatingCamera = false
			val location = currentLocation ?: return@postDelayed
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
		}, NAVIGATION_MAP_STARTZOOM_TIME.toLong())
	}

	private fun drawNavigation() {
		// make sure we are in the UI thread, and then draw navigation lines onto it
		// because route search comes back on a network thread
		if (Looper.myLooper() != handler.looper) {
			handler.post {
				projection?.drawNavigation(navController)
			}
		} else {
			projection?.drawNavigation(navController)
		}
	}
}
