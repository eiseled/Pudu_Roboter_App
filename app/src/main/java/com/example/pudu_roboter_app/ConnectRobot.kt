package com.example.pudu_roboter_app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Robot(val id: String, val name: String)

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
            connection.connectTimeout = 5000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val groupsArray = JSONObject(response).getJSONObject("data").getJSONArray("robotGroups")
            if (groupsArray.length() > 0) {
                val groupId = groupsArray.getJSONObject(0).getString("id")
                Result.Success(groupId)
            } else {
                Result.Error("Keine Gruppen gefunden.")
            }
        } catch (e: Exception) {
            Result.Error("Fehler beim Abrufen der Group-ID: ${e.message}")
        }
    }

    suspend fun fetchRobots(deviceId: String, groupId: String): Result<List<Robot>> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(getUrl("robots?device=$deviceId&group_id=$groupId")).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val robotsArray = JSONObject(response).getJSONObject("data").getJSONArray("robots")
            val robots = mutableListOf<Robot>()
            for (i in 0 until robotsArray.length()) {
                val robotJson = robotsArray.getJSONObject(i)
                robots.add(Robot(robotJson.getString("id"), robotJson.getString("name")))
            }
            Result.Success(robots)
        } catch (e: Exception) {
            Result.Error("Fehler beim Abrufen der Roboter: ${e.message}")
        }
    }

    suspend fun fetchRobotMap(deviceId: String, robotId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(getUrl("robot/map?device_id=$deviceId&robot_id=$robotId")).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val mapObject = JSONObject(response).getJSONObject("data").getJSONObject("map")
            Result.Success(mapObject)
        } catch (e: Exception) {
            Result.Error("Fehler beim Abrufen der Roboterkarte: ${e.message}")
        }
    }

    fun extractDeliveryLocations(map: JSONObject): List<DeliveryLocation> {
        val elements = map.getJSONArray("elements")
        val locations = mutableListOf<DeliveryLocation>()
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            if (element.optString("type") == "LOCATION") {
                locations.add(
                    DeliveryLocation(
                        id = element.getString("id"),
                        name = element.getString("name"),
                        x = element.getDouble("x"),
                        y = element.getDouble("y")
                    )
                )
            }
        }
        return locations
    }
}

data class DeliveryLocation(val id: String, val name: String, val x: Double, val y: Double)
