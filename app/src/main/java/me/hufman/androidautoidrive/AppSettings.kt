package me.hufman.androidautoidrive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * This implements read-only access to a singleton of loaded settings
 * This singleton will only return defaults until AppSettings.loadSettings(context) loads persisted settings
 */
interface AppSettings {
	enum class KEYS(val key: String, val default: String, val comment: String) {
		FIRST_START_DONE("First_Start_Done", "false", "Whether the initial setup wizard finished"),
		ENABLED_NOTIFICATIONS("Enabled_Notifications", "false", "Show phone notifications in the car"),
		ENABLED_NOTIFICATIONS_POPUP("Enabled_Notifications_Popup", "true", "Show notification popups in the car"),
		ENABLED_NOTIFICATIONS_POPUP_PASSENGER("Enabled_Notifications_Popup_Passenger", "false", "Show notification popups in the car when a passenger is seated"),
		NOTIFICATIONS_SOUND("Notifications_Sound", "true", "Play a notification sound"),
		NOTIFICATIONS_READOUT("Notifications_Readout", "false", "Viewing a notification also reads it aloud"),
		NOTIFICATIONS_READOUT_POPUP("Notifications_Readout_Popup", "false", "New notifications are read aloud"),
		NOTIFICATIONS_READOUT_POPUP_PASSENGER("Notifications_Readout_Popup_Passenger", "false", "New notifications are read aloud when a passenger is seated"),
		NOTIFICATIONS_QUICK_REPLIES("Notifications_Quick_Replies", "[]", "A list of quick replies"),
		ENABLED_CALENDAR("Enabled_Calendar", "false", "Show Calendar in the car"),
		CALENDAR_DETAILED_EVENTS("Calendar_Detailed_Events", "false", "Only show detailed appointments"),
		CALENDAR_AUTOMATIC_NAVIGATION("Calendar_Automatic_Navigation", "false", "Automatically navigate to upcoming appointments"),
		CALENDAR_IGNORE_VISIBILITY("Calendar_Ignore_Visibility", "false", "Ignore calendar visibility for events"),
		ENABLED_MAPS("Enabled_Maps", "false", "Show Custom Maps in the car"),
		MAP_QUICK_DESTINATIONS("Map_Quick_Destinations", "[]", "A list of quick destinations"),
		MAP_WIDESCREEN("Map_Widescreen", "false", "Show Map in widescreen"),
		MAP_INVERT_SCROLL("Map_Invert_Scroll", "false", "Invert zoom direction"),
		MAP_SATELLITE("Map_Satellite", "false", "Show satellite imagery"),
		MAP_TRAFFIC("Map_Traffic", "true", "Show traffic"),
		MAP_USE_PHONE_GPS("Map_Use_Phone_GPS", "false", "Use Phone GPS"),
		NAV_PREFER_CUSTOM_MAP("Nav_Prefer_Custom_Map", "false", "Prefer custom map nav over car nav"),
		MAP_BUILDINGS("Map_Buildings", "true", "Maps 3D Buildings"),
		MAP_TILT("Map_Tilt", "false", "3D tilt and rotate the map"),
		GMAPS_STYLE("GMaps_Style", "auto", "GMaps style"),
		MAP_CUSTOM_STYLE("Mapbox_Custom_Style", "", "Mapbox custom style"),
		MAPBOX_STYLE_URL("Mapbox_Style_Uri", "", "Mapbox style uri"),
		AUDIO_SUPPORTS_USB("Audio_Supports_USB", (Build.VERSION.SDK_INT < Build.VERSION_CODES.O).toString(), "The phone is old enough to support USB accessory audio"),
		AUDIO_FORCE_CONTEXT("Audio_Force_Context", "false", "Force audio context"),
		AUDIO_DESIRED_APP("Audio_Desired_App", "", "Last music app that was playing"),
		SHOW_ADVANCED_SETTINGS("Advanced_Settings", "false", "Show advanced settings"),
		HIDDEN_MUSIC_APPS("Hidden_Music_Apps", "com.android.bluetooth,com.clearchannel.iheartradio.connect,com.google.android.googlequicksearchbox,com.google.android.youtube,com.vanced.android.youtube", "List of music apps to hide from the app list"),
		FORCE_SPOTIFY_LAYOUT("Force_Spotify_Layout", "false", "Use Spotify UI Resources"),
		FORCE_AUDIOPLAYER_LAYOUT("Force_Audioplayer_Layout", "false", "Use legacy music screen even with Spotify resources"),
		CONNECTION_PROBE_IPS("Connection_Probe_IPs", "", "Extra IP(s) to test while probe for the car"),
		DONATION_DAYS_COUNT("Donation_Days_Count", "0", "Number of days that the user has used the app, counting towards the donation request threshold"),
		DONATION_LAST_DAY("Donation_Last_Day", "2000-01-01", "The last day that the user used the app"),
		SPOTIFY_CONTROL_SUCCESS("Spotify_Control_Success", "false", "Whether Spotify Control api worked previously"),
		SPOTIFY_SHOW_UNAUTHENTICATED_NOTIFICATION("Spotify_Show_Unauthenticated_Notification", "false", "Show a notification when the Spotify Web API is not authenticated"),
		SPOTIFY_AUTH_STATE_JSON("Spotify_Auth_State_Json", "", "String serialized JSON representation of the Spotify Web API AuthState."),
		SPOTIFY_LIKED_SONGS_PLAYLIST_STATE("Spotify_Liked_Songs_Playlist_State", "", "Spotify Liked Songs playlist state."),
		SPOTIFY_ARTIST_SONGS_PLAYLIST_STATE("Spotify_Artist_Songs_Playlist_State", "", "Spotify Artist songs playlist state."),
		CACHED_CAR_CAPABILITIES("Cached_Car_Capabilities", "{}", "JSON Object of any previously-cached capabilities"),
		CACHED_CAR_DATA("Cached_Car_Data", "{}", "JSON Object of any previously-cached cds properties"),
		PREFER_CAR_LANGUAGE("Prefer_Car_Language", "true", "Prefer the car's language instead of the phone's language"),
		FORCE_CAR_LANGUAGE("Force_Car_Language", "", "Force a specific language for the car apps"),
		ENABLED_ANALYTICS("Enable_Analytics", "false", "Enable Analytics module"),
		DIMENSIONS_RHMI_WIDTH("Dimensions_RHMI_Width", "", "Force RHMIDimensions to a specific rhmiWidth"),
		DIMENSIONS_RHMI_HEIGHT("Dimensions_RHMI_Height", "", "Force RHMIDimensions to a specific rhmiHeight"),
		DIMENSIONS_MARGIN_LEFT("Dimensions_Margin_Left", "", "Force RHMIDimensions to a specific marginLeft"),
		DIMENSIONS_PADDING_LEFT("Dimensions_Padding_Left", "", "Force RHMIDimensions to a specific paddingLeft"),
		DIMENSIONS_PADDING_TOP("Dimensions_Padding_Top", "", "Force RHMIDimensions to a specific paddingTop"),
		DIMENSIONS_MARGIN_RIGHT("Dimensions_Margin_Right", "", "Force RHMIDimensions to a specific marginRight"),
		BTFETCHUUIDSWITHSDP("btfetchUuidsWithSdp","5000",""),
		ITorqueService("ITorqueService","0",""),
		MUSIC_SEARCH_QUERY_HISTORY("Music_Search_Query_History","", "Music service search query history"),
		MINFRAMETIME("minFrameTime","1000",""),
		compressQuality("compressQuality","30",""),
		wgs84ToGcj02("wgs84ToGcj02","0","")
	}

