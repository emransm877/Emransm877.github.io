package com.emran.waltonac

import android.content.Context

/** Operating modes, matching the frames captured from the official remote. */
enum class AcMode(val label: String) {
    COOL("COOL"), AUTO("AUTO"), DRY("DRY"), HEAT("HEAT"), FAN("FAN");

    fun next(): AcMode = entries[(ordinal + 1) % entries.size]

    /** The exact captured IR frame for switching to this mode. */
    fun frame(): IntArray = when (this) {
        COOL -> WaltonCodes.MODE_COOL
        AUTO -> WaltonCodes.MODE_AUTO
        DRY -> WaltonCodes.MODE_DRY
        HEAT -> WaltonCodes.MODE_HEAT
        FAN -> WaltonCodes.MODE_FAN
    }
}

/**
 * Fan speed is a single "step" IR command on Walton — each press advances the
 * AC's own fan speed. We track a display value locally so the UI can show it.
 */
enum class FanSpeed(val label: String) {
    AUTO("AUTO"), LOW("LOW"), MED("MED"), HIGH("HIGH");

    fun next(): FanSpeed = entries[(ordinal + 1) % entries.size]
}

/**
 * The remote state. This AC (like the official Walton remote) uses discrete IR
 * commands rather than a single stateful frame, so swing is ON/OFF per axis —
 * that is all the hardware's IR protocol exposes. Temperature has a dedicated
 * captured frame for every degree 16–30 °C.
 */
data class AcState(
    var power: Boolean = false,
    var mode: AcMode = AcMode.COOL,
    var temp: Int = 24,               // 16..30 °C
    var fan: FanSpeed = FanSpeed.AUTO,
    var vSwing: Boolean = false,      // up/down louver sweeping
    var hSwing: Boolean = false,      // left/right louver sweeping
    var display: Boolean = true,      // AC panel light
    var turbo: Boolean = false,
    var eco: Boolean = false,
    var health: Boolean = false,
    // Display-only. IR is one-way so the AC cannot report its real sensor value;
    // this is a manual number the user sets to match their own thermometer.
    var roomTemp: Int = 24,
    // 38 kHz is what actually fires on this phone's blaster (76 kHz, the value
    // in the official app, is out of the emitter's range and stays silent).
    var carrierHz: Int = 38000
) {
    companion object {
        const val MIN_TEMP = 16
        const val MAX_TEMP = 30
        private const val PREFS = "ac_state"

        fun load(context: Context): AcState {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return AcState(
                power = p.getBoolean("power", false),
                mode = AcMode.entries[p.getInt("mode", AcMode.COOL.ordinal)],
                temp = p.getInt("temp", 24).coerceIn(MIN_TEMP, MAX_TEMP),
                fan = FanSpeed.entries[p.getInt("fan", 0)],
                vSwing = p.getBoolean("vswing", false),
                hSwing = p.getBoolean("hswing", false),
                display = p.getBoolean("display", true),
                turbo = p.getBoolean("turbo", false),
                eco = p.getBoolean("eco", false),
                health = p.getBoolean("health", false),
                roomTemp = p.getInt("roomTemp", 24),
                carrierHz = p.getInt("carrierHz", 38000)
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("power", power)
            .putInt("mode", mode.ordinal)
            .putInt("temp", temp)
            .putInt("fan", fan.ordinal)
            .putBoolean("vswing", vSwing)
            .putBoolean("hswing", hSwing)
            .putBoolean("display", display)
            .putBoolean("turbo", turbo)
            .putBoolean("eco", eco)
            .putBoolean("health", health)
            .putInt("roomTemp", roomTemp)
            .putInt("carrierHz", carrierHz)
            .apply()
    }

    /** Captured IR frame for the current set temperature. */
    fun tempFrame(): IntArray = WaltonCodes.TEMP[temp.coerceIn(MIN_TEMP, MAX_TEMP)]
        ?: WaltonCodes.TEMP[24]!!
}
