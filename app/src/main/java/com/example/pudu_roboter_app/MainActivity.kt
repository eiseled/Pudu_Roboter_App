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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight


class MainActivity : ComponentActivity() {
    private val brandRed = Color(0xFFE2001A)
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

                    MultiDeliveryScreen(
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
}