package com.rotator.server

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        val serviceState = MutableStateFlow<RotatorService.ServiceState?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val state by serviceState.collectAsState()
            val isRunning = isServiceRunning(RotatorService::class.java)

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ServerControlScreen(
                        isRunning = isRunning,
                        state = state,
                        onStartService = { startRotatorService() },
                        onStopService = { stopRotatorService() }
                    )
                }
            }
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startRotatorService() {
        val serviceIntent = Intent(this, RotatorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopRotatorService() {
        val serviceIntent = Intent(this, RotatorService::class.java)
        stopService(serviceIntent)
    }
}

@Composable
fun ServerControlScreen(
    isRunning: Boolean,
    state: RotatorService.ServiceState?,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Rotator Server",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        StatusChip(isRunning = isRunning)

        Spacer(modifier = Modifier.height(32.dp))

        if (isRunning && state != null) {
            HeadingIndicator(heading = state.currentHeading.toFloat())
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (!isRunning) {
            OfflineCard(onStartService)
        } else {
            StatusCard(state)
            Spacer(modifier = Modifier.height(16.dp))
            NetworkCard(state)
            Spacer(modifier = Modifier.height(16.dp))
            LogCard(state?.lastMessage)
            Spacer(modifier = Modifier.height(24.dp))
            StopButton(onStopService)
        }

        Spacer(modifier = Modifier.height(32.dp))
        RequirementsCard()
    }
}

@Composable
fun HeadingIndicator(heading: Float) {
    val animatedHeading by animateFloatAsState(targetValue = heading, label = "heading")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        // Compass Circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.LightGray,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        // Cardinal Points
        Text("N", modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp), style = MaterialTheme.typography.labelSmall)
        Text("S", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), style = MaterialTheme.typography.labelSmall)
        Text("E", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), style = MaterialTheme.typography.labelSmall)
        Text("W", modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp), style = MaterialTheme.typography.labelSmall)

        // Needle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(animatedHeading),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(160.dp, 4.dp)) {
                drawRect(color = Color.Red, size = size.copy(width = size.width / 2))
                drawRect(color = Color.DarkGray, topLeft = center.copy(y = 0f), size = size.copy(width = size.width / 2))
            }
        }
        
        // Center value
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${heading.toInt()}°",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun OfflineCard(onStartService: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Server is Offline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Connect your Arduino via USB OTG and start the server to begin broadcasting.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartService,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("START SERVER")
            }
        }
    }
}

@Composable
fun StatusCard(state: RotatorService.ServiceState?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Real-time Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            InfoRow(
                label = "Rotation",
                value = if (state?.isRotating == true) "MOVING" else "IDLE",
                icon = if (state?.isRotating == true) Icons.Default.Warning else Icons.Default.CheckCircle,
                valueColor = if (state?.isRotating == true) Color(0xFFFFA000) else Color(0xFF388E3C)
            )

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            InfoRow(
                label = "Arduino",
                value = if (state?.isArduinoConnected == true) "CONNECTED" else "DISCONNECTED",
                icon = if (state?.isArduinoConnected == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                valueColor = if (state?.isArduinoConnected == true) Color(0xFF388E3C) else MaterialTheme.colorScheme.error
            )

            InfoRow(
                label = "Clients",
                value = "${state?.clientCount ?: 0} connected",
                icon = Icons.Default.Info
            )
        }
    }
}

@Composable
fun NetworkCard(state: RotatorService.ServiceState?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Network Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("IP Address: ${state?.localIp ?: "Detecting..."}", style = MaterialTheme.typography.bodyMedium)
            Text("TCP Port: ${RotatorService.TCP_SERVER_PORT}", style = MaterialTheme.typography.bodyMedium)
            Text("UDP Port: ${RotatorService.UDP_BROADCAST_PORT}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun LogCard(message: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message?.takeIf { it.isNotEmpty() } ?: "No messages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StopButton(onStopService: () -> Unit) {
    OutlinedButton(
        onClick = onStopService,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Text("STOP SERVER")
    }
}

@Composable
fun RequirementsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "System Requirements",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            RequirementItem("Disable Battery Optimization")
            RequirementItem("Disable Data Saver")
            RequirementItem("Stay Awake (Developer Options)")
            RequirementItem("USB OTG Y-Cable + 2A Charger")
        }
    }
}

@Composable
fun StatusChip(isRunning: Boolean) {
    Surface(
        color = if (isRunning) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = if (isRunning) "● RUNNING" else "● OFFLINE",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (isRunning) Color(0xFF2E7D32) else Color(0xFFC62828),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, icon: ImageVector, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun RequirementItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "•", modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewServerRunning() {
    MaterialTheme {
        ServerControlScreen(
            isRunning = true,
            state = RotatorService.ServiceState(
                currentHeading = 145,
                isRotating = false,
                isArduinoConnected = true,
                clientCount = 2,
                localIp = "192.168.1.100",
                lastMessage = "Connected to Arduino"
            ),
            onStartService = {},
            onStopService = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewServerOffline() {
    MaterialTheme {
        ServerControlScreen(
            isRunning = false,
            state = null,
            onStartService = {},
            onStopService = {}
        )
    }
}
