package io.github.emransm877.btkeyboard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), HidManager.Listener {

    private lateinit var statusText: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var refreshButton: Button
    private lateinit var connectButton: Button
    private lateinit var pairButton: Button
    private lateinit var inputText: EditText
    private lateinit var sendButton: Button
    private lateinit var pasteSendButton: Button
    private lateinit var clearButton: Button
    private lateinit var liveTypingSwitch: SwitchMaterial

    private var bondedDevices: List<BluetoothDevice> = emptyList()
    private var connected = false
    private var suppressWatcher = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.filterKeys { it.startsWith("android.permission.BLUETOOTH") }
                    .values.all { it }) {
                startHid()
            } else {
                statusText.text = getString(R.string.status_no_permission)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        deviceSpinner = findViewById(R.id.deviceSpinner)
        refreshButton = findViewById(R.id.refreshButton)
        connectButton = findViewById(R.id.connectButton)
        pairButton = findViewById(R.id.pairButton)
        inputText = findViewById(R.id.inputText)
        sendButton = findViewById(R.id.sendButton)
        pasteSendButton = findViewById(R.id.pasteSendButton)
        clearButton = findViewById(R.id.clearButton)
        liveTypingSwitch = findViewById(R.id.liveTypingSwitch)

        refreshButton.setOnClickListener { refreshDeviceList() }
        pairButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        connectButton.setOnClickListener { onConnectClicked() }
        sendButton.setOnClickListener { sendText(inputText.text.toString()) }
        pasteSendButton.setOnClickListener { pasteAndSend() }
        clearButton.setOnClickListener {
            suppressWatcher = true
            inputText.text.clear()
            suppressWatcher = false
        }

        bindSpecialKey(R.id.keyEnter, KeyMap.KEY_ENTER)
        bindSpecialKey(R.id.keyBackspace, KeyMap.KEY_BACKSPACE)
        bindSpecialKey(R.id.keyTab, KeyMap.KEY_TAB)
        bindSpecialKey(R.id.keyEsc, KeyMap.KEY_ESC)
        bindSpecialKey(R.id.keySpace, KeyMap.KEY_SPACE)
        bindSpecialKey(R.id.keyDelete, KeyMap.KEY_DELETE)
        bindSpecialKey(R.id.keyUp, KeyMap.KEY_UP)
        bindSpecialKey(R.id.keyDown, KeyMap.KEY_DOWN)
        bindSpecialKey(R.id.keyLeft, KeyMap.KEY_LEFT)
        bindSpecialKey(R.id.keyRight, KeyMap.KEY_RIGHT)
        bindSpecialKey(R.id.keyHome, KeyMap.KEY_HOME)
        bindSpecialKey(R.id.keyEnd, KeyMap.KEY_END)

        inputText.addTextChangedListener(liveTypingWatcher)

        HidManager.addListener(this)
        updateUi()
        ensurePermissionsThenStart()
    }

    override fun onDestroy() {
        HidManager.removeListener(this)
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Permissions / startup
    // ------------------------------------------------------------------

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_ADVERTISE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms.toTypedArray()
    }

    private fun ensurePermissionsThenStart() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startHid()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startHid() {
        val adapter = HidManager.adapter(this)
        if (adapter == null) {
            statusText.text = getString(R.string.status_no_bluetooth)
            return
        }
        if (!adapter.isEnabled) {
            statusText.text = getString(R.string.status_bluetooth_off)
            Toast.makeText(this, R.string.toast_enable_bluetooth, Toast.LENGTH_LONG).show()
            return
        }
        HidManager.start(this)
        refreshDeviceList()
    }

    // ------------------------------------------------------------------
    // Devices / connection
    // ------------------------------------------------------------------

    private fun refreshDeviceList() {
        val adapter = HidManager.adapter(this) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) return
        bondedDevices = adapter.bondedDevices?.toList() ?: emptyList()
        val names = if (bondedDevices.isEmpty()) {
            listOf(getString(R.string.no_paired_devices))
        } else {
            bondedDevices.map { it.name ?: it.address }
        }
        deviceSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, names
        )
    }

    private fun onConnectClicked() {
        if (connected) {
            HidManager.disconnect()
            return
        }
        if (!HidManager.isRegistered) {
            Toast.makeText(this, R.string.toast_not_registered, Toast.LENGTH_SHORT).show()
            startHid()
            return
        }
        val index = deviceSpinner.selectedItemPosition
        val device = bondedDevices.getOrNull(index)
        if (device == null) {
            Toast.makeText(this, R.string.toast_pair_first, Toast.LENGTH_LONG).show()
            return
        }
        statusText.text = getString(R.string.status_connecting, device.name ?: device.address)
        if (!HidManager.connect(device)) {
            Toast.makeText(this, R.string.toast_connect_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------
    // Sending text
    // ------------------------------------------------------------------

    private fun sendText(text: String) {
        if (!connected) {
            Toast.makeText(this, R.string.toast_not_connected, Toast.LENGTH_SHORT).show()
            return
        }
        if (text.isEmpty()) return
        setSendEnabled(false)
        HidManager.typeText(text) { skipped ->
            runOnUiThread {
                setSendEnabled(true)
                if (skipped > 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_skipped_chars, skipped),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun pasteAndSend() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        sendText(text)
    }

    private fun setSendEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
        pasteSendButton.isEnabled = enabled
    }

    private fun bindSpecialKey(viewId: Int, keyCode: Int) {
        findViewById<Button>(viewId).setOnClickListener {
            if (connected) {
                HidManager.sendKey(keyCode)
            } else {
                Toast.makeText(this, R.string.toast_not_connected, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Live typing: while the switch is on, every character typed into the text
     * field is forwarded to the TV immediately, and deletions send Backspace.
     * Works best when editing at the end of the text.
     */
    private val liveTypingWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (suppressWatcher || !liveTypingSwitch.isChecked || !connected || s == null) return
            repeat(before) { HidManager.sendKey(KeyMap.KEY_BACKSPACE) }
            if (count > 0) {
                HidManager.typeText(s.subSequence(start, start + count).toString())
            }
        }

        override fun afterTextChanged(s: Editable?) {}
    }

    // ------------------------------------------------------------------
    // HidManager.Listener
    // ------------------------------------------------------------------

    override fun onRegistrationChanged(registered: Boolean) {
        runOnUiThread { updateUi() }
    }

    override fun onConnectionChanged(device: BluetoothDevice?, state: Int) {
        runOnUiThread {
            connected = state == BluetoothProfile.STATE_CONNECTED
            if (connected && device != null) {
                val serviceIntent = Intent(this, KeyboardHidService::class.java)
                    .putExtra(KeyboardHidService.EXTRA_DEVICE_NAME, device.name ?: device.address)
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                stopService(Intent(this, KeyboardHidService::class.java))
            }
            updateUi()
        }
    }

    private fun updateUi() {
        val device = HidManager.connectedDevice
        connected = device != null
        statusText.text = when {
            connected -> getString(R.string.status_connected, device?.name ?: device?.address)
            HidManager.isRegistered -> getString(R.string.status_ready)
            else -> getString(R.string.status_starting)
        }
        connectButton.text = getString(
            if (connected) R.string.button_disconnect else R.string.button_connect
        )
        sendButton.isEnabled = connected
        pasteSendButton.isEnabled = connected
    }
}
