package com.emran.waltonac

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Thin wrapper around the phone's IR blaster (Redmi/Xiaomi phones like the
 * Redmi K90 expose it through the standard ConsumerIrManager API).
 */
class IrTransmitter(context: Context) {

    private val ir: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    val available: Boolean
        get() = ir?.hasIrEmitter() == true

    /** Send the full AC state using the state's selected protocol. */
    fun send(state: AcState): Boolean = sendFrame(AcEncoder.encode(state))

    /** Send a raw pre-built frame (e.g. a swing-toggle command). */
    fun sendFrame(frame: IrFrame): Boolean {
        buzz()
        val emitter = ir ?: return false
        if (!emitter.hasIrEmitter()) return false
        return try {
            emitter.transmit(frame.carrierHz, frame.pattern)
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