	/** Store the active preferences in a singleton */
	companion object {
		private const val PREFERENCES_NAME = "AndroidAutoIdrive"

		private val loadedSettings = HashMap<KEYS, String>()

		private val CACHED_KEY_FILENAMES = mapOf(
			KEYS.CACHED_CAR_CAPABILITIES to "car_capabilities.json",
			KEYS.CACHED_CAR_DATA to "car_data.json",
		)

		/**
		 * Initialize the current settings with defaults
		 * which will undo any loadSettings that may have happened
		 */
		fun loadDefaultSettings() {
			synchronized(loadedSettings) {
				loadedSettings.clear()
				KEYS.values().forEach { setting ->
					loadedSettings[setting] = setting.default
				}
			}
		}

		private fun loadCachedKey(ctx: Context, key: KEYS) {
			val filename = CACHED_KEY_FILENAMES[key] ?: return
			val file = File(ctx.cacheDir, filename)
			if (!file.exists()) {
				return
			}
			try {
				BufferedInputStream(FileInputStream(file)).use {
					loadedSettings[key] = it.readBytes().decodeToString()
				}
			} catch (_: IOException) {}
		}

		fun loadSettings(ctx: Context) {
			val preferences = ctx.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
			synchronized(loadedSettings) {
				loadedSettings.clear()
				KEYS.values().forEach { setting ->
					val value = preferences.getString(setting.key, setting.default) ?: setting.default
					loadedSettings[setting] = value
				}
				// default comes from translated strings
				if (loadedSettings[KEYS.NOTIFICATIONS_QUICK_REPLIES] == "[]") {
					loadedSettings[KEYS.NOTIFICATIONS_QUICK_REPLIES] = ctx.getString(R.string.notification_quickreplies_default)
				}

				for (key in CACHED_KEY_FILENAMES.keys) {
					loadCachedKey(ctx, key)
				}
			}
		}

		operator fun get(key: KEYS): String {
			return getSetting(key)
		}
		private fun getSetting(key: KEYS): String {
			synchronized(loadedSettings) {
				return loadedSettings[key] ?: key.default
			}
		}

		private fun saveCachedKey(ctx: Context, key: KEYS, value: String) {
			val filename = CACHED_KEY_FILENAMES[key] ?: return
			val destFile = File(ctx.cacheDir, filename)
			val tmpFile = File(ctx.cacheDir, "$filename.tmp")
			try {
				tmpFile.delete()
				BufferedOutputStream(FileOutputStream(tmpFile)).use {
					it.write(value.encodeToByteArray())
				}
				tmpFile.renameTo(destFile)
			} catch (_: IOException) {}
		}

		fun tempSetSetting(key: KEYS, value: String) {
			synchronized(loadedSettings) {
				loadedSettings[key] = value
			}
		}
		fun saveSetting(ctx: Context, key: KEYS, value: String) {
			if (CACHED_KEY_FILENAMES.containsKey(key)) {
				saveCachedKey(ctx, key, value)

				// clear out this cached data from SharedPreferences
				val preferences = ctx.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
				val editor = preferences.edit()
				editor.remove(key.key)
				editor.apply()
			} else {
				val preferences = ctx.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
				val editor = preferences.edit()
				editor.putString(key.key, value)
				editor.apply()
			}
			loadedSettings[key] = value
		}
	}

