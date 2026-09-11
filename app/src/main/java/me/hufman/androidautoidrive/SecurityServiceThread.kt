package me.hufman.androidautoidrive

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess

class SecurityServiceThread(val securityAccess: SecurityAccess): HandlerThread("SecurityServiceThread") {
	override fun onLooperPrepared() {
		securityAccess.connect()
	}

	fun connect() {
		val threadLooper = looper
		if (threadLooper != null) {
			Handler(threadLooper).post {
				securityAccess.connect()
			}
		}
	}

	fun disconnect() {
		val threadLooper = looper
		if (threadLooper != null) {
			Handler(threadLooper).post {
				securityAccess.disconnect()
				quitSafely()
			}
		} else {
			quitSafely()
		}
	}
}