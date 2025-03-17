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
        var robots = remember { mutableStateListOf<Robot>() }
        var batteryLevels = remember { mutableStateMapOf<String, Int>() }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
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
                                        robots.clear()
                                        robots.addAll(robotsResult.data)

                                        // Batterielevel für jeden Roboter abrufen
                                        robots.forEach { robot ->
                                            coroutineScope.launch {
                                                val batteryResult = connectRobot.fetchRobotStatus(deviceId, robot.id)
                                                if (batteryResult is Result.Success) {
                                                    batteryLevels[robot.id] = batteryResult.data
                                                }
                                            }
                                        }
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

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                    LazyColumn {
                        items(robots) { robot ->
                            Button(
                                onClick = { onRobotSelected(robot) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(robot.name, fontSize = 16.sp)
                                        Text("ID: ${robot.id}", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
                                    }

                                    // Batterieanzeige
                                    val batteryLevel = batteryLevels[robot.id]
                                    if (batteryLevel != null) {
                                        Text(
                                            "$batteryLevel%",
                                            fontSize = 16.sp,
                                            color = if (batteryLevel < 20) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green
                                        )
                                    } else {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { navController.popBackStack() }) {
                Text("Zurück")
            }
        }
    }

}
