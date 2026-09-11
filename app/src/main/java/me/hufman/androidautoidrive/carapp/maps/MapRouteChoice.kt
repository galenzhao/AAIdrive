package me.hufman.androidautoidrive.carapp.maps

import java.io.Serializable

data class MapRouteChoice(
	val routeId: Int,
	val labels: String,
	val durationSeconds: Int,
	val lengthMeters: Int,
	val tollCost: Int
): Serializable
