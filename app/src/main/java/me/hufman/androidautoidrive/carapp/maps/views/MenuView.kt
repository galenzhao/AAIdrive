package me.hufman.androidautoidrive.carapp.maps.views

import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.StoredList
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.SettingsToggleList
import me.hufman.androidautoidrive.carapp.maps.FrameUpdater
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.carapp.maps.MapNaviBehavior
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapQuickDestination

class MenuView(val state: RHMIState, val interaction: MapInteractionController, val mapPlaceSearch: MapPlaceSearch, val frameUpdater: FrameUpdater, val mapAppMode: MapAppMode) {
	companion object {
		val TAG = "MapMenu"
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.PlainState &&
				state.componentsList.filterIsInstance<RHMIComponent.Label>().isNotEmpty() &&   // show whether currently navigating
				state.componentsList.filterIsInstance<RHMIComponent.List>().size > 3
		}
	}

	val alwaysMenuEntries = listOf(L.MAP_ACTION_VIEWMAP, L.MAP_ACTION_SEARCH)
	val duringNavMenuEntries = listOf(L.MAP_ACTION_RECALC_NAV, L.MAP_ACTION_CLEARNAV)
	val menuEntries = ArrayList<String>()
	val rhmiMenuEntries = object: RHMIModel.RaListModel.RHMIListAdapter<String>(3, menuEntries) {}
	val menuMap = state.componentsList.filterIsInstance<RHMIComponent.List>()[0]
	val mapModel = menuMap.getModel()!!
	val menuList = state.componentsList.filterIsInstance<RHMIComponent.List>()[1]

	val labelDestinations: RHMIComponent.Label
	val menuDestinations = state.componentsList.filterIsInstance<RHMIComponent.List>()[2]
	val destinationEntries = StoredList(mapAppMode.appSettings, AppSettings.KEYS.MAP_QUICK_DESTINATIONS)
	val rhmiDestinationEntries = object: RHMIModel.RaListModel.RHMIListAdapter<String>(3, destinationEntries) {
		override fun convertRow(index: Int, item: String): Array<Any> {
			return arrayOf("", "", MapQuickDestination.displayName(item))
		}
	}

	val labelSettings: RHMIComponent.Label
	val menuSettings = state.componentsList.filterIsInstance<RHMIComponent.List>()[3]
	val settingsView: SettingsToggleList = SettingsToggleList(menuSettings, mapAppMode.appSettings, mapAppMode.settings, 149)
	private var mapStateId: Int = 0

	init {
		val destinationsListIndex = state.componentsList.indexOf(menuDestinations)
		labelDestinations = state.componentsList.filterIndexed { index, rhmiComponent ->
			index < destinationsListIndex && rhmiComponent is RHMIComponent.Label
		}.filterIsInstance<RHMIComponent.Label>().last()

		val settingsListIndex = state.componentsList.indexOf(menuSettings)
		labelSettings = state.componentsList.filterIndexed { index, rhmiComponent ->
			index < settingsListIndex && rhmiComponent is RHMIComponent.Label
		}.filterIsInstance<RHMIComponent.Label>().last()
	}
	fun initWidgets(stateMap: RHMIState, stateInput: RHMIState, searchResultsView: SearchResultsView? = null) {
		mapStateId = stateMap.id
		mapAppMode.appSettings.callback = {
			redrawDestinations()
			settingsView.redraw()
		}
		state.componentsList.forEach {
			it.setVisible(false)
		}
		redrawCommands()

		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				restoreMapHmiTargets()
				redrawCommands()
				redrawDestinations()
				Log.i(TAG, "Showing map on menu")
				frameUpdater.showWindow(350, 90, mapModel)
			} else {
				Log.i(TAG, "Hiding map on menu")
				frameUpdater.hideWindow(mapModel)
			}
		}

		menuMap.setVisible(true)
		menuMap.setSelectable(true)
		menuMap.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "350,0,*")
		setHmiTarget(menuMap, stateMap.id)
		setHmiTarget(menuList, stateMap.id)

		menuList.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "100,0,*")
		menuList.setVisible(true)

		var focusedList: RHMIComponent.List = menuList
		fun trackFocus(list: RHMIComponent.List) {
			list.getSelectAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback {
				focusedList = list
			}
		}
		trackFocus(menuMap)
		trackFocus(menuList)
		trackFocus(menuDestinations)
		trackFocus(menuSettings)

		val onCommand: (Int) -> Unit = { listIndex ->
			when (listIndex) {
				0 -> {
					Log.i(TAG, "User pressed menu item $listIndex ${menuEntries.getOrNull(listIndex)}, setting target to ${stateMap.id}")
					setHmiTarget(menuMap, stateMap.id)
					setHmiTarget(menuList, stateMap.id)
				}
				1 -> {
					Log.i(TAG, "User pressed menu item $listIndex ${menuEntries.getOrNull(listIndex)}, setting target to ${stateInput.id}")
					setHmiTarget(menuMap, stateInput.id)
					setHmiTarget(menuList, stateInput.id)
				}
				2 -> {
					Log.i(TAG, "User pressed menu item $listIndex ${menuEntries.getOrNull(listIndex)}, staying on menu")
					interaction.recalcNavigation()
					stayOnMenu()
					throw RHMIActionAbort()
				}
				3 -> {
					Log.i(TAG, "User pressed menu item $listIndex ${menuEntries.getOrNull(listIndex)}, staying on menu")
					interaction.stopNavigation()
					mapAppMode.currentNavDestination = null
					redrawCommands()
					stayOnMenu()
					throw RHMIActionAbort()
				}
				else -> {
					stayOnMenu()
					throw RHMIActionAbort()
				}
			}
		}
		val onDestination: (Int) -> Unit = { listIndex ->
			val stored = destinationEntries.getOrNull(listIndex)
			if (stored.isNullOrBlank()) {
				stayOnMenu()
				throw RHMIActionAbort()
			}
			val resultsView = searchResultsView
			val targetId = if (resultsView != null && MapNaviBehavior.selectRouteBeforeStart) {
				resultsView.startFavoriteDestination(stored)
				resultsView.state.id
			} else {
				val location = MapQuickDestination.parseLocation(stored)
				if (location != null) {
					interaction.navigateTo(location)
				} else {
					searchResultsView?.startFavoriteDestination(stored) ?: run {
						stayOnMenu()
						throw RHMIActionAbort()
					}
				}
				stateMap.id
			}
			// Let the car follow this HMI target. Do not restore the map target or abort:
			// the emulator still navigates after ack=false, which stacked the full map on
			// top of the route list (Back then showed the route picker).
			setClickHmiTarget(targetId)
		}

		val menuAction = menuList.getAction()?.asRAAction()
		val mapAction = menuMap.getAction()?.asRAAction()
		val destAction = menuDestinations.getAction()?.asRAAction()
		menuAction?.rhmiActionCallback = RHMIActionListCallback(onCommand)
		if (mapAction !== menuAction) {
			mapAction?.rhmiActionCallback = RHMIActionListCallback(onCommand)
		}
		if (destAction != null && destAction !== menuAction && destAction !== mapAction) {
			destAction.rhmiActionCallback = RHMIActionListCallback(onDestination)
		}

		labelDestinations.getModel()?.asRaDataModel()?.value = L.MAP_DESTINATIONS
		menuDestinations.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH.id, "55,0,*")
		redrawDestinations()

		// decorate the settings
		labelSettings.setVisible(true)
		labelSettings.getModel()?.asRaDataModel()?.value = L.MAP_OPTIONS

		settingsView.initWidgets()
		val settingsAction = menuSettings.getAction()?.asRAAction()
		val sharedAction = menuAction ?: mapAction ?: destAction ?: settingsAction
		val needsSharedDispatch = sharedAction != null && (
				sharedAction === destAction ||
				sharedAction === settingsAction ||
				menuAction === destAction ||
				menuAction === settingsAction ||
				(destAction != null && destAction === settingsAction)
			)
		if (needsSharedDispatch) {
			Log.i(TAG, "Menu lists share an RHMI action, dispatching by last focused list")
			sharedAction!!.rhmiActionCallback = RHMIActionListCallback { listIndex ->
				when (focusedList) {
					menuDestinations -> onDestination(listIndex)
					menuSettings -> {
						stayOnMenu()
						settingsView.onClicked(listIndex)
					}
					else -> onCommand(listIndex)
				}
			}
		}
	}

	private fun setHmiTarget(list: RHMIComponent.List, stateId: Int) {
		list.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = stateId
	}

	private fun restoreMapHmiTargets() {
		if (mapStateId == 0) return
		setHmiTarget(menuMap, mapStateId)
		setHmiTarget(menuList, mapStateId)
	}

	private fun stayOnMenu() {
		// Target 0 means no page change. Do not use the current menu stateId (self-transition
		// crashes the emulator) and do not use the full map (the emulator follows HMI even
		// when the RA action is aborted).
		setHmiTarget(menuMap, 0)
		setHmiTarget(menuList, 0)
		menuDestinations.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
		menuSettings.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = 0
	}

	private fun setClickHmiTarget(targetId: Int) {
		menuDestinations.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = targetId
		// Destinations often share the menu list CombinedAction; keep that HMI on the same page.
		setHmiTarget(menuList, targetId)
	}

	private fun redrawCommands() {
		menuEntries.clear()
		menuEntries.addAll(alwaysMenuEntries)
		if (mapAppMode.currentNavDestination != null) {
			menuEntries.addAll(duringNavMenuEntries)
		}
		menuList.getModel()?.value = rhmiMenuEntries
	}

	private fun redrawDestinations() {
		val hasDestinations = destinationEntries.isNotEmpty()
		labelDestinations.setVisible(hasDestinations)
		menuDestinations.setVisible(hasDestinations)
		if (hasDestinations) {
			menuDestinations.getModel()?.value = rhmiDestinationEntries
		}
	}
}