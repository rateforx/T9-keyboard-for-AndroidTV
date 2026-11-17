package io.github.rateforx.t9tv.setup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import io.github.rateforx.t9tv.R
import io.github.rateforx.t9tv.ime.LayoutRepository

/** Launcher entry (required for Play on TV): keyboard setup status, shortcuts, layout choice and test fields. */
class SetupActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var layoutButton: TextView
    private lateinit var layouts: LayoutRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        layouts = LayoutRepository(this)
        status = findViewById(R.id.status)
        layoutButton = findViewById(R.id.btn_layout)
        findViewById<TextView>(R.id.btn_enable).setOnClickListener { openImeSettings() }
        findViewById<TextView>(R.id.btn_pick).setOnClickListener { imm().showInputMethodPicker() }
        layoutButton.setOnClickListener { selectNextLayout() }
        showLayout()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus() // e.g. after the keyboard picker closes
    }

    private fun selectNextLayout() {
        val all = layouts.all()
        if (all.isEmpty()) return
        val next = all[(all.indexOf(layouts.selected()) + 1) % all.size]
        layouts.select(next)
        showLayout()
    }

    private fun showLayout() {
        layoutButton.text = getString(R.string.setup_layout, layouts.selected().name)
    }

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
