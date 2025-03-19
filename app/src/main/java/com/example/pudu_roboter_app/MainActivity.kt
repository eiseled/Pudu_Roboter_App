package com.example.pudu_roboter_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            var serverAddress by remember { mutableStateOf("192.168.178.75:9050") }
            var deviceId by remember { mutableStateOf("") }
            var selectedRobot by remember { mutableStateOf<Robot?>(null) }

            NavHost(navController = navController, startDestination = "serverInput") {
                composable("serverInput") {
                    ServerInputScreen(
                        serverAddress = serverAddress,
                        onAddressChange = { serverAddress = it },
                        onConnect = {
                            navController.navigate("robotList/${serverAddress}")
                        }
                    )
                }
                composable("robotList/{serverAddress}") { backStackEntry ->
                    val server = backStackEntry.arguments?.getString("serverAddress") ?: ""
                    RobotListScreen(
                        navController = navController,
                        serverAddress = server,
                        onDeviceIdReceived = { deviceId = it },
                        onRobotSelected = { robot ->
                            selectedRobot = robot
                            navController.navigate("robotDetails/${server}/${robot.id}")
                        }
                    )
                }
                composable(
                    "robotDetails/{serverAddress}/{robotId}",
                    arguments = listOf(
                        navArgument("serverAddress") { type = NavType.StringType },
                        navArgument("robotId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val server = backStackEntry.arguments?.getString("serverAddress") ?: ""
                    val robotId = backStackEntry.arguments?.getString("robotId") ?: ""

                    // Find the selected robot from its ID
                    val robot = selectedRobot ?: Robot(robotId, "Unbekannter Roboter")

                    RobotDeliveryScreen(
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
    fun ServerInputScreen(
        serverAddress: String,
        onAddressChange: (String) -> Unit,
        onConnect: () -> Unit
    ) {
        val buttonColors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE2001A), // Your specified red color
            contentColor = Color.White // Text color that shows well on red
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(350.dp)  // Passe die Größe nach Bedarf an
            )
            Text("Serveradresse eingeben", fontSize = 24.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = serverAddress,
                onValueChange = onAddressChange,
                label = { Text("Serveradresse (IP:Port)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onConnect,
                colors = buttonColors) {
                Text("Verbinden")
            }
        }
    }

    @Composable
    fun RobotListScreen(
        navController: androidx.navigation.NavHostController,
        serverAddress: String,
        onDeviceIdReceived: (String) -> Unit,
        onRobotSelected: (Robot) -> Unit
    ) {
        val connectRobot = remember { ConnectRobot(serverAddress) }
        var robots by remember { mutableStateOf<List<Robot>>(emptyList()) }
        var robotStates by remember { mutableStateOf<Map<String, Pair<String, Int>>>(emptyMap()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        val coroutineScope = rememberCoroutineScope()
        val buttonColors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE2001A), // Your specified red color
            contentColor = Color.White // Text color that shows well on red
        )
        // Funktion zum Laden der Roboterdaten
        fun loadRobotData() {
            isLoading = true
            coroutineScope.launch {
                val result = connectRobot.updateRobotStatuses()
                when (result) {
                    is Result.Success -> {
                        robots = result.data.robots
                        robotStates = result.data.robotStates
                        errorMessage = null
                    }
                    is Result.Error -> {
                        errorMessage = result.message
                    }
                }
                isLoading = false
            }
        }

        // Automatische Aktualisierung alle 15 Sekunden
        LaunchedEffect(Unit) {
            while (true) {
                val result = connectRobot.updateRobotStatuses()
                when (result) {
                    is Result.Success -> {
                        // Extrahiere die DeviceID aus der ersten Anfrage
                        if (result.data.robots.isNotEmpty()) {
                            // Wir müssen die deviceId aus einer anderen Quelle bekommen
                            val deviceIdResult = connectRobot.fetchDeviceId()
                            if (deviceIdResult is Result.Success) {
                                onDeviceIdReceived(deviceIdResult.data)
                            }
                        }

                        robots = result.data.robots
                        robotStates = result.data.robotStates
                        errorMessage = null
                    }
                    is Result.Error -> {
                        errorMessage = result.message
                    }
                }
                isLoading = false
                kotlinx.coroutines.delay(15000) // Warte 15 Sekunden vor der nächsten Aktualisierung
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Server: $serverAddress", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Verfügbare Roboter", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFE2001A))
                Text("Lade Daten...", modifier = Modifier.padding(top = 16.dp))
            } else {
                if (errorMessage != null) {
                    Text("Fehler: $errorMessage", color = Color.Red, fontSize = 16.sp)
                } else if (robots.isEmpty()) {
                    Text("Keine Roboter gefunden", fontSize = 16.sp, color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(robots) { robot ->
                            val robotData = robotStates[robot.id] ?: Pair("Unknown", 0)
                            val status = robotData.first
                            val batteryLevel = robotData.second
                            val isFree = status == "Free"
                            val isOnline = status != "Offline"

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    // Roboter Name und ID
                                    Text(
                                        text = "${robot.name} (ID: ${robot.id})",
                                        fontSize = 16.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Status mit Farbkodierung
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(
                                                    when (status) {
                                                        "Free" -> Color.Green
                                                        "Busy" -> Color.Red
                                                        "Charging" -> Color.Blue
                                                        "Offline" -> Color.Gray
                                                        else -> Color.Gray
                                                    },
                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Status: $status",
                                            fontSize = 14.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Batterieanzeige nur anzeigen, wenn online
                                    if (isOnline) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Akku: ",
                                                fontSize = 14.sp
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(16.dp)
                                                    .background(Color.LightGray, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(batteryLevel / 100f)
                                                        .background(
                                                            when {
                                                                batteryLevel > 60 -> Color.Green
                                                                batteryLevel > 30 -> Color(0xFFFFA500) // Orange
                                                                else -> Color.Red
                                                            },
                                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                                        )
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "$batteryLevel%",
                                                fontSize = 14.sp
                                            )
                                        }
                                    } else {
                                        // Offline-Hinweis anzeigen
                                        Text(
                                            text = "Roboter ist ausgeschaltet oder nicht erreichbar",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Aktionsbutton
                                    Button(
                                        onClick = { onRobotSelected(robot) },
                                        enabled = isFree && isOnline,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFree && isOnline) Color(0xFFE2001A) else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isFree && isOnline) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Text(
                                            text = when {
                                                !isOnline -> "Nicht erreichbar"
                                                !isFree -> "Nicht verfügbar, der Roboter ist beschäftigt"
                                                else -> "Auswählen"
                                            },
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Refresh-Button für manuelle Aktualisierung
            Button(onClick = { loadRobotData() }, colors = buttonColors) {
                Text("Refresh")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zurück-Button
            Button(onClick = { navController.popBackStack() }, colors = buttonColors) {
                Text("Zurück")
            }
        }
    }
    @Composable
    fun RobotDeliveryScreen(
        navController: androidx.navigation.NavHostController,
        serverAddress: String,
        deviceId: String,
        robot: Robot
    ) {
        val connectRobot = remember { ConnectRobot(serverAddress) }
        var allDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
        var filteredDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var debugInfo by remember { mutableStateOf("") }
        var taskStatus by remember { mutableStateOf<String?>(null) }
        var isTaskSending by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val buttonColors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE2001A), // Your specified red color
            contentColor = Color.White // Text color that shows well on red
        )

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
                    val result = connectRobot.sendDeliveryTask(deviceId, robot.id, destination.name)
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
                    val result = connectRobot.fetchDestinations(deviceId, robot.id)
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
                CircularProgressIndicator(color = Color(0xFFE2001A))
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
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = buttonColors
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(destination.name, fontSize = 16.sp)
                                    Text(
                                        connectRobot.getTypeLabel(destination.type),
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
                enabled = !isTaskSending,
                colors = buttonColors
            ) {
                Text("Zurück")
            }
        }
    }
}