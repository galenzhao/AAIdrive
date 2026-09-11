package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.NaviSetting
import com.amap.api.services.core.ServiceSettings
import com.amap.apis.utils.core.api.AMapUtilCoreApi

/**
 * Manifest / privacy / collect bootstrap for Amap navi+map+location+search
 * (docs: 其他配置注意事项).
 *
 * Key + APSService + permissions live in `src/amap/AndroidManifest.xml`.
 * Call [prepare] before any AMapNavi / map / search SDK use.
 */
object AmapSdkBootstrap {
	private const val TAG = "AmapSdkBootstrap"
	@Volatile private var prepared = false

	fun prepare(context: Context) {
		if (prepared) return
		synchronized(this) {
			if (prepared) return
			val app = context.applicationContext
			try {
				// Location / Maps / Search / Navi each need their own privacy flags before use.
				AMapLocationClient.updatePrivacyShow(app, true, true)
				AMapLocationClient.updatePrivacyAgree(app, true)
				MapsInitializer.updatePrivacyShow(app, true, true)
				MapsInitializer.updatePrivacyAgree(app, true)
				ServiceSettings.updatePrivacyShow(app, true, true)
				ServiceSettings.updatePrivacyAgree(app, true)
				NaviSetting.updatePrivacyShow(app, true, true)
				NaviSetting.updatePrivacyAgree(app, true)
				// We feed CDS / extra GPS; disable optional device/info collection by default.
				AMapUtilCoreApi.setCollectInfoEnable(false)
				prepared = true
			} catch (e: Exception) {
				Log.w(TAG, "Amap privacy / collect bootstrap failed", e)
			}
		}
	}

	/** Docs: wrap getInstance in try/catch and null-check before use. */
	fun getNaviOrNull(context: Context): AMapNavi? {
		prepare(context)
		return try {
			AMapNavi.getInstance(context.applicationContext)
		} catch (e: Exception) {
			Log.e(TAG, "AMapNavi.getInstance failed", e)
			null
		}
	}

	/**
	 * Force-load AMapNavi native libs before inflating [com.amap.api.navi.AMapNaviView].
	 * View init can call AMapNaviLogger JNI before lazy getInstance would otherwise load .so.
	 */
	fun warmUpNavi(context: Context): AMapNavi? {
		val navi = getNaviOrNull(context)
		if (navi == null) {
			Log.w(TAG, "warmUpNavi: AMapNavi instance unavailable")
		}
		return navi
	}
}