	/** Convenience function for getting a setting */
	operator fun get(key: KEYS): String
}

/** A concrete class to read settings from the AppSettings singleton */
class AppSettingsViewer: AppSettings {
	override operator fun get(key: AppSettings.KEYS): String {
		return AppSettings[key]
	}
}

/**
 * An interface for modifying the settings
 */
interface MutableAppSettings: AppSettings {
	operator fun set(key: AppSettings.KEYS, value: String)
}

/**
 * An interface for being notified on changes to the settings
 */
interface AppSettingsObserver: AppSettings {
	var callback: (() -> Unit)?
}

/**
 * A combined interface for modifying the settings and being notified on changes
 */
interface MutableAppSettingsObserver: AppSettingsObserver, MutableAppSettings {
}

/**
 * An isolated MutableAppSettings object, unrelated to the AppSettings singleton settings
 * By default it returns all the default settings values
 */
class MockAppSettings(vararg settings: Pair<AppSettings.KEYS, String> = emptyArray()): MutableAppSettingsObserver {
	override var callback: (() -> Unit)? = null
	val settings = mutableMapOf(*settings)
	override operator fun get(key: AppSettings.KEYS): String {
		return settings[key] ?: key.default
	}
	override operator fun set(key: AppSettings.KEYS, value: String) {
		settings[key] = value
		callback?.invoke()
	}
}

/**
 * A MutableAppSettings object that modifies the global singleton and notifies other instances
 * Clients should set a callback to subscribe, and they should set it to null to unsubscribe to avoid leaking
 * Clients can specify a custom handler on which to receive the callback
 */
