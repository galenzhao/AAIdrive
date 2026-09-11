package android.os

object SystemClock {
	@JvmStatic
	fun elapsedRealtime(): Long {
		return System.currentTimeMillis()
	}

	@JvmStatic
	fun elapsedRealtimeNanos(): Long {
		return System.nanoTime()
	}
}