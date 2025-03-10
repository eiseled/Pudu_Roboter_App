package com.example.pudu_roboter_app

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "wifiCheck") {
                composable("wifiCheck") { WifiCheckScreen(navController, getWifiIpAddress()) }
                composable("secondScreen") { SecondScreen(navController) }
            }
        }
    }

    @Composable
    fun WifiCheckScreen(navController: NavHostController, currentIp: String) {
        var targetIp by remember { mutableStateOf("10.0.2.16") }
        val isConnected = currentIp == targetIp
        val statusText = if (isConnected) {
            "Mit der gewünschten IP $targetIp verbunden"
        } else {
            "Nicht mit der gewünschten IP verbunden. Aktuelle IP: $currentIp"
        }
        var showError by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = "test",
                tint = if (isConnected) Color.Green else Color.Red,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = statusText, fontSize = 18.sp)
            OutlinedTextField(
                value = targetIp,
                onValueChange = { targetIp = it },
                label = { Text("Ziel IP-Adresse") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                if (isConnected) {
                    navController.navigate("secondScreen")
                } else {
                    showError = true
                }
            }) {
                Text("Zum zweiten Screen")
            }

            if (showError) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Fehler: Verbinde dich mit dem richtigen Netzwerk!", color = Color.Red, fontSize = 16.sp)
            }
        }
    }
    @Composable
    fun SecondScreen(navController: NavHostController) {
        var responseText by remember { mutableStateOf("Noch keine Antwort") }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text("Dies ist der zweite Screen", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { sendApiRequest("https://jsonplaceholder.typicode.com/posts/1") { responseText = it } }) {
                Text("GET Request senden")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { sendApiRequest("https://jsonplaceholder.typicode.com/posts", "POST") { responseText = it } }) {
                Text("POST Request senden")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(responseText, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack() }) {
                Text("Zurück")
            }
        }
    }

    private fun sendApiRequest(url: String, method: String = "GET", callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                callback(response)
            } catch (e: Exception) {
                callback("Fehler: ${e.message}")
            }
        }
    }

    private fun getWifiIpAddress(): String {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return "Keine Verbindung"
        val linkProperties: LinkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return "IP nicht verfügbar"

        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            if (address is Inet4Address) {
                return address.hostAddress ?: "IP nicht verfügbar"
            }
        }
        return "IP nicht verfügbar"
    }
}
