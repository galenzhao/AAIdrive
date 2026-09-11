package me.hufman.androidautoidrive.maps

import android.content.Context
import me.hufman.androidautoidrive.CarInformation

class PlaceSearchProvider(private val context: Context) {
	fun getInstance(applicationContext: Context = context): MapPlaceSearch {
		val locationProvider = CdsLocationProvider(CarInformation.cachedCdsData, false)
		return GMapsPlaceSearch.getInstance(applicationContext, locationProvider)
	}
}