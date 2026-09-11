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
	@Volatile var destination: RHMIModel? = null
	@Volatile var isRunning = true
	@Volatile private var handler: Handler? = null
	@Volatile private var lastFrameTime = 0L

	fun start(handler: Handler) {
		this.handler = handler
		isRunning = true
		Log.i(TAG, "Starting FrameUpdater thread with handler $handler")
		display.registerImageListener(ImageReader.OnImageAvailableListener {
			// Called from the UI thread for every rendered image
			// throttle: aim at a fixed time after the last sent frame, so that a continuously
			// rendering map doesn't keep pushing the next send further back
			val wait = lastFrameTime + frameDelayMs() - System.currentTimeMillis()
			schedule(wait.coerceIn(0, frameDelayMs().toLong()).toInt())
		})
		schedule()  // check for a first image
	}

	override fun run() {
		if (!isRunning || destination == null) {
			// showWindow will check for a frame when the map is visible again
			return
		}
		val bitmap = display.getFrame()
		if (bitmap != null) {
			sendImage(bitmap)
			lastFrameTime = System.currentTimeMillis()
			// check again in case more images arrived while sending
			schedule(frameDelayMs())
		}
		// otherwise wait for the image listener to say a new image is available
	}

	private fun frameDelayMs(): Int = MapFrameRate.intervalMs()

	fun schedule(delayMs: Int = 0) {
		if (!isRunning) {
			return
		}
		val handler = handler ?: return
		handler.removeCallbacks(this)   // remove any previously-scheduled invocations
		handler.postDelayed(this, delayMs.toLong())
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
		schedule()
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
