package io.github.rateforx.t9tv.ime

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import io.github.rateforx.t9tv.R

/**
 * Multi-tap keyboard driven by the 0–9 buttons of a TV remote.
 *
 * Key events reach the IME before the app (the IME window itself never gets D-pad focus),
 * so all navigation of the panel is handled here in [onKeyDown]/[onKeyUp].
 */
class T9ImeService : InputMethodService() {

    /** Ordinals match [KeyLayout.caseLabels] (abc, Abc, ABC). */
    private enum class CaseMode {
        LOWER, SENTENCE, UPPER;

        fun next(): CaseMode = entries[(ordinal + 1) % entries.size]
    }

    private lateinit var layouts: LayoutRepository
    private lateinit var layout: KeyLayout
    private lateinit var engine: MultiTapEngine
    private val handler = Handler(Looper.getMainLooper())
    private val commitPendingRunnable = Runnable { commitPending() }
    private val consumedDowns = BooleanArray(KEY_TABLE_SIZE)

    private var keypad: KeypadView? = null
    private var debugLog = false
    private var numeric = false
    private var caseMode = CaseMode.LOWER

    /** Abc picked by hand: the next letter is upper case even in the middle of a sentence. */
    private var shiftOnce = false
    private var focusedButton = KeypadView.BTN_BACKSPACE
    private var longPressHandled = false
    private var actionLabel = "↵"
    private val timeoutMs = 1000L
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()

    override fun onCreate() {
        super.onCreate()
        debugLog = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        layouts = LayoutRepository(this)
        layout = layouts.selected()
        engine = MultiTapEngine(layout)
    }

    override fun onCreateInputView(): View {
        // Everything outside the corner panel stays transparent.
        window.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return KeypadView(this).also {
            keypad = it
            syncKeypad()
        }
    }

    /** Never use the landscape full-screen "extract" mode — on a TV that would be always. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** Remotes may register as keyboards, which would hide the panel by default. */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        handler.removeCallbacks(commitPendingRunnable)
        engine.cancel()
        configureFor(attribute)
        log {
            "startInput pkg=${attribute.packageName} type=0x${attribute.inputType.toString(16)} " +
                "ime=0x${attribute.imeOptions.toString(16)} restarting=$restarting"
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        focusedButton = KeypadView.BTN_BACKSPACE
        syncKeypad()
    }

