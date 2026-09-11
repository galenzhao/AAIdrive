package me.hufman.androidautoidrive.carapp.maps

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer

object MapNaviBehavior {
	const val renderDpi = 420
	const val defaultRenderScale = 2
	private const val minRenderScale = 1
	private const val maxRenderScale = 3
	// each capture buffer is 4 bytes per pixel, and there are several of them
	private const val maxCaptureDimension = 4096
	private const val maxCapturePixels = 3840L * 2160

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

	/** The configured scale, reduced until the capture of a width x height image fits the memory budget */
	fun renderScale(appSettings: AppSettings, width: Int, height: Int): Int {
		var scale = renderScale(appSettings)
		while (scale > minRenderScale && (width * scale > maxCaptureDimension ||
						height * scale > maxCaptureDimension ||
						width.toLong() * height * scale * scale > maxCapturePixels)) {
			scale--
		}
		return scale
	}

	fun emulatorSpeed(appSettings: AppSettings = AppSettingsViewer()): Int {
		return appSettings[AppSettings.KEYS.AMAP_EMULATOR_SPEED].toIntOrNull()
				?.coerceIn(10, 200)
				?: 80
	}
}
