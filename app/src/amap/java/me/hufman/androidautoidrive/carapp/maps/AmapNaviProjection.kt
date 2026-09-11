package me.hufman.androidautoidrive.carapp.maps

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt
import cn.hutool.core.util.CoordinateUtil
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.navi.*
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.AMapLaneInfo
import com.amap.api.navi.model.AMapModelCross
import com.amap.api.navi.model.AMapNaviCameraInfo
import com.amap.api.navi.model.AMapNaviCross
import com.amap.api.navi.model.AMapNaviLocation
import com.amap.api.navi.model.AMapNaviRouteNotifyData
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo
import com.amap.api.navi.model.AMapServiceAreaInfo
import com.amap.api.navi.model.AimLessModeCongestionInfo
import com.amap.api.navi.model.AimLessModeStat
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong
import me.hufman.androidautoidrive.maps.SimulatedCarLocation

@SuppressLint("Lifecycle")
class AmapNaviProjection(
    val parentContext: Context, 
    display: Display, 
    private val appSettings: AppSettings,
    private val locationProvider: CarLocationProvider,
    private val mapAppMode: MapAppMode
) : Presentation(parentContext, display), AMapNaviListener, AMapNaviViewListener {

    companion object {
        private const val TAG = "AmapNaviProjection"
    }

    val naviView: AMapNaviView by lazy { findViewById(R.id.naviView) }
    val naviWrapper: View by lazy { findViewById(R.id.naviViewWrapper) }
    var mapListener: Runnable? = null

    init {
        // Privacy must be agreed before AMapNavi.getInstance, which also needs location permission
        AMapLocationClient.updatePrivacyShow(parentContext, true, true)
        AMapLocationClient.updatePrivacyAgree(parentContext, true)
    }

    // 导航相关对象
    private val mAMapNavi: AMapNavi by lazy { AMapNavi.getInstance(parentContext) }
    var isNavigating = false
        private set
    private var currentDestination: LatLong? = null
    private var pendingDestination: LatLong? = null
    private var naviStartRequested = false
    private var autoStartAfterCalc = false
    private var routesPublished = false
    private var awaitingSecondRouteCallback = false
    private var naviReady = false
    private var naviInitFailed = false
    private var naviListenerAdded = false
    private var lastSettings: AmapSettings? = null
    private var emulatorNavi = false
    private val handler = Handler(Looper.getMainLooper())
    private val initRetryRunnable = Runnable {
        if (!naviReady && pendingDestination != null && !naviStartRequested) {
            // Allow one soft retry even after onInitNaviFailure; permanent fail still needs a new Presentation.
            Log.w(TAG, "AMap navi init callback missing/failed, retrying route (initFailed=$naviInitFailed)")
            naviInitFailed = false
            naviReady = true
            tryStartRoute()
        }
    }
    private val secondRouteTimeoutRunnable = Runnable {
        if (!routesPublished && awaitingSecondRouteCallback) {
            Log.w(TAG, "Timed out waiting for second route callback, publishing empty routes")
            publishCalculatedRoutes(intArrayOf(), allowEmpty = true)
        }
    }

    private fun postTryStartRoute() {
        handler.post {
            if (pendingDestination != null && !naviStartRequested) {
                tryStartRoute()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize AMap privacy compliance
        AMapLocationClient.updatePrivacyShow(parentContext, true, true)
        AMapLocationClient.updatePrivacyAgree(parentContext, true)

        window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
        applyLandscapeConfiguration()
        setContentView(R.layout.amap_navi_projection)
        try {
            naviView.onCreate(savedInstanceState)
            applyNaviViewOptions(AmapSettings.build(appSettings, locationProvider.currentLocation?.toLatLong()))
            naviView.dispatchConfigurationChanged(resources.configuration)
            Log.i(TAG, "AMapNaviView landscape=${naviView.isOrientationLandscape} config=${resources.configuration.orientation}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize AMapNaviView", e)
        }
        initNavi()
    }

    /**
     * AMap inflates portrait vs landscape chrome from [Configuration.orientation].
     * Virtual displays often copy the phone's portrait configuration even when the
     * pixel size is wide, so force landscape before [AMapNaviView] is created.
     */
    private fun applyLandscapeConfiguration() {
        val res = resources
        val metrics = res.displayMetrics
        val config = Configuration(res.configuration)
        val widthDp = (metrics.widthPixels * 160f / metrics.densityDpi).roundToInt().coerceAtLeast(1)
        val heightDp = (metrics.heightPixels * 160f / metrics.densityDpi).roundToInt().coerceAtLeast(1)
        config.orientation = Configuration.ORIENTATION_LANDSCAPE
        config.screenWidthDp = widthDp
        config.screenHeightDp = heightDp
        config.smallestScreenWidthDp = minOf(widthDp, heightDp)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, metrics)
        Log.i(TAG, "Presentation ${metrics.widthPixels}x${metrics.heightPixels} ${widthDp}x${heightDp}dp dpi=${metrics.densityDpi} ori=${config.orientation}")
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "NaviProjection Start")
        try {
            naviView.onResume()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resume AMapNaviView", e)
        }
        disableAmapBuiltinLocation()
        mapListener?.run()
    }

    override fun onStop() {
        Log.i(TAG, "NaviProjection Stopped")
        try {
            naviView.onPause()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pause AMapNaviView", e)
        }
        super.onStop()
    }

    fun destroy() {
        Log.i(TAG, "NaviProjection Destroyed")
        handler.removeCallbacksAndMessages(null)
        naviReady = false
        naviInitFailed = false
        pendingDestination = null
        val shouldStop = isNavigating || naviStartRequested
        naviStartRequested = false
        emulatorNavi = false
        awaitingSecondRouteCallback = false
        try {
            if (shouldStop) {
                mAMapNavi.stopNavi()
            }
            isNavigating = false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop AMap navi", e)
        }
        try {
            if (naviListenerAdded) {
                mAMapNavi.removeAMapNaviListener(this)
                naviListenerAdded = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove AMap navi listener", e)
        }
        try {
            naviView.onDestroy()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to destroy AMapNaviView", e)
        }
    }

    fun saveInstanceState(outState: Bundle) {
        try {
            naviView.onSaveInstanceState(outState)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save AMapNaviView state", e)
        }
    }

    private fun initNavi() {
        ensureNavi()
        try {
            naviView.setAMapNaviViewListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach AMapNaviView listener", e)
        }
    }

    private fun ensureNavi() {
        if (!naviListenerAdded) {
            mAMapNavi.addAMapNaviListener(this)
            naviListenerAdded = true
        }
        try {
            // Inner TTS crashes this process (FileTransManager / destroyed mutex).
            mAMapNavi.setUseInnerVoice(false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable AMap inner voice", e)
        }
        try {
            mAMapNavi.setMultipleRouteNaviMode(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable multiple route mode", e)
        }
        disableAmapPhoneGps()
        (locationProvider.currentLocation ?: SimulatedCarLocation.create()).let { feedExtraGps(it) }
        if (!naviReady) {
            handler.removeCallbacks(initRetryRunnable)
            handler.postDelayed(initRetryRunnable, 2000)
        }
        Log.i(TAG, "Navigation initialized with extra GPS and phone GPS disabled")
    }

    fun onCarLocationUpdate(location: Location) {
        if (!emulatorNavi) {
            feedExtraGps(location)
        }
        postTryStartRoute()
    }

    fun navigateTo(dest: LatLong) {
        Log.i(TAG, "Planning navigation to $dest")
        // Stop any in-progress navi before a new calculateDriveRoute.
        val shouldStop = isNavigating || naviStartRequested
        if (shouldStop) {
            try {
                mAMapNavi.stopNavi()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop previous AMap navi before new route", e)
            }
            isNavigating = false
        }
        currentDestination = dest
        pendingDestination = dest
        naviStartRequested = false
        autoStartAfterCalc = false
        routesPublished = false
        awaitingSecondRouteCallback = false
        locationProvider.start()
        ensureNavi()
        postTryStartRoute()
    }

    fun recalcNavigation() {
        val dest = currentDestination ?: return
        Log.i(TAG, "Recalculating navigation to $dest")
        pendingDestination = dest
        naviStartRequested = false
        autoStartAfterCalc = true
        routesPublished = false
        awaitingSecondRouteCallback = false
        locationProvider.start()
        ensureNavi()
        postTryStartRoute()
    }

    fun selectRoute(routeId: Int) {
        Log.i(TAG, "Selecting route $routeId")
        naviStartRequested = false
        try {
            mAMapNavi.selectRouteId(routeId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select route $routeId", e)
        }
        startActiveNavi()
    }

    fun stopNavigation() {
        Log.i(TAG, "Stopping navigation")
        val shouldStop = isNavigating || naviStartRequested
        pendingDestination = null
        naviStartRequested = false
        emulatorNavi = false
        autoStartAfterCalc = false
        routesPublished = false
        awaitingSecondRouteCallback = false
        try {
            // startNavi can run before onStartNavi flips isNavigating; still stop the engine.
            if (shouldStop) {
                mAMapNavi.stopNavi()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop AMap navi", e)
        }
        isNavigating = false
        currentDestination = null
    }

    private fun tryStartRoute() {
        val dest = pendingDestination ?: return
        if (!naviReady) {
            Log.i(TAG, "Holding route calculation until AMap navi is ready")
            return
        }
        val origin = locationProvider.currentLocation ?: SimulatedCarLocation.create()
        if (locationProvider.currentLocation == null) {
            Log.w(TAG, "No CDS location, using simulated origin ${origin.latitude},${origin.longitude}")
        }
        val amapOrigin = toAmapLocation(origin)
        try {
            startRouteCalculation(LatLong(amapOrigin.latitude, amapOrigin.longitude), toAmapLatLong(dest))
            pendingDestination = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to submit route calculation, will retry when navi is ready", e)
        }
    }

    private fun disableAmapPhoneGps() {
        try {
            mAMapNavi.setIsUseExtraGPSData(true)
            mAMapNavi.stopGPS()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable AMap phone GPS", e)
        }
        disableAmapBuiltinLocation()
    }

    private fun disableAmapBuiltinLocation() {
        try {
            val map = naviView.map
            map?.isMyLocationEnabled = false
            map?.uiSettings?.isMyLocationButtonEnabled = false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable AMap map location", e)
        }
        try {
            AMapNavi.releaseLocManager()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release AMap location manager", e)
        }
    }

    private fun feedExtraGps(location: Location) {
        try {
            val amapLocation = toAmapLocation(location)
            mAMapNavi.setIsUseExtraGPSData(true)
            mAMapNavi.setExtraGPSData(AMapLocation.LOCATION_TYPE_GPS, amapLocation)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to feed extra GPS to AMap", e)
        }
    }

    private fun toAmapLocation(location: Location): Location {
        val amapLocation = Location(location)
        if (!CoordinateUtil.outOfChina(location.longitude, location.latitude) && locationProvider.wgs84ToGcj02) {
            val coord = CoordinateUtil.wgs84ToGcj02(location.longitude, location.latitude)
            amapLocation.latitude = coord.lat
            amapLocation.longitude = coord.lng
        }
        if (amapLocation.time == 0L) {
            amapLocation.time = System.currentTimeMillis()
        }
        if (amapLocation.accuracy <= 0f) {
            amapLocation.accuracy = 8f
        }
        return amapLocation
    }

    private fun toAmapLatLong(latLong: LatLong): LatLong {
        if (!CoordinateUtil.outOfChina(latLong.longitude, latLong.latitude) && locationProvider.wgs84ToGcj02) {
            val coord = CoordinateUtil.wgs84ToGcj02(latLong.longitude, latLong.latitude)
            return LatLong(coord.lat, coord.lng)
        }
        return latLong
    }

    private fun startRouteCalculation(start: LatLong, dest: LatLong) {
        val startLatLng = NaviLatLng(start.latitude, start.longitude)
        val endLatLng = NaviLatLng(dest.latitude, dest.longitude)

        val startList = listOf(startLatLng)
        val endList = listOf(endLatLng)


        /**
         * 方法: int strategy=mAMapNavi.strategyConvert(congestion, avoidhightspeed, cost, hightspeed, multipleroute); 参数:
         *
         * @congestion 躲避拥堵
         * @avoidhightspeed 不走高速
         * @cost 避免收费
         * @hightspeed 高速优先
         * @multipleroute 多路径
         *
         * 说明: 以上参数都是boolean类型，其中multipleroute参数表示是否多条路线，如果为true则此策略会算出多条路线。
         * 注意: 不走高速与高速优先不能同时为true 高速优先与避免收费不能同时为true
         */
        var avoidHighway = appSettings[AppSettings.KEYS.AMAP_AVOID_HIGHWAY].toBoolean()
        var avoidCost = appSettings[AppSettings.KEYS.AMAP_AVOID_COST].toBoolean()
        var preferHighway = appSettings[AppSettings.KEYS.AMAP_PREFER_HIGHWAY].toBoolean()
        val avoidCongestion = appSettings[AppSettings.KEYS.AMAP_AVOID_CONGESTION].toBoolean()
        if (avoidHighway && preferHighway) {
            preferHighway = false
        }
        if (preferHighway && avoidCost) {
            avoidCost = false
        }
        var strategy = 0
        try {
            strategy = mAMapNavi.strategyConvert(avoidCongestion, avoidHighway, avoidCost, preferHighway, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert navi strategy", e)
        }
        Log.i(TAG, "Calculating multiple routes congestion=$avoidCongestion avoidHighway=$avoidHighway avoidCost=$avoidCost preferHighway=$preferHighway")
        mAMapNavi.calculateDriveRoute(startList, endList, null, strategy)
    }

    fun applySettings(settings: AmapSettings) {
        try {
            naviWrapper.setPadding(0, 0, 0, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply navi view settings", e)
        }
        if (lastSettings != settings) {
            lastSettings = settings
            applyNaviViewOptions(settings)
        }
        try {
            val map = naviView.map
            map?.isMyLocationEnabled = false
            map?.uiSettings?.isMyLocationButtonEnabled = false
            map?.isTrafficEnabled = settings.mapTraffic
            map?.showBuildings(settings.mapBuildings)
            map?.showMapText(settings.mapText)
            map?.uiSettings?.isScaleControlsEnabled = settings.scale
            map?.uiSettings?.isCompassEnabled = settings.compass
            map?.mapType = when {
                settings.mapSatellite -> com.amap.api.maps.AMap.MAP_TYPE_SATELLITE
                settings.nightAuto && !settings.mapDaytime -> com.amap.api.maps.AMap.MAP_TYPE_NIGHT
                settings.night && !settings.nightAuto -> com.amap.api.maps.AMap.MAP_TYPE_NIGHT
                else -> com.amap.api.maps.AMap.MAP_TYPE_NORMAL
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply AMap overlay settings", e)
        }
    }

    private fun applyNaviViewOptions(settings: AmapSettings) {
        try {
            val options = naviView.viewOptions ?: AMapNaviViewOptions()
            options.setSensorEnable(false)
            options.setSettingMenuEnabled(false)
            options.setLayoutVisible(settings.layout)
            options.setLaneInfoShow(settings.laneInfo)
            options.setRealCrossDisplayShow(settings.crossView)
            options.setModeCrossDisplayShow(settings.crossView)
            options.setTrafficBarEnabled(settings.trafficBar)
            options.setCompassEnabled(settings.compass)
            options.setAutoLockCar(settings.lockCar)
            options.setAutoChangeZoom(settings.autoZoom)
            options.setCameraBubbleShow(settings.cameras)
            options.setCameraInfoUpdateEnabled(settings.cameras)
            options.setTrafficLine(settings.trafficLine)
            options.setTrafficLayerEnabled(settings.mapTraffic)
            options.setEagleMapVisible(settings.eagle)
            options.setNaviArrowVisible(settings.naviArrow)
            options.setAutoNaviViewNightMode(settings.nightAuto)
            options.setNaviNight(if (settings.nightAuto) !settings.mapDaytime else settings.night)
            options.setTilt(if (settings.mapTilt) 45 else 0)
            if (settings.mapCustomStyle && settings.amapStyleUrl.isNotBlank()) {
                options.setCustomMapStylePath(settings.amapStyleUrl)
            }
            naviView.viewOptions = options
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply AMap navi view options", e)
        }
    }

    fun zoomIn(steps: Int = 1) {
        try {
            repeat(steps.coerceAtLeast(1)) {
                naviView.map?.animateCamera(com.amap.api.maps.CameraUpdateFactory.zoomIn())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to zoom in", e)
        }
    }

    fun zoomOut(steps: Int = 1) {
        try {
            repeat(steps.coerceAtLeast(1)) {
                naviView.map?.animateCamera(com.amap.api.maps.CameraUpdateFactory.zoomOut())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to zoom out", e)
        }
    }

    // AMapNaviListener 回调方法
    override fun onInitNaviFailure() {
        Log.e(TAG, "Navigation initialization failed")
        naviReady = false
        naviInitFailed = true
    }

    override fun onInitNaviSuccess() {
        Log.i(TAG, "Navigation initialization success")
        naviInitFailed = false
        naviReady = true
        postTryStartRoute()
    }

    override fun onStartNavi(type: Int) {
        Log.i(TAG, "Navigation started, type: $type")
        isNavigating = true
    }

    override fun onTrafficStatusUpdate() {
    }

    override fun onCalculateRouteSuccess(ids: IntArray) {
        Log.i(TAG, "Route calculation success, route IDs: ${ids.contentToString()}")
        publishCalculatedRoutes(ids, allowEmpty = false)
    }

    override fun onCalculateRouteFailure(errorInfo: Int) {
        Log.w(TAG, "Route calculation failed, error: $errorInfo")
        publishCalculatedRoutes(intArrayOf(), allowEmpty = true)
    }

    override fun onReCalculateRouteForYaw() {
        Log.i(TAG, "Recalculating route for yaw")
    }

    override fun onReCalculateRouteForTrafficJam() {
        Log.i(TAG, "Recalculating route for traffic jam")
    }

    override fun onArrivedWayPoint(wayID: Int) {
        Log.i(TAG, "Arrived at waypoint: $wayID")
    }

    override fun onGpsOpenStatus(enabled: Boolean) {
        Log.i(TAG, "GPS status: $enabled")
    }

    override fun onNaviInfoUpdate(naviInfo: NaviInfo?) {
        // 导航信息更新
        naviInfo?.let {
            Log.d(TAG, "Navigation info update: ${it.getCurrentRoadName()}")
        }
    }

    override fun updateCameraInfo(p0: Array<out AMapNaviCameraInfo>?) {
    }

    override fun updateIntervalCameraInfo(
        p0: AMapNaviCameraInfo?,
        p1: AMapNaviCameraInfo?,
        p2: Int
    ) {
    }

    override fun onServiceAreaUpdate(p0: Array<out AMapServiceAreaInfo>?) {
    }

    override fun showCross(p0: AMapNaviCross?) {
    }

    override fun hideCross() {
    }

    override fun showModeCross(p0: AMapModelCross?) {
    }

    override fun hideModeCross() {
    }

    override fun showLaneInfo(p0: Array<out AMapLaneInfo>?, p1: ByteArray?, p2: ByteArray?) {
    }

    override fun showLaneInfo(p0: AMapLaneInfo?) {
    }

    override fun hideLaneInfo() {
    }

    override fun onLocationChange(aMapNaviLocation: AMapNaviLocation?) {
        // 位置变化回调
        aMapNaviLocation?.let {
//            Log.d(TAG, "Location changed: ${it.latitude}, ${it.longitude}")
        }
    }

    override fun onGetNavigationText(type: Int, text: String?) {
        // 获取导航文本信息，用于语音播报
        text?.let {
            Log.i(TAG, "Navigation text: $text")
        }
    }

    override fun onGetNavigationText(text: String?) {
        // 获取导航文本信息，用于语音播报
        text?.let {
            Log.i(TAG, "Navigation text: $text")
        }
    }

    override fun onEndEmulatorNavi() {
        Log.i(TAG, "Emulator navigation ended")
        emulatorNavi = false
        isNavigating = false
        currentDestination = null
        mapAppMode.currentNavDestination = null
        mapAppMode.finishRouteSelection()
    }

    override fun onArriveDestination() {
        Log.i(TAG, "Arrived at destination")
        isNavigating = false
        currentDestination = null
        mapAppMode.currentNavDestination = null
        mapAppMode.finishRouteSelection()
    }

    override fun onCalculateRouteSuccess(result: AMapCalcRouteResult?) {
        Log.i(TAG, "Route calculation success with result: $result")
        publishCalculatedRoutes(result?.routeid ?: intArrayOf(), allowEmpty = false)
    }

    override fun onCalculateRouteFailure(result: AMapCalcRouteResult?) {
        Log.w(TAG, "Route calculation failed with result: $result")
        publishCalculatedRoutes(intArrayOf(), allowEmpty = true)
    }

    private fun publishCalculatedRoutes(ids: IntArray, allowEmpty: Boolean) {
        if (routesPublished) {
            return
        }
        val paths = try {
            mAMapNavi.naviPaths ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read navi paths", e)
            emptyMap()
        }
        val routeIds = ids.takeIf { it.isNotEmpty() } ?: paths.keys.map { it.toInt() }.toIntArray()
        val choices = ArrayList<MapRouteChoice>()
        for (id in routeIds) {
            val path = paths[id] ?: continue
            choices.add(MapRouteChoice(
                    id,
                    path.labels ?: "",
                    path.allTime,
                    path.allLength,
                    path.tollCost
            ))
        }
        if (choices.isEmpty()) {
            if (!awaitingSecondRouteCallback) {
                awaitingSecondRouteCallback = true
                Log.w(TAG, "Route callback before navi paths were ready, waiting for the other callback")
                handler.removeCallbacks(secondRouteTimeoutRunnable)
                handler.postDelayed(secondRouteTimeoutRunnable, 2500)
                return
            }
            if (!allowEmpty) {
                Log.w(TAG, "Both route success callbacks were empty")
            }
        }
        handler.removeCallbacks(secondRouteTimeoutRunnable)
        awaitingSecondRouteCallback = false
        routesPublished = true
        val requested = mapAppMode.completePendingRoutes(choices)
        if (autoStartAfterCalc || !requested) {
            autoStartAfterCalc = false
            if (choices.isNotEmpty()) {
                try {
                    mAMapNavi.selectRouteId(choices[0].routeId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to select default route", e)
                }
                startActiveNavi()
            }
        }
    }

    private fun startActiveNavi() {
        if (naviStartRequested) {
            return
        }
        naviStartRequested = true
        disableAmapPhoneGps()
        val origin = locationProvider.currentLocation ?: SimulatedCarLocation.create()
        if (locationProvider.isSimulated || SimulatedCarLocation.matches(origin)) {
            emulatorNavi = true
            try {
                mAMapNavi.setIsUseExtraGPSData(false)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release extra GPS for emulator", e)
            }
            try {
                mAMapNavi.setEmulatorNaviSpeed(MapNaviBehavior.emulatorSpeed(appSettings))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set emulator navi speed", e)
            }
            Log.i(TAG, "Starting emulator navigation from ${origin.latitude},${origin.longitude}")
            mAMapNavi.startNavi(NaviType.EMULATOR)
        } else {
            emulatorNavi = false
            feedExtraGps(origin)
            Log.i(TAG, "Starting GPS navigation with CDS extra GPS")
            mAMapNavi.startNavi(NaviType.GPS)
        }
    }

    override fun notifyParallelRoad(p0: Int) {
    }

    override fun OnUpdateTrafficFacility(p0: Array<out AMapNaviTrafficFacilityInfo>?) {
    }

    override fun OnUpdateTrafficFacility(p0: AMapNaviTrafficFacilityInfo?) {
    }

    override fun updateAimlessModeStatistics(p0: AimLessModeStat?) {
    }

    override fun updateAimlessModeCongestionInfo(p0: AimLessModeCongestionInfo?) {
    }

    override fun onPlayRing(p0: Int) {
    }

    override fun onNaviRouteNotify(p0: AMapNaviRouteNotifyData?) {
    }

    override fun onGpsSignalWeak(p0: Boolean) {
    }

    // AMapNaviViewListener 回调方法
    override fun onNaviSetting() {
        Log.i(TAG, "Navigation setting from view")
    }

    override fun onNaviCancel() {
        Log.i(TAG, "Navigation cancelled from view")
        stopNavigation()
    }

    override fun onNaviBackClick(): Boolean {
        Log.i(TAG, "Navigation back clicked")
        return false
    }

    override fun onNaviMapMode(type: Int) {
        Log.i(TAG, "Navigation map mode from view: $type")
    }

    override fun onNaviTurnClick() {
        Log.i(TAG, "Navigation turn clicked from view")
    }

    override fun onNextRoadClick() {
        Log.i(TAG, "Next road clicked from view")
    }

    override fun onScanViewButtonClick() {
        Log.i(TAG, "Scan view button clicked from view")
    }

    override fun onLockMap(isLock: Boolean) {
        Log.i(TAG, "Map lock from view: $isLock")
    }

    override fun onNaviViewLoaded() {
        Log.i(TAG, "Navigation view loaded from view")
    }

    override fun onMapTypeChanged(mapType: Int) {
        Log.i(TAG, "Map type changed from view: $mapType")
    }

    override fun onNaviViewShowMode(showMode: Int) {
        Log.i(TAG, "Navigation view show mode from view: $showMode")
    }
}
