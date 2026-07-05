package io.github.emransm877.btkeyboard

/**
 * Maps characters to USB HID keyboard usage codes (US layout).
 * Each entry is a pair of (usage code, needs-shift).
 */
object KeyMap {

    const val MOD_NONE: Byte = 0x00
    const val MOD_LEFT_CTRL: Byte = 0x01
    const val MOD_LEFT_SHIFT: Byte = 0x02
    const val MOD_LEFT_ALT: Byte = 0x04

    // Non-printing keys
    const val KEY_ENTER = 0x28
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_CAPS_LOCK = 0x39
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52
    const val KEY_HOME = 0x4A
    const val KEY_END = 0x4D
    const val KEY_PAGE_UP = 0x4B
    const val KEY_PAGE_DOWN = 0x4E
    const val KEY_DELETE = 0x4C

    data class Key(val usage: Int, val shift: Boolean)

    private val map = HashMap<Char, Key>().apply {
        // Letters: a=0x04 .. z=0x1D
        for (c in 'a'..'z') put(c, Key(0x04 + (c - 'a'), false))
        for (c in 'A'..'Z') put(c, Key(0x04 + (c - 'A'), true))
        // Digits: 1..9 = 0x1E..0x26, 0 = 0x27
        put('1', Key(0x1E, false)); put('!', Key(0x1E, true))
        put('2', Key(0x1F, false)); put('@', Key(0x1F, true))
        put('3', Key(0x20, false)); put('#', Key(0x20, true))
        put('4', Key(0x21, false)); put('$', Key(0x21, true))
        put('5', Key(0x22, false)); put('%', Key(0x22, true))
        put('6', Key(0x23, false)); put('^', Key(0x23, true))
        put('7', Key(0x24, false)); put('&', Key(0x24, true))
        put('8', Key(0x25, false)); put('*', Key(0x25, true))
        put('9', Key(0x26, false)); put('(', Key(0x26, true))
        put('0', Key(0x27, false)); put(')', Key(0x27, true))
        // Whitespace / control
        put('\n', Key(KEY_ENTER, false))
        put('\t', Key(KEY_TAB, false))
        put(' ', Key(KEY_SPACE, false))
        // Punctuation
        put('-', Key(0x2D, false)); put('_', Key(0x2D, true))
        put('=', Key(0x2E, false)); put('+', Key(0x2E, true))
        put('[', Key(0x2F, false)); put('{', Key(0x2F, true))
        put(']', Key(0x30, false)); put('}', Key(0x30, true))
        put('\\', Key(0x31, false)); put('|', Key(0x31, true))
        put(';', Key(0x33, false)); put(':', Key(0x33, true))
        put('\'', Key(0x34, false)); put('"', Key(0x34, true))
        put('`', Key(0x35, false)); put('~', Key(0x35, true))
        put(',', Key(0x36, false)); put('<', Key(0x36, true))
        put('.', Key(0x37, false)); put('>', Key(0x37, true))
        put('/', Key(0x38, false)); put('?', Key(0x38, true))
    }

    fun forChar(c: Char): Key? = map[c]
}
