package com.emran.waltonac

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var state: AcState
    private lateinit var ir: IrTransmitter
    private lateinit var display: AcDisplayView

    private lateinit var txtTemp: TextView
    private lateinit var txtRoom: TextView
    private lateinit var btnPower: Button
    private lateinit var btnMode: Button
    private lateinit var btnFan: Button
    private lateinit var btnVSwing: Button
    private lateinit var btnHSwing: Button
    private lateinit var btnDisplay: Button
    private lateinit var btnTurbo: Button
    private lateinit var btnEco: Button
    private lateinit var btnHealth: Button
    private lateinit var btnCarrier: Button
    private lateinit var txtDiag: TextView

    // Carriers to offer: 76 kHz is what the official app sends; 38 kHz is the
    // standard many blasters actually support if 76 kHz is out of range.
    private val carriers = intArrayOf(38000, 76000, 56000, 33000, 40000)

    private val handler = Handler(Looper.getMainLooper())
    private var warnedNoIr = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        state = AcState.load(this)
        ir = IrTransmitter(this)

        display = findViewById(R.id.displayView)
        txtTemp = findViewById(R.id.txtTemp)
        txtRoom = findViewById(R.id.txtRoom)
        btnPower = findViewById(R.id.btnPower)
        btnMode = findViewById(R.id.btnMode)
        btnFan = findViewById(R.id.btnFan)
        btnVSwing = findViewById(R.id.btnVSwing)
        btnHSwing = findViewById(R.id.btnHSwing)
        btnDisplay = findViewById(R.id.btnDisplay)
        btnTurbo = findViewById(R.id.btnTurbo)
        btnEco = findViewById(R.id.btnEco)
        btnHealth = findViewById(R.id.btnHealth)
        btnCarrier = findViewById(R.id.btnCarrier)
        txtDiag = findViewById(R.id.txtDiag)

        btnCarrier.setOnClickListener {
            val idx = carriers.indexOf(state.carrierHz).let { if (it < 0) 0 else it }
            state.carrierHz = carriers[(idx + 1) % carriers.size]
            state.save(this)
            // Fire a POWER test at the new carrier so the user hears it immediately.
            fire(if (state.power) WaltonCodes.POWER_ON else WaltonCodes.POWER_OFF)
        }

        btnPower.setOnClickListener {
            state.power = !state.power
            if (state.power) {
                // Turning on: power on, then apply the app's mode + temperature
                // so the AC matches the screen instead of the snapshot's 25 °C.
                sendSequence(listOf(WaltonCodes.POWER_ON, state.mode.frame(), state.tempFrame()))
            } else {
                fire(WaltonCodes.POWER_OFF)
            }
        }
        findViewById<Button>(R.id.btnSync).setOnClickListener { sync() }

        findViewById<Button>(R.id.btnTempMinus).setOnClickListener {
            if (state.temp > AcState.MIN_TEMP) { state.temp--; fire(state.tempFrame()) }
        }
        findViewById<Button>(R.id.btnTempPlus).setOnClickListener {
            if (state.temp < AcState.MAX_TEMP) { state.temp++; fire(state.tempFrame()) }
        }

        // MODE and FAN frames are full-state snapshots that carry 25 °C, so
        // after switching, re-apply the real temperature so the AC doesn't jump.
        btnMode.setOnClickListener {
            state.mode = state.mode.next()
            fireThenReapplyTemp(state.mode.frame())
        }
        btnFan.setOnClickListener {
            state.fan = state.fan.next()
            fireThenReapplyTemp(WaltonCodes.FAN_STEP)
        }

        btnVSwing.setOnClickListener {
            state.vSwing = !state.vSwing
            fire(if (state.vSwing) WaltonCodes.VSWING_ON else WaltonCodes.VSWING_OFF)
        }
        btnHSwing.setOnClickListener {
            state.hSwing = !state.hSwing
            fire(if (state.hSwing) WaltonCodes.HSWING_ON else WaltonCodes.HSWING_OFF)
        }
        btnDisplay.setOnClickListener {
            state.display = !state.display
            fire(if (state.display) WaltonCodes.DISPLAY_ON else WaltonCodes.DISPLAY_OFF)
        }
        btnTurbo.setOnClickListener { state.turbo = !state.turbo; fire(WaltonCodes.TURBO) }
        btnEco.setOnClickListener { state.eco = !state.eco; fire(WaltonCodes.ECO) }
        btnHealth.setOnClickListener { state.health = !state.health; fire(WaltonCodes.HEALTH) }

        findViewById<Button>(R.id.btnRoomMinus).setOnClickListener {
            state.roomTemp--; refreshUi(); state.save(this)
        }
        findViewById<Button>(R.id.btnRoomPlus).setOnClickListener {
            state.roomTemp++; refreshUi(); state.save(this)
        }

        refreshUi()

        if (!ir.available) {
            Toast.makeText(this, getString(R.string.no_ir_warning), Toast.LENGTH_LONG).show()
            warnedNoIr = true
        }
    }

    /** Transmit a captured Walton frame, then persist + refresh the display. */
    private fun fire(pattern: IntArray) {
        ir.send(pattern, state.carrierHz)
        state.save(this)
        refreshUi()
    }

    /**
     * Send a full-snapshot frame (mode/fan) then re-apply the real temperature
     * ~450 ms later, so the AC keeps the user's temperature instead of the
     * 25 °C baked into the snapshot. Only re-applies while powered on.
     */
    private fun fireThenReapplyTemp(pattern: IntArray) {
        ir.send(pattern, state.carrierHz)
        state.save(this)
        refreshUi()
        if (state.power) {
            handler.postDelayed({ ir.send(state.tempFrame(), state.carrierHz); refreshUi() }, 450L)
        }
    }

    /**
     * Re-send power → mode → temperature so the AC matches the app. Each frame
     * is a full-state snapshot, and the mode snapshot carries 25 °C, so we send
     * the real temperature LAST and space the frames ~450 ms apart — otherwise
     * the AC's IR receiver drops the trailing frames and stays at the mode
     * frame's built-in 25 °C.
     */
    private fun sync() {
        val queue = ArrayList<IntArray>()
        queue.add(if (state.power) WaltonCodes.POWER_ON else WaltonCodes.POWER_OFF)
        if (state.power) {
            queue.add(state.mode.frame())
            queue.add(state.tempFrame())   // last so it overrides the mode's 25 °C
        }
        sendSequence(queue)
        display.flashSync()
        Toast.makeText(this, getString(R.string.synced_toast), Toast.LENGTH_SHORT).show()
    }

    /** Send several full-state frames ~450 ms apart so the AC decodes each one. */
    private fun sendSequence(frames: List<IntArray>) {
        val gap = 450L
        for ((i, frame) in frames.withIndex()) {
            handler.postDelayed({ ir.send(frame, state.carrierHz); refreshUi() }, i * gap)
        }
        state.save(this)
    }

    private fun refreshUi() {
        display.update(state)

        txtTemp.text = getString(R.string.temp_format, state.temp)
        txtRoom.text = getString(R.string.temp_format, state.roomTemp)

        btnPower.text = if (state.power) getString(R.string.power_on)
        else getString(R.string.power_off)
        btnPower.isSelected = state.power
        btnMode.text = getString(R.string.mode_format, state.mode.label)
        btnFan.text = getString(R.string.fan_format, state.fan.label)
        btnVSwing.isSelected = state.vSwing
        btnHSwing.isSelected = state.hSwing
        btnDisplay.isSelected = state.display
        btnTurbo.isSelected = state.turbo
        btnEco.isSelected = state.eco
        btnHealth.isSelected = state.health

        btnCarrier.text = getString(R.string.carrier_format, state.carrierHz / 1000)
        txtDiag.text = getString(
            R.string.diag_format,
            if (ir.available) "yes" else "NO",
            ir.supportedCarriers(),
            ir.lastStatus
        )
    }
}
