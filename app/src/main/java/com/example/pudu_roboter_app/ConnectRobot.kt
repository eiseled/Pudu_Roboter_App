package com.example.pudu_roboter_app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Robot(val id: String, val name: String, var batteryLevel: Int? = null, var isOnline: Boolean = true)
data class Destination(val name: String, val type: String)
data class DeliveryTaskResponse(val success: Boolean, val errorMessage: String?)

data class RobotStatusUpdate(
    val robots: List<Robot>,
    val robotStates: Map<String, Pair<String, Int>>
)

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class ConnectRobot(private val serverAddress: String) {

    private fun getUrl(endpoint: String) = "http://$serverAddress/api/$endpoint"

    suspend fun fetchDeviceId(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(getUrl("devices")).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val devicesArray = JSONObject(response).getJSONObject("data").getJSONArray("devices")
            if (devicesArray.length() > 0) {
                val deviceId = devicesArray.getJSONObject(0).getString("deviceId")
                Result.Success(deviceId)
            } else {
                Result.Error("Keine Geräte gefunden.")
            }
        } catch (e: Exception) {
            Result.Error("Fehler beim Abrufen der Device-ID: ${e.message}")
        }
    }

    suspend fun getGroupId(deviceId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(getUrl("robot/groups?device=$deviceId")).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.Error("Server-Fehler: HTTP-Statuscode $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)
            val dataObject = jsonObject.getJSONObject("data")

            if (!dataObject.has("robotGroups")) {
                return@withContext Result.Error("Keine Gruppen gefunden.")
            }

            val groupsArray = dataObject.getJSONArray("robotGroups")

            if (groupsArray.length() > 0) {
                val groupId = groupsArray.getJSONObject(0).getString("id")
                Result.Success(groupId)
            } else {
                Result.Error("Keine Gruppen gefunden.")
            }
        } catch (e: Exception) {
            Result.Error("Fehler beim Abrufen der Gruppen-ID: ${e.message}")
        }
    }

    suspend fun fetchRobots(deviceId: String, groupId: String): Result<List<Robot>> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(getUrl("robots?device=$deviceId&group_id=$groupId")).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.Error("Server-Fehler: HTTP-Statuscode $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)
            val dataObject = jsonObject.getJSONObject("data")

            if (!dataObject.has("robots")) {
                return@withContext Result.Error("Keine Roboter gefunden.")
            }

            val robotsArray = dataObject.getJSONArray("robots")
            val robots = mutableListOf<Robot>()

            for (i in 0 until robotsArray.length()) {
                val robotObject = robotsArray.getJSONObject(i)
                robots.add(
                    Robot(
                        id = robotObject.getString("id"),
                        name = robotObject.getString("name"),
                        isOnline = true // Standardmäßig werden alle Roboter als online markiert
                    )
                )
            }

            Result.Success(robots)
        } catch (e: Exception) {
            Result.Error("Fehler beim Abrufen der Roboter: ${e.message}")
        }
    }

    suspend fun fetchDestinations(deviceId: String, robotId: String): Result<List<Destination>> = withContext(Dispatchers.IO) {
        try {
            val urlString = getUrl("destinations?device=$deviceId&robot_id=$robotId")
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val jsonResponse = JSONObject(response)
                val data = jsonResponse.optJSONObject("data")
                val destinationsArray = data?.optJSONArray("destinations")

                if (destinationsArray != null) {
                    val destinations = mutableListOf<Destination>()
                    for (i in 0 until destinationsArray.length()) {
                        val destinationJson = destinationsArray.getJSONObject(i)
                        destinations.add(Destination(
                            name = destinationJson.optString("name", ""),
                            type = destinationJson.optString("type", "")
                        ))
                    }
                    return@withContext Result.Success(destinations)
                } else {
                    return@withContext Result.Error("Keine Zielorte gefunden")
                }
            } else {
                return@withContext Result.Error("HTTP-Fehler: $responseCode")
            }
        } catch (e: Exception) {
            return@withContext Result.Error("Fehler beim Abrufen der Zielorte: ${e.message}")
        }
    }

    suspend fun getRobotStatus(deviceId: String, robotId: String): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(getUrl("robot/status?device_id=$deviceId&robot_id=$robotId")).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)
            val data = jsonObject.getJSONObject("data")

            val robotState = data.getString("robotState") // "Free" oder "Busy"
            val batteryLevel = data.getInt("robotPower") // Akku in %

            Result.Success(Pair(robotState, batteryLevel))
        } catch (e: Exception) {
            Result.Error("Fehler beim Abrufen des Robot-Status: ${e.message}")
        }
    }

    // Verbesserte Funktion mit Timeout-Handling für unresponsive Roboter
    suspend fun getRobotStatusWithTimeout(deviceId: String, robotId: String, timeoutMs: Long = 2000): Result<Pair<String, Int>>? {
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                try {
                    val connection = URL(getUrl("robot/status?device_id=$deviceId&robot_id=$robotId")).openConnection() as HttpURLConnection
                    connection.connectTimeout = 1000 // Kürzere Verbindungszeit
                    connection.readTimeout = 1000 // Kürzere Lesezeit

                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        connection.disconnect()
                        return@withContext null
                    }

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()

                    val jsonObject = JSONObject(response)
                    val data = jsonObject.getJSONObject("data")

                    val robotState = data.getString("robotState")
                    val batteryLevel = data.getInt("robotPower")

                    Result.Success(Pair(robotState, batteryLevel))
                } catch (e: Exception) {
                    null // Bei Ausnahme null zurückgeben anstatt einen Fehler
                }
            }
        }
    }

    suspend fun updateRobotStatuses(deviceId: String? = null): Result<RobotStatusUpdate> = withContext(Dispatchers.IO) {
        try {
            // Wenn deviceId nicht übergeben wurde, rufe sie ab
            val finalDeviceId = deviceId ?: when (val deviceIdResult = fetchDeviceId()) {
                is Result.Success -> deviceIdResult.data
                is Result.Error -> return@withContext Result.Error(deviceIdResult.message)
            }

            // Hole die Gruppen-ID
            val groupId = when (val groupIdResult = getGroupId(finalDeviceId)) {
                is Result.Success -> groupIdResult.data
                is Result.Error -> return@withContext Result.Error(groupIdResult.message)
            }

            // Hole die Roboter
            val robots = when (val robotsResult = fetchRobots(finalDeviceId, groupId)) {
                is Result.Success -> robotsResult.data.toMutableList()
                is Result.Error -> return@withContext Result.Error(robotsResult.message)
            }

            // Lade den Status & Batterie-Level für jeden Roboter mit Timeout
            val states = mutableMapOf<String, Pair<String, Int>>()

            for (i in robots.indices) {
                val robot = robots[i]
                val statusResult = getRobotStatusWithTimeout(finalDeviceId, robot.id)

                if (statusResult != null && statusResult is Result.Success) {
                    states[robot.id] = statusResult.data
                    robots[i] = robot.copy(isOnline = true)
                } else {
                    // Wenn kein Status erhalten wurde oder Timeout, markiere als offline
                    states[robot.id] = Pair("Offline", 0)
                    robots[i] = robot.copy(isOnline = false)
                }
            }

            // Erstelle und gib das Update-Objekt zurück
            Result.Success(RobotStatusUpdate(robots, states))
        } catch (e: Exception) {
            Result.Error("Fehler beim Aktualisieren der Roboterstatus: ${e.message}")
        }
    }

    // Moved from RobotDetailsScreen.kt
    suspend fun sendDeliveryTask(
        deviceId: String,
        robotId: String,
        destination: String
    ): Result<DeliveryTaskResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL(getUrl("robot/delivery/task"))
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

    // Helper functions
    fun getTypeLabel(type: String): String {
        return when (type) {
            "table" -> "Tisch"
            "dining_outlet" -> "Ausgabestation"
            "transit" -> "Übergangspunkt"
            "dishwashing" -> "Spülstation"
            else -> type
        }
    }
}