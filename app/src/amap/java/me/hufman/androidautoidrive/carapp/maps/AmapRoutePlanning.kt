package me.hufman.androidautoidrive.carapp.maps

import android.util.Log
import com.amap.api.maps.model.LatLng
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.enums.PathPlanningStrategy
import com.amap.api.navi.model.AMapCarInfo
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.navi.model.NaviPoi
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.maps.LatLong

/**
 * Drive (car) route planning helpers.
 * Supports strategy, lat/lng, POI, start bearing, waypoints, plate restriction.
 * Truck sizing / carType≠0 is intentionally not supported.
 */
object AmapRoutePlanning {
	private const val TAG = "AmapRoutePlanning"
	const val MAX_WAYPOINTS = 16

	fun resolveStrategy(appSettings: AppSettings, navi: AMapNavi): Int {
		val explicit = appSettings[AppSettings.KEYS.AMAP_ROUTE_STRATEGY].trim().toIntOrNull()
		if (explicit != null && explicit >= 0) {
			return explicit
		}
		var avoidHighway = appSettings[AppSettings.KEYS.AMAP_AVOID_HIGHWAY].toBoolean()
		var avoidCost = appSettings[AppSettings.KEYS.AMAP_AVOID_COST].toBoolean()
		var preferHighway = appSettings[AppSettings.KEYS.AMAP_PREFER_HIGHWAY].toBoolean()
		val avoidCongestion = appSettings[AppSettings.KEYS.AMAP_AVOID_CONGESTION].toBoolean()
		if (avoidHighway && preferHighway) preferHighway = false
		if (preferHighway && avoidCost) avoidCost = false
		return try {
			navi.strategyConvert(avoidCongestion, avoidHighway, avoidCost, preferHighway, true)
		} catch (e: Exception) {
			Log.w(TAG, "strategyConvert failed, using MULTIPLE_ROUTES_DEFAULT", e)
			PathPlanningStrategy.DRIVING_MULTIPLE_ROUTES_DEFAULT
		}
	}

	/** Plate + restriction for a normal car only (carType=0). */
	fun applyCarInfo(navi: AMapNavi, appSettings: AppSettings) {
		val plate = appSettings[AppSettings.KEYS.AMAP_CAR_NUMBER].trim()
		val restriction = appSettings[AppSettings.KEYS.AMAP_CAR_RESTRICTION].toBoolean()
		navi.setCarInfo(AMapCarInfo().apply {
			setCarType("0")
			setRestriction(restriction)
			if (plate.isNotEmpty()) setCarNumber(plate)
		})
	}

	fun calculate(navi: AMapNavi, appSettings: AppSettings, request: AmapRouteRequest): Boolean {
		val strategy = resolveStrategy(appSettings, navi)
		try {
			applyCarInfo(navi, appSettings)
		} catch (e: Exception) {
			Log.w(TAG, "Failed to set AMapCarInfo before route calc", e)
		}

		val ways = request.waypoints.take(MAX_WAYPOINTS)
		val usePoi = !request.start.poiId.isNullOrBlank() ||
				!request.end.poiId.isNullOrBlank() ||
				ways.any { !it.poiId.isNullOrBlank() } ||
				request.startBearingDeg != null

		return try {
			val ok = if (usePoi) {
				navi.calculateDriveRoute(
					request.start.toNaviPoi(request.startBearingDeg),
					request.end.toNaviPoi(null),
					ways.map { it.toNaviPoi(null) }.ifEmpty { null },
					strategy,
				)
			} else {
				navi.calculateDriveRoute(
					listOf(request.start.toNaviLatLng()),
					listOf(request.end.toNaviLatLng()),
					ways.map { it.toNaviLatLng() }.ifEmpty { null },
					strategy,
				)
			}
			Log.i(TAG, "calculateDriveRoute ok=$ok strategy=$strategy poi=$usePoi ways=${ways.size}")
			ok
		} catch (e: Exception) {
			Log.w(TAG, "calculateDriveRoute failed", e)
			false
		}
	}

	fun latLngRequest(
		start: LatLong,
		end: LatLong,
		endName: String? = null,
		endPoiId: String? = null,
		waypoints: List<AmapRoutePoint> = emptyList(),
		startBearingDeg: Float? = null,
	): AmapRouteRequest {
		return AmapRouteRequest(
			start = AmapRoutePoint(null, start.latitude, start.longitude, null),
			end = AmapRoutePoint(endName, end.latitude, end.longitude, endPoiId),
			waypoints = waypoints,
			startBearingDeg = startBearingDeg,
		)
	}
}

data class AmapRoutePoint(
	val name: String?,
	val latitude: Double?,
	val longitude: Double?,
	val poiId: String?,
) {
	fun toNaviLatLng(): NaviLatLng {
		require(latitude != null && longitude != null) { "lat/lng required" }
		return NaviLatLng(latitude, longitude)
	}

	fun toNaviPoi(bearingDeg: Float?): NaviPoi {
		val latLng = if (latitude != null && longitude != null) LatLng(latitude, longitude) else null
		val poi = NaviPoi(name ?: "", latLng, poiId ?: "")
		if (bearingDeg != null && poiId.isNullOrBlank() && latLng != null) {
			poi.direction = bearingDeg
		}
		return poi
	}
}

data class AmapRouteRequest(
	val start: AmapRoutePoint,
	val end: AmapRoutePoint,
	val waypoints: List<AmapRoutePoint> = emptyList(),
	/** Applied only when start has lat/lng and no poiId. */
	val startBearingDeg: Float? = null,
)
