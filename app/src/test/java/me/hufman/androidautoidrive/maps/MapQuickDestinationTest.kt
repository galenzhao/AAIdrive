package me.hufman.androidautoidrive.maps

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class MapQuickDestinationTest {
	@Test
	fun formatAndParseRoundTrip() {
		val result = MapResult(
			id = "B0FFFA123",
			name = "东软软件园B区",
			address = "大连河口园区",
			location = LatLong(38.851238, 121.509359),
		)
		val stored = MapQuickDestination.format(result)
		assertEquals(
			"东软软件园B区\n大连河口园区\nid:B0FFFA123\n38.851238,121.509359",
			stored,
		)
		assertEquals(result, MapQuickDestination.parse(stored))
		assertEquals("东软软件园B区", MapQuickDestination.displayName(stored))
	}

	@Test
	fun parseLegacyCoordsOnly() {
		val stored = "东软软件园B区(大连河口园区)\n38.851238,121.509359"
		val parsed = MapQuickDestination.parse(stored)
		assertEquals("", parsed.id)
		assertEquals("东软软件园B区(大连河口园区)", parsed.name)
		assertNull(parsed.address)
		assertEquals(LatLong(38.851238, 121.509359), parsed.location)
	}

	@Test
	fun resolveUsesSavedPoiIdWithoutResearch() = runBlocking {
		val search = mock<MapPlaceSearch>()
		val stored = MapQuickDestination.format(
			MapResult("B0ID", "Place", "Addr", LatLong(1.0, 2.0)),
		)
		val resolved = MapQuickDestination.resolve(stored, search)
		assertEquals(MapResult("B0ID", "Place", "Addr", LatLong(1.0, 2.0)), resolved)
		verify(search, never()).searchLocationsAsync(any())
		verify(search, never()).resultInformationAsync(any())
		Unit
	}

	@Test
	fun resolveEnrichesLegacyFavoriteWithoutId() = runBlocking {
		val enriched = MapResult("B0NEW", "Place", "Addr", LatLong(1.0, 2.0))
		val search = mock<MapPlaceSearch> {
			on { enrichMissingPoiIdAsync(any()) } doReturn CompletableDeferred(enriched)
			on { searchLocationsAsync(any()) } doReturn CompletableDeferred(emptyList())
		}
		val stored = "Place\nAddr\n1.0,2.0"
		val resolved = MapQuickDestination.resolve(stored, search)
		assertEquals(enriched, resolved)
		verify(search).enrichMissingPoiIdAsync(any())
		Unit
	}
}
