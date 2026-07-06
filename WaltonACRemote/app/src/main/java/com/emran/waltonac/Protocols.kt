package com.emran.waltonac

/**
 * Walton has shipped indoor units on several Chinese platforms over the years,
 * so the app supports the three protocol families they use and includes a
 * "Find my AC" tester to pick the right one:
 *
 *  - MIDEA-24 (a.k.a. Coolix): most Walton splits — their remotes are
 *    Midea RG52-family clones.
 *  - GREE (YAW1F family): some Walton inverter models.
 *  - MIDEA-48: newer Midea-platform units.
 */
enum class IrProtocol(val label: String) {
    COOLIX("MIDEA-24"),
    GREE("GREE"),
    MIDEA("MIDEA-48");
}

/** A ready-to-transmit IR frame. */
class IrFrame(val carrierHz: Int, val pattern: IntArray)

object AcEncoder {
    /** Full-state frame for the state's selected protocol. */
    fun encode(s: AcState): IrFrame = when (s.protocol) {
        IrProtocol.GREE -> IrFrame(GreeProtocol.CARRIER_HZ, GreeProtocol.buildPattern(s))
        IrProtocol.COOLIX -> CoolixEncoder.encode(s)
        IrProtocol.MIDEA -> MideaEncoder.encode(s)
    }

    /**
     * Extra frame that toggles/steps the louvers on protocols where swing is a
     * toggle command instead of part of the state (returns null for GREE,
     * which encodes swing in the main state frame).
     */
    fun encodeSwingToggle(s: AcState): IrFrame? = when (s.protocol) {
        IrProtocol.GREE -> null
        IrProtocol.COOLIX -> CoolixEncoder.swingToggle()
        IrProtocol.MIDEA -> MideaEncoder.swingToggle()
    }
}

/**
 * Coolix / "MIDEA-24" 24-bit protocol (Midea RG52-family remotes).
 * 38 kHz; header 4692/4416 µs; bit 552 µs mark, 1656/552 µs space; each byte
 * is sent MSB-first followed by its bitwise inverse; whole frame sent twice.
 */
object CoolixEncoder {
    private const val CARRIER = 38000
    private const val HDR_MARK = 4692
    private const val HDR_SPACE = 4416
    private const val BIT_MARK = 552
    private const val ONE_SPACE = 1656
    private const val ZERO_SPACE = 552
    private const val GAP = 5244

    private const val MSG_OFF = 0xB27BE0
    private const val MSG_SWING = 0xB26BE0
    private const val FAN_MODE_TEMP_CODE = 0b1110

    // Coolix does not use plain binary temperatures; each degree has a code.
    private val TEMP_CODES = intArrayOf(
        //17      18      19      20      21      22      23
        0b0000, 0b0001, 0b0011, 0b0010, 0b0110, 0b0111, 0b0101,
        //24      25      26      27      28      29      30
        0b0100, 0b1100, 0b1101, 0b1001, 0b1000, 0b1010, 0b1011
    )

    fun encode(s: AcState): IrFrame =
        IrFrame(CARRIER, patternFor(stateMessage(s), s.coolixRepeats))

    fun swingToggle(): IrFrame = IrFrame(CARRIER, patternFor(MSG_SWING, 2))

    private fun stateMessage(s: AcState): Int {
        if (!s.power) return MSG_OFF

        var modeBits: Int
        var tempCode = TEMP_CODES[(s.temp.coerceIn(17, 30)) - 17]
        when (s.mode) {
            AcMode.COOL -> modeBits = 0b00
            AcMode.DRY -> modeBits = 0b01
            AcMode.AUTO -> modeBits = 0b10
            AcMode.HEAT -> modeBits = 0b11
            AcMode.FAN -> {
                // Fan-only is encoded as Dry with a special temperature code.
                modeBits = 0b01
                tempCode = FAN_MODE_TEMP_CODE
            }
        }
        // Auto/Dry require the "auto0" fan value; other modes use real speeds.
        // The "auto fan" code differs between Coolix clones, so it comes from
        // the state (discovered by the Find-My-AC lab).
        val fanBits = when {
            s.mode == AcMode.AUTO || s.mode == AcMode.DRY -> 0b000
            s.fan == FanSpeed.LOW -> 0b100
            s.fan == FanSpeed.MED -> 0b010
            s.fan == FanSpeed.HIGH -> 0b001
            else -> s.coolixFanAutoCode and 0b111
        }

        val low = (tempCode shl 4) or (modeBits shl 2)
        val mid = (fanBits shl 5) or 0b11111 // sensor temp "off"
        return (0xB2 shl 16) or (mid shl 8) or low
    }

