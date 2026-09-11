package me.hufman.androidautoidrive.carapp.assistant

import android.os.Handler
import android.os.Looper
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import me.hufman.androidautoidrive.carapp.AMAppList
import me.hufman.androidautoidrive.utils.GraphicsHelpers

class AssistantApp(val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val controller: AssistantController, val graphicsHelpers: GraphicsHelpers) {
	val TAG = "AssistantApp"
	val carConnection = createRHMIApp()
	val amAppList = AMAppList<AssistantAppInfo>(carConnection, graphicsHelpers, "me.hufman.androidautoidrive.assistant")
	private var redrawHandler: Handler? = null
	private var redrawRunnable: Runnable? = null

	private fun createRHMIApp(): BMWRemotingServer {
		val carappListener = CarAppListener()
		val carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)

		return carConnection
	}

	fun onCreate() {
		val assistants = controller.getAssistants()
		amAppList.setApps(assistants.toList())
	}

	fun onDestroy() {
		redrawRunnable?.let { redrawHandler?.removeCallbacks(it) }
		redrawRunnable = null
		redrawHandler = null
		try {
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch (_: Exception) {}
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			appId ?: return
			val assistant = amAppList.getAppInfo(appId) ?: return
			controller.triggerAssistant(assistant)
			val looper = Looper.myLooper() ?: return
			val handler = Handler(looper)
			redrawHandler = handler
			redrawRunnable?.let { handler.removeCallbacks(it) }
			val runnable = Runnable {
				redrawRunnable = null
				try {
					synchronized(carConnection) {
						amAppList.redrawApp(assistant)
					}
				} catch (_: Exception) {}
			}
			redrawRunnable = runnable
			handler.postDelayed(runnable, 2000)
		}
	}
}