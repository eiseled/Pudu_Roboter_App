package com.example.pudu_roboter_app

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "secondScreen") {
                //composable("wifiCheck") { WifiCheckScreen(navController, getWifiIpAddress()) }
                composable("secondScreen") { SecondScreen(navController) }
                composable(
                    "robotMapScreen/{robotId}/{robotName}",
                    arguments = listOf(
                        navArgument("robotId") { type = NavType.StringType },
                        navArgument("robotName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val robotId = backStackEntry.arguments?.getString("robotId") ?: ""
                    val robotName = backStackEntry.arguments?.getString("robotName") ?: ""
                    RobotMapScreen(navController, robotId, robotName)
                }
                composable(
                    "robotCall/{serverAddress}/{robotId}",
                    arguments = listOf(
                        navArgument("serverAddress") { type = NavType.StringType },
                        navArgument("robotId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val server = backStackEntry.arguments?.getString("serverAddress") ?: ""
                    val robotId = backStackEntry.arguments?.getString("robotId") ?: ""
                    val robot = selectedRobot ?: Robot(robotId, "Unbekannter Roboter")

                    RobotCallScreen(
                        navController = navController,
                        serverAddress = server,
                        deviceId = deviceId,
                        robot = robot
                    )
                }
            }
        }
    }

    @Composable
    fun RobotMapScreen(navController: NavHostController, robotId: String, robotName: String) {
        var deliveryLocations by remember { mutableStateOf<List<DeliveryLocation>>(emptyList()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }

        // Daten abrufen
        LaunchedEffect(robotId) {
            // DeviceId abrufen
            val deviceIdResult = withContext(Dispatchers.IO) {
                fetchDeviceId()
            }

            when (deviceIdResult) {
                is Result.Success -> {
                    val deviceId = deviceIdResult.data

                    // Roboterkarte abrufen
                    val mapResult = withContext(Dispatchers.IO) {
                        fetchRobotMap(deviceId, robotId)
                    }

                    when (mapResult) {
                        is Result.Success -> {
                            // Delivery-Orte aus der Karte extrahieren
                            deliveryLocations = extractDeliveryLocations(mapResult.data)
                        }
                        is Result.Error -> errorMessage = mapResult.message
                    }
                }
                is Result.Error -> errorMessage = deviceIdResult.message
            }

            isLoading = false
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(
                text = "Roboter: $robotName",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Verfügbare Delivery-Orte",
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
                Text("Lade Karteninformationen...", modifier = Modifier.padding(top = 16.dp))
            } else if (errorMessage != null) {
                Text(
                    text = "Fehler: $errorMessage",
                    color = Color.Red,
                    fontSize = 16.sp
                )
            } else if (deliveryLocations.isEmpty()) {
                Text(
                    text = "Keine Delivery-Orte gefunden",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            } else {
                LazyColumn {
                    items(deliveryLocations) { location ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = location.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ID: ${location.id}",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Position: (${location.x}, ${location.y})",
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
//                                Button(
//                                    onClick = {
//                                        // Hier könnte man eine Auslieferung starten
//                                        CoroutineScope(Dispatchers.IO).launch {
//                                            startDelivery(robotId, location.id)
//                                        }
//                                    },
//                                    modifier = Modifier.align(Alignment.End)
//                                ) {
//                                    Text("Auslieferung starten")
//                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Zurück")
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
        var deviceId by remember { mutableStateOf<String?>(null) }
        var groupId by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            val deviceIdResult = withContext(Dispatchers.IO) {
                fetchDeviceId()
            }

            when (deviceIdResult) {
                is Result.Success -> {
                    deviceId = deviceIdResult.data
                    deviceId?.let { devId ->
                        val groupIdResult = withContext(Dispatchers.IO) {
                            getGroupId(devId)
                        }

                        when (groupIdResult) {
                            is Result.Success -> {
                                groupId = groupIdResult.data
                                val robotsResult = withContext(Dispatchers.IO) {
                                    fetchRobots(devId, groupIdResult.data)
                                }

                                when (robotsResult) {
                                    is Result.Success -> robots = robotsResult.data
                                    is Result.Error -> errorMessage = robotsResult.message
                                }
                            }
                            is Result.Error -> errorMessage = groupIdResult.message
                        }
                    }
                }
                is Result.Error -> errorMessage = deviceIdResult.message
            }

            isLoading = false
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator()
                Text("Lade Daten...", modifier = Modifier.padding(top = 16.dp))
            } else {
                Text("Device ID: ${deviceId ?: "Nicht verfügbar"}", fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Group ID: ${groupId ?: "Nicht verfügbar"}", fontSize = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Roboter in der Gruppe", fontSize = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage != null) {
                    Text("Fehler: $errorMessage", color = Color.Red, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (robots.isNotEmpty()) {
                    LazyColumn {
                        items(robots) { robot ->
                            Button(
                                onClick = { navController.navigate("robotMapScreen/${robot.id}/${robot.name}") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${robot.name} (ID: ${robot.id})",
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                } else if (errorMessage == null) {
                    Text("Keine Roboter gefunden", color = Color.Gray, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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

    private fun fetchRobots(deviceId: String, groupId: String): Result<List<Robot>> {
        return try {
            val url = "http://192.168.178.75:9050/api/robots?device=$deviceId&group_id=$groupId"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return Result.Error("Server-Fehler: HTTP-Statuscode $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)

            // Überprüfen, ob "data" existiert
            if (!jsonObject.has("data")) {
                return Result.Error("Unerwartetes JSON-Format: 'data' fehlt. Erhaltene Schlüssel: ${jsonObject.keys().asSequence().toList()}")
            }

            val dataObject = jsonObject.getJSONObject("data")

            // Überprüfen, ob "robots" existiert
            if (!dataObject.has("robots")) {
                return Result.Error("Unerwartetes JSON-Format: 'robots' fehlt. Erhaltene Schlüssel: ${dataObject.keys().asSequence().toList()}")
            }

            val robotsArray = dataObject.getJSONArray("robots")

            val robots = mutableListOf<Robot>()
            for (i in 0 until robotsArray.length()) {
                val robotObject = robotsArray.getJSONObject(i)

                // Überprüfen, ob die erforderlichen Felder vorhanden sind
                if (!robotObject.has("id") || !robotObject.has("name")) {
                    return Result.Error("Unerwartetes JSON-Format: 'id' oder 'name' fehlt im Roboter")
                }

                robots.add(Robot(
                    id = robotObject.getString("id"),
                    name = robotObject.getString("name")
                ))
            }

            Result.Success(robots)
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unbekannter Fehler"
            val exceptionType = e.javaClass.simpleName
            Result.Error("Fehler beim Abrufen der Roboter ($exceptionType): $errorMsg")
        }
    }

    private fun fetchDeviceId(): Result<String> {
        return try {
            val url = "http://192.168.178.75:9050/api/devices"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return Result.Error("Server-Fehler: HTTP-Statuscode $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()


            val jsonObject = JSONObject(response)

            // Überprüfen, ob "data" existiert
            if (!jsonObject.has("data")) {
                return Result.Error("Unerwartetes JSON-Format: 'data' fehlt. Erhaltene Schlüssel: ${jsonObject.keys().asSequence().toList()}")
            }

            val dataObject = jsonObject.getJSONObject("data")

            // Überprüfen, ob "devices" existiert
            if (!dataObject.has("devices")) {
                return Result.Error("Unerwartetes JSON-Format: 'devices' fehlt. Erhaltene Schlüssel: ${dataObject.keys().asSequence().toList()}")
            }

            val devicesArray = dataObject.getJSONArray("devices")

            if (devicesArray.length() > 0) {
                val deviceObject = devicesArray.getJSONObject(0)
                if (!deviceObject.has("deviceId")) {
                    return Result.Error("Unerwartetes JSON-Format: 'deviceId' fehlt im ersten Gerät")
                }
                val deviceId = deviceObject.getString("deviceId")
                Result.Success(deviceId)
            } else {
<<<<<<< Updated upstream
                Result.Error("Keine Geräte gefunden")
=======
                if (errorMessage != null) {
                    Text("Fehler: $errorMessage", color = Color.Red, fontSize = 16.sp)
                } else if (filteredDestinations.isEmpty()) {
                    Text("Keine Tische gefunden", fontSize = 16.sp, color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredDestinations) { destination ->
                            Button(
                                onClick = {
                                    isTaskSending = true
                                    coroutineScope.launch {
                                        val result = connectRobot.sendDeliveryTask(deviceId, robot.id, destination.name)
                                        taskStatus = if (result is Result.Success && result.data.success) {
                                            "Lieferauftrag zu ${destination.name} erfolgreich gesendet!"
                                        } else {
                                            "Fehler: Lieferauftrag konnte nicht gesendet werden."
                                        }
                                        isTaskSending = false
                                    }
                                },
                                enabled = !isTaskSending,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(destination.name, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keep existing buttons
            Button(
                onClick = { navController.navigate("multiDelivery/$serverAddress/${robot.id}") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("Mehrfach-Lieferung")
            }

            Button(
                onClick = { navController.navigate("robotMap/$serverAddress/${robot.id}") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("Karte anzeigen")
            }
            Button(
                onClick = { navController.navigate("robotCall/$serverAddress/${robot.id}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Single Calls")
            }
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zurück")
>>>>>>> Stashed changes
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unbekannter Fehler"
            val exceptionType = e.javaClass.simpleName
            Result.Error("Fehler beim Abrufen der Device ID ($exceptionType): $errorMsg")
        }
    }


    private fun getGroupId(deviceId: String): Result<String> {
        return try {
            val url = "http://192.168.178.75:9050/api/robot/groups?device=$deviceId"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return Result.Error("Server-Fehler: HTTP-Statuscode $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)

            // Überprüfen, ob "data" existiert
            if (!jsonObject.has("data")) {
                return Result.Error("Unerwartetes JSON-Format: 'data' fehlt. Erhaltene Schlüssel: ${jsonObject.keys().asSequence().toList()}")
            }

            val dataObject = jsonObject.getJSONObject("data")

            // Überprüfen, ob "robotGroups" existiert
            if (!dataObject.has("robotGroups")) {
                return Result.Error("Unerwartetes JSON-Format: 'robotGroups' fehlt. Erhaltene Schlüssel: ${dataObject.keys().asSequence().toList()}")
            }

            val groupsArray = dataObject.getJSONArray("robotGroups")

            if (groupsArray.length() > 0) {
                val groupObject = groupsArray.getJSONObject(0)
                if (!groupObject.has("id")) {
                    return Result.Error("Unerwartetes JSON-Format: 'id' fehlt in der ersten Gruppe")
                }
                val groupId = groupObject.getString("id")
                Result.Success(groupId)
            } else {
                Result.Error("Keine Gruppen gefunden")
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unbekannter Fehler"
            val exceptionType = e.javaClass.simpleName
            Result.Error("Fehler beim Abrufen der Group ID ($exceptionType): $errorMsg")
        }
    }
    @Composable
    fun RobotCallScreen(
        navController: androidx.navigation.NavHostController,
        serverAddress: String,
        deviceId: String,
        robot: Robot
    ) {
        val connectRobot = remember { ConnectRobot(serverAddress) }
        var allDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var taskStatus by remember { mutableStateOf<String?>(null) }
        var isTaskSending by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                val result = connectRobot.fetchDestinations(deviceId, robot.id)
                if (result is Result.Success) {
                    allDestinations = result.data
                } else if (result is Result.Error) {
                    errorMessage = result.message
                }
                isLoading = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Einzelruf für ${robot.name}", fontSize = 24.sp)
            Text("ID: ${robot.id}", fontSize = 16.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))

            if (taskStatus != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (taskStatus!!.startsWith("Fehler")) Color.Red else Color.Green
                    )
                ) {
                    Text(
                        text = taskStatus!!,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator()
                Text("Lade Zielorte...", modifier = Modifier.padding(top = 16.dp))
            } else {
                if (errorMessage != null) {
                    Text("Fehler: $errorMessage", color = Color.Red, fontSize = 16.sp)
                } else if (allDestinations.isEmpty()) {
                    Text("Keine Ziele gefunden", fontSize = 16.sp, color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(allDestinations) { destination ->
                            Button(
                                onClick = {
                                    if (!isTaskSending) {
                                        isTaskSending = true
                                        coroutineScope.launch {
                                            val result = connectRobot.sendRobotCall(deviceId, robot.id, destination)
                                            taskStatus = if (result is Result.Success && result.data.success) {
                                                "Roboter wurde zu ${destination.name} geschickt!"
                                            } else {
                                                "Fehler: Roboter konnte nicht gerufen werden."
                                            }
                                            isTaskSending = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(destination.name, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zurück")
            }
        }
    }


    private fun fetchRobotMap(deviceId: String, robotId: String): Result<Map<String, Any>> {
        return try {
            val url = "http://192.168.178.75:9050/api/robot/map?device_id=$deviceId&robot_id=$robotId"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return Result.Error("Server-Fehler: HTTP-Statuscode $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = JSONObject(response)

            // Überprüfen, ob "data" existiert
            if (!jsonObject.has("data")) {
                return Result.Error("Unerwartetes JSON-Format: 'data' fehlt. Erhaltene Schlüssel: ${jsonObject.keys().asSequence().toList()}")
            }

            val dataObject = jsonObject.getJSONObject("data")

            // Überprüfen, ob "map" existiert
            if (!dataObject.has("map")) {
                return Result.Error("Unerwartetes JSON-Format: 'map' fehlt. Erhaltene Schlüssel: ${dataObject.keys().asSequence().toList()}")
            }

            val mapObject = dataObject.getJSONObject("map")

            // Überprüfen, ob "elements" existiert
            if (!mapObject.has("elements")) {
                return Result.Error("Unerwartetes JSON-Format: 'elements' fehlt. Erhaltene Schlüssel: ${mapObject.keys().asSequence().toList()}")
            }

            // Map-Daten als generische Map zurückgeben, um sie später verarbeiten zu können
            val resultMap = mutableMapOf<String, Any>()
            resultMap["map"] = mapObject

            Result.Success(resultMap)
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unbekannter Fehler"
            val exceptionType = e.javaClass.simpleName
            Result.Error("Fehler beim Abrufen der Roboterkarte ($exceptionType): $errorMsg")
        }
    }

    // Datenklasse für Delivery-Orte
    data class DeliveryLocation(
        val id: String,
        val name: String,
        val x: Double,
        val y: Double
    )

    // Hilfsfunktion, um Delivery-Orte aus den Map-Elementen zu extrahieren
    private fun extractDeliveryLocations(mapData: Map<String, Any>): List<DeliveryLocation> {
        val locations = mutableListOf<DeliveryLocation>()

        try {
            val mapObject = mapData["map"] as JSONObject
            val elementsArray = mapObject.getJSONArray("elements")

            for (i in 0 until elementsArray.length()) {
                val element = elementsArray.getJSONObject(i)

                // Wir suchen nach Elementen, die Delivery-Orte sind
                // (Annahme: Typ oder andere Eigenschaften identifizieren Delivery-Orte)
                if (element.has("type") && element.getString("type") == "LOCATION") {
                    // Prüfen, ob alle notwendigen Felder vorhanden sind
                    if (element.has("id") && element.has("name") && element.has("x") && element.has("y")) {
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
            }
        } catch (e: Exception) {
            // Bei Fehler leere Liste zurückgeben und optional Fehler loggen
            // Log.e("ExtractDeliveryLocations", "Fehler beim Extrahieren der Delivery-Orte", e)
        }

        return locations
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
