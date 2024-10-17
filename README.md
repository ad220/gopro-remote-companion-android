# Companion app for the GoPro remote on Garmin watch

An Android app used as a bridge between this [Garmin smartwatch remote control widget](https://github.com/ad220/gopro-remote-connectiq) and a GoPro camera. It uses the [Open GoPro Bluetooth Low Energy API](https://gopro.github.io/OpenGoPro/ble_2_0) and the [Garmin ConnectIQ Mobile SDK](https://developer.garmin.com/connect-iq/overview/).

<img src="doc/screenshots/main_view.jpg" width="300">

Please note that this app was mainly developed for personal use, it should now be stable enough but you may still encounter a few bugs.


## Features
- pick the watch you want to use from all paired devices on the ConnectIQ app
- choose the GoPro to connect to from all paired cameras on your phone
- log the connection and message history to UI.
- background service to keep the bridge on while app closed

## Installation
This app is not released on the Play Store and I do not plan of doing so.
However, you can download the latest release from the [GitHub releases page](https://github.com/ad220/gopro-remote-companion-android/releases).
Make sure you are using a version of the app that is compatible with the widget version installed on your watch.
Alternatively, you can use Android Studio to build, install and run the app on a connected smartphone with USB debugging on.

For now, the location or bluetooth permission must be given through the settings (asking on first
app launch not implemented yet), as it is required to scan for nearby Bluetooth devices.

## How to use it
Make sure you have Garmin ConnectIQ app installed on your phone and that your watch is paired to it (that should not be a problem as it is the proper way to sync your smartwatch). 

Pair the GoPro to your smartphone using the GoPro Quik app or just by scanning for it in the Android Bluetooth settings.

Launch the companion app, make sure the selected devices are the ones you want to use. You are now ready to use the widget on your watch.