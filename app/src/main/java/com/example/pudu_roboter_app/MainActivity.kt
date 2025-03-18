package com.example.pudu_roboter_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

                    RobotDetailsScreen(
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

        // Funktion für das manuelle und automatische Update
        fun updateRobotStatuses() {
            coroutineScope.launch {
                val deviceIdResult = connectRobot.fetchDeviceId()
                when (deviceIdResult) {
                    is Result.Success -> {
                        val deviceId = deviceIdResult.data
                        onDeviceIdReceived(deviceId)

                        val groupIdResult = connectRobot.getGroupId(deviceId)
                        when (groupIdResult) {
                            is Result.Success -> {
                                val groupId = groupIdResult.data
                                val robotsResult = connectRobot.fetchRobots(deviceId, groupId)
                                when (robotsResult) {
                                    is Result.Success -> {
                                        robots = robotsResult.data

                                        // Lade den Status & Batterie-Level für jeden Roboter
                                        val states = mutableMapOf<String, Pair<String, Int>>()
                                        for (robot in robots) {
                                            val statusResult = connectRobot.getRobotStatus(deviceId, robot.id)
                                            if (statusResult is Result.Success) {
                                                states[robot.id] = statusResult.data
                                            }
                                        }
                                        robotStates = states
                                    }
                                    is Result.Error -> errorMessage = robotsResult.message
                                }
                            }
                            is Result.Error -> errorMessage = groupIdResult.message
                        }
                    }
                    is Result.Error -> errorMessage = deviceIdResult.message
                }
                isLoading = false
            }
        }

        // Automatische Aktualisierung alle 15 Sekunden
        LaunchedEffect(Unit) {
            while (true) {
                updateRobotStatuses()
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
                    Text("Fehler: $errorMessage", color = androidx.compose.ui.graphics.Color.Red, fontSize = 16.sp)
                } else if (robots.isEmpty()) {
                    Text("Keine Roboter gefunden", fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(robots) { robot ->
                            val robotData = robotStates[robot.id] ?: Pair("Unknown", 0)
                            val isFree = robotData.first == "Free"
                            val batteryLevel = robotData.second

                            Button(
                                onClick = { onRobotSelected(robot) },
                                enabled = isFree,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isFree) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    "${robot.name} (ID: ${robot.id}) - Status: ${robotData.first} - Akku: ${batteryLevel}%",
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Refresh-Button für manuelle Aktualisierung
            Button(onClick = { updateRobotStatuses() }) {
                Text("Refresh")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zurück-Button
            Button(onClick = { navController.popBackStack() }) {
                Text("Zurück")
            }
        }
    }
}