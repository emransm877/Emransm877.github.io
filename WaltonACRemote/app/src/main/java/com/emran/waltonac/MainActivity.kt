package com.emran.waltonac

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
    private lateinit var btnTurbo: Button
    private lateinit var btnLight: Button
    private lateinit var btnSleep: Button

    private val vSwingButtons = HashMap<VerticalSwing, Button>()
    private val leftZoneButtons = HashMap<ZoneAim, Button>()
    private val rightZoneButtons = HashMap<ZoneAim, Button>()

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
        btnTurbo = findViewById(R.id.btnTurbo)
        btnLight = findViewById(R.id.btnLight)
        btnSleep = findViewById(R.id.btnSleep)

        btnPower.setOnClickListener { state.power = !state.power; commit() }
        findViewById<Button>(R.id.btnSync).setOnClickListener { sync() }

        findViewById<Button>(R.id.btnTempMinus).setOnClickListener {
            if (state.temp > AcState.MIN_TEMP) { state.temp--; commit() }
        }
        findViewById<Button>(R.id.btnTempPlus).setOnClickListener {
            if (state.temp < AcState.MAX_TEMP) { state.temp++; commit() }
        }

        btnMode.setOnClickListener { state.mode = state.mode.next(); commit() }
        btnFan.setOnClickListener { state.fan = state.fan.next(); commit() }
        btnTurbo.setOnClickListener { state.turbo = !state.turbo; commit() }
        btnLight.setOnClickListener { state.light = !state.light; commit() }
        btnSleep.setOnClickListener { state.sleep = !state.sleep; commit() }

        // Room temperature is a local display value (IR is one-way, the AC
        // cannot report back) — adjust it to match your room thermometer.
        findViewById<Button>(R.id.btnRoomMinus).setOnClickListener {
            state.roomTemp--; refreshUi(); state.save(this)
        }
        findViewById<Button>(R.id.btnRoomPlus).setOnClickListener {
            state.roomTemp++; refreshUi(); state.save(this)
        }

        buildVSwingGrid(findViewById(R.id.vSwingContainer))
        buildZoneColumn(findViewById(R.id.leftZoneContainer), leftZoneButtons) { aim ->
            state.leftZone = aim; commit()
        }
        buildZoneColumn(findViewById(R.id.rightZoneContainer), rightZoneButtons) { aim ->
            state.rightZone = aim; commit()
        }

        refreshUi()

        if (!ir.available) {
            Toast.makeText(
                this,
                getString(R.string.no_ir_warning),
                Toast.LENGTH_LONG
            ).show()
            warnedNoIr = true
        }
    }

    // ------------------------------------------------------------- UI builders

    private fun buildVSwingGrid(container: LinearLayout) {
        val rows = listOf(
            listOf(VerticalSwing.FULL_SWING, VerticalSwing.SWEEP_UPPER,
                VerticalSwing.SWEEP_MIDDLE, VerticalSwing.SWEEP_LOWER),
            listOf(VerticalSwing.TOP, VerticalSwing.UPPER_MID, VerticalSwing.MIDDLE,
                VerticalSwing.LOWER_MID, VerticalSwing.BOTTOM)
        )
        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (option in row) {
                val b = makeOptionButton(option.label)
                b.setOnClickListener { state.vSwing = option; commit() }
                vSwingButtons[option] = b
                rowLayout.addView(b)
            }
            container.addView(rowLayout)
        }
    }

    private fun buildZoneColumn(
        container: LinearLayout,
        map: HashMap<ZoneAim, Button>,
        onPick: (ZoneAim) -> Unit
    ) {
        for (aim in ZoneAim.entries) {
            val b = makeOptionButton(aim.label)
            b.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            b.setOnClickListener { onPick(aim) }
            map[aim] = b
            container.addView(b)
        }
    }

    private fun makeOptionButton(label: String): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(10), dp(4), dp(10))
        setBackgroundResource(R.drawable.btn_option)
        setTextColor(resources.getColorStateList(R.color.option_text))
        layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        stateListAnimator = null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // --------------------------------------------------------------- actions

    /** Any change = transmit full state + persist + animate. */
    private fun commit() {
        val (_, exact) = state.resolveHorizontal()
        transmit()
        state.save(this)
        refreshUi()
        if (!exact) {
            Toast.makeText(this, getString(R.string.approx_warning), Toast.LENGTH_SHORT).show()
        }
    }

    /** SYNC: rebroadcast the entire remembered state so AC == display. */
    private fun sync() {
        transmit()
        display.flashSync()
        Toast.makeText(this, getString(R.string.synced_toast), Toast.LENGTH_SHORT).show()
    }

    private fun transmit() {
        val ok = ir.send(state)
        if (!ok && !warnedNoIr) {
            Toast.makeText(this, getString(R.string.no_ir_warning), Toast.LENGTH_LONG).show()
            warnedNoIr = true
        }
    }

    // ------------------------------------------------------------------ state

    private fun refreshUi() {
        val (_, exact) = state.resolveHorizontal()
        display.update(state, exact)

        txtTemp.text = getString(R.string.temp_format, state.temp)
        txtRoom.text = getString(R.string.temp_format, state.roomTemp)

        btnPower.text = if (state.power) getString(R.string.power_on)
        else getString(R.string.power_off)
        btnPower.isSelected = state.power
        btnMode.text = getString(R.string.mode_format, state.mode.label)
        btnFan.text = getString(R.string.fan_format, state.fan.label)
        btnTurbo.isSelected = state.turbo
        btnLight.isSelected = state.light
        btnSleep.isSelected = state.sleep

        for ((option, b) in vSwingButtons) b.isSelected = state.vSwing == option
        for ((aim, b) in leftZoneButtons) b.isSelected = state.leftZone == aim
        for ((aim, b) in rightZoneButtons) b.isSelected = state.rightZone == aim
    }
}
