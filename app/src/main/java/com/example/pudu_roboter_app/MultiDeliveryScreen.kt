package com.example.pudu_roboter_app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header area
        Text("Mehrfachlieferung", fontSize = 24.sp)
        Text("Roboter: ${robot.name}", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        // Status card
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

        // Content area - using weight to make it scrollable and fill available space
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Available destinations
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(filteredDestinations) { destination ->
                    val isSelected = selectedDestinations.contains(destination)
                    Button(
                        onClick = { selectedDestinations = selectedDestinations + destination },
                        enabled = !isSelected,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))
                    ) {
                        Text(destination.name, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Ausgewählte Ziele:", fontSize = 18.sp, color = Color.Gray,fontWeight = FontWeight.Bold,)

            // Selected destinations
            LazyColumn(
                Modifier
                .fillMaxWidth()
                .weight(1f) // Ensures the list takes up available space
            ) {
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

            // Execute button
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
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))
            ) {
                Text("Ausführen")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { navController.navigate("robotCall/$serverAddress/${robot.id}") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("One-Way Calls")
            }

            Spacer(modifier = Modifier.width(8.dp)) // Optional, für etwas Abstand zwischen den Buttons

            Button(
                onClick = { navController.navigate("robotMap/$serverAddress/${robot.id}") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))
            ) {
                Text("Karte anzeigen")
            }
        }

        Spacer(modifier = Modifier.height(8.dp)) // Optional, Abstand zwischen den Reihen

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        val result = connectRobot.cancelAllDeliveries(deviceId, robot.id)
                        taskStatus = if (result is Result.Success && result.data) {
                            "Alle Lieferungen wurden abgebrochen!"
                        } else {
                            "Fehler: Lieferung konnte nicht abgebrochen werden."
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Abbrechen")
            }

            Spacer(modifier = Modifier.width(8.dp)) // Optional, für etwas Abstand zwischen den Buttons

            Button(
                onClick = {
                    coroutineScope.launch {
                        val result = connectRobot.forceCompleteDelivery(deviceId, robot.id)
                        taskStatus = if (result is Result.Success && result.data) {
                            "Lieferung wurde erzwungen!"
                        } else {
                            "Fehler: Lieferung konnte nicht erzwungen werden."
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))
            ) {
                Text("Lieferung erzwingen")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { navController.popBackStack() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))
            ) {
                Text("Zurück")
            }
        }
    }
