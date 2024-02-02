package me.hufman.androidautoidrive.carapp.carinfo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import com.soywiz.korio.dynamic.KDynamic.Companion.toInt
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.CarInformationDiscovery
import me.hufman.androidautoidrive.CarInformationUpdater
import me.hufman.androidautoidrive.MainService
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.carapp.CarAppService
import me.hufman.androidautoidrive.cds.CDSConnection
import me.hufman.androidautoidrive.cds.CDSConnectionAsync
import org.prowl.torque.remote.ITorqueService
import java.util.Timer

class CarInformationDiscoveryService: CarAppService() {
	val appSettings by lazy { MutableAppSettingsReceiver(applicationContext) }
	var carappCapabilities: CarInformationDiscovery? = null

	private var torqueService: ITorqueService? = null
	private val updateTimer: Timer? = null

	override fun shouldStartApp(): Boolean {
		return true
	}

	override fun onCarStart() {
		Log.i(MainService.TAG, "Starting to discover car capabilities")
		val handler = handler!!

		// receiver to receive capabilities and cds properties
		// wraps the CDSConnection with a Handler async wrapper
		val carInformationUpdater = object: CarInformationUpdater(appSettings) {
			override fun onCdsConnection(connection: CDSConnection?) {
				super.onCdsConnection(connection?.let { CDSConnectionAsync(handler, connection) })
			}
		}

		val certName = if (CarAppAssetResources(applicationContext, "cdsbaseapp").getAppCertificateRaw("") != null) {
			"cdsbaseapp" } else { "smartthings" }
		carappCapabilities = CarInformationDiscovery(iDriveConnectionStatus, securityAccess,
				CarAppAssetResources(applicationContext, certName), carInformationUpdater)
		carappCapabilities?.onCreate()


		AppSettings.loadSettings(applicationContext)

		val settingsViewer = AppSettingsViewer()
		if (settingsViewer[AppSettings.KEYS.ITorqueService].isNotEmpty() &&
			settingsViewer[AppSettings.KEYS.ITorqueService].toInt()>10) {
			// Bind to the torque service
			val intent = Intent()
			intent.setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService")
			val successfulBind = bindService(intent, connection, Context.BIND_EXTERNAL_SERVICE)


			if (successfulBind) {

			} else {
				tip("Unable to connect to Torque plugin service")
			}
		}
	}

	fun tip(tip: String?) {
		//if (!ScanActivity.tipsShown.contains(tip)) {
		//	ScanActivity.tipsShown.add(tip)
			toast(tip, null)
		//}
	}

	fun toast(message: String?) {
		toast(message, null)
	}


	fun toast(message: String?, c: Context?) {
		// If not visible, then don't toast.
		// if (!isForeground)
		//    return;
		var c = c
		if (c == null) c = this
		val context: Context = c
		handler!!.post {
			try {
				// try { Thread.sleep(100); } catch(InterruptedException e) { }
				Toast.makeText(context, message, Toast.LENGTH_LONG).show()
			} catch (e: Throwable) {
				// Do nothing
			}
		}
	}
	/**
	 * Bits of service code. You usually won't need to change this.
	 */
	private val connection: ServiceConnection = object : ServiceConnection {
		override fun onServiceConnected(arg0: ComponentName, service: IBinder) {
			torqueService = ITorqueService.Stub.asInterface(service)
			try {
				// Bind to the torque service

				if (torqueService!!.getVersion() < 19) {
//					popupMessage(
//						"Incorrect version",
//						"You are using an old version of Torque with this plugin.\n\nThe plugin needs the latest version of Torque to run correctly.\n\nPlease upgrade to the latest version of Torque from Google Play",
//						true
//					)
					return
				}
				// String used for code readability.
				var text = ""
				try {
					text = """
	      	${text}API Version: ${torqueService!!.version}
	      	
	      	""".trimIndent()
				} catch(e: RemoteException) {
					Log.e(javaClass.canonicalName, e.message, e)
				}
				tip(text)
			} catch (e: RemoteException) {
				Log.e(javaClass.canonicalName, e.message, e)
			}
		}

		override fun onServiceDisconnected(name: ComponentName) {
			torqueService = null
		}
	}
	override fun onCarStop() {
		if (updateTimer != null) updateTimer.cancel()
		try {
			tip("Press menu for options")
			if(torqueService!=null) {
				unbindService(connection)
			}
		} catch (e: RuntimeException) {
			Log.e(javaClass.canonicalName, e.message, e)
		}
		carappCapabilities?.onDestroy()
		carappCapabilities = null
	}
}