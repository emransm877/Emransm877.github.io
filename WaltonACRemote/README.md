# Walton AC Remote — custom IR remote for Android

A custom air-conditioner remote app built for **Walton split ACs**, designed for
phones with a built-in IR blaster (e.g. **Redmi K90**). It goes beyond the stock
remote with individual vane-group control, multiple swing styles, and a live
animated display that always shows what the AC was told to do.

## Features

- **Power, Mode (Auto/Cool/Dry/Fan/Heat), Temp 16–30 °C, Fan speed, Turbo, Light, Sleep**
- **Up/Down swing** — 5 fixed positions (Top → Bottom) **plus 4 sweep styles**:
  full sweep, top-region sweep, middle-region sweep, bottom-region sweep
- **Left/Right swing with individual side control** — pick a direction (or SWING)
  for the **left vane group** and the **right vane group** separately. The app
  maps your combination to the closest command the AC hardware understands and
  shows a `≈` marker on the display whenever the hardware has to approximate
  (IR ACs accept one horizontal-swing command for the whole vane bar — no IR
  protocol can physically move the two halves independently).
- **Live animated display** — a front view of the AC where the left vanes, right
  vanes and the up/down flap animate in real time to the commanded direction,
  including sweep motion, plus a cooling/airflow particle animation (snowflakes
  in COOL mode), 7-segment set-temperature readout, room temperature, mode and
  fan-level bars.
- **SYNC button** — every button press already transmits the *complete* remote
  state (the protocol is full-state), but if someone changes the AC with the
  original remote, press **SYNC** once and the AC is forced back to exactly what
  the app display shows. A pulse animation confirms the broadcast.
- **Room temperature display** — IR is strictly one-way (the AC cannot talk back
  to the phone), so the ROOM readout is a value you set to match your room
  thermometer; it's kept on the display next to the set temperature.

## Protocol

Walton split ACs use the **Gree YAW1F-family IR protocol** (38 kHz). The encoder
lives in [`GreeProtocol.kt`](app/src/main/java/com/emran/waltonac/GreeProtocol.kt):
9000/4500 µs header, two 32-bit LSB-first blocks separated by a 0b010 footer and
~20 ms gap, nibble checksum. If your particular Walton model doesn't respond,
that file is the only place that needs adjusting.

## Getting the APK

**Option A — GitHub builds it for you (no tools needed):**
GitHub Actions builds the APK on every push (see `.github/workflows/build-apk.yml`).
Open the repo's **Actions** tab → latest *Build Walton AC Remote APK* run →
download the **WaltonACRemote-debug-apk** artifact → unzip → copy
`app-debug.apk` to your phone and install it (allow "install unknown apps").

**Option B — Android Studio:**
Open the `WaltonACRemote` folder in Android Studio and press Run, or:

```bash
cd WaltonACRemote
gradle assembleDebug   # or use Android Studio's bundled Gradle
```

## Notes for the Redmi K90

- The app uses the standard Android `ConsumerIrManager` API, which Xiaomi/Redmi
  IR blasters support natively — no extra permissions to grant at runtime.
- Point the top edge of the phone (where the IR blaster is) at the AC.
- If no IR hardware is found the app runs in demo mode (animations only) and
  tells you so.
