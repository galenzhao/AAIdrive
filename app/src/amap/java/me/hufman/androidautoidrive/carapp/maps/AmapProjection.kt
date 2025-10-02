package me.hufman.androidautoidrive.carapp.maps

//import android.R
import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import io.bimmergestalt.idriveconnectkit.SubsetRHMIDimensions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.UpdatingSidebarRHMIDimensions


@SuppressLint("Lifecycle")
class AmapProjection(val parentContext: Context, display: Display, private val appSettings: AppSettings,
                     private val locationProvider: AmapLocationSource): Presentation(parentContext, display),
	AMapLocationListener {

	val TAG = "AmapProjection"
	val mapView: MapView by lazy { findViewById(R.id.mapView) }
	val map: AMap by lazy { mapView.map }
	val mapWrapper: View by lazy { findViewById(R.id.mapViewWrapper) }
	var mapListener: Runnable? = null

	//声明AMapLocationClient类对象
	private val mLocationClient: AMapLocationClient = AMapLocationClient(parentContext)

	//声明AMapLocationClientOption对象
	private val mLocationOption: AMapLocationClientOption = AMapLocationClientOption()

	companion object {
		private const val MAP_ZOOM_SIZE = 18
	}

	private var destinationMarker: Marker? = null
	private var routePolyline: Polyline? = null

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
		setContentView(R.layout.amap_projection)
		mapView.onCreate(savedInstanceState)

		initMap()
	}


	override fun onStart() {
		super.onStart()
		Log.i(TAG, "Projection Start")
		mapView.onResume()
		applyCommonSettings()
		mapListener?.run()
	}

	/** Display settings that don't change based on user settings */
	fun applyCommonSettings() {
		// 地图UI设置
		map.uiSettings.isCompassEnabled = false  // 隐藏指南针
		map.uiSettings.isScaleControlsEnabled = false  // 隐藏比例尺
		map.uiSettings.isZoomControlsEnabled = false  // 隐藏缩放按钮
		map.uiSettings.isMyLocationButtonEnabled = false  // 隐藏定位按钮
//		map.uiSettings.isRotateGesturesEnabled = true  // 允许旋转手势
//		map.uiSettings.isTiltGesturesEnabled = true  // 允许倾斜手势
		map.uiSettings.isScrollGesturesEnabled = true  // 允许滚动手势
		map.uiSettings.isZoomGesturesEnabled = true  // 允许缩放手势
		
		// 启用定位
		map.isMyLocationEnabled = true
		
		// 设置地图最小缩放级别
//		map.minZoomLevel = 3f
		// 设置地图最大缩放级别
//		map.maxZoomLevel = 20f
	}

	/** Call this function whenever we think the settings have been changed and need to be applied */
	fun applySettings(settings: AmapSettings) {
		// the narrow-screen option centers the viewport to the middle of the display
		// so update the map's margin to match
		val margin = if (settings.mapWidescreen) 0 else fullDimensions.appWidth - sidebarDimensions.appWidth
		mapWrapper.setPadding(margin/2, fullDimensions.paddingTop, margin/2, 0)

		// Apply map style
		// AMap supports: MAP_TYPE_NORMAL, MAP_TYPE_SATELLITE
		when {
			settings.mapSatellite -> map.mapType = AMap.MAP_TYPE_SATELLITE
			else -> map.mapType = AMap.MAP_TYPE_NORMAL
		}

		// Apply traffic layer
		map.isTrafficEnabled = settings.mapTraffic

		// Apply buildings - AMap doesn't have a direct showBuildings method
		// Buildings are shown by default in AMap
	}

	fun drawNavigation(navController: AmapNavController) {
		// 清除之前的导航元素
		destinationMarker?.remove()
		routePolyline?.remove()

		val destination = navController.currentNavDestination
		Log.i(TAG, "Adding destination $destination")
		if (destination != null) {
			// 添加目的地标记
			destinationMarker = map.addMarker(MarkerOptions()
					.position(LatLng(destination.latitude, destination.longitude))
					.title("目的地")
					.snippet("导航终点")
			)
		}

		val route = navController.currentNavRoute
		Log.i(TAG, "Adding route $route")
		if (route != null && route.paths.isNotEmpty()) {
			val path = route.paths[0]
			val latLngList = path.steps.map { step ->
				step.polyline.map { point ->
					LatLng(point.latitude, point.longitude)
				}
			}.flatten()

			// 绘制导航路线
			if (latLngList.isNotEmpty()) {
				routePolyline = map.addPolyline(PolylineOptions()
						.addAll(latLngList)
						.color(context.getColor(R.color.mapRouteLine))
						.width(12f)  // 增加路线宽度
						.setDottedLine(false)  // 实线
				)
				
				// 自动调整地图视野以显示完整路线
				val bounds = com.amap.api.maps.model.LatLngBounds.Builder()
				latLngList.forEach { bounds.include(it) }
				val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
				map.animateCamera(cameraUpdate)
			}
		}
	}

	override fun onStop() {
		Log.i(TAG, "Projection Stopped")
		mapView.onPause()
		super.onStop()
	}

