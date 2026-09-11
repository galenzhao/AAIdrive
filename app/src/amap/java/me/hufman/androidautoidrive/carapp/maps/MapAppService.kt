package me.hufman.androidautoidrive.carapp.maps

import android.hardware.display.VirtualDisplay
import android.util.Log
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.carapp.CustomRHMIDimensions
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.cds.CDSDataProvider
import me.hufman.androidautoidrive.maps.CdsLocationProvider
import me.hufman.androidautoidrive.maps.AmapPlaceSearch
import java.lang.Exception

class MapAppService: CarAppService() {
	//val appSettings = AppSettingsViewer()
	var mapAppMode: MapAppMode? = null
	var mapApp: MapApp? = null
	var mapScreenCapture: VirtualDisplayScreenCapture? = null
	var virtualDisplay: VirtualDisplay? = null
	var mapController: AmapController? = null
	var mapListener: MapsInteractionControllerListener? = null

	override fun shouldStartApp(): Boolean {
		AppSettings.loadSettings(applicationContext);
		val appSettings = AppSettingsViewer();

		return appSettings[AppSettings.KEYS.ENABLED_MAPS].toBoolean()
	}

	override fun onCarStart() {
		Log.i(MainService.TAG, "Starting AMap")
		try {
			AppSettings.loadSettings(applicationContext);
			val appSettings = AppSettingsViewer();
			// Load navi .so early so first AMapNaviView open is less likely to hit UnsatisfiedLinkError
			AmapSdkBootstrap.warmUpNavi(applicationContext)
			val cdsData = CDSDataProvider()
			cdsData.setConnection(CarInformation.cdsData.asConnection(cdsData))
			val dimensions = CustomRHMIDimensions(RHMIDimensions.create(carInformation.capabilities), appSettings)
			Log.i(MainService.TAG, "AMap canvas ${dimensions.rhmiWidth}x${dimensions.rhmiHeight} pad=${dimensions.paddingLeft},${dimensions.paddingTop}")

			val mapAppMode = MapAppMode.build(dimensions, MutableAppSettingsReceiver(this, handler), cdsData, MusicAppMode.TRANSPORT_PORTS.fromPort(iDriveConnectionStatus.port) ?: MusicAppMode.TRANSPORT_PORTS.BT)
			val carLocationProvider = CdsLocationProvider(appSettings, cdsData, CarCapabilitiesSummarized(CarInformation()).isId4)
			this.mapAppMode = mapAppMode
			val renderScale = MapNaviBehavior.renderScale(appSettings, mapAppMode.imageWidth, mapAppMode.imageHeight)
			val captureConfig = object : ScreenCaptureConfig {
				override val maxWidth = mapAppMode.imageWidth * renderScale
				override val maxHeight = mapAppMode.imageHeight * renderScale
				override val compressFormat get() = mapAppMode.compressFormat
				override val compressQuality get() = mapAppMode.compressQuality
			}
			val mapScreenCapture = VirtualDisplayScreenCapture.build(captureConfig)
			this.mapScreenCapture = mapScreenCapture
			Log.i(MainService.TAG, "AMap virtual display ${mapScreenCapture.imageCapture.width}x${mapScreenCapture.imageCapture.height} @ ${MapNaviBehavior.renderDpi}dpi, scale ${renderScale}x → ${mapAppMode.imageWidth}x${mapAppMode.imageHeight}")
			if (renderScale >= 3) {
				Log.w(MainService.TAG, "AMap 3x capture is large; keep Map FPS low to avoid GC stalls")
			}
			val virtualDisplay = VirtualDisplayScreenCapture.createVirtualDisplay(applicationContext, mapScreenCapture.imageCapture, MapNaviBehavior.renderDpi)
			this.virtualDisplay = virtualDisplay
			val mapController = AmapController(applicationContext, carLocationProvider, virtualDisplay, MutableAppSettingsReceiver(applicationContext, null /* specifically main thread */), mapAppMode)
			this.mapController = mapController
			val mapPlaceSearch = AmapPlaceSearch.getInstance(applicationContext, carLocationProvider)
			val mapListener = MapsInteractionControllerListener(applicationContext, mapController)
			mapListener.onCreate()
			this.mapListener = mapListener

			Log.i(MainService.TAG, "AMap connecting RHMI")
			val mapApp = MapApp(iDriveConnectionStatus, securityAccess,
					CarAppAssetResources(applicationContext, "smartthings"),
					mapAppMode, carLocationProvider,
					MapInteractionControllerIntent(applicationContext), mapPlaceSearch, mapScreenCapture)
			this.mapApp = mapApp
			val handler = this.handler!!
			mapApp.onCreate(handler)
			Log.i(MainService.TAG, "AMap started")
		} catch (e: Exception) {
			Log.e(MainService.TAG, "AMap failed to start", e)
			onCarStop()
		}
	}

	override fun onCarStop() {
		mapAppMode?.resetSessionState()

		// Stop RHMI frame uploads before tearing down the ImageReader / VirtualDisplay.
		try {
			mapApp?.onDestroy()
		} catch (e: Exception) {
			Log.w(TAG, "Encountered an exception while shutting down MapApp frames", e)
		}

		try {
			mapController?.destroy()
			mapListener?.onDestroy()
			mapScreenCapture?.onDestroy()
			virtualDisplay?.release()

			mapScreenCapture = null
			virtualDisplay = null
			mapController = null
			mapListener = null
		} catch (e: Exception) {
			Log.w(TAG, "Encountered an exception while shutting down Maps", e)
		}

		mapApp?.disconnect()
		mapApp = null
	}
}
