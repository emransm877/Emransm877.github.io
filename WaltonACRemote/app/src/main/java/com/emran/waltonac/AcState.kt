package com.emran.waltonac

import android.content.Context

/** Operating modes (values match the Gree/Walton IR protocol). */
enum class AcMode(val protocolValue: Int, val label: String) {
    AUTO(0, "AUTO"),
    COOL(1, "COOL"),
    DRY(2, "DRY"),
    FAN(3, "FAN"),
    HEAT(4, "HEAT");

    fun next(): AcMode = entries[(ordinal + 1) % entries.size]
}

/** Fan speeds (values match the protocol). */
enum class FanSpeed(val protocolValue: Int, val label: String) {
    AUTO(0, "AUTO"),
    LOW(1, "LOW"),
    MED(2, "MED"),
    HIGH(3, "HIGH");

    fun next(): FanSpeed = entries[(ordinal + 1) % entries.size]
}

/**
 * Vertical (up/down) louver setting. The Walton/Gree protocol supports fixed
 * positions, a full sweep, and three partial-region sweeps.
 */
enum class VerticalSwing(val protocolValue: Int, val label: String, val isSweep: Boolean) {
    FULL_SWING(1, "FULL SWING", true),
    TOP(2, "TOP", false),
    UPPER_MID(3, "UP-MID", false),
    MIDDLE(4, "MIDDLE", false),
    LOWER_MID(5, "LOW-MID", false),
    BOTTOM(6, "BOTTOM", false),
    SWEEP_UPPER(11, "SWEEP TOP", true),    // sweeps only the upper region
    SWEEP_MIDDLE(9, "SWEEP MID", true),    // sweeps only the middle region
    SWEEP_LOWER(7, "SWEEP LOW", true)      // sweeps only the lower region
}

/**
 * What the user wants each half (left blade group / right blade group) of the
 * horizontal vanes to do. The app maps the (left, right) pair to the closest
 * command the AC hardware understands.
 */
enum class ZoneAim(val label: String) {
    LEFT("LEFT"),
    CENTER("CENTER"),
    RIGHT("RIGHT"),
    SWING("SWING")
}

/** Raw horizontal swing values in the protocol. */
object HSwing {
    const val OFF = 0
    const val AUTO = 1       // full left<->right sweep
    const val MAX_LEFT = 2
    const val LEFT = 3
    const val MIDDLE = 4
    const val RIGHT = 5
    const val MAX_RIGHT = 6
}

/**
 * The complete remote state. Every IR transmission sends the FULL state
 * (this protocol is stateful), which is what makes the SYNC button reliable:
 * one press pushes everything the display shows onto the AC.
 */
data class AcState(
    var power: Boolean = false,
    var mode: AcMode = AcMode.COOL,
    var temp: Int = 24,                       // 16..30 °C
    var fan: FanSpeed = FanSpeed.AUTO,
    var turbo: Boolean = false,
    var light: Boolean = true,
    var sleep: Boolean = false,
    var vSwing: VerticalSwing = VerticalSwing.MIDDLE,
    var leftZone: ZoneAim = ZoneAim.CENTER,   // left vane group target
    var rightZone: ZoneAim = ZoneAim.CENTER,  // right vane group target
    var roomTemp: Int = 28                    // shown on display; user-adjustable
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
                turbo = p.getBoolean("turbo", false),
                light = p.getBoolean("light", true),
                sleep = p.getBoolean("sleep", false),
                vSwing = VerticalSwing.entries[p.getInt("vswing", VerticalSwing.MIDDLE.ordinal)],
                leftZone = ZoneAim.entries[p.getInt("lzone", ZoneAim.CENTER.ordinal)],
                rightZone = ZoneAim.entries[p.getInt("rzone", ZoneAim.CENTER.ordinal)],
                roomTemp = p.getInt("roomTemp", 28)
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("power", power)
            .putInt("mode", mode.ordinal)
            .putInt("temp", temp)
            .putInt("fan", fan.ordinal)
            .putBoolean("turbo", turbo)
            .putBoolean("light", light)
            .putBoolean("sleep", sleep)
            .putInt("vswing", vSwing.ordinal)
            .putInt("lzone", leftZone.ordinal)
            .putInt("rzone", rightZone.ordinal)
            .putInt("roomTemp", roomTemp)
            .apply()
    }

    /**
     * Collapse the two per-side aims into the single horizontal-swing command
     * the AC's IR protocol can express. Returns protocol value + whether the
     * hardware can honour the request exactly (false = best approximation).
     */
    fun resolveHorizontal(): Pair<Int, Boolean> {
        val l = leftZone
        val r = rightZone
        return when {
            l == ZoneAim.SWING && r == ZoneAim.SWING -> HSwing.AUTO to true
            l == ZoneAim.SWING || r == ZoneAim.SWING -> HSwing.AUTO to false
            l == r -> when (l) {
                ZoneAim.LEFT -> HSwing.MAX_LEFT to true
                ZoneAim.CENTER -> HSwing.MIDDLE to true
                ZoneAim.RIGHT -> HSwing.MAX_RIGHT to true
                else -> HSwing.MIDDLE to true
            }
            l == ZoneAim.LEFT && r == ZoneAim.CENTER -> HSwing.LEFT to true
            l == ZoneAim.CENTER && r == ZoneAim.RIGHT -> HSwing.RIGHT to true
            l == ZoneAim.CENTER && r == ZoneAim.LEFT -> HSwing.LEFT to false
            l == ZoneAim.RIGHT && r == ZoneAim.CENTER -> HSwing.RIGHT to false
            // opposite / spread combos: full sweep is the closest thing
            else -> HSwing.AUTO to false
        }
    }
}
