package io.github.rateforx.t9tv.setup

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import io.github.rateforx.t9tv.R
import io.github.rateforx.t9tv.ime.LayoutRepository
import io.github.rateforx.t9tv.settings.PanelPosition
import io.github.rateforx.t9tv.settings.Prefs

/** Launcher entry (required for Play on TV): keyboard setup status, shortcuts, settings and test fields. */
class SetupActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var layoutButton: TextView
    private lateinit var timeoutButton: TextView
    private lateinit var positionButton: TextView
    private lateinit var layouts: LayoutRepository
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        layouts = LayoutRepository(this)
        prefs = Prefs(this)
        status = findViewById(R.id.status)
        layoutButton = findViewById(R.id.btn_layout)
        timeoutButton = findViewById(R.id.btn_timeout)
        positionButton = findViewById(R.id.btn_position)
        findViewById<TextView>(R.id.btn_enable).setOnClickListener { openImeSettings() }
        findViewById<TextView>(R.id.btn_pick).setOnClickListener { imm().showInputMethodPicker() }
        layoutButton.setOnClickListener { chooseLayout() }
        timeoutButton.setOnClickListener { chooseTimeout() }
        positionButton.setOnClickListener { choosePosition() }
        showSettings()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus() // e.g. after the keyboard picker closes
    }

    private fun chooseLayout() {
        val all = layouts.all()
        if (all.isEmpty()) return
        choose(R.string.setting_layout, all.map { it.name }, all.indexOf(layouts.selected())) {
            layouts.select(all[it])
        }
    }

    private fun chooseTimeout() {
        val all = Prefs.TIMEOUTS_MS
        choose(R.string.setting_timeout, all.map(::timeoutLabel), all.indexOf(prefs.timeoutMs)) {
            prefs.timeoutMs = all[it]
        }
    }

    private fun choosePosition() {
        val all = PanelPosition.entries
        choose(R.string.setting_position, all.map(::positionLabel), all.indexOf(prefs.panelPosition)) {
            prefs.panelPosition = all[it]
        }
    }

    /** A D-pad friendly single-choice list; [onPick] gets the index of the picked item. */
    private fun choose(title: Int, items: List<String>, checked: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), checked) { dialog, which ->
                onPick(which)
                showSettings()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSettings() {
        layoutButton.text = settingText(R.string.setting_layout, layouts.selected().name)
        timeoutButton.text = settingText(R.string.setting_timeout, timeoutLabel(prefs.timeoutMs))
        positionButton.text = settingText(R.string.setting_position, positionLabel(prefs.panelPosition))
    }

    private fun settingText(name: Int, value: String) = getString(R.string.setting_value, getString(name), value)

    private fun timeoutLabel(ms: Int) = getString(R.string.timeout_value, ms / 1000f)

    private fun positionLabel(p: PanelPosition) = getString(
        when (p) {
            PanelPosition.BOTTOM_RIGHT -> R.string.position_bottom_right
            PanelPosition.BOTTOM_CENTER -> R.string.position_bottom_center
            PanelPosition.BOTTOM_LEFT -> R.string.position_bottom_left
            PanelPosition.TOP_LEFT -> R.string.position_top_left
            PanelPosition.TOP_CENTER -> R.string.position_top_center
            PanelPosition.TOP_RIGHT -> R.string.position_top_right
            PanelPosition.CENTER -> R.string.position_center
        },
    )

    private fun imm() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    private fun refreshStatus() {
        val enabled = imm().enabledInputMethodList.any { it.packageName == packageName }
        val current = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        } catch (e: SecurityException) {
            null
        }
        val selected = current?.startsWith("$packageName/") == true
        status.text = getString(R.string.setup_status, yesNo(enabled), yesNo(selected))
    }

    private fun yesNo(value: Boolean) = getString(if (value) R.string.yes else R.string.no)

    private fun openImeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            status.text = getString(R.string.setup_no_settings)
        }
    }
}
