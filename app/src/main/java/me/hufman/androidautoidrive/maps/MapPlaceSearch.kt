package me.hufman.androidautoidrive.maps

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.io.Serializable

data class MapResult(val id: String, val name: String,
                     val address: String? = null,
                     val location: LatLong? = null,
                     val distanceKm: Float? = null): Serializable {
	override fun toString(): String {
		return if (address != null) {
			if (name.isNotBlank()) {
				"$name\n$address"
			} else {
				address
			}
		} else {
			name
		}
	}
}

interface MapPlaceSearch {
	fun searchLocationsAsync(query: String): Deferred<List<MapResult>>
	fun resultInformationAsync(resultId: String): Deferred<MapResult?>
	/** Best-effort fill blank poi id (e.g. keyword/nearby POI search). Default no-op. */
	fun enrichMissingPoiIdAsync(result: MapResult): Deferred<MapResult> =
		CompletableDeferred(result)
}

/** Max distance (km) when matching a tip without poiId to a nearby tip that has one. */
private const val POI_ENRICH_MAX_KM = 0.2

/**
 * Ensure a search tip/result is ready for navigation:
 * fill missing location via POI id lookup, or fill missing poiId via a nearby named tip /
 * provider-specific enrichment when possible.
 */
suspend fun MapPlaceSearch.resolveNavigable(result: MapResult): MapResult? {
	val withLocation = when {
		result.location != null -> result
		result.id.isNotBlank() -> resultInformationAsync(result.id).await()
		result.name.isNotBlank() -> {
			val first = searchLocationsAsync(result.name).await().firstOrNull() ?: return null
			if (first.location != null) first else resultInformationAsync(first.id).await()
		}
		else -> null
	} ?: return null

	val loc = withLocation.location ?: return null
	if (withLocation.id.isNotBlank()) {
		return withLocation
	}
	if (withLocation.name.isBlank()) {
		return withLocation
	}

	val candidates = searchLocationsAsync(withLocation.name).await()
	val nearbyWithId = candidates.firstOrNull { tip ->
		tip.id.isNotBlank() && tip.location != null && tip.location.distanceFrom(loc) <= POI_ENRICH_MAX_KM
	} ?: candidates.firstOrNull { tip ->
		tip.id.isNotBlank() && tip.name.equals(withLocation.name, ignoreCase = true)
	}
	if (nearbyWithId != null) {
		return withLocation.copy(
			id = nearbyWithId.id,
			name = withLocation.name.ifBlank { nearbyWithId.name },
			address = withLocation.address ?: nearbyWithId.address,
		)
	}
	return enrichMissingPoiIdAsync(withLocation).await()
}
