package me.hufman.androidautoidrive.maps

import android.location.Location
import android.util.Log
import com.google.gson.JsonObject
import com.soywiz.kmem.isNanOrInfinite
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.cds.CDSData
import me.hufman.androidautoidrive.cds.CDSEventHandler
import me.hufman.androidautoidrive.cds.subscriptions
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsDouble
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import java.io.Serializable
import kotlin.math.*
import cn.hutool.core.util.CoordinateUtil

data class LatLong(val latitude: Double, val longitude: Double): Serializable {
	/**
	 * Returns the distance to the other point in KM
	 */
	fun distanceFrom(other: LatLong): Double {
		// from https://stackoverflow.com/a/1253545
		// hilariously inaccurate, but probably good enough
		val latDistance = abs(other.latitude - this.latitude) * 110.574
		val latRadians = this.latitude * PI / 180
		val longDistance = abs(other.longitude - this.longitude) * 111.320 * cos(latRadians)
		return sqrt(latDistance*latDistance + longDistance*longDistance)
	}

	/**
	 * Returns angle towards the other point
	 * 0 pointing North, increasing clockwise
	 */
	fun bearingTowards(other: LatLong): Float {
		// From https://stackoverflow.com/a/69822454/169035
		val currentLat = Math.toRadians(latitude)
		val currentLong = Math.toRadians(longitude)
		val destLat = Math.toRadians(other.latitude)
		val destLong = Math.toRadians(other.longitude)

		val x = cos(destLat) * sin(destLong - currentLong)
		val y = (cos(currentLat) * sin(destLat)) -
				(sin(currentLat) * cos(destLat) * cos(destLong - currentLong))

		val radBearing = atan2(x, y)
		return (Math.toDegrees(radBearing).toFloat() + 360f) % 360f
	}

	override fun toString(): String {
		return "%.6f,%.6f".format(latitude, longitude)
	}
}
data class CarHeading(val heading: Float, val speed: Float): Serializable

object SimulatedCarLocation {
	const val PROVIDER = "SimulatedLocationProvider"
	const val LATITUDE = 38.91
	const val LONGITUDE = 121.61

	fun matches(location: Location?): Boolean {
		return location?.provider == PROVIDER
	}

	fun create(): Location {
		return Location(PROVIDER).also {
			it.latitude = LATITUDE
			it.longitude = LONGITUDE
			it.time = System.currentTimeMillis()
			it.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
			it.accuracy = 8f
			it.bearing = 0f
			it.speed = 0f
		}
	}
}

abstract class CarLocationProvider {
	public var wgs84ToGcj02: Boolean = false

	var currentLocation: Location? = null
		protected set

	val isSimulated: Boolean
		get() = SimulatedCarLocation.matches(currentLocation)

	var callback: ((Location) -> Unit)? = null

	protected fun sendCallback() {
		currentLocation?.also { location -> callback?.invoke(location) }
	}

	abstract fun start()
	abstract fun stop()
}

class CdsLocationProvider(val appSettings: AppSettings?, val cdsData: CDSData, val id4: Boolean): CarLocationProvider() {
	companion object {
		private const val TAG = "CdsLocationProvider"
	}

	constructor(cdsData: CDSData, id4: Boolean): this(null, cdsData, id4)

	var currentLatLong: LatLong? = null
	var currentHeading: CarHeading? = null
	private var hasCdsPosition = false

	init {
		refreshCoordinateMode()
		parseGPS()
		parseHeading()
		cdsData.addEventHandler(CDS.NAVIGATION.GPSPOSITION, 500, object: CDSEventHandler {
			override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
				parseGPS()
			}
		})
		cdsData.addEventHandler(CDS.NAVIGATION.GPSEXTENDEDINFO, 500, object: CDSEventHandler {
			override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
				parseHeading()
			}
		})
	}

	override fun start() {
		refreshCoordinateMode()
		cdsData.subscriptions[CDS.NAVIGATION.GPSPOSITION] = {
			parseGPS()
		}
		cdsData.subscriptions[CDS.NAVIGATION.GPSEXTENDEDINFO] = {
			parseHeading()
		}
		if (hasCdsPosition) {
			parseGPS()
		} else {
			applySimulatedLocation()
		}
	}

	private fun refreshCoordinateMode() {
		val raw = appSettings?.get(AppSettings.KEYS.wgs84ToGcj02)?.trim().orEmpty()
		wgs84ToGcj02 = raw.equals("true", ignoreCase = true) || (raw.toIntOrNull() ?: 0) != 0
	}

	private fun parseGPS() {
		val gpsPosition = cdsData[CDS.NAVIGATION.GPSPOSITION] ?: return
		val position = gpsPosition.tryAsJsonObject("GPSPosition")
		val latitude = position?.tryAsJsonPrimitive("latitude")?.tryAsDouble
		val longitude = position?.tryAsJsonPrimitive("longitude")?.tryAsDouble
		if (longitude != null && latitude != null && !longitude.isNanOrInfinite() && !latitude.isNanOrInfinite() && longitude.absoluteValue < 180 && latitude.absoluteValue < 90) {
			if(CoordinateUtil.outOfChina(longitude, latitude)) {
				currentLatLong = LatLong(latitude, longitude)
			}else{
				if(wgs84ToGcj02){
					val coord = CoordinateUtil.gcj02ToWgs84(longitude, latitude);
					currentLatLong = LatLong(coord.lat, coord.lng)
				}else{
					currentLatLong = LatLong(latitude, longitude)
				}
			}
			hasCdsPosition = true
			onLocationUpdate()
		}
	}

	private fun parseHeading() {
		val gpsHeading = cdsData[CDS.NAVIGATION.GPSEXTENDEDINFO] ?: return
		val position = gpsHeading.tryAsJsonObject("GPSExtendedInfo")
		val heading = position?.tryAsJsonPrimitive("heading")?.tryAsDouble   // in degrees, needs to be negated for Location usage
		val headingAdj = if (id4) -1.40625f else -1f
		val speed = position?.tryAsJsonPrimitive("speed")?.tryAsDouble ?: 0.0  // in kmph
		val validSpeed = if (speed < 4000) speed else 0
		if (heading != null) {
			currentHeading = CarHeading(heading.toFloat() * headingAdj, validSpeed.toFloat() / 3.6f)
			if (hasCdsPosition) {
				onLocationUpdate()
			}
		}
	}

	private fun applySimulatedLocation() {
		Log.i(TAG, "No CDS GPS yet, using simulated location ${SimulatedCarLocation.LATITUDE},${SimulatedCarLocation.LONGITUDE}")
		currentLocation = SimulatedCarLocation.create()
		sendCallback()
	}

	private fun onLocationUpdate() {
		val latLong = currentLatLong
		val heading = currentHeading
		currentLocation = if (latLong != null) {
			Location("CdsLocationProvider").also {
				it.latitude = latLong.latitude
				it.longitude = latLong.longitude
				it.time = System.currentTimeMillis()
				it.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
				it.accuracy = 8f
				if (heading != null) {
					it.bearing = heading.heading
					it.speed = heading.speed
				}
			}
		} else {
			null
		}
		sendCallback()
	}

	override fun stop() {
		cdsData.subscriptions[CDS.NAVIGATION.GPSPOSITION] = null
		cdsData.subscriptions[CDS.NAVIGATION.GPSEXTENDEDINFO] = null
	}
}