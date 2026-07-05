package com.emran.waltonac

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The "smart display" of the remote:
 *  - 7-segment set-temperature + room temperature readout
 *  - a live front view of the AC where the left and right vane groups and the
 *    up/down flap animate in real time to exactly what was commanded
 *    (including independent left/right directions and sweep styles)
 *  - airflow / cooling particle animation coloured by mode
 *  - a sync pulse so you can visually confirm the state was broadcast
 */
class AcDisplayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var state: AcState = AcState()
    private var hardwareExact = true

    // Animated (current) angles, eased toward targets each frame
    private var flapAngle = 45f            // degrees below horizontal
    private var leftVaneAngle = 0f         // degrees, negative = left
    private var rightVaneAngle = 0f
    private var lastFrameNanos = 0L
    private var timeSec = 0f
    private var syncFlash = 0f             // 1 -> 0 after SYNC pressed

    // Airflow particles
    private class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 1f; var size = 4f; var fromLeft = true
    }

    private val particles = ArrayList<Particle>(120)
    private val rng = Random(42)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val path = Path()

    fun update(newState: AcState, exact: Boolean) {
        state = newState.copy()
        hardwareExact = exact
        postInvalidateOnAnimation()
    }

    fun flashSync() {
        syncFlash = 1f
        postInvalidateOnAnimation()
    }

    // ------------------------------------------------------------------ math

    private fun vFlapTarget(t: Float): Float = when (state.vSwing) {
        VerticalSwing.TOP -> 15f
        VerticalSwing.UPPER_MID -> 30f
        VerticalSwing.MIDDLE -> 45f
        VerticalSwing.LOWER_MID -> 60f
        VerticalSwing.BOTTOM -> 75f
        VerticalSwing.FULL_SWING -> sweep(t, 15f, 75f)
        VerticalSwing.SWEEP_UPPER -> sweep(t, 15f, 40f)
        VerticalSwing.SWEEP_MIDDLE -> sweep(t, 35f, 60f)
        VerticalSwing.SWEEP_LOWER -> sweep(t, 50f, 75f)
    }

    private fun zoneTarget(zone: ZoneAim, t: Float, phase: Float): Float = when (zone) {
        ZoneAim.LEFT -> -32f
        ZoneAim.CENTER -> 0f
        ZoneAim.RIGHT -> 32f
        ZoneAim.SWING -> sweep(t + phase, -32f, 32f)
    }

    private fun sweep(t: Float, lo: Float, hi: Float): Float {
        val mid = (lo + hi) / 2f
        val amp = (hi - lo) / 2f
        return mid + amp * sin(t * 1.4f).toFloat()
    }

    private fun modeColor(): Int = when {
        !state.power -> Color.rgb(70, 80, 95)
        state.mode == AcMode.COOL -> Color.rgb(80, 200, 255)
        state.mode == AcMode.HEAT -> Color.rgb(255, 150, 70)
        state.mode == AcMode.DRY -> Color.rgb(120, 230, 190)
        state.mode == AcMode.FAN -> Color.rgb(200, 210, 230)
        else -> Color.rgb(170, 160, 255) // AUTO
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.016f
        else ((now - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = now
        timeSec += dt

        // Ease current angles toward targets
        val ease = (dt * 5f).coerceAtMost(1f)
        flapAngle += (vFlapTarget(timeSec) - flapAngle) * ease
        leftVaneAngle += (zoneTarget(state.leftZone, timeSec, 0f) - leftVaneAngle) * ease
        rightVaneAngle += (zoneTarget(state.rightZone, timeSec, 0.9f) - rightVaneAngle) * ease
        if (syncFlash > 0f) syncFlash = (syncFlash - dt * 0.8f).coerceAtLeast(0f)

        val w = width.toFloat()
        val h = height.toFloat()
        val accent = modeColor()

        // Panel background
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.rgb(16, 22, 34), Color.rgb(8, 11, 18), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 28f, 28f, paint)
        paint.shader = null

        // Subtle ambient glow behind the AC when running
        if (state.power) {
            paint.shader = RadialGradient(
                w / 2f, h * 0.30f, w * 0.55f,
                withAlpha(accent, 46), withAlpha(accent, 0), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }

        drawAcUnit(canvas, w, h, accent)
        drawAirflow(canvas, w, h, accent, dt)
        drawReadouts(canvas, w, h, accent)
        drawSyncPulse(canvas, w, h, accent)

        if (state.power || syncFlash > 0f ||
            state.vSwing.isSweep || state.leftZone == ZoneAim.SWING ||
            state.rightZone == ZoneAim.SWING ||
            kotlin.math.abs(flapAngle - vFlapTarget(timeSec)) > 0.5f
        ) {
            postInvalidateOnAnimation()
        } else {
            // Keep a gentle idle refresh so eased angles settle visually
            postInvalidateDelayed(120)
        }
    }

    /** Front view of the indoor unit with animated flap + vane groups. */
    private fun drawAcUnit(canvas: Canvas, w: Float, h: Float, accent: Int) {
        val unitLeft = w * 0.08f
        val unitRight = w * 0.92f
        val unitTop = h * 0.10f
        val unitBottom = h * 0.34f
        val body = RectF(unitLeft, unitTop, unitRight, unitBottom)

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, unitTop, 0f, unitBottom,
            Color.rgb(235, 240, 247), Color.rgb(196, 205, 218), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(body, 26f, 26f, paint)
        paint.shader = null

        // Brand strip
        textPaint.color = Color.rgb(90, 100, 118)
        textPaint.textSize = h * 0.028f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("WALTON  INVERTER", unitLeft + 24f, unitTop + h * 0.045f, textPaint)

        // Power LED
        paint.color = if (state.power) accent else Color.rgb(120, 128, 140)
        canvas.drawCircle(unitRight - 30f, unitTop + h * 0.035f, 7f, paint)

        // Vent opening
        val ventTop = unitBottom - h * 0.075f
        val vent = RectF(unitLeft + 18f, ventTop, unitRight - 18f, unitBottom - 8f)
        paint.color = Color.rgb(24, 30, 42)
        canvas.drawRoundRect(vent, 12f, 12f, paint)

        val ventMidX = (vent.left + vent.right) / 2f

        // --- Vertical vane groups (left half / right half), drawn as slanted
        // louvers whose slant follows the commanded horizontal angle.
        strokePaint.strokeWidth = 5f
        val vaneH = vent.height() - 10f
        val groups = listOf(
            Triple(vent.left + 10f, ventMidX - 8f, leftVaneAngle),
            Triple(ventMidX + 8f, vent.right - 10f, rightVaneAngle)
        )
        for ((gLeft, gRight, angle) in groups) {
            val n = 6
            val lean = sin(Math.toRadians(angle.toDouble())).toFloat() * vaneH * 0.55f
            strokePaint.color = if (state.power) withAlpha(accent, 235)
            else Color.rgb(90, 100, 118)
            for (i in 0 until n) {
                val x = gLeft + (gRight - gLeft) * i / (n - 1f)
                canvas.drawLine(
                    x, vent.top + 5f,
                    x + lean, vent.top + 5f + vaneH,
                    strokePaint
                )
            }
        }

        // Center divider between the two independent groups
        strokePaint.color = Color.rgb(60, 70, 88)
        strokePaint.strokeWidth = 3f
        canvas.drawLine(ventMidX, vent.top + 4f, ventMidX, vent.bottom - 4f, strokePaint)

        // --- Up/down flap: a bar hanging from the vent whose drop follows the
        // vertical angle (15° = nearly closed/far throw, 75° = straight down).
        val flapLen = h * 0.075f
        val rad = Math.toRadians(flapAngle.toDouble())
        val drop = (sin(rad) * flapLen).toFloat()
        val forward = (cos(rad) * flapLen * 0.35f).toFloat()
        paint.color = if (state.power) Color.rgb(210, 218, 230) else Color.rgb(150, 158, 172)
        path.reset()
        path.moveTo(vent.left + 6f, vent.bottom)
        path.lineTo(vent.right - 6f, vent.bottom)
        path.lineTo(vent.right - 6f - forward, vent.bottom + drop)
        path.lineTo(vent.left + 6f + forward, vent.bottom + drop)
        path.close()
        canvas.drawPath(path, paint)
        strokePaint.color = withAlpha(accent, if (state.power) 200 else 60)
        strokePaint.strokeWidth = 3f
        canvas.drawLine(
            vent.left + 6f + forward, vent.bottom + drop,
            vent.right - 6f - forward, vent.bottom + drop, strokePaint
        )

        // Labels under each half so you always know which side is which
        textPaint.textSize = h * 0.026f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.rgb(110, 125, 150)
        val labelY = unitBottom + h * 0.115f
        canvas.drawText(
            "L: " + state.leftZone.label,
            (vent.left + ventMidX) / 2f, labelY, textPaint
        )
        canvas.drawText(
            "R: " + state.rightZone.label,
            (ventMidX + vent.right) / 2f, labelY, textPaint
        )
        canvas.drawText("V: " + state.vSwing.label, ventMidX, labelY + h * 0.035f, textPaint)

        if (!hardwareExact) {
            textPaint.color = Color.rgb(255, 190, 90)
            canvas.drawText("≈ closest supported pattern", ventMidX, labelY + h * 0.070f, textPaint)
        }
    }

    /** Airflow / cooling particle animation. */
    private fun drawAirflow(canvas: Canvas, w: Float, h: Float, accent: Int, dt: Float) {
        val ventY = h * 0.34f
        val ventMidX = w / 2f
        val fanBoost = when {
            state.turbo -> 1.8f
            else -> 1f + 0.35f * state.fan.protocolValue
        }

        if (state.power) {
            // spawn
            val spawnCount = (2 * fanBoost).toInt().coerceAtLeast(1)
            repeat(spawnCount) {
                if (particles.size < 110) {
                    val p = Particle()
                    p.fromLeft = rng.nextBoolean()
                    val gAngle = if (p.fromLeft) leftVaneAngle else rightVaneAngle
                    val baseX = if (p.fromLeft)
                        w * 0.12f + rng.nextFloat() * (ventMidX - w * 0.14f)
                    else ventMidX + rng.nextFloat() * (w * 0.88f - ventMidX - w * 0.04f)
                    p.x = baseX
                    p.y = ventY + rng.nextFloat() * 8f
                    val vr = Math.toRadians(flapAngle.toDouble())
                    val hr = Math.toRadians(gAngle.toDouble())
                    val speed = (h * 0.16f) * fanBoost * (0.8f + rng.nextFloat() * 0.4f)
                    p.vx = (sin(hr) * speed * 0.8f).toFloat()
                    p.vy = (sin(vr) * speed).toFloat().coerceAtLeast(h * 0.03f)
                    p.maxLife = 1.4f + rng.nextFloat() * 0.8f
                    p.life = p.maxLife
                    p.size = 3f + rng.nextFloat() * 5f
                    particles.add(p)
                }
            }
        }

        // update + draw
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f || p.y > h * 0.62f) { it.remove(); continue }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx += sin((timeSec * 3f + p.y * 0.02f).toDouble()).toFloat() * 6f * dt
            val a = (150 * (p.life / p.maxLife)).toInt()
            if (state.mode == AcMode.COOL && p.size > 6.4f) {
                drawSnowflake(canvas, p.x, p.y, p.size * 1.5f, withAlpha(accent, a))
            } else {
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(accent, a)
                canvas.drawCircle(p.x, p.y, p.size * 0.6f, paint)
            }
        }
    }

    private fun drawSnowflake(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        strokePaint.color = color
        strokePaint.strokeWidth = 2f
        for (i in 0 until 3) {
            val a = Math.toRadians((i * 60 + timeSec * 40).toDouble())
            val dx = (cos(a) * r).toFloat()
            val dy = (sin(a) * r).toFloat()
            canvas.drawLine(x - dx, y - dy, x + dx, y + dy, strokePaint)
        }
    }

    /** Digital readouts: big 7-segment set temp, room temp, mode, fan bars. */
    private fun drawReadouts(canvas: Canvas, w: Float, h: Float, accent: Int) {
        val panelTop = h * 0.64f
        val panel = RectF(w * 0.05f, panelTop, w * 0.95f, h * 0.96f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(10, 14, 22)
        canvas.drawRoundRect(panel, 20f, 20f, paint)
        strokePaint.color = withAlpha(accent, 90)
        strokePaint.strokeWidth = 2.5f
        canvas.drawRoundRect(panel, 20f, 20f, strokePaint)

        val digitH = panel.height() * 0.52f
        val digitW = digitH * 0.52f
        val cx = panel.left + panel.width() * 0.30f
        val cy = panel.centerY() - panel.height() * 0.06f

        val setColor = if (state.power) accent else Color.rgb(60, 70, 88)
        val tens = state.temp / 10
        val ones = state.temp % 10
        drawSevenSeg(canvas, cx - digitW - 10f, cy - digitH / 2f, digitW, digitH, tens, setColor)
        drawSevenSeg(canvas, cx + 10f, cy - digitH / 2f, digitW, digitH, ones, setColor)

        textPaint.color = setColor
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = digitH * 0.30f
        canvas.drawText("°C", cx + digitW + 26f, cy - digitH / 2f + digitH * 0.28f, textPaint)
        textPaint.textSize = panel.height() * 0.11f
        textPaint.color = Color.rgb(110, 125, 150)
        canvas.drawText("SET", cx - digitW - 10f, panel.bottom - panel.height() * 0.10f, textPaint)

        // Room temperature block
        val rx = panel.left + panel.width() * 0.62f
        textPaint.color = Color.rgb(110, 125, 150)
        textPaint.textSize = panel.height() * 0.11f
        canvas.drawText("ROOM", rx, panel.top + panel.height() * 0.22f, textPaint)
        textPaint.color = if (state.power) Color.rgb(240, 245, 252) else Color.rgb(90, 100, 118)
        textPaint.textSize = panel.height() * 0.30f
        canvas.drawText("${state.roomTemp}°", rx, panel.top + panel.height() * 0.52f, textPaint)

        // Mode + fan bars
        textPaint.color = accent
        textPaint.textSize = panel.height() * 0.13f
        val modeLabel = StringBuilder(state.mode.label)
        if (state.turbo) modeLabel.append("  TURBO")
        if (state.sleep) modeLabel.append("  SLEEP")
        canvas.drawText(modeLabel.toString(), rx, panel.top + panel.height() * 0.72f, textPaint)

        val bars = if (state.turbo) 4 else state.fan.protocolValue
        val barY = panel.bottom - panel.height() * 0.14f
        for (i in 0 until 4) {
            paint.color = if (state.power && (bars == 0 || i < bars))
                withAlpha(accent, if (bars == 0) 110 else 255)
            else Color.rgb(45, 55, 72)
            val bh = 8f + i * 7f
            canvas.drawRoundRect(
                RectF(rx + i * 26f, barY - bh, rx + i * 26f + 16f, barY), 4f, 4f, paint
            )
        }
        textPaint.color = Color.rgb(110, 125, 150)
        textPaint.textSize = panel.height() * 0.10f
        canvas.drawText(
            "FAN " + (if (state.turbo) "MAX" else state.fan.label),
            rx + 118f, barY - 2f, textPaint
        )
    }

    /** Minimal 7-segment digit renderer. */
    private fun drawSevenSeg(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, digit: Int, color: Int
    ) {
        //      a
        //    f   b
        //      g
        //    e   c
        //      d
        val on = intArrayOf(
            0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011,
            0b1011011, 0b1011111, 0b1110000, 0b1111111, 0b1111011
        )[digit.coerceIn(0, 9)]
        segPaint.strokeWidth = w * 0.16f
        val m = w * 0.12f
        val midY = y + h / 2f
        fun seg(bit: Int, x1: Float, y1: Float, x2: Float, y2: Float) {
            segPaint.color = if ((on shr bit) and 1 == 1) color
            else withAlpha(color, 26)
            canvas.drawLine(x1, y1, x2, y2, segPaint)
        }
        seg(6, x + m, y, x + w - m, y)                       // a
        seg(5, x + w, y + m, x + w, midY - m)                // b
        seg(4, x + w, midY + m, x + w, y + h - m)            // c
        seg(3, x + m, y + h, x + w - m, y + h)               // d
        seg(2, x, midY + m, x, y + h - m)                    // e
        seg(1, x, y + m, x, midY - m)                        // f
        seg(0, x + m, midY, x + w - m, midY)                 // g
    }

    /** Expanding ring + label when SYNC fires. */
    private fun drawSyncPulse(canvas: Canvas, w: Float, h: Float, accent: Int) {
        if (syncFlash <= 0f) return
        val progress = 1f - syncFlash
        strokePaint.color = withAlpha(accent, (200 * syncFlash).toInt())
        strokePaint.strokeWidth = 6f * syncFlash + 1f
        canvas.drawCircle(w / 2f, h * 0.22f, w * 0.12f + progress * w * 0.42f, strokePaint)
        textPaint.color = withAlpha(Color.WHITE, (255 * syncFlash).toInt())
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = h * 0.045f
        canvas.drawText("STATE SYNCED ⇄", w / 2f, h * 0.055f, textPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
