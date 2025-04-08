package me.hufman.androidautoidrive.maps

import android.content.Context
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.CarInformation

class PlaceSearchProvider(private val context: Context) {
	fun getInstance(applicationContext: Context): MapPlaceSearch {
		AppSettings.loadSettings(applicationContext);
		val appSettings = AppSettingsViewer();
		return MapboxPlaceSearch.getInstance(CdsLocationProvider(appSettings, CarInformation.cachedCdsData, false))
	}
}