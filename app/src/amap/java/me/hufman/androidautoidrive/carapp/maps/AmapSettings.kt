package me.hufman.androidautoidrive.carapp.maps

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.maps.LatLong
import me.hufman.androidautoidrive.utils.TimeUtils

data class AmapSettings(
		val mapWidescreen: Boolean,
		val mapDaytime: Boolean,
		val mapBuildings: Boolean,
		val mapTraffic: Boolean,
		val mapSatellite: Boolean,
		val mapTilt: Boolean,
		val mapCustomStyle: Boolean,
		val amapStyleUrl: String,
) {
	companion object {
		fun build(appSettings: AppSettings, location: LatLong?): AmapSettings {
			val daytime = location == null || TimeUtils.getDayMode(location)
			return AmapSettings(
					appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean(),
					daytime,
					appSettings[AppSettings.KEYS.MAP_BUILDINGS].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_TRAFFIC].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_SATELLITE].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_TILT].toBoolean(),
					appSettings[AppSettings.KEYS.MAP_CUSTOM_STYLE].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_STYLE_URL],
			)
		}
	}

	val mapStyleType: Int
		get() = when {
			mapCustomStyle && amapStyleUrl.isNotBlank() -> {
				// Custom style handling would go here
				if (mapDaytime) 1 else 2 // Normal or Night
			}
			mapSatellite -> 2 // Satellite
			else -> if (mapDaytime) 1 else 2 // Normal or Night
		}
}
