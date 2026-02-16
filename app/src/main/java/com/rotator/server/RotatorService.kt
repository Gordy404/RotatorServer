package com.rotator.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import kotlin.coroutines.coroutineContext

class RotatorService : Service() {

	data class ServiceState(
		val currentHeading: Int = 0,
		val isRotating: Boolean = false,
		val isArduinoConnected: Boolean = false,
		val clientCount: Int = 0,
		val localIp: String = "0.0.0.0",
		val lastMessage: String = ""
	)

	companion object {
		const val CHANNEL_ID = "RotatorServiceChannel"
		const val NOTIFICATION_ID = 1
		const val UDP_BROADCAST_PORT = 8888
		const val TCP_SERVER_PORT = 9999
		const val HEARTBEAT_INTERVAL = 1000L // Send heartbeat every 1 second
		const val PASSWORD = "MyRotatorPassword" // Change this!
	}

	private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	// USB Serial
	private var serialPort: UsbSerialPort? = null
	private var currentHeading = 0
	private var isRotating = false
	private var isArduinoConnected = false

	// Network
	private var udpSocket: DatagramSocket? = null
	private var tcpServerSocket: ServerSocket? = null
	private val connectedClients = mutableListOf<ClientHandler>()

	override fun onCreate() {
		super.onCreate()
		createNotificationChannel()
		
		val notification = createNotification("Starting...")
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
		} else {
			startForeground(NOTIFICATION_ID, notification)
		}

