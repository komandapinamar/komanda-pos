package com.komanda.business.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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

            // Pick the most natural Spanish voice instead of the engine's default locale
            // voice, which is usually the flat/robotic one.
            selectNaturalSpanishVoice()

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
     * Selects the most natural sounding Spanish voice exposed by the TTS engine.
     *
     * On Google TTS (`com.google.android.tts`), generic `*-language` entries resolve to a
     * compact embedded LSTM voice that sounds flat/robotic, whereas explicit `-x-` neural
     * voices (especially `-network` Cloud WaveNet/Neural2 voices, which automatically fall
     * back to the local superpack if offline) produce natural human prosody.
     */
    private fun selectNaturalSpanishVoice() {
        val engine = tts ?: return
        val candidates = engine.voices
            ?.filter { it.locale.language.equals("es", ignoreCase = true) }
            ?.sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { if (it.name.endsWith("-language", ignoreCase = true)) 1 else 0 }
                    .thenBy { if (it.isNetworkConnectionRequired || it.name.contains("-network", ignoreCase = true)) 0 else 1 }
                    .thenBy { if (it.name.contains("-x-", ignoreCase = true)) 0 else 1 }
                    .thenBy { accentRank(it.locale) }
                    .thenBy { it.latency }
            )
            .orEmpty()

        if (candidates.isEmpty()) {
            Log.w(tag, "TTS engine exposes no Spanish voice; keeping the locale default")
            return
        }

        val chosen = candidates.firstOrNull { engine.setVoice(it) == TextToSpeech.SUCCESS }
        if (chosen != null) {
            Log.i(
                tag,
                "Spanish voice '${chosen.name}' selected " +
                    "(quality=${chosen.quality}, network=${chosen.isNetworkConnectionRequired})"
            )
        } else {
            Log.w(tag, "No Spanish voice could be applied; keeping the locale default")
        }
    }

    private fun accentRank(locale: Locale): Int = when (locale.country.uppercase(Locale.US)) {
        "AR" -> 0
        "MX" -> 1
        "US" -> 2
        "ES" -> 3
        else -> 9
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