    /** Keeps the cheat sheet showing the case the next letter will get (caret moves, deletes, commits). */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (isInputViewShown && !engine.hasPending) refreshCase()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        commitPending()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        commitPending()
        super.onFinishInput()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val v = keypad ?: return
        if (!isInputViewShown || v.height == 0) return
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        val p = v.panelRect
        val top = loc[1] + p.top
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(loc[0] + p.left, top, loc[0] + p.right, loc[1] + p.bottom)
    }

    // ---------------------------------------------------------------- keys

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        log {
            "down ${KeyEvent.keyCodeToString(keyCode)} sc=${event.scanCode} rep=${event.repeatCount} " +
                "held=${event.eventTime - event.downTime}ms dev=${event.device?.name} " +
                "shown=$isInputViewShown focus=$focusedButton"
        }
        if (handleKeyDown(keyCode, event)) {
            if (keyCode in consumedDowns.indices) consumedDowns[keyCode] = true
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in consumedDowns.indices && consumedDowns[keyCode]) {
            consumedDowns[keyCode] = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val digit = digitFor(keyCode)
        if (digit >= 0) {
            if (!canType()) return false
            if (event.repeatCount == 0) {
                longPressHandled = false
                onDigit(digit)
            } else if (!longPressHandled && event.eventTime - event.downTime >= longPressMs) {
                longPressHandled = true
                onDigitLongPress(digit)
            }
            return true
        }
        // While the panel is shown: ◀/▶ always move the caret, ▲/▼ walk the button column, OK clicks.
        if (!isInputViewShown) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { caretLeft(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { caretRight(); true }
            KeyEvent.KEYCODE_DPAD_UP -> { moveFocus(-1); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { moveFocus(+1); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { ok(event); true }
            else -> false
        }
    }

    private fun digitFor(keyCode: Int): Int = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
        else -> -1
    }

    /** Digits are only taken over in real text fields, so e.g. channel switching keeps working. */
    private fun canType(): Boolean {
        val ei = currentInputEditorInfo ?: return false
        return currentInputStarted && currentInputConnection != null && ei.inputType != InputType.TYPE_NULL
    }

    private fun onDigit(digit: Int) {
        val ic = currentInputConnection ?: return
        if (!isInputViewShown) showPanel()
        handler.removeCallbacks(commitPendingRunnable)
        if (numeric) {
            commitPending()
            ic.commitText(('0' + digit).toString(), 1)
            return
        }
        if (engine.hasPending && engine.activeKey != digit) commitPending()
        val newCycle = !engine.hasPending
        val c = engine.press(digit, upperCase = newCycle && isUpperNow(ic))
        if (newCycle && Character.isLetter(c)) shiftOnce = false
        ic.setComposingText(c.toString(), 1)
        keypad?.setActive(engine.activeKey, engine.activeIndex, engine.activeUpper)
        if (newCycle) refreshCase()
        handler.postDelayed(commitPendingRunnable, timeoutMs)
    }

    private fun onDigitLongPress(digit: Int) {
        if (numeric) return // the digit is already typed
        val ic = currentInputConnection ?: return
        handler.removeCallbacks(commitPendingRunnable)
        ic.commitText(engine.longPress(digit), 1) // replaces the pending (composing) character
        keypad?.clearActive()
    }

    private fun commitPending() {
        handler.removeCallbacks(commitPendingRunnable)
        if (engine.commit() == null) return
        currentInputConnection?.finishComposingText()
        keypad?.clearActive()
    }

    /** OK always clicks the highlighted button; holding it repeats only ⌫ and ⌦. */
    private fun ok(event: KeyEvent) {
        val repeatable = focusedButton == KeypadView.BTN_BACKSPACE || focusedButton == KeypadView.BTN_DELETE
        if (event.repeatCount == 0 || repeatable) clickButton(focusedButton)
    }

    private fun moveFocus(delta: Int) {
        focusedButton = (focusedButton + delta).coerceIn(0, KeypadView.BUTTON_COUNT - 1)
        keypad?.focusedButton = focusedButton
    }

    /** With a pending letter, ◀ confirms it and moves before it. */
    private fun caretLeft() {
        commitPending()
        moveCursor(-1)
    }

    /** With a pending letter, ▶ only confirms it (the caret already stands after it). */
    private fun caretRight() {
        if (engine.hasPending) commitPending() else moveCursor(+1)
    }

    private fun clickButton(button: Int) {
        when (button) {
            KeypadView.BTN_BACKSPACE -> backspace()
            KeypadView.BTN_DELETE -> deleteForward()
            KeypadView.BTN_CASE -> {
                commitPending()
                if (numeric) {
                    numeric = false
                } else {
                    caseMode = caseMode.next()
                    shiftOnce = caseMode == CaseMode.SENTENCE
                }
                syncKeypad()
            }
            KeypadView.BTN_NUMERIC -> {
                commitPending()
                numeric = !numeric
                syncKeypad()
            }
            KeypadView.BTN_ACTION -> performEnter()
        }
    }

    // ---------------------------------------------------------------- editing

    private fun moveCursor(dir: Int) {
        val ic = currentInputConnection ?: return
        val et = ic.getExtractedText(ExtractedTextRequest(), 0)
        val text = et?.text
        if (et == null || text == null) {
            log { "getExtractedText() == null -> falling back to a D-pad key event" }
            sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
            return
        }
        val start = minOf(et.selectionStart, et.selectionEnd)
        val end = maxOf(et.selectionStart, et.selectionEnd)
        var pos = if (start != end) {
            if (dir < 0) start else end // collapse the selection
        } else {
            var p = start + dir
            if (p in 1 until text.length && Character.isLowSurrogate(text[p]) && Character.isHighSurrogate(text[p - 1])) p += dir
            p
        }
        pos = pos.coerceIn(0, text.length)
        ic.setSelection(et.startOffset + pos, et.startOffset + pos)
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (engine.hasPending) {
            handler.removeCallbacks(commitPendingRunnable)
            engine.cancel()
            ic.commitText("", 1) // drops the composing character
            keypad?.clearActive()
            return
        }
        if (isTypeNull()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        if (!TextUtils.isEmpty(ic.getSelectedText(0))) {
            ic.commitText("", 1)
            return
        }
        if (Build.VERSION.SDK_INT >= 24) ic.deleteSurroundingTextInCodePoints(1, 0) else ic.deleteSurroundingText(1, 0)
    }

    private fun deleteForward() {
        commitPending()
        val ic = currentInputConnection ?: return
        if (isTypeNull()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_FORWARD_DEL)
            return
        }
        if (!TextUtils.isEmpty(ic.getSelectedText(0))) {
            ic.commitText("", 1)
            return
        }
        if (Build.VERSION.SDK_INT >= 24) ic.deleteSurroundingTextInCodePoints(0, 1) else ic.deleteSurroundingText(0, 1)
    }

    private fun performEnter() {
        commitPending()
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        log { "enter action=$action actionId=${ei.actionId} label=${ei.actionLabel}" }
        when {
            (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ->
                if (isTypeNull()) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else ic.commitText("\n", 1)
            ei.actionLabel != null && ei.actionId != 0 -> ic.performEditorAction(ei.actionId)
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED -> ic.performEditorAction(action)
            else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun isTypeNull() = currentInputEditorInfo?.inputType == InputType.TYPE_NULL

    // ---------------------------------------------------------------- state

    private fun configureFor(ei: EditorInfo) {
        val type = ei.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        val web = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI
            )
        val password = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
        layout = layouts.selected()
        engine.setLayout(layout, web)
        caseMode = when {
            password || web -> CaseMode.LOWER
            (type and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0 -> CaseMode.UPPER
            (type and CAP_FLAGS) != 0 -> CaseMode.SENTENCE
            else -> CaseMode.LOWER
        }
        shiftOnce = false
        actionLabel = actionLabelFor(ei)
        syncKeypad()
    }

    private fun actionLabelFor(ei: EditorInfo): String {
        if ((ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵"
        ei.actionLabel?.let { return it.toString() }
        return when (ei.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> getString(R.string.action_search)
            EditorInfo.IME_ACTION_GO -> getString(R.string.action_go)
            EditorInfo.IME_ACTION_SEND -> getString(R.string.action_send)
            EditorInfo.IME_ACTION_NEXT -> getString(R.string.action_next)
            EditorInfo.IME_ACTION_DONE -> getString(R.string.action_done)
            EditorInfo.IME_ACTION_PREVIOUS -> getString(R.string.action_previous)
            else -> "↵"
        }
    }

    /**
     * Upper case for a new multi-tap cycle at the cursor: ABC always, abc never, Abc for the
     * letter after picking it by hand and at sentence starts (asks the field when it has CAP_* flags).
     */
    private fun isUpperNow(ic: InputConnection): Boolean = when (caseMode) {
        CaseMode.UPPER -> true
        CaseMode.LOWER -> false
        CaseMode.SENTENCE -> shiftOnce || run {
            val ei = currentInputEditorInfo
            if (ei != null && (ei.inputType and CAP_FLAGS) != 0) ic.getCursorCapsMode(ei.inputType) != 0
            else CaseRules.isSentenceStart(ic.getTextBeforeCursor(CaseRules.LOOKBEHIND, 0))
        }
    }

    private fun syncKeypad() {
        val v = keypad ?: return
        v.setCycles(engine.cycles, engine.upperCycles)
        v.setActionLabel(actionLabel)
        v.focusedButton = focusedButton
        if (!engine.hasPending) v.clearActive()
        refreshCase()
    }

    /** Mode label and the case of the cheat sheet letters: what the next press of a key types. */
    private fun refreshCase() {
        val v = keypad ?: return
        val upper = when {
            numeric || caseMode == CaseMode.LOWER -> false
            caseMode == CaseMode.UPPER -> true
            engine.hasPending -> shiftOnce // after a pending letter the next one is lower case
            else -> currentInputConnection?.let { isUpperNow(it) } ?: false
        }
        v.setModeLabel(if (numeric) "123" else layout.caseLabels[caseMode.ordinal], upper, numeric)
    }

    private fun showPanel() {
        if (Build.VERSION.SDK_INT >= 28) {
            requestShowSelf(0)
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInputFromInputMethod(window.window?.attributes?.token, 0)
        }
    }

    private inline fun log(message: () -> String) {
        if (debugLog) Log.d(TAG, message())
    }

    private companion object {
        const val TAG = "T9IME"
        const val KEY_TABLE_SIZE = 512
        const val CAP_FLAGS = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
}
