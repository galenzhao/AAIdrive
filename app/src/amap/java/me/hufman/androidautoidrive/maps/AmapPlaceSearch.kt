package me.hufman.androidautoidrive.maps

import android.content.Context
import android.location.Location
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.location.AMapLocationClient
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

fun LatLonPoint.toLatLong(): LatLong {
	return LatLong(this.latitude, this.longitude)
}

fun MapResult(geocodeResult: GeocodeResult, origin: LatLong?): MapResult {
	val featureLocation = geocodeResult.geocodeAddressList?.getOrNull(0)?.latLonPoint?.toLatLong()
	val distance = if (origin != null && featureLocation != null) {
		featureLocation.distanceFrom(origin)
	} else { null }

	val address = geocodeResult.geocodeAddressList?.getOrNull(0)?.formatAddress ?: ""
	val name = geocodeResult.geocodeAddressList?.getOrNull(0)?.district ?: ""
	
	return MapResult(
		geocodeResult.geocodeAddressList?.getOrNull(0)?.adcode ?: "",
		name,
		address,
		featureLocation,
		distance?.toFloat()
	)
}

class AmapPlaceSearch(val searchEngine: GeocodeSearch, val locationProvider: CarLocationProvider): MapPlaceSearch {
	companion object {
		fun getInstance(context: Context, locationProvider: CarLocationProvider): AmapPlaceSearch {
			// Initialize AMap privacy compliance
			AMapLocationClient.updatePrivacyShow(context, true, true)
			AMapLocationClient.updatePrivacyAgree(context, true)
			
			val searchEngine = GeocodeSearch(context)
			return AmapPlaceSearch(searchEngine, locationProvider)
		}
	}

	override fun searchLocationsAsync(query: String): Deferred<List<MapResult>> {
		if (query.length < 3) {
			return CompletableDeferred(emptyList())
		}
		val results = CompletableDeferred<List<MapResult>>()
		val location = locationProvider.currentLocation
		val latLong = location?.let { LatLong(it.latitude, it.longitude) }
		
		val geocodeQuery = GeocodeQuery(query, "")
		searchEngine.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
			override fun onGeocodeSearched(geocodeResult: GeocodeResult?, errorCode: Int) {
				if (errorCode == AMapException.CODE_AMAP_SUCCESS && geocodeResult != null) {
					val resultPlaces = geocodeResult.geocodeAddressList?.map {
						MapResult(geocodeResult, latLong)
					} ?: emptyList()
					results.complete(resultPlaces)
				} else {
					results.complete(emptyList())
				}
			}

			override fun onRegeocodeSearched(regeocodeResult: RegeocodeResult?, errorCode: Int) {
				// Not used for forward geocoding
			}
		})
		
		searchEngine.getFromLocationNameAsyn(geocodeQuery)

		return results
	}

	override fun resultInformationAsync(resultId: String): Deferred<MapResult?> {
		return CompletableDeferred(null as MapResult?)
	}
}
