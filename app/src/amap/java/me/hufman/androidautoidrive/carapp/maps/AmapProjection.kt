package me.hufman.androidautoidrive.carapp.maps

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.MapView
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.Marker
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.maps2d.model.Polyline
import com.amap.api.maps2d.model.PolylineOptions
import io.bimmergestalt.idriveconnectkit.SubsetRHMIDimensions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.UpdatingSidebarRHMIDimensions
import me.hufman.androidautoidrive.maps.LatLong

@SuppressLint("Lifecycle")
class AmapProjection(val parentContext: Context, display: Display, private val appSettings: AppSettings,
                     private val locationProvider: AmapLocationSource): Presentation(parentContext, display) {

	val TAG = "AmapProjection"
	val mapView: MapView by lazy { findViewById(R.id.mapView) }
	val map: AMap by lazy { mapView.map }
	val mapWrapper: View by lazy { findViewById(R.id.mapViewWrapper) }
	var mapListener: Runnable? = null

	private var destinationMarker: Marker? = null
	private var routePolyline: Polyline? = null

	val fullDimensions = display.run {
		val dimension = Point()
		@Suppress("DEPRECATION")
		display.getSize(dimension)
		SubsetRHMIDimensions(dimension.x, dimension.y)
	}
	val sidebarDimensions = UpdatingSidebarRHMIDimensions(fullDimensions) {
		appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Initialize AMap privacy compliance
		AMapLocationClient.updatePrivacyShow(parentContext, true, true)
		AMapLocationClient.updatePrivacyAgree(parentContext, true)

		window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
		setContentView(R.layout.amap_projection)
	}

	override fun onStart() {
		super.onStart()
		Log.i(TAG, "Projection Start")
		mapView.onResume()
		applyCommonSettings()
		mapListener?.run()
	}

	/** Display settings that don't change based on user settings */
	fun applyCommonSettings() {
		map.uiSettings.isCompassEnabled = true
		map.uiSettings.isScaleControlsEnabled = true
		map.uiSettings.isZoomControlsEnabled = false
		map.uiSettings.isMyLocationButtonEnabled = false
		map.isMyLocationEnabled = true
	}

	/** Call this function whenever we think the settings have been changed and need to be applied */
	fun applySettings(settings: AmapSettings) {
		// the narrow-screen option centers the viewport to the middle of the display
		// so update the map's margin to match
		val margin = if (settings.mapWidescreen) 0 else fullDimensions.appWidth - sidebarDimensions.appWidth
		mapWrapper.setPadding(margin/2, fullDimensions.paddingTop, margin/2, 0)

		// Apply map style
		// AMap supports: MAP_TYPE_NORMAL, MAP_TYPE_SATELLITE
		when {
			settings.mapSatellite -> map.mapType = AMap.MAP_TYPE_SATELLITE
			else -> map.mapType = AMap.MAP_TYPE_NORMAL
		}

		// Apply traffic layer
		map.isTrafficEnabled = settings.mapTraffic

		// Apply buildings - AMap doesn't have a direct showBuildings method
		// Buildings are shown by default in AMap
	}

	fun drawNavigation(navController: AmapNavController) {
		// Clear previous navigation elements
		destinationMarker?.remove()
		routePolyline?.remove()

		val destination = navController.currentNavDestination
		Log.i(TAG, "Adding destination $destination")
		if (destination != null) {
			destinationMarker = map.addMarker(MarkerOptions()
					.position(LatLng(destination.latitude, destination.longitude))
					.title("Destination")
			)
		}

		val route = navController.currentNavRoute
		Log.i(TAG, "Adding route $route")
		if (route != null && route.paths.isNotEmpty()) {
			val path = route.paths[0]
			val latLngList = path.steps.map { step ->
				step.polyline.map { point ->
					LatLng(point.latitude, point.longitude)
				}
			}.flatten()

			routePolyline = map.addPolyline(PolylineOptions()
					.addAll(latLngList)
					.color(context.getColor(R.color.mapRouteLine))
					.width(8f)
			)
		}
	}

	override fun onStop() {
		super.onStop()
		Log.i(TAG, "Projection Stopped")
		mapView.onPause()
	}
}
