package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream

interface ScreenCaptureConfig {
	val maxWidth: Int
	val maxHeight: Int
	val compressFormat: Bitmap.CompressFormat
	val compressQuality: Int
}

data class StaticScreenCaptureConfig(override val maxWidth: Int,
                                     override val maxHeight: Int,
                                     override var compressFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
                                     override var compressQuality: Int = 30  //quality 65 is fine, and you get readable small texts, below that it was sometimes hard to read
): ScreenCaptureConfig

/**
 * Generates images from an ImageReader, handily resized and compressed to JPG
 * VirtualDisplayScreenCapture.createVirtualDisplay can take this imageCapture and render to it
 */
class VirtualDisplayScreenCapture(val imageCapture: ImageReader, val bitmapConfig: Bitmap.Config, val screenCaptureConfig: ScreenCaptureConfig) {
	companion object {
		// the menu preview and the fullscreen map alternate, so keep both resize buffers around
		private const val RESIZE_CACHE_SIZE = 3

		fun build(config: ScreenCaptureConfig): VirtualDisplayScreenCapture {
			return VirtualDisplayScreenCapture(
					ImageReader.newInstance(config.maxWidth, config.maxHeight, PixelFormat.RGBA_8888, 2),
					Bitmap.Config.ARGB_8888,
					config)
		}

		fun createVirtualDisplay(context: Context, imageCapture: ImageReader, dpi:Int = 100,
		                         name: String = "IDriveVirtualDisplay"): VirtualDisplay {
			val displayManager = context.getSystemService(DisplayManager::class.java)
			Log.i(TAG, "Creating virtual display ${imageCapture.width}x${imageCapture.height} @ ${dpi}dpi")
			return displayManager.createVirtualDisplay(name,
					imageCapture.width, imageCapture.height, dpi,
					imageCapture.surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
					null, Handler(Looper.getMainLooper()))
		}

	}

	// guards the ImageReader and bitmaps, because the car thread reads frames
	// while onCarStop may tear everything down from another thread
	private val lock = Any()
	@Volatile private var destroyed = false

	/** Prepares an ImageReader, and sends JPG-compressed images to a callback */
	private val origRect = Rect(0, 0, imageCapture.width, imageCapture.height)    // the full size of the main map
	private var sourceRect = Rect(0, 0, imageCapture.width, imageCapture.height)    // the capture region from the main map
	private var bitmap = Bitmap.createBitmap(imageCapture.width, imageCapture.height, bitmapConfig)
	private val resizeFilter = Paint().apply { this.isFilterBitmap = true }
	private var resizedBitmap = Bitmap.createBitmap(imageCapture.width, imageCapture.height, bitmapConfig)
	private var resizedCanvas = Canvas(resizedBitmap)
	private var resizedRect = Rect(0, 0, resizedBitmap.width, resizedBitmap.height) // draw to the full region of the resize canvas
	private val resizedBitmaps = HashMap<Pair<Int, Int>, Bitmap>().apply {
		this[resizedBitmap.width to resizedBitmap.height] = resizedBitmap
	}
	private val outputFile = ByteArrayOutputStream()


	fun registerImageListener(listener: ImageReader.OnImageAvailableListener?) {
		synchronized(lock) {
			if (destroyed) return
			this.imageCapture.setOnImageAvailableListener(listener, Handler(Looper.getMainLooper()))
		}
	}

	fun changeImageSize(width: Int, height: Int) {
		synchronized(lock) {
			if (destroyed) return
			val key = width to height
			val target = resizedBitmaps[key] ?: Bitmap.createBitmap(width, height, bitmapConfig).also {
				if (resizedBitmaps.size >= RESIZE_CACHE_SIZE) {
					resizedBitmaps.clear()
				}
				resizedBitmaps[key] = it
			}
			if (target !== resizedBitmap) {
				resizedBitmap = target
				resizedCanvas = Canvas(target)
			}
			resizedRect = Rect(0, 0, resizedBitmap.width, resizedBitmap.height)
			sourceRect = findInnerRect(origRect, resizedRect)    // the capture region from the main map
			Log.i(TAG, "Preparing resize pipeline of $sourceRect to $resizedRect")
		}
	}

	private fun findInnerRect(fullRect: Rect, smallRect: Rect): Rect {
		/** Given a destination smallRect,
		 * find the biggest rect inside fullRect that matches the aspect ratio
		 */
		val aspectRatio: Float = 1.0f * smallRect.width() / smallRect.height()
		// try for max width
		var width = fullRect.width()
		var height = (width / aspectRatio).toInt()
		if (height > fullRect.height()) {
			// try for max height
			height = fullRect.height()
			width = (height * aspectRatio).toInt()
		}
		val left = fullRect.width() / 2 - width / 2
		val top = fullRect.height() / 2 - height / 2
		return Rect(left, top, left+width, top+height)
	}

	/** Must be called while holding the lock */
	private fun convertToBitmap(image: Image): Bitmap {
		// read from the image store to a Bitmap object
		val planes = image.planes
		val buffer = planes[0].buffer
		val padding = planes[0].rowStride - planes[0].pixelStride * image.width
		val width = image.width + padding / planes[0].pixelStride
		if (bitmap.width != width) {
			Log.i(TAG, "Setting capture bitmap to ${width}x${imageCapture.height}")
			bitmap = Bitmap.createBitmap(width, imageCapture.height, bitmapConfig)
		}
		bitmap.copyPixelsFromBuffer(buffer)

		// resize the image
		if (sourceRect != resizedRect) {
			// if we need to resize
			resizedCanvas.drawBitmap(bitmap, sourceRect, resizedRect, resizeFilter)
			return resizedBitmap
		}
		return bitmap
	}

	fun getFrame(): Bitmap? {
		synchronized(lock) {
			if (destroyed) return null
			val image = try {
				imageCapture.acquireLatestImage()
			} catch (e: IllegalStateException) {
				null
			} ?: return null
			try {
				return convertToBitmap(image)
			} finally {
				image.close()
			}
		}
	}

	fun compressBitmap(bitmap: Bitmap): ByteArray {
		// send to car
		synchronized(lock) {
			outputFile.reset()
			bitmap.compress(screenCaptureConfig.compressFormat, screenCaptureConfig.compressQuality, outputFile)
			return outputFile.toByteArray()
		}
	}

	fun onDestroy() {
		synchronized(lock) {
			if (destroyed) return
			destroyed = true
			try {
				this.imageCapture.setOnImageAvailableListener(null, null)
				this.imageCapture.close()
			} catch (_: Exception) {}
			// the bitmaps are left for the GC, in case a frame is still being sent to the car
			resizedBitmaps.clear()
		}
	}
}
