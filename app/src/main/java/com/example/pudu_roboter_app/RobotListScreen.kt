package com.example.pudu_roboter_app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
                                        containerColor = if (isFree && isOnline) Color(0xFFE2001A) else Color.Gray,
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
        Button(onClick = { loadRobotData() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))) {
            Text("Refresh")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Zurück-Button
        Button(onClick = { navController.popBackStack() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))) {
            Text("Zurück")
        }
    }
}