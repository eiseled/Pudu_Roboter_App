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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            var serverAddress by remember { mutableStateOf("192.168.178.75:9050") }

            NavHost(navController = navController, startDestination = "serverInput") {
                composable("serverInput") {
                    ServerInputScreen(
                        serverAddress = serverAddress,
                        onAddressChange = { serverAddress = it },
                        onConnect = {
                            navController.navigate("secondScreen/${serverAddress}")
                        }
                    )
                }
                composable("secondScreen/{serverAddress}") { backStackEntry ->
                    val server = backStackEntry.arguments?.getString("serverAddress") ?: ""
                    SecondScreen(navController, server)
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
}
@Composable
fun SecondScreen(navController: androidx.navigation.NavHostController, serverAddress: String) {
    val connectRobot = remember { ConnectRobot(serverAddress) }
    var robots by remember { mutableStateOf<List<Robot>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val deviceIdResult = connectRobot.fetchDeviceId()
            when (deviceIdResult) {
                is Result.Success -> {
                    val deviceId = deviceIdResult.data
                    val groupIdResult = connectRobot.getGroupId(deviceId)
                    when (groupIdResult) {
                        is Result.Success -> {
                            val groupId = groupIdResult.data
                            val robotsResult = connectRobot.fetchRobots(deviceId, groupId)
                            when (robotsResult) {
                                is Result.Success -> robots = robotsResult.data
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Server: $serverAddress", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))

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
                            onClick = { /* Navigiere zu RobotDetailsScreen */ },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("${robot.name} (ID: ${robot.id})", fontSize = 16.sp)
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