class MutableAppSettingsReceiver(val context: Context, val handler: Handler? = null): MutableAppSettingsObserver {
	companion object {
		val INTENT_SETTINGS_CHANGED = "me.hufman.androidautoidrive.INTENT_SETTINGS_CHANGED"
	}

	val receiver = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, p1: Intent?) {
			callback?.invoke()
		}
	}
	override var callback: (() -> Unit)? = null
		set(value) {
			if (field == null && value != null) {
				ContextCompat.registerReceiver(context, receiver, IntentFilter(INTENT_SETTINGS_CHANGED), null, handler, RECEIVER_NOT_EXPORTED)
			}
			if (field != null && value == null) {
				context.unregisterReceiver(receiver)
			}
			field = value
		}

	override operator fun get(key: AppSettings.KEYS): String {
		return AppSettings[key]
	}

	override operator fun set(key: AppSettings.KEYS, value: String) {
		val oldValue = this[key]
		if (oldValue != value) {
			AppSettings.saveSetting(context, key, value)
			context.sendBroadcast(Intent(INTENT_SETTINGS_CHANGED)
					.setPackage(BuildConfig.APPLICATION_ID))
		}
	}
}

abstract class LiveSetting<K>(val context: Context, val key: AppSettings.KEYS): MutableLiveData<K>() {
	val appSettings = MutableAppSettingsReceiver(context, null /* specifically the main thread */)
	init {
		try {
			super.setValue(getData())
		} catch (e: IllegalStateException) {
			super.postValue(getData())
		}
	}

	// backing data access
	abstract fun serialize(value: K): String
	abstract fun deserialize(value: String): K

	private fun getData(): K {
		return deserialize(appSettings[key])
	}
	private fun setData(value: K) {
		val newValue = serialize(value)
		if (appSettings[key] != newValue) {
			appSettings[key] = newValue
		}
	}

	// LiveData interface
	// set the LiveData internal data, to update any observers,
	// and then trigger any other AppSettings listeners
	// getValue just returns the internal data
	override fun getValue(): K? {
		val backing = getData()
		if (backing != super.getValue()) {
			if (Looper.getMainLooper() == Looper.myLooper()) {
				super.setValue(backing)
			} else {
				super.postValue(backing)
			}
		}
		return backing
	}

	override fun setValue(value: K) {
		super.setValue(value)
		setData(value)
	}

	override fun postValue(value: K) {
		super.postValue(value)
		setData(value)
	}

	override fun onActive() {
		super.onActive()

		// subscribe to AppSettings changes from other setters
		// trigger any LiveData observers when the underlying data changes
		appSettings.callback = {
			setValue(getData())
		}

		// refresh the current LiveData state from the current data
		setValue(getData())
	}

	override fun onInactive() {
		super.onInactive()
		// unsubscribe the callback
		appSettings.callback = null
	}
}

class StringLiveSetting(context: Context, key: AppSettings.KEYS): LiveSetting<String>(context, key) {
	override fun serialize(value: String) = value
	override fun deserialize(value: String) = value
}
class BooleanLiveSetting(context: Context, key: AppSettings.KEYS): LiveSetting<Boolean>(context, key) {
	override fun serialize(value: Boolean) = value.toString()
	override fun deserialize(value: String) = value.toBoolean()
}

/**
 * A set that is backed by a comma-separated string in AppSettings
 */
class StoredSet(val appSettings: MutableAppSettings, val key: AppSettings.KEYS): MutableSet<String> {
	fun getAll(): MutableSet<String> {
		val stringValue = appSettings[key]
		 // prefer json but support comma-separated strings for legacy settings
		return try {
			if (stringValue.isNotEmpty() && stringValue[0] == '[') {
				val parsedJson = JsonParser.parseString(stringValue).asJsonArray
				HashSet<String>(parsedJson.size()).apply {
					(0 until parsedJson.size()).forEach {
						add(parsedJson[it].asString)
					}
				}
			} else {
				appSettings[key].split(",").toMutableSet()
			}
		} catch (e: Exception) { HashSet() }
	}
	fun setAll(values: Set<String>) {
		val newSetting = JsonArray().apply {
			values.sorted().forEach { add(it) }
		}.toString()
		if (appSettings[key] != newSetting) {
			appSettings[key] = newSetting
		}
	}
	inline fun <K> withSet(callback: MutableSet<String>.() -> K): K {
		val values = getAll()
		val response = callback(values)
		setAll(values)
		return response
	}

