package me.hufman.androidautoidrive.maps

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsString
import java.io.IOException
import java.net.URL

object AmapTokenValidation {
	suspend fun validateToken(token: String): Boolean? {
		return withContext(Dispatchers.IO) {
			// AMap doesn't have a public token validation API
			// We'll just check if the token is not empty and not "unset"
			if (token.isBlank() || token == "unset") {
				false
			} else {
				// For AMap, we can't easily validate the token without making a geocoding request
				// So we'll just assume it's valid if it's not empty
				true
			}
		}
	}
}
