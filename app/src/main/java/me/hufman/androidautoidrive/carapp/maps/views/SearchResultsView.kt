package me.hufman.androidautoidrive.carapp.maps.views

import androidx.annotation.VisibleForTesting
import io.bimmergestalt.idriveconnectkit.rhmi.*
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.CarThreadExceptionHandler
import me.hufman.androidautoidrive.carapp.FullImageView
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.carapp.maps.MapNaviBehavior
import me.hufman.androidautoidrive.carapp.maps.MapRouteChoice
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.CDSVehicleUnits
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.LatLong
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapQuickDestination
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.maps.resolveNavigable
import me.hufman.androidautoidrive.utils.truncate
import kotlin.coroutines.CoroutineContext

class SearchResultsView(val state: RHMIState, val mapPlaceSearch: MapPlaceSearch, val interaction: MapInteractionController, val mapAppMode: MapAppMode, val locationProvider: CarLocationProvider): CoroutineScope {
	companion object {
		// current default row width only supports 22 chars before rolling over
		private const val ROW_LINE_MAX_LENGTH = 22

		val emptyList = RHMIModel.RaListModel.RHMIListConcrete(2).apply {
			this.addRow(arrayOf("", L.MAP_SEARCH_RESULTS_EMPTY))
		}
		val searchingList = RHMIModel.RaListModel.RHMIListConcrete(2).apply {
			this.addRow(arrayOf("", L.MAP_SEARCH_RESULTS_SEARCHING))
		}

		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
					state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().isEmpty()
		}
	}
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO + CarThreadExceptionHandler

	@VisibleForTesting
	var loaderJob: Job? = null
	@VisibleForTesting
	var searchJob: Job? = null      // to expand search results with missing Locations
	private var destinationJob: Job? = null
	private var loadingContents: Deferred<List<MapResult>> = CompletableDeferred(emptyList())
	private var contents: List<MapResult> = emptyList()
	private var loadingRoutes: Deferred<List<MapRouteChoice>> = CompletableDeferred(emptyList())
	private var routeContents: List<MapRouteChoice> = emptyList()
	private var routeMode = false
	private var routeChosen = false
	@VisibleForTesting
	var usesRouteSelectionOverride: Boolean? = null
	val usesRouteSelection: Boolean
		get() = usesRouteSelectionOverride ?: MapNaviBehavior.selectRouteBeforeStart
	private var mapStateId: Int = 0
	private val listComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
	private val listModel = listComponent.getModel()!!

	fun initWidgets(fullImageView: FullImageView) {
		mapStateId = fullImageView.state.id
		state.getTextModel()?.asRaDataModel()?.value = L.MAP_SEARCH_RESULTS_TITLE
		state.focusCallback = FocusCallback {
			if (it) {
				show()
			} else {
				loaderJob?.cancel()
				if (routeMode && !routeChosen && mapAppMode.isRouteSelectionPending) {
					// left the route list without choosing: the map page kept running for the
					// route list, and is not coming back, so let it pause
					mapAppMode.cancelRouteSelection()
					interaction.pauseMap()
				}
			}
		}

		listComponent.setVisible(true)
		listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "125,*")
		listComponent.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			if (routeMode) {
				onRouteSelected(routeContents.getOrNull(index))
			} else {
				onSelected(contents.getOrNull(index))
				if (usesRouteSelection) {
					setListHmiTarget(0)
					throw RHMIActionAbort()
				}
			}
		}
		setListHmiTarget(if (usesRouteSelection) 0 else mapStateId)
	}
	fun setContents(loadingContents: Deferred<List<MapResult>>) {
		loaderJob?.cancel()
		routeMode = false
		this.loadingContents = loadingContents
		state.getTextModel()?.asRaDataModel()?.value = L.MAP_SEARCH_RESULTS_TITLE
	}

	fun prepareRouteSelection(): Deferred<List<MapRouteChoice>> {
		loaderJob?.cancel()
		routeMode = true
		routeChosen = false
		routeContents = emptyList()
		loadingRoutes = mapAppMode.requestRouteSelection()
		state.getTextModel()?.asRaDataModel()?.value = L.MAP_ROUTE_RESULTS_TITLE
		listComponent.setEnabled(false)
		listModel.value = searchingList
		return loadingRoutes
	}

	fun startFavoriteDestination(stored: String) {
		destinationJob?.cancel()
		if (usesRouteSelection) {
			prepareRouteSelection()
		}
		destinationJob = launch {
			val result = MapQuickDestination.resolve(stored, mapPlaceSearch)
			val location = result?.location
			if (location != null) {
				interaction.navigateTo(
					location,
					result.name.takeIf { it.isNotBlank() },
					result.id.ifBlank { null },
				)
				if (usesRouteSelection) {
					show()
				}
			} else if (usesRouteSelection) {
				mapAppMode.completePendingRoutes(emptyList())
			}
		}
	}

	fun show() {
		loaderJob?.cancel()
		loaderJob = launch {
			if (routeMode) {
				showRoutes()
			} else {
				showPlaces()
			}
		}
	}

	private suspend fun showPlaces() {
		if (!loadingContents.isCompleted) {
			contents = emptyList()
			listComponent.setEnabled(false)
			listModel.value = searchingList
		}
		contents = loadingContents.await()
		if (contents.isEmpty()) {
			listComponent.setEnabled(false)
			listModel.value = emptyList
		} else {
			listComponent.setEnabled(true)
			listModel.value = MapResultListAdapter(mapAppMode, locationProvider, contents)
		}
		setListHmiTarget(if (usesRouteSelection) 0 else mapStateId)
	}

	private suspend fun showRoutes() {
		if (!loadingRoutes.isCompleted) {
			routeContents = emptyList()
			listComponent.setEnabled(false)
			listModel.value = searchingList
		}
		routeContents = loadingRoutes.await()
		if (routeContents.isEmpty()) {
			listComponent.setEnabled(false)
			listModel.value = emptyList
		} else {
			listComponent.setEnabled(true)
			listModel.value = RouteChoiceListAdapter(mapAppMode, routeContents)
		}
		setListHmiTarget(mapStateId)
	}

	private fun setListHmiTarget(stateId: Int) {
		listComponent.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateId
	}

	class MapResultListAdapter(mapAppMode: MapAppMode, locationProvider: CarLocationProvider, contents: List<MapResult>): RHMIModel.RaListModel.RHMIListAdapter<MapResult>(2, contents) {
		val currentLocation = locationProvider.currentLocation
		val currentLatLong = currentLocation?.let { LatLong(it.latitude, it.longitude) }
		val distanceUnits = mapAppMode.distanceUnits        // cache across each row for this set of results
		override fun convertRow(index: Int, item: MapResult): Array<Any> {
			val bearing = item.location?.let {
				currentLatLong?.bearingTowards(it)
			}
			val relativeToCarBearing = bearing
					?.minus(currentLocation?.bearing ?: 0f)
					?.plus(360f)
					?.mod(360f)
			val bearingArrow = CDSMetrics.compassArrow(relativeToCarBearing)
			val distance = item.distanceKm?.let {
				val distance = distanceUnits.fromCarUnit(it).toInt()
				val label = if (distanceUnits == CDSVehicleUnits.Distance.Miles) "mi" else "km"
				"$distance $label\n$bearingArrow"
			}
			val title = "${item.name.truncate(ROW_LINE_MAX_LENGTH)}\n${item.address ?: ""}"

			return arrayOf(distance ?: "", title)
		}
	}

	class RouteChoiceListAdapter(mapAppMode: MapAppMode, contents: List<MapRouteChoice>): RHMIModel.RaListModel.RHMIListAdapter<MapRouteChoice>(2, contents) {
		val distanceUnits = mapAppMode.distanceUnits
		override fun convertRow(index: Int, item: MapRouteChoice): Array<Any> {
			val minutes = maxOf(1, (item.durationSeconds + 59) / 60)
			val distanceKm = item.lengthMeters / 1000f
			val distance = if (distanceUnits == CDSVehicleUnits.Distance.Miles) {
				"${distanceUnits.fromCarUnit(distanceKm).toInt()} mi"
			} else {
				"${distanceKm.toInt()} km"
			}
			val summary = "${minutes} min\n$distance"
			val labels = item.labels.ifBlank { "${index + 1}" }.truncate(ROW_LINE_MAX_LENGTH)
			val toll = if (item.tollCost > 0) "¥${item.tollCost}" else ""
			val title = if (toll.isNotEmpty()) "$labels\n$toll" else labels
			return arrayOf(summary, title)
		}
	}

	fun onRouteSelected(route: MapRouteChoice?) {
		if (route == null) {
			setListHmiTarget(0)
			throw RHMIActionAbort()
		}
		routeChosen = true
		setListHmiTarget(mapStateId)
		interaction.selectRoute(route.routeId)
	}

	fun onSelected(result: MapResult?) {
		if (result == null) {
			setListHmiTarget(0)
			throw RHMIActionAbort()
		}
		searchJob?.cancel()
		searchJob = launch {
			val locationResult = mapPlaceSearch.resolveNavigable(result)
			if (locationResult?.location != null) {
				if (usesRouteSelection) {
					prepareRouteSelection()
					interaction.navigateTo(
						locationResult.location,
						locationResult.name.takeIf { it.isNotBlank() },
						locationResult.id.ifBlank { null },
					)
					show()
				} else {
					interaction.navigateTo(
						locationResult.location,
						locationResult.name.takeIf { it.isNotBlank() },
						locationResult.id.ifBlank { null },
					)
				}
			}
		}
	}
}