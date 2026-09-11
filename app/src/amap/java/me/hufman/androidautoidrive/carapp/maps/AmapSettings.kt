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
		val nightAuto: Boolean,
		val night: Boolean,
		val layout: Boolean,
		val laneInfo: Boolean,
		val crossView: Boolean,
		val trafficBar: Boolean,
		val compass: Boolean,
		val scale: Boolean,
		val mapText: Boolean,
		val lockCar: Boolean,
		val autoZoom: Boolean,
		val cameras: Boolean,
		val trafficLine: Boolean,
		val eagle: Boolean,
		val naviArrow: Boolean,
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
					appSettings[AppSettings.KEYS.AMAP_NIGHT_AUTO].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_NIGHT].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_LAYOUT].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_LANE_INFO].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_CROSS_VIEW].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_TRAFFIC_BAR].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_COMPASS].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_SCALE].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_MAP_TEXT].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_LOCK_CAR].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_AUTO_ZOOM].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_CAMERAS].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_TRAFFIC_LINE].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_EAGLE].toBoolean(),
					appSettings[AppSettings.KEYS.AMAP_NAVI_ARROW].toBoolean(),
			)
		}
	}
}
