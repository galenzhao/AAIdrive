package me.hufman.androidautoidrive.maps

import android.content.Context

class PlaceSearchProvider(private val context: Context) {
	fun getInstance(applicationContext: Context = context): MapPlaceSearch {
		return AddressPlaceSearch.getInstance(applicationContext)
	}
}