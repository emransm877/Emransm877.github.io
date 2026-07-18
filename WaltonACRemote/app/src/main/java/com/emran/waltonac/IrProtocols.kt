package com.emran.waltonac

/** A ready-to-fire IR signal: carrier frequency + mark/space pattern (µs). */
data class IrSignal(val carrierHz: Int, val pattern: IntArray)

/**
 * Universal IR protocol engine. Encodes the world's common consumer-IR
 * protocols into raw mark/space patterns. Combined with the Pronto-hex parser
 * it can drive virtually any IR device a phone can physically reach.
 */
object IrProtocols {

    val names = listOf(
        "NEC", "NEC extended", "Sony SIRC 12", "Sony SIRC 15",
        "Sony SIRC 20", "RC5", "Samsung", "JVC"
    )

    /** Encode by protocol name. address/command are integers (hex in the UI). */
    fun encode(name: String, address: Int, command: Int): IrSignal = when (name) {
        "NEC" -> nec(address, command, extended = false)
        "NEC extended" -> nec(address, command, extended = true)
        "Sony SIRC 12" -> sony(address, command, bits = 12)
        "Sony SIRC 15" -> sony(address, command, bits = 15)
        "Sony SIRC 20" -> sony(address, command, bits = 20)
        "RC5" -> rc5(address, command)
        "Samsung" -> samsung(address, command)
        "JVC" -> jvc(address, command)
        else -> nec(address, command, extended = false)
    }

    // ---- pulse-distance protocols (NEC family) ---------------------------

    private fun nec(address: Int, command: Int, extended: Boolean): IrSignal {
        val bytes = if (extended) {
            intArrayOf(address and 0xFF, (address shr 8) and 0xFF,
                command and 0xFF, command.inv() and 0xFF)
        } else {
            intArrayOf(address and 0xFF, address.inv() and 0xFF,
                command and 0xFF, command.inv() and 0xFF)
        }
        val out = ArrayList<Int>(72)
        out.add(9000); out.add(4500)
        for (b in bytes) for (i in 0 until 8) {
            out.add(560)
            out.add(if ((b shr i) and 1 == 1) 1690 else 560)
        }
        out.add(560); out.add(40000)
        return IrSignal(38000, out.toIntArray())
    }

    private fun samsung(address: Int, command: Int): IrSignal {
        val bytes = intArrayOf(address and 0xFF, address and 0xFF,
            command and 0xFF, command.inv() and 0xFF)
        val out = ArrayList<Int>(72)
        out.add(4500); out.add(4500)
        for (b in bytes) for (i in 0 until 8) {
            out.add(560)
            out.add(if ((b shr i) and 1 == 1) 1690 else 560)
        }
        out.add(560); out.add(40000)
        return IrSignal(38000, out.toIntArray())
    }

    private fun jvc(address: Int, command: Int): IrSignal {
        val out = ArrayList<Int>(40)
        out.add(8400); out.add(4200)
        val data = (address and 0xFF) or ((command and 0xFF) shl 8)
        for (i in 0 until 16) {
            out.add(526)
            out.add(if ((data shr i) and 1 == 1) 1574 else 526)
        }
        out.add(526); out.add(40000)
        return IrSignal(38000, out.toIntArray())
    }

    // ---- Sony SIRC (pulse-width, sent 3x) --------------------------------

    private fun sony(address: Int, command: Int, bits: Int): IrSignal {
        val (dataBits, value) = when (bits) {
            12 -> 12 to ((command and 0x7F) or ((address and 0x1F) shl 7))
            15 -> 15 to ((command and 0x7F) or ((address and 0xFF) shl 7))
            else -> 20 to ((command and 0x7F) or ((address and 0x1FFF) shl 7))
        }
        val frame = ArrayList<Int>()
        frame.add(2400); frame.add(600)
        for (i in 0 until dataBits) {
            frame.add(if ((value shr i) and 1 == 1) 1200 else 600)
            frame.add(600)
        }
        // Sony frames repeat with a ~45 ms period; send 3 copies.
        val out = ArrayList<Int>()
        repeat(3) {
            out.addAll(frame)
            out.add(600)
            out.add(15000)
        }
        return IrSignal(40000, out.toIntArray())
    }

    // ---- RC5 (Manchester) ------------------------------------------------

    private fun rc5(address: Int, command: Int): IrSignal {
        val half = 889
        // 14 bits: start(1) start(1) toggle(0) addr[4..0] cmd[5..0]
        val bitsList = ArrayList<Int>()
        bitsList.add(1); bitsList.add(1); bitsList.add(0)
        for (i in 4 downTo 0) bitsList.add((address shr i) and 1)
        for (i in 5 downTo 0) bitsList.add((command shr i) and 1)
        // Manchester: 1 = off,on ; 0 = on,off  (carrier-on = "on")
        val levels = ArrayList<Int>()
        for (b in bitsList) {
            if (b == 1) { levels.add(0); levels.add(1) }
            else { levels.add(1); levels.add(0) }
        }
        // Drop leading silence, then run-length encode into mark/space.
        var start = 0
        while (start < levels.size && levels[start] == 0) start++
        val out = ArrayList<Int>()
        var i = start
        while (i < levels.size) {
            val level = levels[i]
            var run = 0
            while (i < levels.size && levels[i] == level) { run++; i++ }
            out.add(run * half)
        }
        out.add(100000) // trailing gap
        return IrSignal(36000, out.toIntArray())
    }
}
