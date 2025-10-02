package me.hufman.androidautoidrive.carapp.maps

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.amap.api.location.AMapLocationClient
import com.amap.api.navi.*
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
import io.bimmergestalt.idriveconnectkit.SubsetRHMIDimensions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.UpdatingSidebarRHMIDimensions
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong
import com.amap.api.navi.enums.NaviType;
import com.amap.api.navi.model.AMapCalcRouteResult

@SuppressLint("Lifecycle")
class AmapNaviProjection(
    val parentContext: Context, 
    display: Display, 
    private val appSettings: AppSettings,
    private val locationProvider: CarLocationProvider
) : Presentation(parentContext, display), AMapNaviListener, AMapNaviViewListener {

    companion object {
        private const val TAG = "AmapNaviProjection"
    }

    val naviView: AMapNaviView by lazy { findViewById(R.id.naviView) }
    val naviWrapper: View by lazy { findViewById(R.id.naviViewWrapper) }
    var mapListener: Runnable? = null

    // 导航相关对象
    private val mAMapNavi: AMapNavi = AMapNavi.getInstance(parentContext)
    private var isNavigating = false
    private var currentDestination: LatLong? = null

    val fullDimensions = display.run {
        val dimension = Point()
        @Suppress("DEPRECATION")
        display.getSize(dimension)
        SubsetRHMIDimensions(dimension.x, dimension.y)
    }
    val sidebarDimensions = UpdatingSidebarRHMIDimensions(fullDimensions) {
        appSettings[AppSettings.KEYS.MAP_WIDESCREEN].toBoolean()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize AMap privacy compliance
        AMapLocationClient.updatePrivacyShow(parentContext, true, true)
        AMapLocationClient.updatePrivacyAgree(parentContext, true)

        window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
        setContentView(R.layout.amap_navi_projection)
        naviView.onCreate(savedInstanceState)

        initNavi()
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "NaviProjection Start")
        naviView.onResume()
        mapListener?.run()
    }

    override fun onStop() {
        Log.i(TAG, "NaviProjection Stopped")
        naviView.onPause()
        super.onStop()
    }

    fun destroy() {
        Log.i(TAG, "NaviProjection Destroyed")
        naviView.onDestroy()
//        mAMapNavi.destroy()
    }

    fun saveInstanceState(outState: Bundle) {
        naviView.onSaveInstanceState(outState)
    }

    private fun initNavi() {
        // 设置导航监听器
        mAMapNavi.addAMapNaviListener(this)
        naviView.setAMapNaviViewListener(this)
        
        // 启用内置语音播报
        mAMapNavi.setUseInnerVoice(true)
        
        // 设置模拟导航速度（用于测试）
        mAMapNavi.setEmulatorNaviSpeed(60)
        
        Log.i(TAG, "Navigation initialized")
    }

    fun navigateTo(dest: LatLong) {
        Log.i(TAG, "Starting navigation to $dest")
        currentDestination = dest

        val currentLocation = locationProvider.currentLocation
        if (currentLocation == null) {
            Log.w(TAG, "No car location yet, using Dalian, China as default location")
            // 大连市坐标: 38.914003, 121.614682
            val defaultLocation = LatLong(38.914003, 121.614682)
            startRouteCalculation(defaultLocation, dest)
            return
        }
        startRouteCalculation(LatLong(currentLocation.latitude, currentLocation.longitude), dest)
    }

    fun stopNavigation() {
        Log.i(TAG, "Stopping navigation")
        if (isNavigating) {
            mAMapNavi.stopNavi()
            isNavigating = false
        }
        currentDestination = null
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
        var strategy = 0
        try {
            //再次强调，最后一个参数为true时代表多路径，否则代表单路径
            strategy = mAMapNavi.strategyConvert(true, false, false, false, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 开始路径规划
        mAMapNavi.calculateDriveRoute(startList, endList, null, strategy)
    }

    fun applySettings(settings: AmapSettings) {
        // 应用地图设置
        val margin = if (settings.mapWidescreen) 0 else fullDimensions.appWidth - sidebarDimensions.appWidth
        naviWrapper.setPadding(margin/2, fullDimensions.paddingTop, margin/2, 0)
    }

    // AMapNaviListener 回调方法
    override fun onInitNaviFailure() {
        Log.e(TAG, "Navigation initialization failed")
    }

    override fun onInitNaviSuccess() {
        Log.i(TAG, "Navigation initialization success")
    }

    override fun onStartNavi(type: Int) {
        Log.i(TAG, "Navigation started, type: $type")
        isNavigating = true
    }

    override fun onTrafficStatusUpdate() {
        TODO("Not yet implemented")
    }

    override fun onCalculateRouteSuccess(ids: IntArray) {
        Log.i(TAG, "Route calculation success, route IDs: ${ids.contentToString()}")
        if (ids.isNotEmpty()) {
            // 自动开始导航
            mAMapNavi.startNavi(NaviType.EMULATOR)
        }
    }

    override fun onCalculateRouteFailure(errorInfo: Int) {
        Log.w(TAG, "Route calculation failed, error: $errorInfo")
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
        TODO("Not yet implemented")
    }

    override fun updateIntervalCameraInfo(
        p0: AMapNaviCameraInfo?,
        p1: AMapNaviCameraInfo?,
        p2: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun onServiceAreaUpdate(p0: Array<out AMapServiceAreaInfo>?) {
        TODO("Not yet implemented")
    }

    override fun showCross(p0: AMapNaviCross?) {
        TODO("Not yet implemented")
    }

    override fun hideCross() {
        TODO("Not yet implemented")
    }

    override fun showModeCross(p0: AMapModelCross?) {
        TODO("Not yet implemented")
    }

    override fun hideModeCross() {
        TODO("Not yet implemented")
    }

    override fun showLaneInfo(p0: Array<out AMapLaneInfo>?, p1: ByteArray?, p2: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun showLaneInfo(p0: AMapLaneInfo?) {
        TODO("Not yet implemented")
    }

    override fun hideLaneInfo() {
        TODO("Not yet implemented")
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
        isNavigating = false
    }

    override fun onArriveDestination() {
        Log.i(TAG, "Arrived at destination")
        isNavigating = false
    }

    override fun onCalculateRouteSuccess(result: AMapCalcRouteResult?) {
        Log.i(TAG, "Route calculation success with result: $result")
        result?.let {
            // 自动开始导航
            mAMapNavi.startNavi(NaviType.EMULATOR)
        }
    }

    override fun notifyParallelRoad(p0: Int) {
        TODO("Not yet implemented")
    }

    override fun OnUpdateTrafficFacility(p0: Array<out AMapNaviTrafficFacilityInfo>?) {
        TODO("Not yet implemented")
    }

    override fun OnUpdateTrafficFacility(p0: AMapNaviTrafficFacilityInfo?) {
        TODO("Not yet implemented")
    }

    override fun updateAimlessModeStatistics(p0: AimLessModeStat?) {
        TODO("Not yet implemented")
    }

    override fun updateAimlessModeCongestionInfo(p0: AimLessModeCongestionInfo?) {
        TODO("Not yet implemented")
    }

    override fun onPlayRing(p0: Int) {
        TODO("Not yet implemented")
    }

    override fun onNaviRouteNotify(p0: AMapNaviRouteNotifyData?) {
        TODO("Not yet implemented")
    }

    override fun onGpsSignalWeak(p0: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onCalculateRouteFailure(result: AMapCalcRouteResult?) {
        Log.w(TAG, "Route calculation failed with result: $result")
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
