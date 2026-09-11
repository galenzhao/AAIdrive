package me.hufman.androidautoidrive.carapp.maps

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.MutableAppSettingsObserver
import me.hufman.androidautoidrive.carapp.FullImageConfig
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.cds.CDSData
import me.hufman.androidautoidrive.cds.CDSEventHandler
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.maps.LatLong
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred

class DynamicScreenCaptureConfig(val fullDimensions: RHMIDimensions,
                                 val appSettings: MutableAppSettingsObserver,
                                 val carTransport: MusicAppMode.TRANSPORT_PORTS,
                                 val timeProvider: () -> Long = {System.currentTimeMillis()}): ScreenCaptureConfig {
	companion object {
		const val RECENT_INTERACTION_THRESHOLD = 5000
	}

	// capture at the size of the image shown in the car, so it isn't cropped and upscaled
	override val maxWidth: Int = appSettings[AppSettings.KEYS.MAP_DISPLAY_WIDTH].trim().toIntOrNull()?.takeIf { it > 0 }
			?: fullDimensions.rhmiWidth
	override val maxHeight: Int = appSettings[AppSettings.KEYS.MAP_DISPLAY_HEIGHT].trim().toIntOrNull()?.takeIf { it > 0 }
			?: fullDimensions.rhmiHeight
	override val compressFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
	override val compressQuality: Int
		get() {
			// an empty or out-of-range setting falls back to the dynamic quality
			val configured = appSettings[AppSettings.KEYS.compressQuality].trim().toIntOrNull()
			if (configured != null && configured in 1..100) {
				return configured
			}
			val recentInteraction = recentInteractionUntil > timeProvider()
			return if (carTransport == MusicAppMode.TRANSPORT_PORTS.USB) {
				if (recentInteraction) 40 else 65
			} else {
				if (recentInteraction) 12 else 40
			}
		}

	var recentInteractionUntil: Long = 0
		private set

	fun startInteraction(timeoutMs: Int = DynamicScreenCaptureConfig.RECENT_INTERACTION_THRESHOLD) {
		recentInteractionUntil = max(recentInteractionUntil, timeProvider() + timeoutMs)
	}
}

private enum class RouteSelectionState { NONE, REQUESTED, SHOWN, CANCELLED }

/** What happened to a finished route calculation */
enum class RouteDelivery {
	/** shown to the user in the route list */
	DELIVERED,
	/** the user left the route list, so don't start navigating */
	CANCELLED,
	/** nobody asked to choose a route, so start navigating right away */
	NOT_REQUESTED
}