//	override fun onDestroy() {
//		Log.i(TAG, "Projection Destroyed")
//		// 停止定位
//		mLocationClient.stopLocation()
//		mLocationClient.onDestroy()
//
//		// 销毁地图
//		mapView.onDestroy()
//		super.onDestroy()
//	}
//
//	override fun onSaveInstanceState(outState: Bundle) {
//		super.onSaveInstanceState(outState)
//		mapView.onSaveInstanceState(outState)
//	}


	private fun initMap() {
		// 设置地图初始位置为大连（默认位置）
		val defaultLocation = LatLng(38.914003, 121.614682) // 大连市坐标
		val mCameraUpdate = CameraUpdateFactory.newLatLngZoom(defaultLocation, MAP_ZOOM_SIZE.toFloat())
		map.moveCamera(mCameraUpdate)

		// 自定义定位蓝点样式
		val myLocationStyle = MyLocationStyle()
		// 连续定位、且将视角移动到地图中心点，定位蓝点跟随设备移动
		myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE)
		// 设置定位间隔，单位毫秒，默认为2000ms
		myLocationStyle.interval(2000)
		// 设置定位蓝点的Style
		map.setMyLocationStyle(myLocationStyle)

		// 设置定位回调监听
		mLocationClient.setLocationListener(this)

		// 设置定位模式为高精度模式
		mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy)
		// 获取最近3s内精度最高的一次定位结果
		mLocationOption.setOnceLocationLatest(true)
		// 设置是否返回地址信息
		mLocationOption.setNeedAddress(true)
		// 设置是否允许模拟位置
		mLocationOption.setMockEnable(false)
		// 关闭缓存机制
		mLocationOption.setLocationCacheEnable(false)
		// 设置定位间隔，单位毫秒
		mLocationOption.setInterval(2000)
		mLocationClient.setLocationOption(mLocationOption)

		// 启用定位蓝点显示
		map.setMyLocationEnabled(true)
		// 隐藏缩放按钮
		map.getUiSettings().setZoomControlsEnabled(false)
		// 隐藏默认定位按钮
		map.getUiSettings().setMyLocationButtonEnabled(false)
		// 隐藏指南针
		map.getUiSettings().setCompassEnabled(false)
		// 隐藏比例尺
		map.getUiSettings().setScaleControlsEnabled(false)

		// 启动定位
		mLocationClient.startLocation()
	}

	override
	fun onLocationChanged(amapLocation: AMapLocation?) {
		if (amapLocation != null) {
			if (amapLocation.errorCode == 0) {
				// 定位成功回调信息
				val latitude = amapLocation.latitude
				val longitude = amapLocation.longitude
				val accuracy = amapLocation.accuracy
				
				Log.d(TAG, "Location updated: $latitude, $longitude, accuracy: $accuracy")
				
				// 将位置信息传递给AmapLocationSource
				val location = android.location.Location("AMapLocation")
				location.latitude = latitude
				location.longitude = longitude
				location.accuracy = accuracy
				location.time = amapLocation.time
				
				// 如果有方向信息，也设置进去
				if (amapLocation.hasBearing()) {
					location.bearing = amapLocation.bearing
				}
				
				locationProvider.onLocationUpdate(location)
			} else {
				// 定位失败，使用默认位置（大连）
				Log.w(TAG, "Location failed, using default location (Dalian). Error: ${amapLocation.errorCode} - ${amapLocation.errorInfo}")
				
				val defaultLocation = android.location.Location("DefaultLocation")
				defaultLocation.latitude = 38.914003
				defaultLocation.longitude = 121.614682
				defaultLocation.accuracy = 1000f // 默认精度
				defaultLocation.time = System.currentTimeMillis()
				
				locationProvider.onLocationUpdate(defaultLocation)
			}
		} else {
			// 位置信息为空，使用默认位置
			Log.w(TAG, "Location is null, using default location (Dalian)")
			
			val defaultLocation = android.location.Location("DefaultLocation")
			defaultLocation.latitude = 38.914003
			defaultLocation.longitude = 121.614682
			defaultLocation.accuracy = 1000f
			defaultLocation.time = System.currentTimeMillis()
			
			locationProvider.onLocationUpdate(defaultLocation)
		}
	}
}
