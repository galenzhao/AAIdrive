package me.hufman.androidautoidrive.carapp.maps

import me.hufman.androidautoidrive.AppSettings

object MapToggleSettings {
	val mapSettings = listOf(
			AppSettings.KEYS.MAP_INVERT_SCROLL,
			AppSettings.KEYS.MAP_TILT,
			AppSettings.KEYS.MAP_BUILDINGS,
			AppSettings.KEYS.MAP_SATELLITE,
			AppSettings.KEYS.MAP_TRAFFIC,
	)
	val amapSettings = listOf(
			AppSettings.KEYS.AMAP_AVOID_CONGESTION,
			AppSettings.KEYS.AMAP_AVOID_HIGHWAY,
			AppSettings.KEYS.AMAP_AVOID_COST,
			AppSettings.KEYS.AMAP_PREFER_HIGHWAY,
	)
	val settings = mapSettings + amapSettings
}
