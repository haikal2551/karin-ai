package com.karin.mobile

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        setContent { KarinApp() }
    }

    @Composable
    private fun KarinApp() {
        val ice = Color(0xFFBFF4FF)
        val ice2 = Color(0xFF73DFFF)
        val bg = Color(0xFF06111D)
        val panel = Color(0xFF102C40)

        var pcIp by remember { mutableStateOf("") }
        var token by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Not connected") }
        var files by remember { mutableStateOf<List<String>>(emptyList()) }
        val scope = rememberCoroutineScope()

        MaterialTheme(colorScheme = darkColorScheme(primary = ice2, secondary = ice, background = bg, surface = panel)) {
            Column(
                modifier = Modifier.fillMaxSize().background(bg).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("KARIN AI", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = ice)
                Text("MOBILE LINK • ICE BLUE", color = Color(0xFF89B3C4))
                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier.size(170.dp).border(2.dp, ice, CircleShape).background(panel, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("K", fontSize = 74.sp, fontWeight = FontWeight.Bold, color = ice)
                }

                Spacer(Modifier.height(20.dp))
                OutlinedTextField(pcIp, { pcIp = it }, label = { Text("PC IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(token, { token = it }, label = { Text("Pairing token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                status = testPc(pcIp)
                                if (status.startsWith("Connected")) files = loadFiles(pcIp, token)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Connect PC") }

                    Button(
                        onClick = {
                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                Intent(this@MainActivity, WakeWordService::class.java)
                            )
                            status = "Voice service listening for “Karin”"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Voice ON") }
                }

                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = panel, modifier = Modifier.fillMaxWidth()) {
                    Text(status, modifier = Modifier.padding(14.dp), color = ice)
                }

                Spacer(Modifier.height(18.dp))
                Text("PC DOWNLOADS", color = Color(0xFF89B3C4), modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(6.dp))
                files.take(12).forEach { name ->
                    Surface(color = panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(name, modifier = Modifier.padding(11.dp), color = Color.White)
                    }
                }
            }
        }
    }

    private suspend fun testPc(ip: String): String = withContext(Dispatchers.IO) {
        if (ip.isBlank()) return@withContext "Enter PC IP"
        try {
            val request = Request.Builder().url("http://" + ip + ":51721/ping").build()
            OkHttpClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) "Connected to KARIN Desktop" else "PC replied: " + response.code
            }
        } catch (e: Exception) {
            "Connection failed: " + e.message
        }
    }

    private suspend fun loadFiles(ip: String, token: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://" + ip + ":51721/files")
                .header("X-KARIN-TOKEN", token)
                .build()
            OkHttpClient().newCall(request).execute().use { response ->
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