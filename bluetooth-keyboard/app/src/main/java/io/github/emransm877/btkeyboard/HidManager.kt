package io.github.emransm877.btkeyboard

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * Singleton that owns the BluetoothHidDevice proxy, registers this phone as a
 * Bluetooth HID keyboard, manages the connection to the TV box, and sends
 * keystroke reports.
 *
 * All permission checks are done by the caller (MainActivity) before invoking
 * Bluetooth operations, hence the @SuppressLint("MissingPermission").
 */
@SuppressLint("MissingPermission")
object HidManager {

    private const val TAG = "HidManager"
    private const val REPORT_ID = 1

    /** Standard HID report descriptor for a boot-compatible keyboard. */
    private val REPORT_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),       // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), REPORT_ID.toByte(),  //   Report ID (1)
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),       //   Usage Minimum (224) - modifiers
        0x29.toByte(), 0xE7.toByte(),       //   Usage Maximum (231)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x95.toByte(), 0x08.toByte(),       //   Report Count (8)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data, Variable, Absolute)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1) - reserved byte
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x81.toByte(), 0x01.toByte(),       //   Input (Constant)
        0x95.toByte(), 0x05.toByte(),       //   Report Count (5) - LEDs
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x05.toByte(), 0x08.toByte(),       //   Usage Page (LEDs)
        0x19.toByte(), 0x01.toByte(),       //   Usage Minimum (1)
        0x29.toByte(), 0x05.toByte(),       //   Usage Maximum (5)
        0x91.toByte(), 0x02.toByte(),       //   Output (Data, Variable, Absolute)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1) - LED padding
        0x75.toByte(), 0x03.toByte(),       //   Report Size (3)
        0x91.toByte(), 0x01.toByte(),       //   Output (Constant)
        0x95.toByte(), 0x06.toByte(),       //   Report Count (6) - key array
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),       //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(),       //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),       //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(),       //   Input (Data, Array)
        0xC0.toByte()                       // End Collection
    )

    /** Delay between key press and release, and between characters. */
    private const val KEY_DELAY_MS = 12L

    interface Listener {
        fun onRegistrationChanged(registered: Boolean)
        fun onConnectionChanged(device: BluetoothDevice?, state: Int)
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val typingExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var hidDevice: BluetoothHidDevice? = null
    @Volatile var isRegistered = false
        private set
    @Volatile var connectedDevice: BluetoothDevice? = null
        private set
    @Volatile private var proxyRequested = false

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Acquire the HID_DEVICE profile proxy and register our keyboard app. */
    fun start(context: Context) {
        if (hidDevice != null || proxyRequested) {
            // Already started; re-notify listeners with the current state.
            notifyRegistration()
            notifyConnection(connectedDevice,
                if (connectedDevice != null) BluetoothProfile.STATE_CONNECTED
                else BluetoothProfile.STATE_DISCONNECTED)
            return
        }
        val adapter = adapter(context) ?: return
        proxyRequested = true
        val ok = adapter.getProfileProxy(
            context.applicationContext,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.HID_DEVICE) return
                    hidDevice = proxy as BluetoothHidDevice
                    registerApp(context.applicationContext)
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile != BluetoothProfile.HID_DEVICE) return
                    hidDevice = null
                    isRegistered = false
                    proxyRequested = false
                    connectedDevice = null
                    notifyRegistration()
                    notifyConnection(null, BluetoothProfile.STATE_DISCONNECTED)
                }
            },
            BluetoothProfile.HID_DEVICE
        )
        if (!ok) {
            proxyRequested = false
            Log.e(TAG, "getProfileProxy(HID_DEVICE) failed")
        }
    }

    private fun registerApp(context: Context) {
        val hid = hidDevice ?: return
        if (isRegistered) return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            context.getString(R.string.hid_device_name),
            context.getString(R.string.hid_device_description),
            "Emransm877",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            REPORT_DESCRIPTOR
        )
        val ok = hid.registerApp(sdp, null, null, callbackExecutor, object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                Log.i(TAG, "onAppStatusChanged registered=$registered")
                isRegistered = registered
                notifyRegistration()
            }

            override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                Log.i(TAG, "onConnectionStateChanged device=${device.address} state=$state")
                connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
                notifyConnection(device, state)
            }
        })
        if (!ok) Log.e(TAG, "registerApp failed")
    }

    fun connect(device: BluetoothDevice): Boolean =
        hidDevice?.connect(device) ?: false

    fun disconnect(): Boolean {
        val device = connectedDevice ?: return false
        return hidDevice?.disconnect(device) ?: false
    }

    /** Unregister and release the profile proxy. */
    fun stop(context: Context) {
        val hid = hidDevice
        if (hid != null) {
            try {
                if (isRegistered) hid.unregisterApp()
            } catch (e: Exception) {
                Log.w(TAG, "unregisterApp failed", e)
            }
            adapter(context)?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
        hidDevice = null
        isRegistered = false
        proxyRequested = false
        connectedDevice = null
        notifyRegistration()
        notifyConnection(null, BluetoothProfile.STATE_DISCONNECTED)
    }

    // ---------------------------------------------------------------------
    // Sending keys
    // ---------------------------------------------------------------------

    private fun sendReport(modifier: Byte, keyCode: Int): Boolean {
        val hid = hidDevice ?: return false
        val device = connectedDevice ?: return false
        val report = byteArrayOf(modifier, 0, keyCode.toByte(), 0, 0, 0, 0, 0)
        return hid.sendReport(device, REPORT_ID, report)
    }

    /** Press and release a single key synchronously (call off the main thread). */
    private fun tapKey(modifier: Byte, keyCode: Int) {
        sendReport(modifier, keyCode)
        Thread.sleep(KEY_DELAY_MS)
        sendReport(KeyMap.MOD_NONE, 0)
        Thread.sleep(KEY_DELAY_MS)
    }

    /** Queue a single special key (Enter, arrows, etc.). */
    fun sendKey(keyCode: Int, modifier: Byte = KeyMap.MOD_NONE) {
        typingExecutor.execute { tapKey(modifier, keyCode) }
    }

    /**
     * Queue a string to be typed character by character.
     * @param onDone called (on the typing thread) with the count of characters
     *               that could not be mapped to a key.
     */
    fun typeText(text: String, onDone: ((skipped: Int) -> Unit)? = null) {
        typingExecutor.execute {
            var skipped = 0
            for (c in text) {
                if (connectedDevice == null) break
                val ch = if (c == '\r') '\n' else c
                val key = KeyMap.forChar(ch)
                if (key == null) {
                    skipped++
                    continue
                }
                tapKey(if (key.shift) KeyMap.MOD_LEFT_SHIFT else KeyMap.MOD_NONE, key.usage)
            }
            onDone?.invoke(skipped)
        }
    }

    // ---------------------------------------------------------------------

    private fun notifyRegistration() {
        for (l in listeners) l.onRegistrationChanged(isRegistered)
    }

    private fun notifyConnection(device: BluetoothDevice?, state: Int) {
        for (l in listeners) l.onConnectionChanged(device, state)
    }
}
