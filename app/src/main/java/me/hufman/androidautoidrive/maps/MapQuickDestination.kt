package me.hufman.androidautoidrive.maps

/**
 * Serialize / restore [MapResult] values used as map quick destinations.
 *
 * Stored text (newline-separated), backward compatible with older name/address/coords-only entries:
 * ```
 * name
 * address          (optional)
 * id:<poiId>       (optional)
 * lat,lng          (optional)
 * ```
 */
object MapQuickDestination {
	private const val ID_PREFIX = "id:"
	private val COORD_PATTERN = Regex("""(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")

	fun displayName(stored: String): String {
		return parse(stored).name.ifBlank { stored.trim() }
	}

	fun searchQuery(stored: String): String = displayName(stored)

	fun parseLocation(stored: String): LatLong? = parse(stored).location

	/** Restore name / address / poi id / coordinates from a stored favorite string. */
	fun parse(stored: String): MapResult {
		val lines = stored.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
		var id = ""
		var location: LatLong? = null
		val textLines = ArrayList<String>()
		for (line in lines) {
			when {
				isCoordinateLine(line) -> {
					parseCoordinate(line)?.let { location = it }
				}
				line.startsWith(ID_PREFIX, ignoreCase = true) -> {
					id = line.substring(ID_PREFIX.length).trim()
				}
				else -> textLines.add(line)
			}
		}
		val name = textLines.firstOrNull().orEmpty()
		val address = textLines.drop(1).joinToString("\n").ifBlank { null }
		return MapResult(id = id, name = name, address = address, location = location)
	}

	/** Persist all durable fields from a search / place result. */
	fun format(result: MapResult): String {
		val name = result.name.ifBlank { result.address ?: "" }
		val address = result.address?.takeIf { it.isNotBlank() && it != name }
		return buildString {
			append(name)
			if (address != null) {
				append('\n')
				append(address)
			}
			if (result.id.isNotBlank()) {
				append('\n')
				append(ID_PREFIX)
				append(result.id)
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
		val parsed = parse(stored)
		if (parsed.location != null) {
			if (parsed.id.isNotBlank()) {
				return parsed
			}
			// Older favorites (or tips without poiId) only have coords — try to enrich.
			return mapPlaceSearch.resolveNavigable(parsed) ?: parsed
		}
		if (parsed.id.isNotBlank()) {
			return mapPlaceSearch.resultInformationAsync(parsed.id).await()
					?: mapPlaceSearch.resolveNavigable(parsed)
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

	private fun parseCoordinate(line: String): LatLong? {
		val match = COORD_PATTERN.matchEntire(line) ?: return null
		val lat = match.groupValues[1].toDoubleOrNull() ?: return null
		val lng = match.groupValues[2].toDoubleOrNull() ?: return null
		if (lat in -90.0..90.0 && lng in -180.0..180.0) {
			return LatLong(lat, lng)
		}
		return null
	}
}