		// Start all services
		serviceScope.launch { connectToArduino() }
		serviceScope.launch { startUdpBroadcast() }
		serviceScope.launch { startTcpServer() }
		serviceScope.launch { heartbeatLoop() }
		serviceScope.launch { updateStateLoop() }
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		return START_STICKY // Restart if killed
	}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onDestroy() {
		super.onDestroy()
		serviceScope.cancel()
		serialPort?.close()
		udpSocket?.close()
		tcpServerSocket?.close()
		connectedClients.forEach { it.close() }
		MainActivity.serviceState.value = null
	}

	private suspend fun updateStateLoop() {
		while (coroutineContext.isActive) {
			MainActivity.serviceState.value = ServiceState(
				currentHeading = currentHeading,
				isRotating = isRotating,
				isArduinoConnected = isArduinoConnected,
				clientCount = connectedClients.size,
				localIp = getLocalIpAddress() ?: "0.0.0.0",
				lastMessage = "" // Could track this if needed
			)
			delay(500)
		}
	}

	private fun getLocalIpAddress(): String? {
		try {
			val socket = DatagramSocket()
			socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
			val ip = socket.localAddress.hostAddress
			socket.close()
			return ip
		} catch (e: Exception) {
			return null
		}
	}

	// ===========================
	// USB SERIAL
	// ===========================

	private suspend fun connectToArduino() {
		withContext(Dispatchers.IO) {
			try {
				val manager = getSystemService(Context.USB_SERVICE) as UsbManager
				val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

				if (availableDrivers.isEmpty()) {
					updateNotification("No USB devices found")
					isArduinoConnected = false
					return@withContext
				}

				val driver = availableDrivers[0]
				val connection = manager.openDevice(driver.device)
					?: throw IOException("Failed to open device")

				serialPort = driver.ports[0].apply {
					open(connection)
					setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
				}

				isArduinoConnected = true
				updateNotification("Connected to Arduino")
				startSerialReader()

			} catch (e: Exception) {
				isArduinoConnected = false
				updateNotification("USB Error: ${e.message}")
				delay(5000)
				connectToArduino() // Retry
			}
		}
	}

	private fun startSerialReader() {
		serviceScope.launch(Dispatchers.IO) {
			val buffer = ByteArray(1024)
			var accumulated = ""

			while (isActive) {
				try {
					val len = serialPort?.read(buffer, 1000) ?: 0
					if (len > 0) {
						accumulated += String(buffer, 0, len, Charset.forName("UTF-8"))

						// Process complete lines
						while ("\n" in accumulated) {
							val line = accumulated.substringBefore("\n").trim()
							accumulated = accumulated.substringAfter("\n")

							processArduinoMessage(line)
						}
					}
				} catch (e: Exception) {
					isArduinoConnected = false
					updateNotification("Serial read error: ${e.message}")
					delay(1000)
					break // Break to reconnect
				}
			}
			connectToArduino()
		}
	}

	private fun processArduinoMessage(message: String) {
		when {
			message.startsWith("POS:") -> {
				currentHeading = message.substringAfter("POS:").toIntOrNull() ?: 0
				// Broadcast to all connected clients
				broadcastToClients("POS:$currentHeading\n")
			}

			message.startsWith("ACK:") -> {
				broadcastToClients("$message\n")
			}

			message.startsWith("ERROR:") -> {
				updateNotification("Arduino: $message")
				broadcastToClients("$message\n")
			}

			message == "READY" -> {
				updateNotification("Arduino Ready - Heading: $currentHeading°")
			}
		}
	}

	private fun sendToArduino(command: String) {
		serviceScope.launch(Dispatchers.IO) {
			try {
				serialPort?.write("$command\n".toByteArray(), 1000)
			} catch (e: Exception) {
				updateNotification("Send error: ${e.message}")
			}
		}
	}

	// ===========================
	// UDP BROADCAST (Discovery)
	// ===========================

	private suspend fun startUdpBroadcast() {
		withContext(Dispatchers.IO) {
			try {
				udpSocket = DatagramSocket().apply {
					broadcast = true
				}

				while (coroutineContext.isActive) {
					try {
						val message = "ROTATOR_SERVER:$TCP_SERVER_PORT"
						val buffer = message.toByteArray()
						val packet = DatagramPacket(
							buffer,
							buffer.size,
							InetAddress.getByName("255.255.255.255"),
							UDP_BROADCAST_PORT
						)
						udpSocket?.send(packet)
					} catch (e: Exception) {
						// Ignore broadcast errors
					}

					delay(2000) // Broadcast every 2 seconds
				}
			} catch (e: Exception) {
				updateNotification("UDP error: ${e.message}")
			}
		}
	}

	// ===========================
	// TCP SERVER
	// ===========================

	private suspend fun startTcpServer() {
		withContext(Dispatchers.IO) {
			try {
				tcpServerSocket = ServerSocket(TCP_SERVER_PORT)
				updateNotification("TCP Server listening on :$TCP_SERVER_PORT")

				while (coroutineContext.isActive) {
					try {
						val socket = tcpServerSocket?.accept()
						socket?.let {
							val handler = ClientHandler(it, this@RotatorService)
							connectedClients.add(handler)
							serviceScope.launch { handler.handle() }
						}
					} catch (e: Exception) {
						if (coroutineContext.isActive) {
							updateNotification("Accept error: ${e.message}")
						}
					}
				}
			} catch (e: Exception) {
				updateNotification("TCP error: ${e.message}")
			}
		}
	}

	// ===========================
	// CLIENT HANDLER
	// ===========================

	inner class ClientHandler(
		private val socket: Socket,
		private val service: RotatorService
	) {
		private var authenticated = false

		suspend fun handle() {
			withContext(Dispatchers.IO) {
				try {
					val input = socket.getInputStream().bufferedReader()
					val output = socket.getOutputStream()

					// Send welcome
					output.write("ROTATOR_SERVER v1.0\n".toByteArray())
					output.flush()

					while (coroutineContext.isActive && !socket.isClosed) {
						val line = input.readLine() ?: break
						processClientCommand(line.trim(), output)
					}
				} catch (e: Exception) {
					// Client disconnected
				} finally {
					close()
				}
			}
		}

		private fun processClientCommand(command: String, output: java.io.OutputStream) {
			if (!authenticated) {
				// Check for password
				if (command.startsWith("$PASSWORD|")) {
					authenticated = true
					output.write("AUTH_OK\n".toByteArray())
					output.flush()

					// Process the command after the password
					val actualCommand = command.substringAfter("|")
					if (actualCommand.isNotEmpty()) {
						processAuthenticatedCommand(actualCommand, output)
					}
				} else {
					output.write("AUTH_REQUIRED\n".toByteArray())
					output.flush()
				}
			} else {
				processAuthenticatedCommand(command, output)
			}
		}

		private fun processAuthenticatedCommand(command: String, output: java.io.OutputStream) {
			when (command.uppercase()) {
				"CW" -> {
					service.sendToArduino("CW")
					isRotating = true
				}

				"CCW" -> {
					service.sendToArduino("CCW")
					isRotating = true
				}

				"STOP" -> {
					service.sendToArduino("STOP")
					isRotating = false
				}

				"STATUS" -> {
					service.sendToArduino("STATUS")
				}

				"PING" -> {
					output.write("PONG\n".toByteArray())
					output.flush()
				}

				else -> {
					output.write("UNKNOWN_CMD\n".toByteArray())
					output.flush()
				}
			}
		}

		fun sendMessage(message: String) {
			try {
				socket.getOutputStream().write(message.toByteArray())
				socket.getOutputStream().flush()
			} catch (e: Exception) {
				close()
			}
		}

		fun close() {
			try {
				socket.close()
			} catch (e: Exception) {
				// Ignore
			}
			connectedClients.remove(this)
		}
	}

	// ===========================
	// HEARTBEAT
	// ===========================

	private suspend fun heartbeatLoop() {
		withContext(Dispatchers.IO) {
			while (coroutineContext.isActive) {
				try {
					// Send STOP command to Arduino to keep heartbeat alive
					// Arduino needs a command every 2 seconds
					if (!isRotating) {
						sendToArduino("STOP")
					}
					// If rotating, the remote app should be sending commands
				} catch (e: Exception) {
					// Ignore
				}

				delay(HEARTBEAT_INTERVAL)
			}
		}
	}

	// ===========================
	// HELPERS
	// ===========================

	private fun broadcastToClients(message: String) {
		connectedClients.forEach { it.sendMessage(message) }
	}

	private fun updateNotification(message: String) {
		val notification = createNotification(message)
		val notificationManager =
			getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.notify(NOTIFICATION_ID, notification)
	}

	private fun createNotification(content: String): Notification {
		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle("Rotator Server")
			.setContentText(content)
			.setSmallIcon(android.R.drawable.ic_dialog_info)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.build()
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				"Rotator Service",
				NotificationManager.IMPORTANCE_LOW
			).apply {
				description = "Rotator control server"
			}

			val notificationManager =
				getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			notificationManager.createNotificationChannel(channel)
		}
	}
}
