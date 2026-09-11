package me.hufman.androidautoidrive.carapp.carinfo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.widget.Toast
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

class CarInformationDiscoveryService: CarAppService() {
	val appSettings by lazy { MutableAppSettingsReceiver(applicationContext) }
	var carappCapabilities: CarInformationDiscovery? = null

	// whether bindService was called for the Torque plugin service, which must be unbound later
	private var torqueBindRequested = false

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
		val torqueSetting = AppSettingsViewer()[AppSettings.KEYS.ITorqueService].trim().toIntOrNull() ?: 0
		if (torqueSetting > 10) {
			bindTorque()
		}
	}

	private fun bindTorque() {
		val intent = Intent().setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService")
		val successfulBind = try {
			torqueBindRequested = true
			bindService(intent, torqueConnection, Context.BIND_AUTO_CREATE)
		} catch (e: SecurityException) {
			Log.w(MainService.TAG, "Not allowed to bind to the Torque plugin service", e)
			false
		}
		if (!successfulBind) {
			toast("Unable to connect to Torque plugin service")
		}
	}

	private fun unbindTorque() {
		if (torqueBindRequested) {
			torqueBindRequested = false
			try {
				unbindService(torqueConnection)
			} catch (e: IllegalArgumentException) {
				// was never bound
			}
		}
	}

	private fun toast(message: String) {
		handler?.post {
			try {
				Toast.makeText(this, message, Toast.LENGTH_LONG).show()
			} catch (e: Throwable) {
				// Do nothing
			}
		}
	}

	private val torqueConnection: ServiceConnection = object : ServiceConnection {
		override fun onServiceConnected(name: ComponentName, service: IBinder) {
			// TODO read PIDs through org.prowl.torque.remote.ITorqueService
			Log.i(MainService.TAG, "Connected to Torque plugin service $name")
		}

		override fun onServiceDisconnected(name: ComponentName) {
			Log.i(MainService.TAG, "Disconnected from Torque plugin service $name")
		}
	}

	override fun onCarStop() {
		unbindTorque()
		carappCapabilities?.onDestroy()
		carappCapabilities = null
	}
}
