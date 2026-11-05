package dev.adbremote.navhelper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Minimal, D-pad friendly settings screen: service status, debug overlay, target apps. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        root.addView(TextView(this).apply { text = getString(R.string.app_name); textSize = 26f })
        status = TextView(this).apply { textSize = 18f; setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Switch(this).apply {
            text = "Debug overlay"; textSize = 18f; isChecked = prefs.debug
            setOnCheckedChangeListener { _, on -> prefs.debug = on }
        })
        root.addView(TextView(this).apply { text = "Apps (one package per line):"; setPadding(0, pad, 0, 0) })
        val targets = EditText(this).apply {
            setText(prefs.targets.sorted().joinToString("\n")); gravity = Gravity.TOP; minLines = 3
        }
        root.addView(targets)
        root.addView(Button(this).apply {
            text = "Save apps"
            setOnClickListener {
                prefs.targets = targets.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        status.text = if (enabled) "Service: ON" else "Service: OFF – enable \"TV Nav Helper\" in the accessibility settings"
    }
}
