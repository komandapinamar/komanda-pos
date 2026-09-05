package com.komanda.business.core.audio

import android.content.Context
import android.media.AudioAttributes
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
    private var tts: TextToSpeech? = TextToSpeech(
        context.applicationContext,
        this,
        GOOGLE_TTS_ENGINE
    )
    private val pendingAnnouncements = ArrayDeque<String>()
    private val lock = Any()

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
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

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

            val queuedAnnouncements = synchronized(lock) {
                _isInitialized.value = true
                pendingAnnouncements.toList().also { pendingAnnouncements.clear() }
            }
            queuedAnnouncements.forEach(::speakNow)
            Log.i(tag, "TextToSpeech successfully initialized with Spanish locale.")
        } else {
            Log.e(tag, "Failed to initialize TextToSpeech engine. Status: $status")
        }
    }

    /**
     * Called when a kitchen/order operator marks an order as READY.
     * The Telpo audio routes through Bluetooth or 3.5mm jack to restaurant speakers.
     */
    fun announceOrderReady(purchaseNumber: String, clientName: String? = null) {
        val customer = clientName?.trim()?.takeIf { it.isNotBlank() }
        val message = if (customer != null) {
            "¡Pedido número $purchaseNumber, a nombre de $customer, acercate a retirar!"
        } else {
            "¡Pedido número $purchaseNumber listo para retirar!"
        }
        speak(message)
    }

    /**
     * Called when order is delivered to the customer.
     */
    fun announceOrderDelivered(purchaseNumber: String, clientName: String? = null) {
        val customer = clientName?.trim()?.takeIf { it.isNotBlank() }
        val message = if (customer != null) {
            "Pedido número $purchaseNumber, a nombre de $customer, entregado. ¡Muchas gracias!"
        } else {
            "Pedido número $purchaseNumber entregado. ¡Muchas gracias!"
        }
        speak(message)
    }

    fun speak(text: String) {
        synchronized(lock) {
            if (!_isInitialized.value) {
                pendingAnnouncements.addLast(text)
                return
            }
        }

        speakNow(text)
    }

    private fun speakNow(text: String) {
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun shutdown() {
        synchronized(lock) {
            pendingAnnouncements.clear()
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
    }
}

private const val GOOGLE_TTS_ENGINE = "com.google.android.tts"
