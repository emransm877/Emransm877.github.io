package com.emran.waltonac

/**
 * IR encoder for the Gree "YAW1F"-family protocol, which the large majority of
 * Walton split ACs (Krystaline / Riverine / Venturi series etc.) use — Walton
 * indoor units are Gree-compatible.
 *
 * Frame: 38 kHz carrier.
 *   header (9000/4500) + bytes 0..3 LSB-first + 3 footer bits 0b010
 *   + block gap (~20 ms) + bytes 4..7 LSB-first + trailing mark.
 *
 * Every frame carries the FULL remote state, so any button press re-syncs the
 * whole AC to what the app shows.
 */
object GreeProtocol {

    const val CARRIER_HZ = 38000

    private const val HDR_MARK = 9000
    private const val HDR_SPACE = 4500
    private const val BIT_MARK = 620
    private const val ONE_SPACE = 1600
    private const val ZERO_SPACE = 540
    private const val MSG_SPACE = 19980

    /** Build the 8-byte state block from the app state. */
    fun buildState(s: AcState): IntArray {
        val b = IntArray(8)
        val (hSwing, _) = s.resolveHorizontal()
        val vSwingAuto = s.vSwing.isSweep

        b[0] = (s.mode.protocolValue and 0x07) or
                ((if (s.power) 1 else 0) shl 3) or
                ((s.fan.protocolValue and 0x03) shl 4) or
                ((if (vSwingAuto) 1 else 0) shl 6) or
                ((if (s.sleep) 1 else 0) shl 7)

        b[1] = (s.temp.coerceIn(AcState.MIN_TEMP, AcState.MAX_TEMP) - 16) and 0x0F

        b[2] = ((if (s.turbo) 1 else 0) shl 4) or
                ((if (s.light) 1 else 0) shl 5)

        b[3] = 0x50  // constant bits required by the protocol

        b[4] = (s.vSwing.protocolValue and 0x0F) or
                ((hSwing and 0x07) shl 4)

        b[5] = 0x20  // display/feature defaults
        b[6] = 0x00

        // Checksum: (10 + low nibbles of bytes 0-3 + high nibbles of bytes 4-6)
        // & 0xF, stored in the high nibble of byte 7.
        var sum = 10
        for (i in 0..3) sum += b[i] and 0x0F
        for (i in 4..6) sum += (b[i] shr 4) and 0x0F
        b[7] = (sum and 0x0F) shl 4

        return b
    }

    /**
     * Convert the state block into a mark/space pattern in microseconds,
     * ready for ConsumerIrManager.transmit().
     */
    fun buildPattern(s: AcState): IntArray {
        val bytes = buildState(s)
        val out = ArrayList<Int>(160)

        out.add(HDR_MARK)
        out.add(HDR_SPACE)

        // First block: bytes 0..3, LSB first
        for (i in 0..3) appendByte(out, bytes[i])
        // 3-bit footer 0b010, sent LSB first -> 0, 1, 0
        appendBit(out, 0)
        appendBit(out, 1)
        appendBit(out, 0)
        // Block separator
        out.add(BIT_MARK)
        out.add(MSG_SPACE)

        // Second block: bytes 4..7, LSB first
        for (i in 4..7) appendByte(out, bytes[i])
        // Trailing mark + gap so repeated presses don't merge
        out.add(BIT_MARK)
        out.add(MSG_SPACE)

        return out.toIntArray()
    }

    private fun appendByte(out: ArrayList<Int>, byte: Int) {
        for (bit in 0..7) appendBit(out, (byte shr bit) and 1)
    }

    private fun appendBit(out: ArrayList<Int>, bit: Int) {
        out.add(BIT_MARK)
        out.add(if (bit == 1) ONE_SPACE else ZERO_SPACE)
    }
}
