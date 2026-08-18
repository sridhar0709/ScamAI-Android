package com.scamai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class ScanMode { MESSAGE, URL, SCREENSHOT, CONVERSATION }

private data class ScanResult(
    val score: Int,
    val level: String,
    val category: String,
    val reasons: List<String>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScamAIApp() }
    }
}

@Composable
private fun ScamAIApp() {
    var mode by remember { mutableStateOf(ScanMode.MESSAGE) }
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var online by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("ScamAI")
                            Text(
                                if (online) "Online intelligence" else "Offline protection",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    actions = {
                        Switch(checked = online, onCheckedChange = { online = it })
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Check before you click, pay, or share.",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(mode == ScanMode.MESSAGE, { mode = ScanMode.MESSAGE }, label = { Text("Message") })
                        FilterChip(mode == ScanMode.URL, { mode = ScanMode.URL }, label = { Text("URL") })
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(mode == ScanMode.CONVERSATION, { mode = ScanMode.CONVERSATION }, label = { Text("Chat") })
                        FilterChip(mode == ScanMode.SCREENSHOT, { mode = ScanMode.SCREENSHOT }, label = { Text("Screenshot") })
                    }
                }

                item {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        label = { Text(if (mode == ScanMode.URL) "Paste URL" else "Paste suspicious text") },
                        placeholder = { Text("Paste a message or URL here…") }
                    )
                }

                item {
                    Button(
                        enabled = input.isNotBlank() && !loading && mode != ScanMode.SCREENSHOT,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            loading = true
                            scope.launch {
                                result = if (online) {
                                    ApiClient.analyze(input)
                                } else {
                                    OfflineDetector.analyze(input)
                                }
                                loading = false
                            }
                        }
                    ) {
                        Text(if (loading) "Analyzing…" else "Analyze")
                    }
                }

                item {
                    result?.let { ScanResultCard(it) }
                }

                item {
                    Text(
                        "Privacy: offline mode keeps the text on this device. Online mode sends the text to your configured ScamAI API.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanResultCard(result: ScanResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("${result.level} • ${result.score}/100", style = MaterialTheme.typography.headlineSmall)
            Text(result.category, style = MaterialTheme.typography.titleMedium)
            result.reasons.forEach { Text("• $it") }
        }
    }
}

private object OfflineDetector {
    fun analyze(text: String): ScanResult {
        val t = text.lowercase()
        var score = 0
        val reasons = mutableListOf<String>()

        val urgency = listOf("immediately", "urgent", "act now", "final warning", "within 24 hours")
        val credentials = listOf("otp", "password", "pin", "cvv", "verification code")
        val money = listOf("pay", "payment", "fee", "deposit", "send money", "upi")
        val fear = listOf("blocked", "suspended", "disconnected", "freeze", "arrest")
        val kyc = listOf("kyc", "aadhaar", "aadhar", "pan card")

        if (urgency.any { t.contains(it) }) { score += 18; reasons += "Uses urgency pressure." }
        if (credentials.any { t.contains(it) }) { score += 25; reasons += "Requests sensitive credentials." }
        if (money.any { t.contains(it) }) { score += 20; reasons += "Contains a payment or money request." }
        if (fear.any { t.contains(it) }) { score += 18; reasons += "Uses fear or account-threat language." }
        if (kyc.any { t.contains(it) }) { score += 15; reasons += "Contains KYC/identity-verification language." }
        if (Regex("""https?://|www\.""").containsMatchIn(t)) { score += 15; reasons += "Contains a link." }

        score = score.coerceIn(0, 100)
        val level = when {
            score >= 80 -> "CRITICAL"
            score >= 60 -> "HIGH"
            score >= 30 -> "CAUTION"
            else -> "LOW"
        }
        val category = when {
            kyc.any { t.contains(it) } -> "KYC / BANK"
            money.any { t.contains(it) } -> "PAYMENT"
            credentials.any { t.contains(it) } -> "CREDENTIAL HARVESTING"
            else -> "GENERAL"
        }
        return ScanResult(score, level, category, reasons.ifEmpty { listOf("No strong offline scam signals detected.") })
    }
}

private object ApiClient {
    // Set this to your deployed HTTPS API when ready.
    private const val BASE_URL = "https://YOUR-SCAMAI-DOMAIN.example"

    suspend fun analyze(text: String): ScanResult {
        // Online implementation is intentionally isolated here so the backend
        // URL/authentication can be configured without changing the UI.
        // Until BASE_URL is configured, safely fall back to offline detection.
        if (BASE_URL.contains("YOUR-SCAMAI-DOMAIN")) {
            return OfflineDetector.analyze(text)
        }

        return try {
            val client = okhttp3.OkHttpClient()
            val json = org.json.JSONObject().put("text", text)
            val request = okhttp3.Request.Builder()
                .url("$BASE_URL/v1/analyze")
                .post(
                    okhttp3.RequestBody.create(
                        "application/json".toMediaTypeOrNull(),
                        json.toString()
                    )
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return OfflineDetector.analyze(text)
                val body = org.json.JSONObject(response.body?.string().orEmpty())
                val evidence = mutableListOf<String>()
                body.optJSONArray("evidence")?.let { arr ->
                    for (i in 0 until arr.length()) evidence += arr.optString(i)
                }
                ScanResult(
                    body.optInt("risk_score", 0),
                    body.optString("risk_level", "LOW"),
                    body.optString("category", "GENERAL"),
                    evidence.ifEmpty { listOf("Online analysis returned no evidence.") }
                )
            }
        } catch (_: Exception) {
            OfflineDetector.analyze(text)
        }
    }

    private fun String.toMediaTypeOrNull() =
        okhttp3.MediaType.parse(this)
}
