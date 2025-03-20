package com.example.pudu_roboter_app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
            CircularProgressIndicator(color = Color(0xFFE2001A))
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

        Button(
            onClick = { navController.popBackStack() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A))
        ) {
            Text("Zurück")
        }
    }
}