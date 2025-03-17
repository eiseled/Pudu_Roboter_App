package com.example.pudu_roboter_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

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
