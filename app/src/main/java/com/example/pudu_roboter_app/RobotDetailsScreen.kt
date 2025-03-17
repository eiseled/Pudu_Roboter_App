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
import org.json.JSONArray
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
    var taskStatus by remember { mutableStateOf<String?>(null) }
    var isTaskSending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Funktion zum Filtern der Zielorte
    fun filterDestinations(destinations: List<Destination>): List<Destination> {
        return destinations.filter { destination ->
            destination.type != "dining_outlet"
        }
    }

    // Funktion zum Senden einer Lieferaufgabe
    fun sendTask(destination: Destination) {
        isTaskSending = true
        taskStatus = "Sende Lieferauftrag zu ${destination.name}..."

        coroutineScope.launch {
            try {
                val result = sendDeliveryTask(serverAddress, deviceId, robot.id, destination.name)
                when (result) {
                    is Result.Success -> {
                        if (result.data.success) {
                            taskStatus = "Lieferauftrag zu ${destination.name} erfolgreich gesendet!"
                        } else {
                            taskStatus = "Fehler: Lieferauftrag konnte nicht gesendet werden."
                        }
                    }
                    is Result.Error -> {
                        taskStatus = "Fehler: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                taskStatus = "Fehler: ${e.message}"
            } finally {
                isTaskSending = false
            }
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

        // Zeige Aufgabenstatus an
        if (taskStatus != null) {
            val statusColor = if (taskStatus?.startsWith("Fehler") == true)
                Color.Red else if (taskStatus?.startsWith("Lieferauftrag zu") == true)
                Color.Green else Color.Blue

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = taskStatus ?: "",
                        color = statusColor,
                        fontSize = 16.sp
                    )

                    if (isTaskSending) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }
        }

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
                Text("Tippen Sie auf ein Ziel, um den Roboter dorthin zu senden",
                    fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredDestinations) { destination ->
                        Button(
                            onClick = {
                                sendTask(destination)
                            },
                            enabled = !isTaskSending,
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
        Button(
            onClick = { navController.popBackStack() },
            enabled = !isTaskSending
        ) {
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

data class DeliveryTaskResponse(
    val success: Boolean,
    val errorMessage: String?
)

suspend fun sendDeliveryTask(
    serverAddress: String,
    deviceId: String,
    robotId: String,
    destination: String
): Result<DeliveryTaskResponse> = withContext(Dispatchers.IO) {
    try {
        val url = URL("http://$serverAddress/api/robot/delivery/task")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        // Erstelle das destinations-Objekt
        val destinationObj = JSONObject()
        destinationObj.put("destination", destination)
        destinationObj.put("id", "Task id, will be returned when the status is synchronized ")  // ID kann leer bleiben, wird vom Server generiert

        val destinationsArray = JSONArray()
        destinationsArray.put(destinationObj)

        // Erstelle das tray-Objekt
        val trayObj = JSONObject()
        trayObj.put("destinations", destinationsArray)

        val traysArray = JSONArray()
        traysArray.put(trayObj)

        // Erstelle das Hauptobjekt
        val jsonRequest = JSONObject()
        jsonRequest.put("deviceId", deviceId)
        jsonRequest.put("robotId", robotId)
        jsonRequest.put("type", "new")  // Neue Aufgabe
        jsonRequest.put("deliverySort", "auto")  // Automatische Sortierung
        jsonRequest.put("executeTask", true)  // Sofort ausführen
        jsonRequest.put("trays", traysArray)


        // Sende den Request
        val outputStream = connection.outputStream
        outputStream.write(jsonRequest.toString().toByteArray())
        outputStream.close()

        // Verarbeite die Antwort
        val responseCode = connection.responseCode

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()


            val jsonResponse = JSONObject(response)
            val code = jsonResponse.optInt("code", -1)

            if (code == 0) {
                val data = jsonResponse.optJSONObject("data")
                if (data != null) {
                    val success = data.optBoolean("success", false)
                    Result.Success(DeliveryTaskResponse(success, null))
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
        Result.Error("Fehler beim Senden der Lieferaufgabe: ${e.message}")
    }
}