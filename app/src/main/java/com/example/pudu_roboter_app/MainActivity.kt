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
import org.json.JSONObject
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
        var robots by remember { mutableStateOf<List<Robot>>(emptyList()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // Automatisches Abrufen der Roboterdaten
        LaunchedEffect(Unit) {
            fetchRobots { result ->
                when (result) {
                    is Result.Success -> robots = result.data
                    is Result.Error -> errorMessage = result.message
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text("Roboter in der Gruppe", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Text("Fehler: $errorMessage", color = Color.Red, fontSize = 16.sp)
            }

            // Dynamische Buttons für jeden Roboter
            robots.forEach { robot ->
                Button(onClick = { /* Hier könnte man den Roboter steuern */ }) {
                    Text(robot.name)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack() }) {
                Text("Zurück")
            }
        }
    }


    data class Robot(val id: String, val name: String)

    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
    }

    private fun fetchRobots(callback: (Result<List<Robot>>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Schritt 1: `device_id` abrufen
                val deviceId = fetchDeviceId() ?: return@launch callback(Result.Error("Keine Device ID gefunden"))

                // Schritt 2: `group_id` abrufen
                val groupId = getGroupId(deviceId) ?: return@launch callback(Result.Error("Keine Gruppen gefunden"))

                // Schritt 3: Roboter abrufen
                val url = "http://127.0.0.1:90/api/robots?device=$deviceId&group_id=$groupId"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val jsonObject = JSONObject(response)
                val dataObject = jsonObject.getJSONObject("data")
                val robotsArray = dataObject.getJSONArray("robots")

                val robots = mutableListOf<Robot>()
                for (i in 0 until robotsArray.length()) {
                    val robot = robotsArray.getJSONObject(i)
                    robots.add(Robot(robot.getString("id"), robot.getString("name")))
                }

                callback(Result.Success(robots))
            } catch (e: Exception) {
                callback(Result.Error("Fehler beim Abrufen der Roboter: ${e.message}"))
            }
        }
    }

    private fun fetchDeviceId(): String? {
        return try {
            val url = "http://127.0.0.1:90/api/devices"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)
            val devicesArray = jsonObject.getJSONObject("data").getJSONArray("devices")

            if (devicesArray.length() > 0) {
                devicesArray.getJSONObject(0).getString("deviceId")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getGroupId(deviceId: String): String? {
        return try {
            val url = "http://127.0.0.1:90/api/robot/groups?device=$deviceId"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)
            val groupsArray = jsonObject.getJSONObject("data").getJSONArray("robotGroups")

            if (groupsArray.length() > 0) {
                groupsArray.getJSONObject(0).getString("id")
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
