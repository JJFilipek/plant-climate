#include <WiFi.h>
#include <Wire.h>
#include "Adafruit_SHTC3.h"

#define SDA_PIN 8
#define SCL_PIN 9

const char* ssid = "*******";
const char* password = "*********";
const char* server_ip = "192.168.0.144"; 
const uint16_t server_port = 9000;

Adafruit_SHTC3 shtc3 = Adafruit_SHTC3();

WiFiClient client;

void setup() {
  Serial.begin(115200);
  delay(1000);
  Wire.begin(SDA_PIN, SCL_PIN);

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.println("Łączenie z WiFi...");
  }
  Serial.println("Połączono z WiFi!");

  if (!shtc3.begin()) {
    Serial.println("Nie wykryto SHTC3. Sprawdź połączenia!");
    while (1);
  }
}

void loop() {
  sensors_event_t humidity, temp;
  if (shtc3.getEvent(&humidity, &temp)) {
    String data = String("{\"deviceId\":\"ESP2\",\"temperature\":") +
                  temp.temperature +
                  ",\"humidity\":" +
                  humidity.relative_humidity +
                  "}\n";

    if (client.connect(server_ip, server_port)) {
      client.print(data);
      client.stop();
      Serial.println("Wysłano: " + data);
    } else {
      Serial.println("Błąd połączenia z serwerem!");
    }
  } else {
    Serial.println("Błąd odczytu SHTC3!");
  }
  delay(5000);
}
