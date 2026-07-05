package com.emran.waltonac

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val protocolButtons = HashMap<IrProtocol, Button>()

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
            state.leftZone = aim; commitSwing()
        }
        buildZoneColumn(findViewById(R.id.rightZoneContainer), rightZoneButtons) { aim ->
            state.rightZone = aim; commitSwing()
        }

        buildProtocolRow(findViewById(R.id.protocolContainer))
        findViewById<Button>(R.id.btnFinder).setOnClickListener { runFinder(0) }

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
                b.setOnClickListener { state.vSwing = option; commitSwing() }
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

    private fun buildProtocolRow(container: LinearLayout) {
        for (proto in IrProtocol.entries) {
            val b = makeOptionButton(proto.label)
            b.setOnClickListener {
                state.protocol = proto
                commit()
                Toast.makeText(
                    this, getString(R.string.protocol_set, proto.label), Toast.LENGTH_SHORT
                ).show()
            }
            protocolButtons[proto] = b
            container.addView(b)
        }
    }

    /**
     * "Find my AC": fires a test signal (power ON, cool, 24°C) with each
     * protocol in turn until the user confirms the AC responded.
     */
    private fun runFinder(step: Int) {
        val order = IrProtocol.entries
        val proto = order[step % order.size]
        val test = state.copy(
            power = true, mode = AcMode.COOL, temp = 24,
            fan = FanSpeed.AUTO, protocol = proto
        )
        ir.send(test)
        val round = step / order.size + 1
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.finder_title, step + 1))
            .setMessage(getString(R.string.finder_message, proto.label) +
                    if (round > 1) "\n\n" + getString(R.string.finder_retry_hint) else "")
            .setPositiveButton(R.string.finder_yes) { _, _ ->
                state.protocol = proto
                state.power = true; state.mode = AcMode.COOL
                state.temp = 24; state.fan = FanSpeed.AUTO
                state.save(this)
                refreshUi()
                display.flashSync()
                Toast.makeText(
                    this, getString(R.string.finder_locked, proto.label), Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton(R.string.finder_no) { _, _ -> runFinder(step + 1) }
            .setNeutralButton(R.string.finder_stop, null)
            .setCancelable(true)
            .show()
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

    /**
     * Swing change: transmit state, and on Midea-family protocols (where the
     * louvers are driven by a separate toggle command, not the state frame)
     * follow up with the swing-toggle frame after a short pause.
     */
    private fun commitSwing() {
        commit()
        AcEncoder.encodeSwingToggle(state)?.let { frame ->
            handler.postDelayed({ ir.sendFrame(frame) }, 350)
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
        // On Midea-family protocols the hardware only supports toggle/step
        // swing, so any targeted vane position is an approximation.
        val exact = if (state.protocol == IrProtocol.GREE) {
            state.resolveHorizontal().second
        } else {
            state.vSwing == VerticalSwing.FULL_SWING &&
                    state.leftZone == ZoneAim.SWING && state.rightZone == ZoneAim.SWING
        }
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
        for ((proto, b) in protocolButtons) b.isSelected = state.protocol == proto
    }
}
