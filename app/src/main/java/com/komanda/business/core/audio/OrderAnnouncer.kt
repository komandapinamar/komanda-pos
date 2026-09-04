package com.komanda.business.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class OrderAnnouncer(
    context: Context
) : TextToSpeech.OnInitListener {

    private val tag = "OrderAnnouncer"
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val arLocale = Locale("es", "AR")
            val result = tts?.setLanguage(arLocale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to generic Spanish
                tts?.setLanguage(Locale("es", "ES"))
            }

            tts?.setSpeechRate(0.95f) // Slightly slower for clarity in noisy restaurants
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(tag, "TTS started speaking: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(tag, "TTS finished speaking: $utteranceId")
                }

                override fun onError(utteranceId: String?) {
                    Log.e(tag, "TTS error speaking: $utteranceId")
                }
            })

            _isInitialized.value = true
            Log.i(tag, "TextToSpeech successfully initialized with Spanish locale.")
        } else {
            Log.e(tag, "Failed to initialize TextToSpeech engine. Status: $status")
        }
    }

    /**
     * Called when a kitchen/order operator marks an order as READY.
     * The Telpo audio routes through Bluetooth or 3.5mm jack to restaurant speakers.
     */
    fun announceOrderReady(purchaseNumber: String) {
        val message = "¡Pedido número $purchaseNumber listo para retirar!"
        speak(message)
    }

    /**
     * Called when order is delivered to the customer.
     */
    fun announceOrderDelivered(purchaseNumber: String) {
        val message = "Pedido número $purchaseNumber entregado. ¡Muchas gracias!"
        speak(message)
    }

    fun speak(text: String) {
        if (!_isInitialized.value) {
            Log.w(tag, "TTS not ready yet, skipping announcement: $text")
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
    }
}
