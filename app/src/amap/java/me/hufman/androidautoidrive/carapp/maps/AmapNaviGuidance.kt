package me.hufman.androidautoidrive.carapp.maps

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Reserved live guidance channel for Amap → future RHMI/HUD.
 *
 * Populated from [AMapNaviListener], [ParallelRoadListener], and [AimlessModeListener]
 * (see Amap docs: 导航实时数据获取). UI can observe these without touching the Projection.
 * High-frequency [naviInfo] updates are throttled in [AmapNaviProjection].
 */
object AmapNaviGuidance {
	private val _naviInfo = MutableLiveData<GuidanceNaviInfo?>()
	val naviInfo: LiveData<GuidanceNaviInfo?> = _naviInfo

	private val _cameras = MutableLiveData<List<GuidanceCamera>>(emptyList())
	val cameras: LiveData<List<GuidanceCamera>> = _cameras

	private val _intervalCamera = MutableLiveData<GuidanceIntervalCamera?>()
	val intervalCamera: LiveData<GuidanceIntervalCamera?> = _intervalCamera

	private val _serviceAreas = MutableLiveData<List<GuidanceServiceArea>>(emptyList())
	val serviceAreas: LiveData<List<GuidanceServiceArea>> = _serviceAreas

	private val _lane = MutableLiveData<GuidanceLane?>()
	val lane: LiveData<GuidanceLane?> = _lane

	private val _crossVisible = MutableLiveData(false)
	val crossVisible: LiveData<Boolean> = _crossVisible

	private val _routeNotify = MutableLiveData<GuidanceRouteNotify?>()
	val routeNotify: LiveData<GuidanceRouteNotify?> = _routeNotify

	private val _parallelRoad = MutableLiveData<GuidanceParallelRoad?>()
	val parallelRoad: LiveData<GuidanceParallelRoad?> = _parallelRoad

	private val _aimless = MutableLiveData<GuidanceAimless?>()
	val aimless: LiveData<GuidanceAimless?> = _aimless

	private val _speechText = MutableLiveData<String?>()
	val speechText: LiveData<String?> = _speechText

	private val _trafficUpdated = MutableLiveData(0L)
	/** Epoch millis of last onTrafficStatusUpdate; refresh path trafficStatuses from AMapNavi. */
	val trafficUpdatedAt: LiveData<Long> = _trafficUpdated

	internal fun postNaviInfo(info: GuidanceNaviInfo?) = _naviInfo.postValue(info)
	internal fun postCameras(cameras: List<GuidanceCamera>) = _cameras.postValue(cameras)
	internal fun postIntervalCamera(info: GuidanceIntervalCamera?) = _intervalCamera.postValue(info)
	internal fun postServiceAreas(areas: List<GuidanceServiceArea>) = _serviceAreas.postValue(areas)
	internal fun postLane(lane: GuidanceLane?) = _lane.postValue(lane)
	internal fun postCrossVisible(visible: Boolean) = _crossVisible.postValue(visible)
	internal fun postRouteNotify(notify: GuidanceRouteNotify?) = _routeNotify.postValue(notify)
	internal fun postParallelRoad(status: GuidanceParallelRoad?) = _parallelRoad.postValue(status)
	internal fun postAimless(info: GuidanceAimless?) = _aimless.postValue(info)
	internal fun postSpeechText(text: String?) = _speechText.postValue(text)
	internal fun postTrafficUpdated() = _trafficUpdated.postValue(System.currentTimeMillis())

	fun clear() {
		_naviInfo.postValue(null)
		_cameras.postValue(emptyList())
		_intervalCamera.postValue(null)
		_serviceAreas.postValue(emptyList())
		_lane.postValue(null)
		_crossVisible.postValue(false)
		_routeNotify.postValue(null)
		_parallelRoad.postValue(null)
		_aimless.postValue(null)
		_speechText.postValue(null)
	}
}

/** Core turn-by-turn panel (from NaviInfo). */
data class GuidanceNaviInfo(
	val currentRoad: String?,
	val nextRoad: String?,
	val iconType: Int,
	val pathRetainDistanceM: Int,
	val pathRetainTimeS: Int,
	val stepRetainDistanceM: Int,
	val stepRetainTimeS: Int,
	val currentSpeedKmh: Int,
	val remainTrafficLights: Int,
)

data class GuidanceCamera(
	val type: Int,
	val distanceM: Int,
	val limitKmh: Int,
	val longitude: Double,
	val latitude: Double,
)

data class GuidanceIntervalCamera(
	val start: GuidanceCamera?,
	val end: GuidanceCamera?,
	val status: Int,
)

data class GuidanceServiceArea(
	val name: String?,
	val type: Int,
	val remainDistanceM: Int,
	val remainTimeS: Int,
	val longitude: Double?,
	val latitude: Double?,
)

data class GuidanceLane(
	val laneCount: Int,
	val backgroundLane: IntArray?,
	val frontLane: IntArray?,
)

data class GuidanceRouteNotify(
	val notifyType: Int,
	val success: Boolean,
	val distanceM: Int,
	val roadName: String?,
	val reason: String?,
	val subTitle: String?,
	val longitude: Double,
	val latitude: Double,
)

data class GuidanceParallelRoad(
	val status: Int,
	val parallelRoadFlag: Int,
	val elevatedRoadFlag: Int,
)

data class GuidanceAimless(
	val facilityCount: Int = 0,
	val elecCameraCount: Int = 0,
	val congestionDistanceM: Int? = null,
	val statisticsSummary: String? = null,
)
