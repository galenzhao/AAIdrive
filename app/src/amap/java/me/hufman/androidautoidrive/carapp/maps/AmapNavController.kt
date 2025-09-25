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
		fun getInstance(context: Context, locationProvider: CarLocationProvider, callback: (AmapNavController) -> Unit): AmapNavController {
			// Initialize AMap privacy compliance
			AMapLocationClient.updatePrivacyShow(context, true, true)
			AMapLocationClient.updatePrivacyAgree(context, true)
			
			val routeSearch = RouteSearch(context)
			return AmapNavController(routeSearch, locationProvider, callback)
		}
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
			Log.w(TAG, "No car location yet, cancelling route search")
			return
		}
		routeNavigation(LatLong(currentLocation.latitude, currentLocation.longitude), dest)
	}

	fun stopNavigation() {
		Log.i(TAG, "Stopping navigation")
		currentNavDestination = null
		currentNavRoute = null
		callback(this)
	}

	private fun routeNavigation(start: LatLong, dest: LatLong) {
		val fromAndTo = RouteSearch.FromAndTo(start.toLatLonPoint(), dest.toLatLonPoint())
		val query = RouteSearch.DriveRouteQuery(fromAndTo, RouteSearch.DRIVING_SINGLE_SHORTEST, null, null, "")
		
		routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
			override fun onDriveRouteSearched(result: DriveRouteResult?, errorCode: Int) {
				if (errorCode == AMapException.CODE_AMAP_SUCCESS && result != null && result.paths.isNotEmpty()) {
					currentNavRoute = result
					Log.i(TAG, "Found route with ${result.paths.size} paths")
					callback(this@AmapNavController)
				} else {
					Log.w(TAG, "Failed to find route! Error code: $errorCode")
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
