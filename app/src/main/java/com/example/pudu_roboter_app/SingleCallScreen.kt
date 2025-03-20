package com.example.pudu_roboter_app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
            CircularProgressIndicator(color = Color(0xFFE2001A))
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
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))

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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))
        ) {
            Text("Zurück")
        }
    }
}