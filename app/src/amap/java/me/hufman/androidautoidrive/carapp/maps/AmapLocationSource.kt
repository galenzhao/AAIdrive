package me.hufman.androidautoidrive.carapp.maps

import android.location.Location
import com.amap.api.maps.model.LatLng

class AmapLocationSource {
	private var listener: ((Location) -> Unit)? = null
	var location: Location? = null
		private set

	fun registerLocationConsumer(locationConsumer: (Location) -> Unit) {
		listener = locationConsumer
		location?.also { onLocationUpdate(it) }
	}

	fun unRegisterLocationConsumer() {
		listener = null
	}

	fun onLocationUpdate(location: Location) {
		listener ?: return

		this.location = location
		listener?.invoke(location)
	}
}
