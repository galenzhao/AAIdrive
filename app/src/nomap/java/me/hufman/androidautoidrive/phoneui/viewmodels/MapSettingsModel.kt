package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.StringLiveSetting

class MapSettingsModel(appContext: Context): ViewModel() {
	class Factory(val appContext: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return MapSettingsModel(appContext) as T
		}
	}

	val mapEnabled = BooleanLiveSetting(appContext, AppSettings.KEYS.ENABLED_MAPS)
	val showAdvancedSettings = BooleanLiveSetting(appContext, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS)
	val mapPhoneGps = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_USE_PHONE_GPS)
	val mapWidescreen = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_WIDESCREEN)
	val mapDisplayWidth = StringLiveSetting(appContext, AppSettings.KEYS.MAP_DISPLAY_WIDTH)
	val mapDisplayHeight = StringLiveSetting(appContext, AppSettings.KEYS.MAP_DISPLAY_HEIGHT)
	val mapDisplayOffsetX = StringLiveSetting(appContext, AppSettings.KEYS.MAP_DISPLAY_OFFSET_X)
	val mapDisplayOffsetY = StringLiveSetting(appContext, AppSettings.KEYS.MAP_DISPLAY_OFFSET_Y)
	val mapFps = StringLiveSetting(appContext, AppSettings.KEYS.MAP_FPS)
	val mapCompressQuality = StringLiveSetting(appContext, AppSettings.KEYS.compressQuality)
	val mapInvertZoom = BooleanLiveSetting(appContext, AppSettings.KEYS.MAP_INVERT_SCROLL)
}