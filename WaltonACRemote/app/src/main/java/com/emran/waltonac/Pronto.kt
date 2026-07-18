package com.emran.waltonac

/**
 * Parses Pronto (CCF) hex — the universal remote-code format used by remote
 * databases worldwide. Handles raw learned codes (type 0000), converting the
 * carrier-cycle burst pairs into a microsecond mark/space pattern.
 *
 * Any Pronto code published for any device becomes a fireable signal here.
 */
object Pronto {

    /** Returns a fireable IrSignal, or null if the hex is not valid Pronto. */
    fun parse(hex: String): IrSignal? {
        val words = hex.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 6) return null
        val w = try {
            words.map { it.toInt(16) }
        } catch (e: Exception) {
            return null
        }
        if (w[0] != 0x0000) return null // only raw oscillated codes
        val freqCode = w[1]
        if (freqCode == 0) return null
        // Pronto time unit: period µs = freqCode × 0.241246.
        val periodUs = freqCode * 0.241246
        val carrierHz = (1_000_000.0 / periodUs).toInt().coerceIn(15000, 500000)
        val seq1Pairs = w[2]
        val seq2Pairs = w[3]
        val pairs = if (seq1Pairs > 0) seq1Pairs else seq2Pairs
        val startWord = if (seq1Pairs > 0) 4 else 4 + seq1Pairs * 2
        val count = pairs * 2
        if (count == 0 || startWord + count > w.size) return null
        val pattern = IntArray(count)
        for (i in 0 until count) {
            pattern[i] = (w[startWord + i] * periodUs).toInt().coerceAtLeast(1)
        }
        return IrSignal(carrierHz, pattern)
    }
}
