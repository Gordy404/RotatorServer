# RotatorServer
**Purpose:** Runs 24/7 on an old Android phone in your shack, connected to the Arduino via USB OTG.

### Features
- ✅ USB Serial communication with Arduino (9600 baud)
- ✅ UDP broadcast on port 8888 for local discovery
- ✅ TCP server on port 9999 for remote commands
- ✅ Password authentication (`Password|Command` protocol)
- ✅ Foreground Service (survives app closure)
- ✅ Automatic reconnection to Arduino
- ✅ Heartbeat timeout (sends STOP every 1 second when idle)



Created Components
1. AppViewModel (AppViewModel.kt)
Manages app state using a shared state model
Tracks: current heading, IP address, Arduino connection status, remote client connections
2. AppStateManager (AppStateManager.kt)
Singleton that bridges the service and UI
Allows the RotatorService to update app state which flows to the UI
3. CompassWidget (CompassWidget.kt)
High-resolution compass with 360° precision
Features:
Cardinal directions (N, S, E, W)
Degree markers every 10° (larger marks every 30°)
Animated pointer showing Arduino angle
Red arrowhead indicator at needle tip
Digital readout displaying current angle in degrees
4. ConnectedScreen (ConnectedScreen.kt)
Status header showing:
Current IP address
Arduino connection status (green/red indicator)
Remote login status with client count
Compass widget (centered, 300dp size)
Manual positioning section with two round buttons:
CCW (↺) - Counter-clockwise
CW (↻) - Clockwise
Stop Server button
5. RotatorServiceCommands (RotatorServiceCommands.kt)
Helper for sending CW/CCW/STOP commands to the service
Commands sent via Intent actions
Updated Components
MainActivity
Now creates AppViewModel instance
Switches between startup screen and ConnectedScreen based on service running state
RotatorService
Gets local IP address on startup
Updates AppStateManager on:
Arduino connection status changes
New position readings from Arduino
Client connection/disconnection
Handles CW/CCW/STOP commands from UI buttons
Properly broadcasts state changes to the ViewModel
The interface is fully functional and ready for use once connected to the Arduino!

