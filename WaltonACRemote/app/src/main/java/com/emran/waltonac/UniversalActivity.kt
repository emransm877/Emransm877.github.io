package com.emran.waltonac

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView

/**
 * Universal IR remote: fire any supported protocol, any Pronto-hex code, or a
 * raw pattern — at a chosen carrier or "shotgun" across every carrier the
 * blaster supports. Turns the phone into a professional universal IR remote.
 */
class UniversalActivity : Activity() {

    private lateinit var ir: IrTransmitter
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var spinner: Spinner
    private lateinit var addr: EditText
    private lateinit var cmd: EditText
    private lateinit var pronto: EditText
    private lateinit var raw: EditText
    private lateinit var rawCarrier: EditText
    private lateinit var shotgun: CheckBox
    private lateinit var status: TextView

    private val protocols = IrProtocols.names + listOf("Pronto HEX", "RAW pattern")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_universal)
        ir = IrTransmitter(this)

        spinner = findViewById(R.id.uniProtocol)
        addr = findViewById(R.id.uniAddr)
        cmd = findViewById(R.id.uniCmd)
        pronto = findViewById(R.id.uniPronto)
        raw = findViewById(R.id.uniRaw)
        rawCarrier = findViewById(R.id.uniRawCarrier)
        shotgun = findViewById(R.id.uniShotgun)
        status = findViewById(R.id.uniStatus)

        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, protocols
        )
        spinner.setSelection(0)

        findViewById<Button>(R.id.uniSend).setOnClickListener { send() }

        status.text = getString(
            R.string.uni_status_idle,
            if (ir.available) "yes" else "NO",
            ir.supportedCarrierList().joinToString(", ") { "${it / 1000}k" }
        )
    }

    private fun send() {
        val sig = buildSignal()
        if (sig == null) {
            status.text = getString(R.string.uni_bad_input)
            return
        }
        if (shotgun.isChecked) {
            val carriers = ir.supportedCarrierList()
            for ((i, c) in carriers.withIndex()) {
                handler.postDelayed({ ir.send(sig.pattern, c) }, i * 300L)
            }
            status.text = getString(
                R.string.uni_shotgun, sig.pattern.size,
                carriers.joinToString(", ") { "${it / 1000}k" }
            )
        } else {
            ir.sendSignal(sig)
            status.text = ir.lastStatus
        }
    }

    /** Build a signal from whichever input matches the selected protocol. */
    private fun buildSignal(): IrSignal? {
        val choice = protocols[spinner.selectedItemPosition]
        return when (choice) {
            "Pronto HEX" -> Pronto.parse(pronto.text.toString())
            "RAW pattern" -> parseRaw()
            else -> IrProtocols.encode(choice, parseHex(addr.text.toString()),
                parseHex(cmd.text.toString()))
        }
    }

    private fun parseRaw(): IrSignal? {
        val nums = raw.text.toString().trim()
            .split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        if (nums.size < 2) return null
        val pattern = try {
            nums.map { it.toInt() }.toIntArray()
        } catch (e: Exception) {
            return null
        }
        val carrier = parseHexOrDec(rawCarrier.text.toString()).let {
            if (it in 15000..500000) it else 38000
        }
        return IrSignal(carrier, pattern)
    }

    private fun parseHex(s: String): Int {
        val t = s.trim().removePrefix("0x").removePrefix("0X")
        return try { if (t.isEmpty()) 0 else t.toInt(16) } catch (e: Exception) { 0 }
    }

    private fun parseHexOrDec(s: String): Int {
        val t = s.trim()
        return try {
            if (t.startsWith("0x") || t.startsWith("0X")) t.substring(2).toInt(16)
            else t.toInt()
        } catch (e: Exception) { 0 }
    }
}
