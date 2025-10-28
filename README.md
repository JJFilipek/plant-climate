# Plant Climate Monitor

Monitor the environment around your plants with a lightweight Java server, a desktop dashboard, and ESP32-based Wi-Fi sensors.

## ✨ Features

- **Real-time telemetry pipeline** – Sensors push JSON payloads to the server, which immediately streams updates to all connected clients. See `SensorListener.java` and `ClientListener.java` for the socket handlers.
- **Historical insight** – The server persists CSV logs and exposes history/export commands so the UI can render charts and generate CSV snapshots on demand via those listener classes.
- **Desktop monitoring app** – Swing-based client for pairing sensors, browsing live readings, editing plant metadata, and visualising trends with custom charts built in `MonitorView.java` and `SensorChartPanel.java`.
- **Sensor firmware samples** – Two ESP32 sketches show how to capture soil moisture, lux, RGB colour temperature, air temperature, and humidity over Wi-Fi (`wifi-soil-humidity-and-light.ino`, `wifi-temperature-and-humidity.ino`).
## 🧱 Project structure

```
plant-climate/
├── code/                     # Maven module with server and Swing client
│   └── src/main/java/jf/plantclimate/
│       ├── server/           # TCP listeners for sensors and UI clients
│       ├── client/           # Networking facade + Swing views
│       ├── data/             # Shared data records and configs
│       └── util/             # Helpers for parsing and formatting
├── sensors_code/             # ESP32 firmware examples
└── pom.xml                   # Multi-module build (Java 17)
```

## 🚀 Getting started

### Prerequisites

- Java Development Kit (JDK) 17 or newer
- Apache Maven 3.8+

### Build

```bash
mvn clean package
```

This command produces `code/target/code-1.0.0-jar-with-dependencies.jar`, bundling every module dependency defined in `pom.xml`.

## 🖧 Running the services

### 1. Start the monitoring server

The server listens on two TCP ports: sensors (`9000`) and clients (`9100`). Both values can be adjusted in `Config.java` before building.

```bash
java -cp code/target/code-1.0.0-jar-with-dependencies.jar \
     jf.plantclimate.server.PlantClimateServer
```

When the process starts you should see log messages announcing the listening ports and confirming successful startup in `PlantClimateServer.java`.

### 2. Launch the desktop client

In a separate terminal (with the server already running):

```bash
java -cp code/target/code-1.0.0-jar-with-dependencies.jar \
     jf.plantclimate.client.ClientMain
```

The Swing UI prompts for a username, shows the paired sensor list, live readings, historical charts, and plant metadata editors handled by `ClientMain.java` and `MonitorView.java`.

> **Tip:** The client persists paired sensor metadata locally in `paired_sensors.dat` and automatically refreshes charts every 30 seconds (see `MonitorClient.java` and `SensorChartPanel.java`).

## 📡 Connecting hardware sensors

The repository includes two ESP32 sketches under `sensors_code/`:

| Sketch | Hardware | Telemetry payload |
| --- | --- | --- |
| `wifi-soil-humidity-and-light.ino` | Soil moisture probe (analog), BH1750 lux sensor, VEML6040 RGB sensor | `deviceId`, `soil`, `lux`, `lightColor.{red,green,blue,white,colorTemperature}` sent every 5 seconds (`sensors_code/wifi-soil-humidity-and-light/wifi-soil-humidity-and-light.ino`). |
| `wifi-temperature-and-humidity.ino` | Adafruit SHTC3 temperature & humidity sensor | `deviceId`, `temperature`, `humidity` every 5 seconds (`sensors_code/wifi-temperature-and-humidity/wifi-temperature-and-humidity.ino`). |

Update the Wi-Fi credentials and `server_ip` constants to point at the machine running `PlantClimateServer`. Each sketch opens a TCP connection, transmits a JSON line, and closes the socket—making it easy to port to other microcontrollers (see the two `.ino` files mentioned above).

## 🛠️ Development notes

- Sensor samples are saved under `sensor_data/{sensorId}.csv` alongside the newest in-memory values for quick history lookups, implemented in `SensorListener.java`.
- The client-server protocol is line-based text. Commands like `LIST`, `GET <id>`, `HISTORY <id>,<count>`, and `EXPORT <id>` are available for future integrations within `ClientListener.java`.
- Utility helpers (`ReadingParser`, `DateFormatter`) convert CSV and timestamp formats shared by the server and client logic; both classes live in the `util` package.

## 📄 License

This project is distributed under the terms of the [MIT License](LICENSE).