    private fun patternFor(msg: Int, repeats: Int): IntArray {
        val out = ArrayList<Int>(200)
        repeat(repeats.coerceIn(1, 6)) {
            out.add(HDR_MARK); out.add(HDR_SPACE)
            for (shift in intArrayOf(16, 8, 0)) {
                val b = (msg shr shift) and 0xFF
                appendByteMsb(out, b)
                appendByteMsb(out, b.inv() and 0xFF)
            }
            out.add(BIT_MARK); out.add(GAP)
        }
        return out.toIntArray()
    }

    private fun appendByteMsb(out: ArrayList<Int>, byte: Int) {
        for (bit in 7 downTo 0) {
            out.add(BIT_MARK)
            out.add(if ((byte shr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE)
        }
    }
}

/**
 * Midea 48-bit protocol. 38 kHz; header 4480/4480 µs; bit 560 µs mark,
 * 1680/560 µs space; 6 bytes MSB-first; the frame is sent once normally and
 * then once fully inverted. Checksum = bit-reversed two's-complement of the
 * sum of the bit-reversed payload bytes (verified against the protocol's
 * documented default state 0xA1826FFFFF62).
 */
object MideaEncoder {
    private const val CARRIER = 38000
    private const val HDR_MARK = 4480
    private const val HDR_SPACE = 4480
    private const val BIT_MARK = 560
    private const val ONE_SPACE = 1680
    private const val ZERO_SPACE = 560
    private const val GAP = 5360

    fun encode(s: AcState): IrFrame = IrFrame(CARRIER, patternFor(stateBytes(s)))

    fun swingToggle(): IrFrame {
        // Vertical-swing toggle command message (0xA2 0x02 payload).
        val b = intArrayOf(0, 0xFF, 0xFF, 0xFF, 0x02, 0xA2)
        b[0] = checksum(b)
        return IrFrame(CARRIER, patternFor(b))
    }

    /** bytes[5] is the MSB (header byte), bytes[0] the checksum. */
    private fun stateBytes(s: AcState): IntArray {
        val b = IntArray(6)
        b[5] = 0xA1
        val mode = when (s.mode) {
            AcMode.COOL -> 0; AcMode.DRY -> 1; AcMode.AUTO -> 2
            AcMode.HEAT -> 3; AcMode.FAN -> 4
        }
        b[4] = ((if (s.power) 1 else 0) shl 7) or
                ((if (s.sleep) 1 else 0) shl 6) or
                (s.fan.protocolValue shl 3) or mode
        b[3] = 0x40 or ((s.temp.coerceIn(16, 30)) - 16)
        b[2] = 0xFF
        b[1] = 0xFF
        b[0] = checksum(b)
        return b
    }

    private fun checksum(b: IntArray): Int {
        var sum = 0
        for (i in 1..5) sum += reverse8(b[i])
        return reverse8((256 - (sum and 0xFF)) and 0xFF)
    }

    private fun reverse8(v: Int): Int {
        var x = v and 0xFF
        var r = 0
        repeat(8) { r = (r shl 1) or (x and 1); x = x shr 1 }
        return r
    }

    private fun patternFor(bytes: IntArray): IntArray {
        val out = ArrayList<Int>(220)
        for (pass in 0..1) {
            out.add(HDR_MARK); out.add(HDR_SPACE)
            for (i in 5 downTo 0) {
                val byte = if (pass == 0) bytes[i] else bytes[i].inv() and 0xFF
                for (bit in 7 downTo 0) {
                    out.add(BIT_MARK)
                    out.add(if ((byte shr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE)
                }
            }
            out.add(BIT_MARK); out.add(GAP)
        }
        return out.toIntArray()
    }
}
