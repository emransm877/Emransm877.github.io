package com.emran.waltonac

import android.app.Activity
import android.os.Bundle
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

        btnPower.setOnClickListener {
            state.power = !state.power
            fire(if (state.power) WaltonCodes.POWER_ON else WaltonCodes.POWER_OFF)
        }
        findViewById<Button>(R.id.btnSync).setOnClickListener { sync() }

        findViewById<Button>(R.id.btnTempMinus).setOnClickListener {
            if (state.temp > AcState.MIN_TEMP) { state.temp--; fire(state.tempFrame()) }
        }
        findViewById<Button>(R.id.btnTempPlus).setOnClickListener {
            if (state.temp < AcState.MAX_TEMP) { state.temp++; fire(state.tempFrame()) }
        }

        btnMode.setOnClickListener { state.mode = state.mode.next(); fire(state.mode.frame()) }
        btnFan.setOnClickListener { state.fan = state.fan.next(); fire(WaltonCodes.FAN_STEP) }

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
        val ok = ir.send(pattern)
        state.save(this)
        refreshUi()
        if (!ok && !warnedNoIr) {
            Toast.makeText(this, getString(R.string.no_ir_warning), Toast.LENGTH_LONG).show()
            warnedNoIr = true
        }
    }

    /** Re-send the current power + mode + temperature so the AC matches the app. */
    private fun sync() {
        ir.send(if (state.power) WaltonCodes.POWER_ON else WaltonCodes.POWER_OFF)
        ir.send(state.mode.frame())
        ir.send(state.tempFrame())
        display.flashSync()
        Toast.makeText(this, getString(R.string.synced_toast), Toast.LENGTH_SHORT).show()
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
    }
}
