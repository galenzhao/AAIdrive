package me.hufman.androidautoidrive.maps

object MapQuickDestination {
	private val COORD_PATTERN = Regex("""(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")

	fun displayName(stored: String): String {
		return stored.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !isCoordinateLine(it) }
				?: stored.trim()
	}

	fun searchQuery(stored: String): String = displayName(stored)

	fun parseLocation(stored: String): LatLong? {
		stored.lineSequence().map { it.trim() }.lastOrNull { isCoordinateLine(it) }?.let { line ->
			val match = COORD_PATTERN.matchEntire(line) ?: return null
			val lat = match.groupValues[1].toDoubleOrNull() ?: return null
			val lng = match.groupValues[2].toDoubleOrNull() ?: return null
			if (lat in -90.0..90.0 && lng in -180.0..180.0) {
				return LatLong(lat, lng)
			}
		}
		return null
	}

	fun format(result: MapResult): String {
		val name = result.name.ifBlank { result.address ?: "" }
		val address = result.address?.takeIf { it.isNotBlank() && it != name }
		return buildString {
			append(name)
			if (address != null) {
				append('\n')
				append(address)
			}
			result.location?.let { loc ->
				append('\n')
				append("${loc.latitude},${loc.longitude}")
			}
		}
	}

	/**
	 * Resolve a stored favorite / quick destination into a navigable [MapResult]
	 * (location + best-effort name / poi id).
	 */
	suspend fun resolve(stored: String, mapPlaceSearch: MapPlaceSearch): MapResult? {
		parseLocation(stored)?.let { loc ->
			val label = stored.lineSequence().map { it.trim() }
					.firstOrNull { it.isNotEmpty() && !isCoordinateLine(it) }
					.orEmpty()
			return MapResult(id = "", name = label, location = loc)
		}
		val query = searchQuery(stored)
		if (query.isBlank()) {
			return null
		}
		val first = mapPlaceSearch.searchLocationsAsync(query).await().firstOrNull() ?: return null
		return mapPlaceSearch.resolveNavigable(first)
	}

	private fun isCoordinateLine(line: String): Boolean {
		return COORD_PATTERN.matchEntire(line) != null
	}
}
