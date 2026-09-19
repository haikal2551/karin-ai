package com.karin.mobile

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val http = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        setContent { KarinApp() }
    }

    @Composable
    private fun KarinApp() {
        val ice = Color(0xFFC9F5FF)
        val ice2 = Color(0xFF75DEFF)
        val bg = Color(0xFF041019)
        val panel = Color(0xFF0B2230)
        val panel2 = Color(0xFF102F41)
        val muted = Color(0xFF83A8B8)
        val prefs = remember { getSharedPreferences("karin_link", MODE_PRIVATE) }

        var mode by remember { mutableStateOf("home") }
        var pcIp by remember { mutableStateOf(prefs.getString("ip", "") ?: "") }
        var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
        var connected by remember { mutableStateOf(false) }
        var voiceOn by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("KARIN siap • Personal Mode") }
        var command by remember { mutableStateOf("") }
        var files by remember { mutableStateOf<List<String>>(emptyList()) }
        val scope = rememberCoroutineScope()

        MaterialTheme(colorScheme = darkColorScheme(primary = ice2, secondary = ice, background = bg, surface = panel)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(bg, Color(0xFF08202D), bg)))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp, 16.dp, 18.dp, 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                            .background(Color(0xFF123B50)).border(1.dp, ice2, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("K", fontSize = 24.sp, fontWeight = FontWeight.Black, color = ice)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("KARIN AI", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(if (mode == "business") "Business Workspace • Ice Blue" else "Personal Assistant • Ice Blue", color = muted, fontSize = 12.sp)
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = if (voiceOn) Color(0xFF123E4D) else panel) {
                        Text(if (voiceOn) "VOICE ON" else "VOICE OFF", modifier = Modifier.padding(10.dp, 6.dp), color = ice2, fontSize = 11.sp)
                    }
                }

                when (mode) {
                    "home" -> HomeScreen(
                        ice, ice2, panel, panel2, muted, connected, voiceOn, status,
                        onVoice = {
                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                Intent(this@MainActivity, WakeWordService::class.java)
                            )
                            voiceOn = true
                            status = "Voice Core aktif • panggil “Karin”"
                        },
                        onLink = { mode = "link" },
                        onBusiness = { mode = "business" },
                        onRoutine = { status = "Routine Center siap dikembangkan dari jadwal personal." }
                    )

                    "business" -> BusinessScreen(
                        ice, ice2, panel, muted,
                        onBack = { mode = "home" },
                        onModule = { module ->
                            scope.launch {
                                if (connected) {
                                    status = sendCommand(pcIp, token, "buka " + module.lowercase())
                                } else {
                                    status = module + " dipilih • hubungkan PC untuk menjalankan modul desktop."
                                }
                            }
                        }
                    )

                    "link" -> LinkScreen(
                        ice, ice2, panel, muted,
                        pcIp, token, connected, status, files, command,
                        onIp = { pcIp = it },
                        onToken = { token = it },
                        onCommand = { command = it },
                        onBack = { mode = "home" },
                        onConnect = {
                            scope.launch {
                                status = testPc(pcIp)
                                connected = status.startsWith("Connected")
                                if (connected) {
                                    prefs.edit().putString("ip", pcIp).putString("token", token).apply()
                                    files = loadFiles(pcIp, token)
                                }
                            }
                        },
                        onSend = {
                            val outbound = command.trim()
                            if (outbound.isNotEmpty()) {
                                scope.launch {
                                    status = sendCommand(pcIp, token, outbound)
                                    command = ""
                                }
                            }
                        }
                    )
                }

                NavigationBar(containerColor = Color(0xFF071A25), tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = mode == "home",
                        onClick = { mode = "home" },
                        icon = { Text("⌂", color = ice2, fontSize = 20.sp) },
                        label = { Text("KARIN") }
                    )
                    NavigationBarItem(
                        selected = mode == "business",
                        onClick = { mode = "business" },
                        icon = { Text("◈", color = ice2, fontSize = 18.sp) },
                        label = { Text("Business") }
                    )
                    NavigationBarItem(
                        selected = mode == "link",
                        onClick = { mode = "link" },
                        icon = { Text("↔", color = ice2, fontSize = 20.sp) },
                        label = { Text("PC Link") }
                    )
                }
            }
        }
    }

    @Composable
    private fun HomeScreen(
        ice: Color, ice2: Color, panel: Color, panel2: Color, muted: Color,
        connected: Boolean, voiceOn: Boolean, status: String,
        onVoice: () -> Unit, onLink: () -> Unit, onBusiness: () -> Unit, onRoutine: () -> Unit
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(210.dp).border(1.dp, Color(0x556FDFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(170.dp).border(1.5.dp, ice2, CircleShape)
                        .background(Color(0xFF12394C), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(112.dp).background(Color(0xFF183F53), CircleShape)) {
                            Box(
                                Modifier.size(86.dp, 38.dp).align(Alignment.TopCenter).offset(y = 12.dp)
                                    .background(Color(0xFF061018), RoundedCornerShape(40.dp, 40.dp, 18.dp, 18.dp))
                            )
                            Row(
                                Modifier.align(Alignment.Center).offset(y = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(34.dp)
                            ) {
                                Box(Modifier.size(10.dp, 5.dp).background(ice, CircleShape))
                                Box(Modifier.size(10.dp, 5.dp).background(ice, CircleShape))
                            }
                            Box(
                                Modifier.size(28.dp, 3.dp).align(Alignment.Center).offset(y = 28.dp)
                                    .background(ice, RoundedCornerShape(3.dp))
                            )
                        }
                        Text("KARIN", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Halo Haikal, ada yang bisa aku bantu?", color = ice, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(status, color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(16.dp))

            Surface(shape = RoundedCornerShape(18.dp), color = panel, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("VOICE CORE", color = ice2, fontSize = 11.sp)
                    Text(
                        if (voiceOn) "Mendengarkan wake word “Karin”" else "Aktifkan, lalu panggil “Karin” dan ucapkan perintah.",
                        color = Color.White, modifier = Modifier.padding(top = 5.dp)
                    )
                    Button(onClick = onVoice, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(if (voiceOn) "Voice Core Active" else "Activate Voice Core")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("PC LINK", if (connected) "Connected" else "Pair desktop", "↔", panel2, ice2, Modifier.weight(1f), onLink)
                ActionCard("ROUTINE", "Reminder & schedule", "◷", panel2, ice2, Modifier.weight(1f), onRoutine)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("BUSINESS", "Finance • Sales • More", "◈", panel2, ice2, Modifier.weight(1f), onBusiness)
                ActionCard("ASSISTANT", "Voice + commands", "✦", panel2, ice2, Modifier.weight(1f), onVoice)
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    @Composable
    private fun ActionCard(
        title: String, subtitle: String, icon: String,
        panel: Color, ice2: Color, modifier: Modifier, onClick: () -> Unit
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp), color = panel,
            modifier = modifier.height(118.dp).clickable(onClick = onClick)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(icon, color = ice2, fontSize = 24.sp)
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Color(0xFF83A8B8), fontSize = 11.sp)
                }
            }
        }
    }

    @Composable
    private fun BusinessScreen(
        ice: Color, ice2: Color, panel: Color, muted: Color,
        onBack: () -> Unit, onModule: (String) -> Unit
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BUSINESS WORKSPACE", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("KARIN Home") }
            }
            Text("Gunakan workspace ini hanya saat kamu masuk ke model bisnis.", color = muted, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))

            val modules = listOf("Finance", "Sales", "Inventory", "Purchasing", "Assets", "Reports", "Manufacturing", "Coretax")
            modules.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { module ->
                        Surface(
                            shape = RoundedCornerShape(18.dp), color = panel,
                            modifier = Modifier.weight(1f).height(108.dp).clickable { onModule(module) }
                        ) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text("◈", color = ice2, fontSize = 22.sp)
                                Column {
                                    Text(module, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Open / command", color = muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            Text("KARIN Business Assistant", color = ice, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text("Saat PC terhubung, modul di atas mengirim perintah nyata ke KARIN Desktop.", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }

    @Composable
    private fun LinkScreen(
        ice: Color, ice2: Color, panel: Color, muted: Color,
        pcIp: String, token: String, connected: Boolean, status: String,
        files: List<String>, command: String,
        onIp: (String) -> Unit, onToken: (String) -> Unit, onCommand: (String) -> Unit,
        onBack: () -> Unit, onConnect: () -> Unit, onSend: () -> Unit
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("KARIN LINK", fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = Color.White)
                TextButton(onClick = onBack) { Text("Back") }
            }
            Text("Android ↔ Windows • token protected", color = muted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(pcIp, onIp, label = { Text("PC IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(token, onToken, label = { Text("Pairing token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(if (connected) "Reconnect PC" else "Connect PC")
            }

            Surface(shape = RoundedCornerShape(16.dp), color = panel, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(status, color = ice, modifier = Modifier.padding(14.dp))
            }

            Text("REMOTE COMMAND", color = ice2, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp, bottom = 7.dp))
            OutlinedTextField(
                command, onCommand,
                placeholder = { Text("contoh: buka dokumen") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = onSend, enabled = connected, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Send to KARIN Desktop")
            }

            Text("PC DOWNLOADS", color = ice2, fontSize = 11.sp, modifier = Modifier.padding(top = 20.dp, bottom = 7.dp))
            if (files.isEmpty()) {
                Text("Belum ada daftar file.", color = muted)
            } else {
                files.take(14).forEach { name ->
                    Surface(color = panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(name, modifier = Modifier.padding(12.dp), color = Color.White)
                    }
                }
            }
        }
    }

    private suspend fun testPc(ip: String): String = withContext(Dispatchers.IO) {
        if (ip.isBlank()) return@withContext "Masukkan IP komputer."
        try {
            val request = Request.Builder().url("http://$ip:51721/ping").build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) "Connected • KARIN Desktop ditemukan" else "PC replied: " + response.code
            }
        } catch (e: Exception) {
            "Connection failed: " + e.message
        }
    }

    private suspend fun sendCommand(ip: String, token: String, text: String): String = withContext(Dispatchers.IO) {
        if (ip.isBlank() || token.isBlank()) return@withContext "PC Link belum dikonfigurasi."
        try {
            val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
            val request = Request.Builder()
                .url("http://$ip:51721/command?text=$encoded")
                .header("X-KARIN-TOKEN", token)
                .build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) "Command sent • $text" else "Command rejected: " + response.code
            }
        } catch (e: Exception) {
            "Command failed: " + e.message
        }
    }

    private suspend fun loadFiles(ip: String, token: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$ip:51721/files")
                .header("X-KARIN-TOKEN", token)
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string().orEmpty())
                val arr = json.optJSONArray("files") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until arr.length()) add(arr.optString(i))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