	override fun add(element: String): Boolean = withSet {
		add(element)
	}

	override fun addAll(elements: Collection<String>): Boolean = withSet {
		addAll(elements)
	}

	override fun clear() = withSet {
		clear()
	}

	override fun iterator(): MutableIterator<String> = withSet {
		iterator()
	}

	override fun remove(element: String): Boolean = withSet {
		remove(element)
	}

	override fun removeAll(elements: Collection<String>): Boolean = withSet {
		removeAll(elements)
	}

	override fun retainAll(elements: Collection<String>): Boolean = withSet {
		retainAll(elements)
	}

	override val size: Int
		get() = withSet {
			size
		}

	override fun contains(element: String): Boolean = withSet {
		contains(element)
	}

	override fun containsAll(elements: Collection<String>): Boolean = withSet {
		containsAll(elements)
	}

	override fun isEmpty(): Boolean = withSet {
		isEmpty()
	}

	override fun equals(other: Any?): Boolean = withSet {
		equals(other)
	}

	override fun hashCode(): Int = withSet {
		hashCode()
	}

	override fun toString(): String = withSet {
		toString()
	}
}

class StoredList(val appSettings: MutableAppSettings, val key: AppSettings.KEYS): MutableList<String> {
	fun getAll(): MutableList<String> {
		return try {
			val parsedJson = JsonParser.parseString(appSettings[key]).asJsonArray
			ArrayList<String>(parsedJson.size()).apply {
				(0 until parsedJson.size()).forEach {
					add(parsedJson[it].asString)
				}
			}
		} catch (e: Exception) { ArrayList() }
	}
	fun setAll(values: List<String>) {
		val newSetting = JsonArray().apply {
			values.forEach { add(it) }
		}.toString()
		if (appSettings[key] != newSetting) {
			appSettings[key] = newSetting
		}
	}
	inline fun <K> withList(callback: MutableList<String>.() -> K): K {
		val values = getAll()
		val response = callback(values)
		setAll(values)
		return response
	}

	override val size: Int
		get() = withList {
			size
		}

	override fun contains(element: String): Boolean = withList {
		contains(element)
	}

	override fun containsAll(elements: Collection<String>): Boolean = withList {
		containsAll(elements)
	}

	override fun get(index: Int): String = withList {
		get(index)
	}

	override fun indexOf(element: String): Int = withList {
		indexOf(element)
	}

	override fun isEmpty(): Boolean = withList {
		isEmpty()
	}

	// not actually a mutable iterator
	override fun iterator(): MutableIterator<String> = withList {
		iterator()
	}

	override fun lastIndexOf(element: String): Int = withList {
		lastIndexOf(element)
	}

	override fun add(element: String): Boolean = withList {
		add(element)
	}

	override fun add(index: Int, element: String) = withList {
		add(index, element)
	}

	override fun addAll(index: Int, elements: Collection<String>): Boolean = withList {
		addAll(index, elements)
	}

	override fun addAll(elements: Collection<String>): Boolean = withList {
		addAll(elements)
	}

	override fun clear() = withList {
		clear()
	}

	// not actually a mutable iterator
	override fun listIterator(): MutableListIterator<String> = withList {
		listIterator()
	}

	// not actually a mutable iterator
	override fun listIterator(index: Int): MutableListIterator<String> = withList {
		listIterator(index)
	}

	override fun remove(element: String): Boolean = withList {
		remove(element)
	}

	override fun removeAll(elements: Collection<String>): Boolean = withList {
		removeAll(elements)
	}

	override fun removeAt(index: Int): String = withList {
		removeAt(index)
	}

	override fun retainAll(elements: Collection<String>): Boolean = withList {
		retainAll(elements)
	}

	override fun set(index: Int, element: String): String = withList {
		set(index, element)
	}

	// not actually a mutable sublist
	override fun subList(fromIndex: Int, toIndex: Int): MutableList<String> = withList {
		subList(fromIndex, toIndex)
	}

	override fun equals(other: Any?): Boolean = withList {
		equals(other)
	}

	override fun hashCode(): Int = withList {
		hashCode()
	}

	override fun toString(): String = withList {
		toString()
	}
}