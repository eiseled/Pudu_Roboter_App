package com.example.pudu_roboter_app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun RobotMapScreen(
    navController: NavHostController,
    serverAddress: String,
    deviceId: String,
    robot: Robot
) {
    val connectRobot = remember { ConnectRobot(serverAddress) }
    var mapElements by remember { mutableStateOf<List<ConnectRobot.MapElement>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) } // Dynamische Skalierung

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
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f) // Begrenzung des Zooms
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val screenSize = min(size.width, size.height)
                    val dynamicScale = (screenSize / 10) * scale // Dynamische Berechnung

                    val centerX = size.width / 2 + offsetX
                    val centerY = size.height / 2 + offsetY

                    mapElements.forEach { element ->
                        when (element.type) {
                            "track" -> {
                                if (element.vector.size == 4) {
                                    val (x1, y1, x2, y2) = element.vector
                                    drawLine(
                                        color = Color.Blue,
                                        start = Offset((centerX + (x1 * dynamicScale)).toFloat(),
                                            (centerY - (y1 * dynamicScale)).toFloat()
                                        ),
                                        end = Offset((centerX + (x2 * dynamicScale)).toFloat(),
                                            (centerY - (y2 * dynamicScale)).toFloat()
                                        ),
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

                                    val position = Offset((centerX + (x * dynamicScale)).toFloat(),
                                        (centerY - (y * dynamicScale)).toFloat()
                                    )

                                    drawCircle(
                                        color = pointColor,
                                        center = position,
                                        radius = 10f
                                    )

                                    drawContext.canvas.nativeCanvas.drawText(
                                        element.name!!,
                                        position.x + 12f, position.y,
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.BLACK
                                            textSize = 28f
                                        }
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
                                                start = Offset((centerX + (x1 * dynamicScale)).toFloat(),
                                                    (centerY - (y1 * dynamicScale)).toFloat()
                                                ),
                                                end = Offset((centerX + (x2 * dynamicScale)).toFloat(),
                                                    (centerY - (y2 * dynamicScale)).toFloat()
                                                ),
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
