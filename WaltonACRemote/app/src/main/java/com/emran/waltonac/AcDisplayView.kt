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
 * Animated "smart display": a live front view of the AC where the up/down flap
 * and left/right vanes sweep in real time when swing is on, a mode-coloured
 * cooling/airflow particle field, a 7-segment set-temperature readout, the room
 * temperature, and a sync pulse confirming a broadcast.
 */
class AcDisplayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var state: AcState = AcState()

    private var flapAngle = 45f
    private var vaneAngle = 0f
    private var lastFrameNanos = 0L
    private var timeSec = 0f
    private var syncFlash = 0f

    private class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 1f; var size = 4f
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

    fun update(newState: AcState) {
        state = newState.copy()
        postInvalidateOnAnimation()
    }

    fun flashSync() {
        syncFlash = 1f
        postInvalidateOnAnimation()
    }

    private fun vFlapTarget(t: Float): Float =
        if (state.vSwing) 45f + 30f * sin(t * 1.4f) else 45f

    private fun vaneTarget(t: Float): Float =
        if (state.hSwing) 30f * sin(t * 1.2f) else 0f

    private fun modeColor(): Int = when {
        !state.power -> Color.rgb(70, 80, 95)
        state.mode == AcMode.COOL -> Color.rgb(80, 200, 255)
        state.mode == AcMode.HEAT -> Color.rgb(255, 150, 70)
        state.mode == AcMode.DRY -> Color.rgb(120, 230, 190)
        state.mode == AcMode.FAN -> Color.rgb(200, 210, 230)
        else -> Color.rgb(170, 160, 255)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.016f
        else ((now - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = now
        timeSec += dt

        val ease = (dt * 5f).coerceAtMost(1f)
        flapAngle += (vFlapTarget(timeSec) - flapAngle) * ease
        vaneAngle += (vaneTarget(timeSec) - vaneAngle) * ease
        if (syncFlash > 0f) syncFlash = (syncFlash - dt * 0.8f).coerceAtLeast(0f)

        val w = width.toFloat()
        val h = height.toFloat()
        val accent = modeColor()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.rgb(16, 22, 34), Color.rgb(8, 11, 18), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 28f, 28f, paint)
        paint.shader = null

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

        if (state.power || syncFlash > 0f || state.vSwing || state.hSwing ||
            kotlin.math.abs(flapAngle - vFlapTarget(timeSec)) > 0.5f
        ) {
            postInvalidateOnAnimation()
        } else {
            postInvalidateDelayed(120)
        }
    }

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

        textPaint.color = Color.rgb(90, 100, 118)
        textPaint.textSize = h * 0.028f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("WALTON  INVERTER", unitLeft + 24f, unitTop + h * 0.045f, textPaint)

        paint.color = if (state.power) accent else Color.rgb(120, 128, 140)
        canvas.drawCircle(unitRight - 30f, unitTop + h * 0.035f, 7f, paint)

        val ventTop = unitBottom - h * 0.075f
        val vent = RectF(unitLeft + 18f, ventTop, unitRight - 18f, unitBottom - 8f)
        paint.color = Color.rgb(24, 30, 42)
        canvas.drawRoundRect(vent, 12f, 12f, paint)

        // Vertical vanes leaning with the horizontal-swing angle
        strokePaint.strokeWidth = 5f
        val vaneH = vent.height() - 10f
        val lean = sin(Math.toRadians(vaneAngle.toDouble())).toFloat() * vaneH * 0.55f
        strokePaint.color = if (state.power) withAlpha(accent, 235) else Color.rgb(90, 100, 118)
        val n = 12
        for (i in 0 until n) {
            val x = vent.left + 10f + (vent.width() - 20f) * i / (n - 1f)
            canvas.drawLine(x, vent.top + 5f, x + lean, vent.top + 5f + vaneH, strokePaint)
        }

        // Up/down flap
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

        textPaint.textSize = h * 0.026f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.rgb(110, 125, 150)
        val labelY = unitBottom + h * 0.11f
        canvas.drawText(
            "↕ " + (if (state.vSwing) "SWING" else "FIXED") +
                "     ↔ " + (if (state.hSwing) "SWING" else "FIXED"),
            w / 2f, labelY, textPaint
        )
    }

    private fun drawAirflow(canvas: Canvas, w: Float, h: Float, accent: Int, dt: Float) {
        val ventY = h * 0.34f
        val fanBoost = if (state.turbo) 1.8f else 1f + 0.3f * state.fan.ordinal

        if (state.power) {
            val spawnCount = (2 * fanBoost).toInt().coerceAtLeast(1)
            repeat(spawnCount) {
                if (particles.size < 110) {
                    val p = Particle()
                    p.x = w * 0.12f + rng.nextFloat() * (w * 0.76f)
                    p.y = ventY + rng.nextFloat() * 8f
                    val vr = Math.toRadians(flapAngle.toDouble())
                    val hr = Math.toRadians(vaneAngle.toDouble())
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
        drawSevenSeg(canvas, cx - digitW - 10f, cy - digitH / 2f, digitW, digitH, state.temp / 10, setColor)
        drawSevenSeg(canvas, cx + 10f, cy - digitH / 2f, digitW, digitH, state.temp % 10, setColor)

        textPaint.color = setColor
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = digitH * 0.30f
        canvas.drawText("°C", cx + digitW + 26f, cy - digitH / 2f + digitH * 0.28f, textPaint)
        textPaint.textSize = panel.height() * 0.11f
        textPaint.color = Color.rgb(110, 125, 150)
        canvas.drawText("SET", cx - digitW - 10f, panel.bottom - panel.height() * 0.10f, textPaint)

        val rx = panel.left + panel.width() * 0.62f
        textPaint.color = Color.rgb(110, 125, 150)
        textPaint.textSize = panel.height() * 0.11f
        canvas.drawText("ROOM", rx, panel.top + panel.height() * 0.22f, textPaint)
        textPaint.color = if (state.power) Color.rgb(240, 245, 252) else Color.rgb(90, 100, 118)
        textPaint.textSize = panel.height() * 0.30f
        canvas.drawText("${state.roomTemp}°", rx, panel.top + panel.height() * 0.52f, textPaint)

        textPaint.color = accent
        textPaint.textSize = panel.height() * 0.13f
        val modeLabel = StringBuilder(state.mode.label)
        if (state.turbo) modeLabel.append("  TURBO")
        if (state.eco) modeLabel.append("  ECO")
        canvas.drawText(modeLabel.toString(), rx, panel.top + panel.height() * 0.72f, textPaint)

        val bars = if (state.turbo) 4 else state.fan.ordinal
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

    private fun drawSevenSeg(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, digit: Int, color: Int
    ) {
        val on = intArrayOf(
            0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011,
            0b1011011, 0b1011111, 0b1110000, 0b1111111, 0b1111011
        )[digit.coerceIn(0, 9)]
        segPaint.strokeWidth = w * 0.16f
        val m = w * 0.12f
        val midY = y + h / 2f
        fun seg(bit: Int, x1: Float, y1: Float, x2: Float, y2: Float) {
            segPaint.color = if ((on shr bit) and 1 == 1) color else withAlpha(color, 26)
            canvas.drawLine(x1, y1, x2, y2, segPaint)
        }
        seg(6, x + m, y, x + w - m, y)
        seg(5, x + w, y + m, x + w, midY - m)
        seg(4, x + w, midY + m, x + w, y + h - m)
        seg(3, x + m, y + h, x + w - m, y + h)
        seg(2, x, midY + m, x, y + h - m)
        seg(1, x, y + m, x, midY - m)
        seg(0, x + m, midY, x + w - m, midY)
    }

    private fun drawSyncPulse(canvas: Canvas, w: Float, h: Float, accent: Int) {
        if (syncFlash <= 0f) return
        val progress = 1f - syncFlash
        strokePaint.color = withAlpha(accent, (200 * syncFlash).toInt())
        strokePaint.strokeWidth = 6f * syncFlash + 1f
        canvas.drawCircle(w / 2f, h * 0.22f, w * 0.12f + progress * w * 0.42f, strokePaint)
        textPaint.color = withAlpha(Color.WHITE, (255 * syncFlash).toInt())
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = h * 0.045f
        canvas.drawText("SIGNAL SENT ⇄", w / 2f, h * 0.055f, textPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
