package com.emran.waltonac

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Sends real Walton IR frames through the phone's IR blaster (the Redmi K90
 * exposes it via the standard ConsumerIrManager API). Frames are the exact
 * mark/space patterns captured from the official Walton remote.
 */
class IrTransmitter(context: Context) {

    private val ir: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    /** Human-readable result of the last transmit, for the on-screen diagnostic. */
    var lastStatus: String = "ready"
        private set

    val available: Boolean
        get() = ir?.hasIrEmitter() == true

    /** The carrier-frequency ranges the phone's blaster reports it supports. */
    fun supportedCarriers(): String {
        val emitter = ir ?: return "no IR service"
        if (!emitter.hasIrEmitter()) return "no emitter"
        return try {
            val f = emitter.carrierFrequencies
            if (f == null || f.isEmpty()) "unknown"
            else f.joinToString(", ") { "${it.minFrequency}-${it.maxFrequency}" }
        } catch (e: Exception) {
            "n/a"
        }
    }

    /** Fire a raw Walton frame at the given carrier. Returns true if it fired. */
    fun send(pattern: IntArray, carrierHz: Int): Boolean {
        buzz()
        val emitter = ir
        if (emitter == null) { lastStatus = "no IR service on this phone"; return false }
        if (!emitter.hasIrEmitter()) { lastStatus = "hasIrEmitter() = false"; return false }
        if (pattern.isEmpty()) { lastStatus = "empty pattern"; return false }
        return try {
            emitter.transmit(carrierHz, pattern)
            lastStatus = "sent ${pattern.size} pulses @ ${carrierHz}Hz ✓"
            true
        } catch (e: Exception) {
            lastStatus = "transmit error @ ${carrierHz}Hz: ${e.javaClass.simpleName} ${e.message}"
            false
        }
    }

    /** Fire an encoded IrSignal (protocol/Pronto/raw) at its own carrier. */
    fun sendSignal(sig: IrSignal): Boolean = send(sig.pattern, sig.carrierHz)

    /**
     * Representative carrier frequencies the blaster reports supporting, for
     * the "shotgun" mode that fires a pattern at every one to guarantee a hit.
     * Falls back to the common IR band if the phone reports nothing.
     */
    fun supportedCarrierList(): List<Int> {
        val fallback = listOf(38000, 36000, 40000, 33000, 56000)
        val emitter = ir ?: return fallback
        return try {
            val ranges = emitter.carrierFrequencies ?: return fallback
            val out = LinkedHashSet<Int>()
            for (r in ranges) {
                out.add(r.minFrequency)
                out.add(r.maxFrequency)
                out.add((r.minFrequency + r.maxFrequency) / 2)
                for (f in intArrayOf(33000, 36000, 38000, 40000, 56000)) {
                    if (f in r.minFrequency..r.maxFrequency) out.add(f)
                }
            }
            if (out.isEmpty()) fallback else out.toList()
        } catch (e: Exception) {
            fallback
        }
    }

    private fun buzz() {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(18, 160))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(18)
            }
        } catch (ignored: Exception) {
        }
    }
}
