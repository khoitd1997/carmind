# Carmind

Carmind is used for remembering parking spot position while requiring minimal interaction from the user, the goal is to have an MCU works with an app to calculate user's position since they enter a preset parking lot and then remember where they last stop

## Directory Structure

- app/: Kotlin Android app. The app uses an RxJava-based BLE library to talk with the nrf52832
- firmware/: nrf52832 firmware code. The firmware is optimized for power usage
