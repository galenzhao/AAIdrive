package me.hufman.androidautoidrive.carapp.maps

import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Handler
import android.util.Log
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel

interface FrameModeListener {
	fun onResume()
	fun onPause()
}

class FrameUpdater(val display: VirtualDisplayScreenCapture, val modeListener: FrameModeListener?): Runnable {
	var destination: RHMIModel? = null
	var isRunning = true
	private var handler: Handler? = null

	fun start(handler: Handler) {
		this.handler = handler
		isRunning = true
		Log.i(TAG, "Starting FrameUpdater thread with handler $handler")
		display.registerImageListener(ImageReader.OnImageAvailableListener {
			schedule(frameDelayMs())
		})
		schedule()  // check for a first image
	}

	override fun run() {
		if (!isRunning) {
			return
		}
		if (destination == null) {
			schedule(frameDelayMs())
			return
		}
		val bitmap = display.getFrame()
		if (bitmap != null) {
			sendImage(bitmap)
		}
		schedule(frameDelayMs())
	}

	private fun frameDelayMs(): Int = MapFrameRate.intervalMs()

	fun schedule(delayMs: Int = 0) {
		if (!isRunning) {
			return
		}
		handler?.removeCallbacks(this)   // remove any previously-scheduled invocations
		handler?.postDelayed(this, delayMs.toLong())
	}

	fun shutDown() {
		isRunning = false
		display.registerImageListener(null)
		handler?.removeCallbacks(this)
	}

	fun showWindow(width: Int, height: Int, destination: RHMIModel) {
		this.destination = destination
		Log.i(TAG, "Changing map mode to $width x $height")
		display.changeImageSize(width, height)
		modeListener?.onResume()
	}
	fun hideWindow(destination: RHMIModel) {
		if (this.destination == destination) {
			this.destination = null
			modeListener?.onPause()
		}
	}

	private fun sendImage(bitmap: Bitmap) {
		val destination = this.destination ?: return
		val imageData = display.compressBitmap(bitmap)
		try {
			if (destination is RHMIModel.RaImageModel) {
				destination.value = imageData
			} else if (destination is RHMIModel.RaListModel) {
				val list = RHMIModel.RaListModel.RHMIListConcrete(1)
				list.addRow(arrayOf(BMWRemoting.RHMIResourceData(BMWRemoting.RHMIResourceType.IMAGEDATA, imageData)))
				destination.value = list
			}
		} catch (e: RuntimeException) {
			Log.w(TAG, "Failed to send map frame to car", e)
		} catch (e: org.apache.etch.util.TimeoutException) {
			// don't crash if the phone is unplugged during a frame update
		}
	}
}