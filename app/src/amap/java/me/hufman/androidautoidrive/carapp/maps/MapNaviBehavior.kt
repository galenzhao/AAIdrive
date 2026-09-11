package me.hufman.androidautoidrive.carapp.maps

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer

object MapNaviBehavior {
	const val renderDpi = 420
	const val defaultRenderScale = 2
	private const val minRenderScale = 1
	private const val maxRenderScale = 3

	val selectRouteBeforeStart: Boolean
		get() {
			val raw = AppSettingsViewer()[AppSettings.KEYS.AMAP_SELECT_ROUTE]
			return if (raw.isBlank()) true else raw.toBoolean()
		}

	fun renderScale(appSettings: AppSettings = AppSettingsViewer()): Int {
		return appSettings[AppSettings.KEYS.AMAP_RENDER_SCALE].toIntOrNull()
				?.coerceIn(minRenderScale, maxRenderScale)
				?: defaultRenderScale
	}

	fun emulatorSpeed(appSettings: AppSettings = AppSettingsViewer()): Int {
		return appSettings[AppSettings.KEYS.AMAP_EMULATOR_SPEED].toIntOrNull()
				?.coerceIn(10, 200)
				?: 80
	}
}
