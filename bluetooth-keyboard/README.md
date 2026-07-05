# BT Keyboard — use your phone as a Bluetooth keyboard for your Android TV box

This app runs on your **phone** and makes it act as a real Bluetooth hardware
keyboard (HID). Your TV box needs **no app at all** — it just sees a normal
Bluetooth keyboard. Because the input is real keyboard input at the system
level, it works inside **every** app on the TV box: Tor Browser, YouTube
search, terminal apps, everything.

## Requirements

- Phone: Android 9 (Pie) or newer, with Bluetooth.
  - Note: a few phone brands ship Android without the "HID device" Bluetooth
    profile. Almost all modern phones have it.
- TV box: any Android TV box (or any device) that supports Bluetooth keyboards.

## Getting the APK

The APK is built automatically by GitHub Actions:

1. Open the repo's **Actions** tab on GitHub.
2. Click the latest **Build Bluetooth Keyboard APK** run.
3. Download the **bt-keyboard-debug-apk** artifact (a zip containing
   `app-debug.apk`).
4. Copy the APK to your phone and install it (allow "install unknown apps"
   when prompted).

## How to use

1. **Pair once:** on the TV box open *Settings → Remotes & Accessories* (or
   *Bluetooth*) and start searching. On your phone, open the app, tap
   **Pair…** to open Bluetooth settings, and pair with the TV box.
2. In the app tap **Refresh**, select the TV box in the list, tap **Connect**.
3. On the TV, open any app and focus a text field.
4. On the phone: type in the box and hit **Send**, use **Paste & send** to
   send the phone's clipboard, or turn on **Live typing** to send every
   keystroke as you type. The arrow/Enter/Esc keys also let you navigate the
   TV interface.

## Building locally

Open `bluetooth-keyboard/` in Android Studio, or run:

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.
