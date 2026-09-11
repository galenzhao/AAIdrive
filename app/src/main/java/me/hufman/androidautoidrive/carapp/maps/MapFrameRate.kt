package me.hufman.androidautoidrive.carapp.maps

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer

/**
 * How often captured map frames are sent to the car.
 * Virtual display / GL rendering stay at the system refresh rate so the map can paint.
 */
object MapFrameRate {
	const val MIN_FPS = 1
	const val MAX_FPS = 20

	fun fps(settings: AppSettings = AppSettingsViewer()): Int {
		val parsed = settings[AppSettings.KEYS.MAP_FPS].trim().toIntOrNull()
		if (parsed != null && parsed > 0) {
			return parsed.coerceIn(MIN_FPS, MAX_FPS)
		}
		val ms = settings[AppSettings.KEYS.MINFRAMETIME].toIntOrNull() ?: 1000
		return (1000 / ms.coerceIn(50, 5000)).coerceIn(MIN_FPS, MAX_FPS)
	}

	fun intervalMs(settings: AppSettings = AppSettingsViewer()): Int {
		return (1000 / fps(settings)).coerceIn(50, 5000)
	}
}
