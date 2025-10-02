package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class AmapVoiceService(private val context: Context) : TextToSpeech.OnInitListener {
    
    companion object {
        private const val TAG = "AmapVoiceService"
    }
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    init {
        initializeTTS()
    }
    
    private fun initializeTTS() {
        tts = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Chinese language not supported, trying English")
                tts?.setLanguage(Locale.ENGLISH)
            }
            isInitialized = true
            Log.i(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }
    
    fun speak(text: String) {
        if (isInitialized && tts != null) {
            Log.i(TAG, "Speaking: $text")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w(TAG, "TTS not initialized, cannot speak: $text")
        }
    }
    
    fun stopSpeaking() {
        tts?.stop()
    }
    
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }
    
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }
    
    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
