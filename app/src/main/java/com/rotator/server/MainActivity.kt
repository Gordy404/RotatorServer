package com.rotator.server

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			MaterialTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background
				) {
					ServerControlScreen(
						onStartService = { startRotatorService() },
						onStopService = { stopRotatorService() }
					)
				}
			}
		}
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
	onStartService: () -> Unit,
	onStopService: () -> Unit
) {
	var isRunning by remember { mutableStateOf(false) }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Text(
			text = "Rotator Server",
			style = MaterialTheme.typography.headlineLarge
		)

		Spacer(modifier = Modifier.height(16.dp))

		Text(
			text = "Keep this phone plugged in and connected to Arduino via USB OTG",
			style = MaterialTheme.typography.bodyMedium
		)

		Spacer(modifier = Modifier.height(32.dp))

		if (!isRunning) {
			Button(
				onClick = {
					onStartService()
					isRunning = true
				},
				modifier = Modifier
					.fillMaxWidth()
					.height(56.dp)
			) {
				Text("START SERVER")
			}
		} else {
			Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.primaryContainer
					)
				) {
					Column(
						modifier = Modifier.padding(16.dp),
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						Text(
							text = "✓ Server Running",
							style = MaterialTheme.typography.titleMedium,
							color = MaterialTheme.colorScheme.onPrimaryContainer
						)
						Spacer(modifier = Modifier.height(8.dp))
						Text(
							text = "UDP Broadcast: Port 8888",
							style = MaterialTheme.typography.bodySmall
						)
						Text(
							text = "TCP Server: Port 9999",
							style = MaterialTheme.typography.bodySmall
						)
					}
				}

				Spacer(modifier = Modifier.height(16.dp))

				OutlinedButton(
					onClick = {
						onStopService()
						isRunning = false
					},
					modifier = Modifier.fillMaxWidth()
				) {
					Text("STOP SERVER")
				}
			}
		}

		Spacer(modifier = Modifier.height(32.dp))

		Card(
			modifier = Modifier.fillMaxWidth(),
			colors = CardDefaults.cardColors(
				containerColor = MaterialTheme.colorScheme.surfaceVariant
			)
		) {
			Column(modifier = Modifier.padding(16.dp)) {
				Text(
					text = "Important Settings",
					style = MaterialTheme.typography.titleSmall
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = "• Disable Battery Optimization for this app\n" +
							"• Disable Data Saver\n" +
							"• Keep screen timeout long or use 'Stay Awake' developer option\n" +
							"• Use USB OTG Y-Cable with 2A charger",
					style = MaterialTheme.typography.bodySmall
				)
			}
		}
	}
}
