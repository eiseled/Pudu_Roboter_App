package com.example.pudu_roboter_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset



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
                    "multiDelivery/{serverAddress}/{robotId}",
                    arguments = listOf(
                        navArgument("serverAddress") { type = NavType.StringType },
                        navArgument("robotId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val server = backStackEntry.arguments?.getString("serverAddress") ?: ""
                    val robotId = backStackEntry.arguments?.getString("robotId") ?: ""
                    val robot = selectedRobot ?: Robot(robotId, "Unbekannter Roboter")

                    MultiDeliveryScreen(
                        navController = navController,
                        serverAddress = server,
                        deviceId = deviceId,
                        robot = robot
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
                composable(
                    "multiDelivery/{serverAddress}/{robotId}",
                    arguments = listOf(
                        navArgument("serverAddress") { type = NavType.StringType },
                        navArgument("robotId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val server = backStackEntry.arguments?.getString("serverAddress") ?: ""
                    val robotId = backStackEntry.arguments?.getString("robotId") ?: ""
                    val robot = selectedRobot ?: Robot(robotId, "Unbekannter Roboter")

                    MultiDeliveryScreen(
                        navController = navController,
                        serverAddress = server,
                        deviceId = deviceId,
                        robot = robot
                    )
                }
                composable(
                    "robotMap/{serverAddress}/{robotId}",
                    arguments = listOf(
                        navArgument("serverAddress") { type = NavType.StringType },
                        navArgument("robotId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val server = backStackEntry.arguments?.getString("serverAddress") ?: ""
                    val robotId = backStackEntry.arguments?.getString("robotId") ?: ""

                    // Prüfe, ob deviceId gesetzt ist
                    if (deviceId.isNotEmpty()) {
                        RobotMapScreen(
                            navController = navController,
                            serverAddress = server,
                            deviceId = deviceId,
                            robot = selectedRobot ?: Robot(robotId, "Unbekannter Roboter")
                        )
                    } else {
                        // Falls deviceId leer ist, zurück zur Roboterliste navigieren
                        navController.popBackStack()
                    }
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
    fun ServerInputScreen(
        serverAddress: String,
        onAddressChange: (String) -> Unit,
        onConnect: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Serveradresse eingeben", fontSize = 24.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = serverAddress,
                onValueChange = onAddressChange,
                label = { Text("Serveradresse (IP:Port)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onConnect) {
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
                CircularProgressIndicator()
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
                                            containerColor = if (isFree && isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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
            Button(onClick = { loadRobotData() }) {
                Text("Refresh")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zurück-Button
            Button(onClick = { navController.popBackStack() }) {
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
        var taskStatus by remember { mutableStateOf<String?>(null) }
        var isTaskSending by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                val result = connectRobot.fetchDestinations(deviceId, robot.id)
                if (result is Result.Success) {
                    allDestinations = result.data
                    filteredDestinations = result.data.filter { it.type == "table" } // ✅ Only show tables
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
            Text("Roboter: ${robot.name}", fontSize = 24.sp)
            Text("ID: ${robot.id}", fontSize = 16.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))

            if (taskStatus != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (taskStatus!!.startsWith("Fehler")) Color.Red else Color.Green)
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
            }
        }
    }


    @Composable
    fun MultiDeliveryScreen(
        navController: androidx.navigation.NavHostController,
        serverAddress: String,
        deviceId: String,
        robot: Robot
    ) {
        val connectRobot = remember { ConnectRobot(serverAddress) }
        var allDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
        var filteredDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
        var selectedDestinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
        var isTaskSending by remember { mutableStateOf(false) }
        var taskStatus by remember { mutableStateOf<String?>(null) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                val result = connectRobot.fetchDestinations(deviceId, robot.id)
                if (result is Result.Success) {
                    allDestinations = result.data
                    filteredDestinations = result.data.filter { it.type == "table" } // ✅ Only show tables
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Mehrfachlieferung", fontSize = 24.sp)
            Text("Roboter: ${robot.name}", fontSize = 16.sp, color = Color.Gray)
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

            LazyColumn {
                items(filteredDestinations) { destination ->
                    val isSelected = selectedDestinations.contains(destination)
                    Button(
                        onClick = { selectedDestinations = selectedDestinations + destination },
                        enabled = !isSelected,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(destination.name, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Ausgewählte Ziele:", fontSize = 18.sp, color = Color.Gray)

            LazyColumn {
                items(selectedDestinations) { destination ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.LightGray)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(destination.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { selectedDestinations = selectedDestinations - destination },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("X", color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isTaskSending = true
                    coroutineScope.launch {
                        val result = connectRobot.sendMultiDeliveryTask(deviceId, robot.id, selectedDestinations)
                        taskStatus = when (result) {
                            is Result.Success -> {
                                selectedDestinations = emptyList()
                                "Lieferauftrag erfolgreich gesendet!"
                            }
                            is Result.Error -> result.message
                        }
                        isTaskSending = false
                    }
                },
                enabled = selectedDestinations.isNotEmpty() && !isTaskSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ausführen")
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
    @Composable
    fun RobotMapScreen(
        navController: androidx.navigation.NavHostController,
        serverAddress: String,
        deviceId: String,
        robot: Robot
    ) {
        val connectRobot = remember { ConnectRobot(serverAddress) }
        var mapElements by remember { mutableStateOf<List<ConnectRobot.MapElement>>(emptyList()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                val result = connectRobot.fetchRobotMap(deviceId, robot.id)
                when (result) {
                    is Result.Success -> mapElements = result.data
                    is Result.Error -> errorMessage = result.message
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
            Text("Karte für Roboter: ${robot.name}", fontSize = 24.sp)
            Text("ID: ${robot.id}", fontSize = 16.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
                Text("Lade Karte...", modifier = Modifier.padding(top = 16.dp))
            } else if (!errorMessage.isNullOrEmpty()) {
                Text("Fehler: $errorMessage", color = Color.Red, fontSize = 16.sp)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(Color.LightGray)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val scale = 50f
                        val offsetX = size.width / 2
                        val offsetY = size.height / 2

                        mapElements.forEach { element ->
                            when (element.type) {
                                "track" -> {
                                    if (element.vector.size == 4) {
                                        val (x1, y1, x2, y2) = element.vector
                                        drawLine(
                                            color = Color.Blue,
                                            start = Offset(offsetX + (x1 * scale).toFloat(), offsetY - (y1 * scale).toFloat()),
                                            end = Offset(offsetX + (x2 * scale).toFloat(), offsetY - (y2 * scale).toFloat()),
                                            strokeWidth = 3f
                                        )
                                    }
                                }

                                "source" -> {
                                    if (element.vector.size >= 2) {
                                        val (x, y) = element.vector
                                        val pointColor = when (element.mode) {
                                            "table" -> Color.Green
                                            "dining_outlet" -> Color.Yellow
                                            "transit" -> Color.Cyan
                                            "dishwashing" -> Color.Red
                                            else -> Color.Black
                                        }

                                        // Draw the point
                                        drawCircle(
                                            color = pointColor,
                                            center = Offset(offsetX + (x * scale).toFloat(), offsetY - (y * scale).toFloat()),
                                            radius = 8f
                                        )
                                    }
                                }

                                "cycle" -> {
                                    if (element.vector.size >= 4) {
                                        for (i in element.vector.indices step 2) {
                                            if (i + 2 < element.vector.size) {
                                                val (x1, y1, x2, y2) = listOf(
                                                    element.vector[i], element.vector[i + 1],
                                                    element.vector[i + 2], element.vector[i + 3]
                                                )
                                                drawLine(
                                                    color = Color.Magenta,
                                                    start = Offset(offsetX + (x1 * scale).toFloat(), offsetY - (y1 * scale).toFloat()),
                                                    end = Offset(offsetX + (x2 * scale).toFloat(), offsetY - (y2 * scale).toFloat()),
                                                    strokeWidth = 2f
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ BACK BUTTON TO PREVIOUS SCREEN
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zurück")
            }
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

}