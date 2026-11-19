package io.github.rateforx.t9tv.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import io.github.rateforx.t9tv.R
import io.github.rateforx.t9tv.settings.PanelPosition

/**
 * The whole keyboard UI drawn on one canvas: a compact panel (bottom-right corner by default,
 * see [position]) with a 3×4 cheat sheet of the keypad and a vertical column of action buttons
 * on its right. The rest of the view is transparent; for a panel at the bottom the view is only
 * as tall as the panel, otherwise it fills the screen height. The IME window never gets D-pad focus, so the service
 * drives [focusedButton] (▲/▼ move it, ◀/▶ are left for the caret).
 */
class KeypadView(context: Context) : View(context) {

    companion object {
        // Top-to-bottom order of the button column.
        const val BTN_BACKSPACE = 0
        const val BTN_ACTION = 1
        const val BTN_CASE = 2
        const val BTN_NUMERIC = 3
        const val BTN_DELETE = 4
        const val BTN_SETTINGS = 5
        const val BUTTON_COUNT = 6

        private const val CELL_WIDTH_DP = 92f
        private const val KEY_INSET_DP = 2f // half the gap between key backgrounds
        private const val ROW_HEIGHT_DP = 44f
        // The button column is as wide as its widest label (bold, as when highlighted) plus padding.
        private const val COLUMN_PADDING_DP = 10f
        private const val COLUMN_MIN_DP = 48f
        private const val COLUMN_MAX_DP = 120f // longer action labels get an ellipsis
        private const val ICON_SCALE = 0.72f // settings icon size relative to the button height
        private const val BUTTON_GAP_DP = 3f
        private const val MARGIN_H_DP = 48f // TV overscan-safe margins
        private const val MARGIN_V_DP = 27f
        private const val PADDING_DP = 10f
        private const val GAP_DP = 8f
        private const val CORNER_DP = 10f
        private const val MAX_VISIBLE_CHARS = 6
        private const val ELLIPSIS = "…"

        private const val COLOR_PANEL = 0xE6202124.toInt()
        private const val COLOR_TEXT = 0xFFE8EAED.toInt()
        private const val COLOR_DIM = 0xFF9AA0A6.toInt()
        private const val COLOR_ACCENT = 0xFF8AB4F8.toInt()
        private const val COLOR_ON_ACCENT = 0xFF202124.toInt()
        private const val COLOR_KEY = 0xFF2D2E31.toInt()
        private const val COLOR_BUTTON = 0xFF3C4043.toInt()
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    /** Panel bounds in view coordinates (used by the service for window insets). */
    val panelRect = Rect()
    private val panel = RectF()
    private val cellRects = Array(10) { RectF() } // index = digit
    private val buttonRects = Array(BUTTON_COUNT) { RectF() }
    private val modeRect = RectF()
    private val tmp = RectF()
    private val oneChar = CharArray(1)

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_PANEL }
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_ACCENT; textSize = sp(22f); typeface = Typeface.DEFAULT_BOLD
    }
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TEXT; textSize = sp(17f) }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ACCENT }
    private val highlightTextPaint = Paint(letterPaint).apply { color = COLOR_ON_ACCENT; typeface = Typeface.DEFAULT_BOLD }
    private val highlightDigitPaint = Paint(digitPaint).apply { color = COLOR_ON_ACCENT }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_KEY }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BUTTON }
    private val buttonFocusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TEXT }
    private val buttonTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEXT; textSize = sp(15f); textAlign = Paint.Align.CENTER
    }
    private val buttonFocusTextPaint = TextPaint(buttonTextPaint).apply { color = COLOR_ON_ACCENT; typeface = Typeface.DEFAULT_BOLD }
    private val modePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_DIM; textSize = sp(15f) }

    private var cycles: Array<String> = Array(10) { "" }
    private var upperCycles: Array<String> = cycles
    private var activeKey = -1
    private var activeIndex = 0
    private var activeUpper = false
    private var upperCase = false
    private var modeLabel = "abc"
    private var actionLabel = "↵"
    private val buttonLabels = arrayOf("⌫", "↵", "Aa", "123", "⌦", "") // ⚙ is drawn as [settingsIcon]
    private val settingsIcon = context.getDrawable(R.drawable.ic_settings)!!.mutate()

    /** The highlighted button of the column (one is always highlighted while the panel is shown). */
    var focusedButton = BTN_BACKSPACE
        set(value) {
            val v = value.coerceIn(0, BUTTON_COUNT - 1)
            if (field != v) {
                field = v
                invalidate()
            }
        }

    var position = PanelPosition.BOTTOM_RIGHT
        set(value) {
            if (field != value) {
                val heightChanges = (field.vertical == 1) != (value.vertical == 1)
                field = value
                if (heightChanges) requestLayout() else if (width > 0) layoutPanel(width, height)
                invalidate()
            }
        }

    /** Lower- and upper-case cycles of every key (index = key), as provided by the layout. */
    fun setCycles(cycles: Array<String>, upperCycles: Array<String>) {
        this.cycles = cycles
        this.upperCycles = upperCycles
        invalidate()
    }

    fun setActive(key: Int, index: Int, upper: Boolean) {
        activeKey = key
        activeIndex = index
        activeUpper = upper
        invalidate()
    }

    fun clearActive() {
        if (activeKey != -1) {
            activeKey = -1
            invalidate()
        }
    }

    fun setModeLabel(label: String, upperCase: Boolean, numeric: Boolean) {
        modeLabel = label
        this.upperCase = upperCase
        buttonLabels[BTN_NUMERIC] = if (numeric) "abc" else "123"
        invalidate()
    }

    fun setActionLabel(label: String) {
        if (label == actionLabel) return
        actionLabel = label
        if (width > 0) layoutPanel(width, height) // the column width follows the label
        invalidate()
    }

    private fun columnWidth(): Float {
        var widest = 0f
        for (label in arrayOf(actionLabel, "⌫", "⌦", "Aa", "123", "abc")) {
            widest = maxOf(widest, buttonFocusTextPaint.measureText(label))
        }
        val icon = dp(ROW_HEIGHT_DP) * 4 / BUTTON_COUNT * ICON_SCALE
        return (maxOf(widest, icon) + 2 * dp(COLUMN_PADDING_DP)).coerceIn(dp(COLUMN_MIN_DP), dp(COLUMN_MAX_DP))
    }

    private fun updateActionButtonLabel() {
        val avail = buttonRects[BTN_ACTION].width() - dp(8f)
        buttonLabels[BTN_ACTION] =
            if (avail <= 0f) actionLabel
            else TextUtils.ellipsize(actionLabel, buttonTextPaint, avail, TextUtils.TruncateAt.END).toString()
    }

    private fun panelHeight() = dp(PADDING_DP) * 2 + dp(ROW_HEIGHT_DP) * 4

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (position.vertical == 1) {
            (panelHeight() + dp(MARGIN_V_DP)).toInt()
        } else {
            val spec = MeasureSpec.getSize(heightMeasureSpec)
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || spec == 0) {
                resources.displayMetrics.heightPixels
            } else {
                spec
            }
        }
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutPanel(w, h)

    private fun layoutPanel(w: Int, h: Int) {
        val pad = dp(PADDING_DP)
        val rowH = dp(ROW_HEIGHT_DP)
        val colW = columnWidth()
        val gap = dp(GAP_DP)
        val pw = minOf(2 * pad + 3 * dp(CELL_WIDTH_DP) + gap + colW, w - 2 * dp(MARGIN_H_DP))
        val ph = panelHeight()
        val left = when (position.horizontal) {
            -1 -> dp(MARGIN_H_DP)
            0 -> (w - pw) / 2
            else -> w - dp(MARGIN_H_DP) - pw
        }
        val top = when (position.vertical) {
            -1 -> dp(MARGIN_V_DP)
            0 -> (h - ph) / 2
            else -> h - dp(MARGIN_V_DP) - ph
        }
        val right = left + pw
        panel.set(left, top, right, top + ph)
        panel.roundOut(panelRect)

        // 3×4 keypad grid on the left.
        val cellW = (pw - 2 * pad - gap - colW) / 3
        for (digit in 0..9) {
            val row = if (digit == 0) 3 else (digit - 1) / 3
            val col = if (digit == 0) 1 else (digit - 1) % 3
            val l = left + pad + col * cellW
            val t = top + pad + row * rowH
            cellRects[digit].set(l, t, l + cellW, t + rowH)
        }
        modeRect.set(left + pad, top + pad + 3 * rowH, left + pad + cellW, top + pad + 4 * rowH)

        // Button column on the right, as tall as the grid.
        val colLeft = right - pad - colW
        val slot = (4 * rowH) / BUTTON_COUNT
        val bgap = dp(BUTTON_GAP_DP)
        for (i in 0 until BUTTON_COUNT) {
            val t = top + pad + i * slot
            buttonRects[i].set(colLeft, t + bgap / 2, right - pad, t + slot - bgap / 2)
        }
        updateActionButtonLabel()
    }

    override fun onDraw(canvas: Canvas) {
        val corner = dp(CORNER_DP)
        canvas.drawRoundRect(panel, corner, corner, panelPaint)
        for (digit in 0..9) drawCell(canvas, digit)
        canvas.drawText(modeLabel, modeRect.left + dp(8f), baseline(modeRect, modePaint), modePaint)
        for (i in 0 until BUTTON_COUNT) drawButton(canvas, i)
    }

    private fun drawCell(canvas: Canvas, digit: Int) {
        val rect = cellRects[digit]
        val active = if (digit == activeKey) activeIndex else -1
        val upper = if (digit == activeKey) activeUpper else upperCase
        val cycle = (if (upper) upperCycles else cycles)[digit]
        if (cycle.isEmpty()) return
        // Key background, slightly lighter than the panel.
        val keyCorner = dp(CORNER_DP) * 0.8f
        tmp.set(rect)
        tmp.inset(dp(KEY_INSET_DP), dp(KEY_INSET_DP))
        canvas.drawRoundRect(tmp, keyCorner, keyCorner, keyPaint)
        val letters = cycle.length - 1 // the last element is the digit itself
        val y = baseline(rect, letterPaint)
        val inset = dp(4f)

        // The digit (highlighted when the cycle reached it).
        var x = rect.left + dp(8f)
        oneChar[0] = '0' + digit
        val dw = digitPaint.measureText(oneChar, 0, 1)
        if (active == letters) {
            tmp.set(x - inset, rect.top + inset, x + dw + inset, rect.bottom - inset)
            canvas.drawRoundRect(tmp, inset, inset, highlightPaint)
        }
        canvas.drawText(oneChar, 0, 1, x, baseline(rect, digitPaint), if (active == letters) highlightDigitPaint else digitPaint)
        x += dw + dp(8f)

        // A window of up to MAX_VISIBLE_CHARS letters that keeps the active one visible.
        val start = if (letters <= MAX_VISIBLE_CHARS || active < 0 || active == letters) 0
        else (active - MAX_VISIBLE_CHARS / 2).coerceIn(0, letters - MAX_VISIBLE_CHARS)
        val end = minOf(letters, start + MAX_VISIBLE_CHARS)

        // Condense the letters when they don't fit the cell (wide upper case, long cycles).
        val charGap = dp(4f)
        var needed = 0f
        for (i in start until end) needed += letterPaint.measureText(displayChar(cycle[i]), 0, 1) + charGap
        val avail = rect.right - dp(4f) - x - (if (end < letters) modePaint.measureText(ELLIPSIS) else 0f)
        val scale = if (needed > avail && avail > 0f) avail / needed else 1f
        letterPaint.textScaleX = scale
        highlightTextPaint.textScaleX = scale

        // The highlight goes first, so it never covers the neighbouring letters.
        if (active in start until end) {
            var hx = x
            for (i in start until active) hx += letterPaint.measureText(displayChar(cycle[i]), 0, 1) + charGap * scale
            val hw = highlightTextPaint.measureText(displayChar(cycle[active]), 0, 1)
            val hpad = charGap / 2 * scale
            tmp.set(hx - hpad, rect.top + inset, hx + hw + hpad, rect.bottom - inset)
            canvas.drawRoundRect(tmp, inset, inset, highlightPaint)
        }
        for (i in start until end) {
            val w = letterPaint.measureText(displayChar(cycle[i]), 0, 1)
            canvas.drawText(oneChar, 0, 1, x, y, if (i == active) highlightTextPaint else letterPaint)
            x += w + charGap * scale
        }
        letterPaint.textScaleX = 1f
        highlightTextPaint.textScaleX = 1f
        if (end < letters) canvas.drawText(ELLIPSIS, x, y, modePaint)
    }

    /** Puts the visible form of [c] into [oneChar] (space is shown as ␣) and returns the buffer. */
    private fun displayChar(c: Char): CharArray {
        oneChar[0] = if (c == ' ') '␣' else c
        return oneChar
    }

    private fun drawButton(canvas: Canvas, i: Int) {
        val rect = buttonRects[i]
        val corner = dp(CORNER_DP) * 0.8f
        val focused = i == focusedButton
        if (focused) {
            tmp.set(rect)
            tmp.inset(-dp(2f), -dp(1f))
            canvas.drawRoundRect(tmp, corner, corner, buttonFocusPaint)
        } else {
            canvas.drawRoundRect(rect, corner, corner, buttonPaint)
        }
        val p = if (focused) buttonFocusTextPaint else buttonTextPaint
        if (i == BTN_SETTINGS) {
            val half = (rect.height() * ICON_SCALE / 2).toInt()
            val cx = rect.centerX().toInt()
            val cy = rect.centerY().toInt()
            settingsIcon.setBounds(cx - half, cy - half, cx + half, cy + half)
            settingsIcon.setTint(p.color)
            settingsIcon.draw(canvas)
        } else {
            canvas.drawText(buttonLabels[i], rect.centerX(), baseline(rect, p), p)
        }
    }

    private fun baseline(rect: RectF, paint: Paint): Float = rect.centerY() - (paint.ascent() + paint.descent()) / 2
}
