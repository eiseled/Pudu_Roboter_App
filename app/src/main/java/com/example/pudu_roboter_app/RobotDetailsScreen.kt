package com.example.pudu_roboter_app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Destination(
    val name: String,
    val type: String
)

suspend fun fetchDestinations(serverAddress: String, deviceId: String, robotId: String): Result<List<Destination>> = withContext(
    Dispatchers.IO) {
    try {
        // Entfernung der Parameter page_size und page_index
        val urlString = "http://$serverAddress/api/destinations?device=$deviceId&robot_id=$robotId"

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()


            val jsonResponse = JSONObject(response)
            val code = jsonResponse.optInt("code", -1)

            if (code == 0) {
                val data = jsonResponse.optJSONObject("data")
                if (data != null) {
                    val destinationsArray = data.optJSONArray("destinations")
                    if (destinationsArray != null) {
                        val destinations = mutableListOf<Destination>()
                        for (i in 0 until destinationsArray.length()) {
                            val destinationJson = destinationsArray.getJSONObject(i)
                            destinations.add(
                                Destination(
                                    name = destinationJson.optString("name", ""),
                                    type = destinationJson.optString("type", "")
                                )
                            )
                        }
                        Result.Success(destinations)
                    } else {
                        Result.Error("Keine Zielorte in der Antwort gefunden")
                    }
                } else {
                    Result.Error("Keine Daten in der Antwort gefunden")
                }
            } else {
                val msg = jsonResponse.optString("msg", "Unbekannter Fehler")
                Result.Error("API-Fehler: $msg")
            }
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
            connection.disconnect()
            Result.Error("HTTP-Fehler: $responseCode")
        }
    } catch (e: Exception) {
        Result.Error("Fehler beim Abrufen der Zielorte: ${e.message}")
    }
}

@Composable
fun RobotDetailsScreen(
    navController: androidx.navigation.NavHostController,
    serverAddress: String,
    deviceId: String,
    robot: Robot
) {
    var allDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var filteredDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var debugInfo by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Funktion zum Filtern der Zielorte - hier entfernen wir den Ausgabetisch (dining_outlet)
    fun filterDestinations(destinations: List<Destination>): List<Destination> {
        return destinations.filter { destination ->
            destination.type != "dining_outlet"
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                debugInfo = "Beginne API-Aufruf mit\nServer: $serverAddress\nDevice ID: $deviceId\nRobot ID: ${robot.id}"
                val result = fetchDestinations(serverAddress, deviceId, robot.id)
                when (result) {
                    is Result.Success -> {
                        allDestinations = result.data
                        filteredDestinations = filterDestinations(result.data)
                        debugInfo += "\nErfolgreich ${result.data.size} Zielorte geladen, ${filteredDestinations.size} nach Filterung"
                    }
                    is Result.Error -> {
                        errorMessage = result.message
                        debugInfo += "\nFehler: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Unbehandelte Ausnahme: ${e.message}"
                debugInfo += "\nAusnahme: ${e.message}\n${e.stackTraceToString()}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Roboter: ${robot.name}", fontSize = 24.sp)
        Text("ID: ${robot.id}", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Text("Lade Zielorte...", modifier = Modifier.padding(top = 16.dp))
        } else {
            if (errorMessage != null) {
                Text("Fehler: $errorMessage", color = Color.Red, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Debug-Info:", fontSize = 14.sp, color = Color.Gray)
                Text(debugInfo, fontSize = 12.sp, color = Color.Gray)
            } else if (filteredDestinations.isEmpty()) {
                Text("Keine Zielorte gefunden", fontSize = 16.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Debug-Info:", fontSize = 14.sp, color = Color.Gray)
                Text(debugInfo, fontSize = 12.sp, color = Color.Gray)
            } else {
                Text("Lieferziele (${filteredDestinations.size})", fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredDestinations) { destination ->
                        Button(
                            onClick = { /* Hier könnte eine Aktion ausgeführt werden */ },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(destination.name, fontSize = 16.sp)
                                Text(
                                    getTypeLabel(destination.type),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }) {
            Text("Zurück")
        }
    }
}


// Übersetzt die Typen in verständliche deutsche Bezeichnungen
fun getTypeLabel(type: String): String {
    return when (type) {
        "table" -> "Tisch"
        "dining_outlet" -> "Ausgabestation"
        "transit" -> "Übergangspunkt"
        "dishwashing" -> "Spülstation"
        else -> type
    }
}