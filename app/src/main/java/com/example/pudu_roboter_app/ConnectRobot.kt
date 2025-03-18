package com.example.pudu_roboter_app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Robot(val id: String, val name: String, var batteryLevel: Int? = null)

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

    suspend fun fetchRobotStatus(deviceId: String, robotId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(getUrl("robot/status?device_id=$deviceId&robot_id=$robotId")).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.Error("Server-Fehler: HTTP-Statuscode $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)
            val data = jsonObject.getJSONObject("data")

            if (!data.has("robotPower")) {
                return@withContext Result.Error("Kein Batteriestatus gefunden.")
            }

            val batteryLevel = data.getInt("robotPower")
            Result.Success(batteryLevel)
        } catch (e: Exception) {
            Result.Error("Fehler beim Abrufen des Robotstatus: ${e.message}")
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
                        name = robotObject.getString("name")
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

}
