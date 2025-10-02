package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.util.Log
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.location.AMapLocationClient
import com.amap.api.services.route.BusRouteResult
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.RideRouteResult
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.WalkRouteResult
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong

fun LatLong.toLatLonPoint(): LatLonPoint {
	return LatLonPoint(this.latitude, this.longitude)
}

class AmapNavController(val routeSearch: RouteSearch, val locationProvider: CarLocationProvider, val callback: (AmapNavController) -> Unit) {
	companion object {
		private const val TAG = "AmapNavController"
		fun getInstance(context: Context, locationProvider: CarLocationProvider, callback: (AmapNavController) -> Unit): AmapNavController {
			// Initialize AMap privacy compliance
			AMapLocationClient.updatePrivacyShow(context, true, true)
			AMapLocationClient.updatePrivacyAgree(context, true)
			
			val routeSearch = RouteSearch(context)
			return AmapNavController(routeSearch, locationProvider, callback, context)
		}
	}

	private lateinit var voiceService: AmapVoiceService

	constructor(routeSearch: RouteSearch, locationProvider: CarLocationProvider, callback: (AmapNavController) -> Unit, context: Context) : this(routeSearch, locationProvider, callback) {
		voiceService = AmapVoiceService(context)
	}

	var currentNavDestination: LatLong? = null
		private set
	var currentNavRoute: DriveRouteResult? = null
		private set

	fun navigateTo(dest: LatLong) {
		Log.i(TAG, "Starting navigation to $dest")
		currentNavDestination = dest

		val currentLocation = locationProvider.currentLocation
		if (currentLocation == null) {
			Log.w(TAG, "No car location yet, using Dalian, China as default location")
			// 大连市坐标: 38.914003, 121.614682
			val defaultLocation = LatLong(38.914003, 121.614682)
			routeNavigation(defaultLocation, dest)
			return
		}
		routeNavigation(LatLong(currentLocation.latitude, currentLocation.longitude), dest)
	}

	fun stopNavigation() {
		Log.i(TAG, "Stopping navigation")
		currentNavDestination = null
		currentNavRoute = null
		voiceService.speak("导航已停止")
		callback(this)
	}

	fun speakNavigationInstruction(instruction: String) {
		voiceService.speak(instruction)
	}

	fun destroy() {
		voiceService.shutdown()
	}

	private fun routeNavigation(start: LatLong, dest: LatLong) {
		val fromAndTo = RouteSearch.FromAndTo(start.toLatLonPoint(), dest.toLatLonPoint())
		val query = RouteSearch.DriveRouteQuery(fromAndTo, RouteSearch.DRIVING_SINGLE_SHORTEST, null, null, "")
		
		routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
			override fun onDriveRouteSearched(result: DriveRouteResult?, errorCode: Int) {
				if (errorCode == AMapException.CODE_AMAP_SUCCESS && result != null && result.paths.isNotEmpty()) {
					currentNavRoute = result
					Log.i(TAG, "Found route with ${result.paths.size} paths")
					
					// 语音播报路线规划成功
					val path = result.paths[0]
					val distance = path.distance / 1000.0 // 转换为公里
					val duration = path.duration / 60.0 // 转换为分钟
					val voiceText = "路线规划成功，距离${String.format("%.1f", distance)}公里，预计用时${String.format("%.0f", duration)}分钟"
					voiceService.speak(voiceText)
					
					callback(this@AmapNavController)
				} else {
					Log.w(TAG, "Failed to find route! Error code: $errorCode")
					// 语音播报路线规划失败
					voiceService.speak("路线规划失败，请检查网络连接或重新选择目的地")
				}
			}

			override fun onBusRouteSearched(result: BusRouteResult?, errorCode: Int) {
				// Not used for driving navigation
			}

			override fun onWalkRouteSearched(result: WalkRouteResult?, errorCode: Int) {
				// Not used for driving navigation
			}

			override fun onRideRouteSearched(result: RideRouteResult?, errorCode: Int) {
				// Not used for driving navigation
			}
		})
		
		routeSearch.calculateDriveRouteAsyn(query)
	}
}
