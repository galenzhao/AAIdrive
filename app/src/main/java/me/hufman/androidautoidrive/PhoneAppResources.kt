package me.hufman.androidautoidrive

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import me.hufman.androidautoidrive.utils.PackageManagerCompat.getApplicationInfoCompat
import java.net.URL

/**
 * Methods for loading package data and graphics
 */
interface PhoneAppResources {
	fun getAppIcon(packageName: String): Drawable
	fun getAppName(packageName: String): String
	fun getBitmapDrawable(bitmap: Bitmap): Drawable
	fun getIconDrawable(icon: Icon): Drawable?
	fun getUriDrawable(uri: String): Drawable
}

class PhoneAppResourcesAndroid(val context: Context): PhoneAppResources {
	override fun getAppIcon(packageName: String): Drawable {
		return context.packageManager.getApplicationInfoCompat(packageName)?.loadIcon(context.packageManager) ?: ColorDrawable(0)
	}
	override fun getAppName(packageName: String): String {
		return context.packageManager.getApplicationInfoCompat(packageName)?.loadLabel(context.packageManager)?.toString() ?: ""
	}

	override fun getBitmapDrawable(bitmap: Bitmap): Drawable {
		return BitmapDrawable(context.resources, bitmap)
	}
	override fun getIconDrawable(icon: Icon): Drawable? {
		// Icon.loadDrawable() logs a full ERROR stack when a TYPE_RESOURCE package is missing.
		// Probe first so stale notification icons (e.g. uninstalled Alipay) stay quiet.
		if (icon.type == Icon.TYPE_RESOURCE) {
			val resPackage = icon.resPackage
			if (!resPackage.isNullOrEmpty() && resPackage != "android") {
				val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					PackageManager.MATCH_UNINSTALLED_PACKAGES
				} else {
					0
				}
				if (context.packageManager.getApplicationInfoCompat(resPackage, flags) == null) {
					return null
				}
			}
		}
		return try {
			icon.loadDrawable(context)
		} catch (_: Exception) {
			null
		}
	}

	override fun getUriDrawable(uri: String): Drawable {
		val parsedUri = Uri.parse(uri)
		val inputStream = when (parsedUri.scheme) {
			"android.resource" -> context.contentResolver.openInputStream(parsedUri)
			"content" -> context.contentResolver.openInputStream(parsedUri)
			"http" -> URL(uri).openStream()
			"https" -> URL(uri).openStream()
			else -> throw IllegalArgumentException("Unknown scheme ${parsedUri.scheme}")
		}
		val drawable = Drawable.createFromStream(inputStream, uri)
		inputStream?.close()
		return drawable ?: ColorDrawable(0)
	}
}