class MapAppMode(val fullDimensions: RHMIDimensions,
                 val appSettings: MutableAppSettingsObserver,
                 val cdsData: CDSData,
                 val screenCaptureConfig: DynamicScreenCaptureConfig): FullImageConfig, ScreenCaptureConfig by screenCaptureConfig {
	companion object {
		// whether the custom map is currently navigating somewhere
		private var currentNavDestination: LatLong? = null
			set(value) {
				currentNavDestinationObservable.postValue(value)
				field = value
			}
		private val currentNavDestinationObservable = MutableLiveData<LatLong?>()

		// route selection is driven from the car thread and completed from the map's main thread
		private val routeLock = Any()
		private var pendingRoutes = CompletableDeferred<List<MapRouteChoice>>()
		private var routeSelectionState = RouteSelectionState.NONE

		fun requestRouteSelection(): CompletableDeferred<List<MapRouteChoice>> = synchronized(routeLock) {
			pendingRoutes.complete(emptyList())
			pendingRoutes = CompletableDeferred()
			routeSelectionState = RouteSelectionState.REQUESTED
			pendingRoutes
		}

		fun completePendingRoutes(routes: List<MapRouteChoice>): RouteDelivery = synchronized(routeLock) {
			val delivery = when (routeSelectionState) {
				RouteSelectionState.REQUESTED -> RouteDelivery.DELIVERED
				RouteSelectionState.CANCELLED -> RouteDelivery.CANCELLED
				else -> RouteDelivery.NOT_REQUESTED
			}
			routeSelectionState = when {
				delivery == RouteDelivery.DELIVERED && routes.isNotEmpty() -> RouteSelectionState.SHOWN
				delivery == RouteDelivery.NOT_REQUESTED -> routeSelectionState
				else -> RouteSelectionState.NONE
			}
			pendingRoutes.complete(routes)
			delivery
		}

		/** A route was chosen, or navigation was stopped */
		fun finishRouteSelection() = synchronized(routeLock) {
			routeSelectionState = RouteSelectionState.NONE
			pendingRoutes.complete(emptyList())
		}

		/** The user left the route list without choosing, so late route results must not start navigating */
		fun cancelRouteSelection() = synchronized(routeLock) {
			routeSelectionState = if (routeSelectionState == RouteSelectionState.REQUESTED) {
				RouteSelectionState.CANCELLED
			} else {
				RouteSelectionState.NONE
			}
			pendingRoutes.complete(emptyList())
		}

		val isRouteSelectionPending: Boolean
			get() = synchronized(routeLock) {
				routeSelectionState == RouteSelectionState.REQUESTED || routeSelectionState == RouteSelectionState.SHOWN
			}

		fun resetSessionState() {
			currentNavDestination = null
			finishRouteSelection()
		}

		fun build(fullDimensions: RHMIDimensions,
		          appSettings: MutableAppSettingsObserver,
		          cdsData: CDSData,
		          carTransport: MusicAppMode.TRANSPORT_PORTS): MapAppMode {
			val screenCaptureConfig = DynamicScreenCaptureConfig(fullDimensions, appSettings, carTransport)
			return MapAppMode(fullDimensions, appSettings, cdsData, screenCaptureConfig)
		}
	}

	init {
		cdsData.addEventHandler(CDS.VEHICLE.UNITS, 10000, object: CDSEventHandler {
			override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
				// just subscribing in order to ensure that distanceUnits is updated
			}
		})
	}

	// current navigation status, for the UI to observe
	// wraps the static fields so that mock MapAppMode objects can be passed around for testing
	var currentNavDestination: LatLong?
		get() = MapAppMode.currentNavDestination
		set(value) {
			MapAppMode.currentNavDestination = value
		}
	val currentNavDestinationObservable: MutableLiveData<LatLong?>
		get() = MapAppMode.currentNavDestinationObservable

	val isRouteSelectionPending: Boolean
		get() = MapAppMode.isRouteSelectionPending

	fun requestRouteSelection() = MapAppMode.requestRouteSelection()
	fun completePendingRoutes(routes: List<MapRouteChoice>) = MapAppMode.completePendingRoutes(routes)
	fun finishRouteSelection() = MapAppMode.finishRouteSelection()
	fun cancelRouteSelection() = MapAppMode.cancelRouteSelection()
	fun resetSessionState() = MapAppMode.resetSessionState()

	// navigation distance units
	val distanceUnits: CDSVehicleUnits.Distance
		get() = CDSVehicleUnits.fromCdsProperty(cdsData[CDSProperty.VEHICLE_UNITS]).distanceUnits

	// toggleable settings
	val settings = listOfNotNull(
			// only show the Widescreen option if the car screen is wide
			if (fullDimensions.rhmiWidth >= 1000)        // RHMIDimensions widescreen cut-off
				AppSettings.KEYS.MAP_WIDESCREEN else null
			) + MapToggleSettings.settings + listOfNotNull(
			// add the Mapbox style toggle if it is filled in
			if (BuildConfig.FLAVOR_map=="mapbox" && appSettings[AppSettings.KEYS.MAPBOX_STYLE_URL].isNotBlank())
				AppSettings.KEYS.MAP_CUSTOM_STYLE else null
	)

	// Fill the configured RHMI canvas (including DIMENSIONS_* used by the emulator).
	// FullImageView positions at (-padding + offset); default offset cancels padding so the image starts at 0,0.
	override val rhmiDimensions = fullDimensions

	// the area of the canvas not covered by the car's split screen, depending on the widescreen setting
	// map flavors use it to keep the map content centered in the visible part of the image
	val appDimensions = UpdatingSidebarRHMIDimensions(fullDimensions) { isWidescreen }

	val isWidescreen: Boolean
		get() = appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()
	override val invertScroll: Boolean
		get() = appSettings[AppSettings.KEYS.MAP_INVERT_SCROLL].toBoolean()
	override val imageWidth: Int
		get() = appSettings[AppSettings.KEYS.MAP_DISPLAY_WIDTH].toIntOrNull()?.takeIf { it > 0 }
				?: rhmiDimensions.rhmiWidth
	override val imageHeight: Int
		get() = appSettings[AppSettings.KEYS.MAP_DISPLAY_HEIGHT].toIntOrNull()?.takeIf { it > 0 }
				?: rhmiDimensions.rhmiHeight
	override val imageOffsetX: Int
		get() = parseOptionalOffset(AppSettings.KEYS.MAP_DISPLAY_OFFSET_X) ?: rhmiDimensions.paddingLeft
	override val imageOffsetY: Int
		get() = parseOptionalOffset(AppSettings.KEYS.MAP_DISPLAY_OFFSET_Y) ?: rhmiDimensions.paddingTop

	private fun parseOptionalOffset(key: AppSettings.KEYS): Int? {
		val raw = appSettings[key].trim()
		if (raw.isEmpty()) return null
		return raw.toIntOrNull()
	}

	// screen capture quality adjustment
	fun startInteraction(timeoutMs: Int = DynamicScreenCaptureConfig.RECENT_INTERACTION_THRESHOLD) {
		screenCaptureConfig.startInteraction(timeoutMs)
	}
}