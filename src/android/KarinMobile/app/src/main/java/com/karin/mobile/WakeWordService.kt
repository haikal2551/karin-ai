package com.karin.mobile

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class WakeWordService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val channelId = "karin_voice"

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "KARIN Voice", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(
            51,
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("KARIN AI")
                .setContentText("Listening for “Karin”")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        )
        tts = TextToSpeech(this, this)
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    private fun inspect(bundle: Bundle?) {
        val items = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (items.any { it.contains("karin", ignoreCase = true) }) {
            tts?.speak("Iya, aku di sini.", TextToSpeech.QUEUE_FLUSH, null, "wake")
        }
    }

    override fun onResults(results: Bundle?) { inspect(results); startListening() }
    override fun onPartialResults(partialResults: Bundle?) { inspect(partialResults) }
    override fun onError(error: Int) { startListening() }
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onInit(status: Int) { tts?.language = Locale("id", "ID") }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { recognizer?.destroy(); tts?.shutdown(); super.onDestroy() }
}