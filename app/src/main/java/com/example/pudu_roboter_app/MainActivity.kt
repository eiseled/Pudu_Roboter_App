package com.example.pudu_roboter_app

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.Inet4Address


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WifiCheckScreen(getWifiIpAddress())
        }
    }

    @Composable
    fun WifiCheckScreen(currentIp: String) {
        var targetIp = "10.0.2.16"
        val isConnected = currentIp == targetIp
        val statusText = if (isConnected) {
            "Mit der gewünschten IP $targetIp verbunden"
        } else {
            "Nicht mit der gewünschten IP verbunden. Aktuelle IP: $currentIp"
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = "test",
                tint = if (isConnected) Color.Green else Color.Red,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = statusText, fontSize = 18.sp)
            OutlinedTextField(
                value = targetIp,
                onValueChange = { targetIp = it },
                label = { Text("Ziel IP-Adresse") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    private fun getWifiIpAddress(): String {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return "Keine Verbindung"
        val linkProperties: LinkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return "IP nicht verfügbar"

        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            if (address is Inet4Address) {
                return address.hostAddress ?: "IP nicht verfügbar"
            }
        }
        return "IP nicht verfügbar"
    }
}
