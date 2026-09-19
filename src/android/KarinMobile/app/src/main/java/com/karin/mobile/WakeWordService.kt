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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class WakeWordService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var waitingForCommand = false
    private val channelId = "karin_voice"
    private val http = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "KARIN Voice", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(
            51,
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("KARIN AI • Voice Core")
                .setContentText("Panggil “Karin” untuk memberi perintah")
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
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    private fun inspect(bundle: Bundle?) {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.trim().orEmpty()
        if (text.isBlank()) return

        if (waitingForCommand) {
            waitingForCommand = false
            executeCommand(text)
            return
        }

        val lower = text.lowercase()
        val index = lower.indexOf("karin")
        if (index < 0) return

        val command = text.substring(index + 5).trim(' ', ',', '.', ':', '-')
        if (command.isBlank()) {
            waitingForCommand = true
            speak("Iya, aku dengar. Silakan ucapkan perintahmu.")
        } else {
            executeCommand(command)
        }
    }

    private fun executeCommand(command: String) {
        val prefs = getSharedPreferences("karin_link", MODE_PRIVATE)
        val ip = prefs.getString("ip", "").orEmpty()
        val token = prefs.getString("token", "").orEmpty()

        if (ip.isBlank() || token.isBlank()) {
            speak("KARIN Link belum terhubung ke komputer.")
            return
        }

        Thread {
            try {
                val encoded = URLEncoder.encode(command, StandardCharsets.UTF_8.toString())
                val request = Request.Builder()
                    .url("http://$ip:51721/command?text=$encoded")
                    .header("X-KARIN-TOKEN", token)
                    .build()
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) speak("Perintah dikirim ke komputer.")
                    else speak("Komputer menolak perintah.")
                }
            } catch (_: Exception) {
                speak("Aku belum bisa terhubung ke komputer.")
            }
        }.start()
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "karin-response")
    }

    override fun onResults(results: Bundle?) {
        inspect(results)
        startListening()
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onError(error: Int) { startListening() }
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        engine.language = Locale("id", "ID")

        val preferred = engine.voices?.firstOrNull {
            it.locale.language == "id" &&
                (it.name.contains("female", true) || it.name.contains("woman", true))
        } ?: engine.voices?.firstOrNull { it.locale.language == "id" }

        if (preferred != null) engine.voice = preferred
        engine.setSpeechRate(1.02f)
        engine.setPitch(1.08f)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
