#include <WiFi.h>
#include <Wire.h>
#include <BH1750.h>
#include "veml6040.h"

#define SOIL_PIN 1
#define SDA_PIN 8
#define SCL_PIN 9

const char* ssid = "*******";
const char* password = "*********";
const char* server_ip = "192.168.0.144";
const uint16_t server_port = 9000;

BH1750 lightMeter;
VEML6040 veml6040 = VEML6040();
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

  if (!lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE)) {
    Serial.println("Nie wykryto BH1750. Sprawdź połączenia!");
    while (1);
  }
  if (!veml6040.begin()) {
    Serial.println("Nie wykryto VEML6040. Sprawdź połączenia!");
    while (1);
  }
  veml6040.setConfiguration(VEML6040_IT_320MS + VEML6040_AF_AUTO + VEML6040_SD_ENABLE);
}

void loop() {
  int soilValue = analogRead(SOIL_PIN);

  float lux_bh = lightMeter.readLightLevel();

  uint16_t r = veml6040.getRed();
  uint16_t g = veml6040.getGreen();
  uint16_t b = veml6040.getBlue();
  uint16_t w = veml6040.getWhite();
  float cct = veml6040.getCCT();

  String data = String("{\"deviceId\":\"ESP1\"") +
                ",\"soil\":" + soilValue +
                ",\"lux\":" + lux_bh +
                ",\"lightColor\":{" +
                  "\"red\":" + r +
                  ",\"green\":" + g +
                  ",\"blue\":" + b +
                  ",\"white\":" + w +
                  ",\"colorTemperature\":" + cct +
                "}}";

  if (client.connect(server_ip, server_port)) {
    client.print(data + "\n");
    client.stop();
    Serial.println("Wysłano: " + data);
  } else {
    Serial.println("Błąd połączenia z serwerem!");
  }

  delay(5000);
}

