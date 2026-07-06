package com.emran.waltonac

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Sends real Walton IR frames through the phone's IR blaster (the Redmi K90
 * exposes it via the standard ConsumerIrManager API). Frames are the exact
 * mark/space patterns captured from the official Walton remote, transmitted on
 * the same 76 kHz carrier the official app uses.
 */
class IrTransmitter(context: Context) {

    private val ir: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    val available: Boolean
        get() = ir?.hasIrEmitter() == true

    /** Fire a raw Walton frame. Returns true if the blaster actually fired. */
    fun send(pattern: IntArray): Boolean {
        buzz()
        val emitter = ir ?: return false
        if (!emitter.hasIrEmitter()) return false
        if (pattern.isEmpty()) return false
        return try {
            emitter.transmit(WaltonCodes.CARRIER_HZ, pattern)
            true
        } catch (e: Exception) {
            false
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
