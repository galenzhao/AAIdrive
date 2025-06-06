package me.hufman.androidautoidrive.carapp

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import me.hufman.androidautoidrive.BuildConfig
import java.util.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object L {
	// access to the Android string resources
	var loadedResources: Resources? = null
		private set

	// all of the strings used in the car app
	// these default string values are used in tests, Android resources are used for real
	val CARINFO_TITLE by StringResourceDelegate("Detailed Vehicle Info")
	val CARINFO_TITLE_DRIVING by StringResourceDelegate("Driving Details")
	val CARINFO_TITLE_SPORT by StringResourceDelegate("Sport Display")
	val CARINFO_TITLE_GPS by StringResourceDelegate("GPS Info")
	val CARINFO_TITLE_WINDOWS by StringResourceDelegate("Windows Info")
	val CARINFO_TITLE_AC by StringResourceDelegate("Air Condition Info")
	val CARINFO_TITLE_TRAVEL by StringResourceDelegate("Travel Info")
	val CARINFO_ENGINE by StringResourceDelegate("Engine")
	val CARINFO_OIL by StringResourceDelegate("Oil")
	val CARINFO_EXCHANGER by StringResourceDelegate("HVAC")
	val CARINFO_EVAPORATOR by StringResourceDelegate("EVAC")
	val CARINFO_BATTERY by StringResourceDelegate("Battery")
	val CARINFO_EXTERIOR by StringResourceDelegate("Exterior")
	val CARINFO_INTERIOR by StringResourceDelegate("Interior")
	val CARINFO_GEAR by StringResourceDelegate("Gear")
	val CARINFO_ACC_BATTERY by StringResourceDelegate("Acc. Battery")
	val CARINFO_EV_BATTERY by StringResourceDelegate("EV Battery")
	val CARINFO_FUEL by StringResourceDelegate("Fuel")
	val CARINFO_GASOLINE by StringResourceDelegate("Gasoline")
	val CARINFO_DIESEL by StringResourceDelegate("Diesel")
	val CARINFO_RANGE by StringResourceDelegate("Range")
	val CARINFO_ACCEL by StringResourceDelegate("Accel")
	val CARINFO_STEERING by StringResourceDelegate("Steering")
	val CARINFO_RPM by StringResourceDelegate("RPM")
	val CARINFO_TORQUE by StringResourceDelegate("Torque")
	val CARINFO_GFORCE by StringResourceDelegate("GForce")
	val CARINFO_SPEEDACTUAL by StringResourceDelegate("SpeedActual")
	val CARINFO_SPEEDGPS by StringResourceDelegate("SpeedGPS")
	val CARINFO_UNIT_C by StringResourceDelegate("\\u2103")
	val CARINFO_UNIT_F by StringResourceDelegate("\\u2109")
	val CARINFO_UNIT_KM by StringResourceDelegate("km")
	val CARINFO_UNIT_MI by StringResourceDelegate("mi")
	val CARINFO_UNIT_GALUK by StringResourceDelegate("gal (UK)")
	val CARINFO_UNIT_GALUS by StringResourceDelegate("gal (US)")
	val CARINFO_UNIT_LITER by StringResourceDelegate("L")
	val CARINFO_UNIT_KMPH by StringResourceDelegate("kmph")
	val CARINFO_UNIT_MPH by StringResourceDelegate("mph")
	val CARINFO_HEADING_NORTH by StringResourceDelegate("N")
	val CARINFO_HEADING_NORTHEAST by StringResourceDelegate("NE")
	val CARINFO_HEADING_EAST by StringResourceDelegate("E")
	val CARINFO_HEADING_SOUTHEAST by StringResourceDelegate("SE")
	val CARINFO_HEADING_SOUTH by StringResourceDelegate("S")
	val CARINFO_HEADING_SOUTHWEST by StringResourceDelegate("SW")
	val CARINFO_HEADING_WEST by StringResourceDelegate("W")
	val CARINFO_HEADING_NORTHWEST by StringResourceDelegate("NW")
	val CARINFO_SUNROOF by StringResourceDelegate("Sunroof")
	val CARINFO_WINDOW_CLOSED by StringResourceDelegate("Closed")
	val CARINFO_WINDOW_TILTED by StringResourceDelegate("Tilted")
	val CARINFO_WINDOW_OPENED by StringResourceDelegate("Opened")
	val CARINFO_COMPRESSOR by StringResourceDelegate("AC Compressor")
	val CARINFO_COMPRESSORSTATE by StringResourceDelegate("AC State")
	val CARINFO_COMPRESSORLEVEL by StringResourceDelegate("AC Level")
	val CARINFO_COMPRESSORPOWER by StringResourceDelegate("AC Power")
	val CARINFO_COMPRESSORDUALMODE by StringResourceDelegate("Dualmode")
	val CARINFO_GPSALTITUDE by StringResourceDelegate("Altiude")
	val CARINFO_GPSLATITUDE by StringResourceDelegate("Lat")
	val CARINFO_GPSLONGITUDE by StringResourceDelegate("Long")
	val CARINFO_STATE_OFF by StringResourceDelegate("Off")
	val CARINFO_STATE_ON by StringResourceDelegate("On")
	val CARINFO_NAV_DISTANCE by StringResourceDelegate("Distance")
	val CARINFO_NAV_ETA by StringResourceDelegate("ETA")
	val NOTIFICATIONS_TITLE by StringResourceDelegate("Notifications")
	val NOTIFICATIONS_EMPTY_LIST by StringResourceDelegate("No Notifications")
	val NOTIFICATION_CLEAR_ACTION by StringResourceDelegate("Clear")
	val NOTIFICATION_READOUT_ACTION by StringResourceDelegate("Speak")
	val NOTIFICATION_OPTIONS by StringResourceDelegate("Options")
	val NOTIFICATION_POPUPS by StringResourceDelegate("Notification Popups")
	val NOTIFICATION_POPUPS_PASSENGER by StringResourceDelegate("Popups with passenger")
	val NOTIFICATION_SOUND by StringResourceDelegate("Play notification sound")
	val NOTIFICATION_READOUT by StringResourceDelegate("Speak when viewing notifications")
	val NOTIFICATION_READOUT_POPUP by StringResourceDelegate("Speak new notifications")
	val NOTIFICATION_READOUT_POPUP_PASSENGER by StringResourceDelegate("... with a passenger")
	val NOTIFICATION_PERMISSION_NEEDED by StringResourceDelegate("This app needs to be granted Notification Access")
	val NOTIFICATION_CENTER_APP by StringResourceDelegate("This app is used to push to the car's Notification Center")
	val READOUT_DESCRIPTION by StringResourceDelegate("This app is used for readout purposes")

	val CALENDAR_TIME_START by StringResourceDelegate("Start")
	val CALENDAR_TIME_END by StringResourceDelegate("End")
	val CALENDAR_TIME_DURATION by StringResourceDelegate("Duration")
	val CALENDAR_TIME_ALLDAY by StringResourceDelegate("All Day")
	val CALENDAR_NAVIGATE by StringResourceDelegate("Navigate")

	val MUSIC_APPLIST_TITLE by StringResourceDelegate("Apps")
	val MUSIC_APPLIST_EMPTY by StringResourceDelegate("<No Apps>")
	val MUSIC_CUSTOMACTIONS_TITLE by StringResourceDelegate("Actions")
	val MUSIC_DISCONNECTED by StringResourceDelegate("<Not Connected>")
	val MUSIC_BROWSE_TITLE by StringResourceDelegate("Browse")
	val MUSIC_BROWSE_EMPTY by StringResourceDelegate("<Empty>")
	val MUSIC_BROWSE_LOADING by StringResourceDelegate("<Loading>")
	val MUSIC_BROWSE_SEARCHING by StringResourceDelegate("<Searching>")
	val MUSIC_BROWSE_ACTION_JUMPBACK by StringResourceDelegate("Jump Back")
	val MUSIC_BROWSE_ACTION_FILTER by StringResourceDelegate("Filter")
	val MUSIC_BROWSE_ACTION_SEARCH by StringResourceDelegate("Search")
	val MUSIC_BROWSE_PLAY_FROM_SEARCH by StringResourceDelegate("Play From Search")
	val MUSIC_SEARCH_RESULTS_LABEL by StringResourceDelegate("Search Results")
	val MUSIC_SEARCH_RESULTS_VIEW_FULL_RESULTS by StringResourceDelegate("View Full Results")
	val MUSIC_SEARCH_RESULTS_ELLIPSIS by StringResourceDelegate("...")
	val MUSIC_QUEUE_TITLE by StringResourceDelegate("Now Playing")
	val MUSIC_QUEUE_EMPTY by StringResourceDelegate("<Empty Queue>")
	val MUSIC_SKIP_PREVIOUS by StringResourceDelegate("Back")
	val MUSIC_SKIP_NEXT by StringResourceDelegate("Next")
	val MUSIC_TURN_SHUFFLE_UNAVAILABLE by StringResourceDelegate("Shuffle Unavailable")
	val MUSIC_TURN_SHUFFLE_ON by StringResourceDelegate("Turn Shuffle On")
	val MUSIC_TURN_SHUFFLE_OFF by StringResourceDelegate("Turn Shuffle Off")
	val MUSIC_TURN_REPEAT_UNAVAILABLE by StringResourceDelegate("Repeat Unavailable")
	val MUSIC_TURN_REPEAT_ALL_ON by StringResourceDelegate("Turn Repeat All On")
	val MUSIC_TURN_REPEAT_ONE_ON by StringResourceDelegate("Turn Repeat One On")
	val MUSIC_TURN_REPEAT_OFF by StringResourceDelegate("Turn Repeat Off")
	val MUSIC_TEMPORARY_PLAYLIST_DESCRIPTION by StringResourceDelegate("AAIdrive temporary playlist")

	val MUSIC_ACTION_SEEK_BACK_5 by StringResourceDelegate("Seek back 5 seconds")
	val MUSIC_ACTION_SEEK_BACK_10 by StringResourceDelegate("Seek back 10 seconds")
	val MUSIC_ACTION_SEEK_BACK_15 by StringResourceDelegate("Seek back 15 seconds")
	val MUSIC_ACTION_SEEK_BACK_20 by StringResourceDelegate("Seek back 20 seconds")
	val MUSIC_ACTION_SEEK_BACK_60 by StringResourceDelegate("Seek back 60 seconds")
	val MUSIC_ACTION_SEEK_FORWARD_5 by StringResourceDelegate("Seek forward 5 seconds")
	val MUSIC_ACTION_SEEK_FORWARD_10 by StringResourceDelegate("Seek forward 10 seconds")
	val MUSIC_ACTION_SEEK_FORWARD_15 by StringResourceDelegate("Seek forward 15 seconds")
	val MUSIC_ACTION_SEEK_FORWARD_20 by StringResourceDelegate("Seek forward 20 seconds")
	val MUSIC_ACTION_SEEK_FORWARD_60 by StringResourceDelegate("Seek forward 60 seconds")

	val MUSIC_SPOTIFY_REMOVE_FROM_COLLECTION by StringResourceDelegate("Dislike")
	val MUSIC_SPOTIFY_START_RADIO by StringResourceDelegate("Make Radio Station")
	val MUSIC_SPOTIFY_ADD_TO_COLLECTION by StringResourceDelegate("Like")
	val MUSIC_SPOTIFY_THUMB_UP by StringResourceDelegate("Thumb Up")
	val MUSIC_SPOTIFY_THUMBS_UP_SELECTED by StringResourceDelegate("Thumbed Up")
	val MUSIC_SPOTIFY_THUMB_DOWN by StringResourceDelegate("Thumb Down")
	val MUSIC_SPOTIFY_THUMBS_DOWN_SELECTED by StringResourceDelegate("Thumbed Down")    // not sure if this exists, but just to be complete
	val MUSIC_SPOTIFY_BROWSEROOT_LIBRARY by StringResourceDelegate("Your Library")
	val MUSIC_SPOTIFY_BROWSEROOT_BROWSE by StringResourceDelegate("Browse")

	val MAP_ACTION_VIEWMAP by StringResourceDelegate("View Full Map")
	val MAP_ACTION_SEARCH by StringResourceDelegate("Search for Place")
	val MAP_ACTION_RECALC_NAV by StringResourceDelegate("Recalculate Navigation")
	val MAP_ACTION_CLEARNAV by StringResourceDelegate("Clear Navigation")
	val MAP_DESTINATIONS by StringResourceDelegate("Favorite Destinations")
	val MAP_OPTIONS = NOTIFICATION_OPTIONS
	val MAP_SEARCH_RESULTS_TITLE = MUSIC_SEARCH_RESULTS_LABEL
	val MAP_SEARCH_RESULTS_SEARCHING = MUSIC_BROWSE_SEARCHING
	val MAP_SEARCH_RESULTS_EMPTY = MUSIC_BROWSE_EMPTY
	val MAP_SEARCH_RESULTS_VIEW_FULL_RESULTS = MUSIC_SEARCH_RESULTS_VIEW_FULL_RESULTS
	val MAP_WIDESCREEN by StringResourceDelegate("Widescreen map")
	val MAP_INVERT_ZOOM by StringResourceDelegate("Invert zoom direction")
	val MAP_SATELLITE by StringResourceDelegate("Show satellite imagery")
	val MAP_TRAFFIC by StringResourceDelegate("Show traffic")
	val MAP_BUILDINGS by StringResourceDelegate("Show 3D buildings")
	val MAP_TILT by StringResourceDelegate("Tilt map")
	val MAP_CUSTOM_STYLE by StringResourceDelegate("Use custom map style")

	fun loadResources(context: Context, locale: Locale? = null) {
		val thisContext = if (locale == null) { context } else {
			val origConf = context.resources.configuration
			val localeConf = Configuration(origConf)
			localeConf.setLocale(locale)
			context.createConfigurationContext(localeConf)
		}

		loadedResources = thisContext.resources
	}
}

class StringResourceDelegate(val default: String): ReadOnlyProperty<L, String> {
	companion object {
		val pluralMatcher = Regex("([A-Z_]+)_([0-9]+)\$")
	}
	override operator fun getValue(thisRef: L, property: KProperty<*>): String {
		val resources = L.loadedResources ?: return default
		return if (property.name.matches(pluralMatcher)) {
			val nameMatch = pluralMatcher.matchEntire(property.name)
					?: throw AssertionError("Could not parse L name ${property.name}")
			val id = resources.getIdentifier(nameMatch.groupValues[1], "plurals", BuildConfig.APPLICATION_ID)
			if (id == 0) {
				throw AssertionError("Could not find Resource value for string ${property.name}")
			}
			val quantity = nameMatch.groupValues[2].toInt()
			resources.getQuantityString(id, quantity, quantity)
		} else {
			val id = resources.getIdentifier(property.name, "string", BuildConfig.APPLICATION_ID)
			if (id == 0) {
				throw AssertionError("Could not find Resource value for string ${property.name}")
			}
			resources.getString(id)
		}
	}
}