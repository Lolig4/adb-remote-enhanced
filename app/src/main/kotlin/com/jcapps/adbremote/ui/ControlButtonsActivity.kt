package com.jcapps.adbremote.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.ControlButtonChooserAdapter
import com.jcapps.adbremote.databinding.ActivityControlButtonsBinding
import com.jcapps.adbremote.model.VirtualButton
import com.jcapps.adbremote.utils.ButtonManager

/** "Customize control buttons": tap a slot to choose its VirtualButton; menu resets all slots. */
class ControlButtonsActivity : BaseActivity() {
    // obf: C1385

    // obf: f1586
    private val binding: ActivityControlButtonsBinding by lazy { ActivityControlButtonsBinding.inflate(layoutInflater) }

    // obf: f1587
    private val buttonManager: ButtonManager by lazy { ButtonManager(this, null, null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarCollapsing)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.customize_control_buttons)
        applySystemBarInsets(binding.root)
        // obf: C2192 case 5
        buttonManager.onButtonSelected = { buttonLayoutPosition ->
            val buttons = VirtualButton.entries.toList()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_button)
                // obf: DialogInterfaceOnClickListenerC0167 case 0
                .setAdapter(ControlButtonChooserAdapter(this, buttons)) { dialog, which ->
                    dialog.dismiss()
                    buttonManager.setButton(buttonLayoutPosition, buttons[which])
                    setupButtons()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        setupButtons()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.control_buttons, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.btn_reset) {
            for ((key, _) in buttonManager.defaultButtons) {
                buttonManager.prefs.dataStore.edit().remove(key).apply()
            }
            setupButtons()
        }
        return super.onOptionsItemSelected(item)
    }

    // obf: m838
    private fun setupButtons() {
        buttonManager.setupButtons(
            binding.btnVolumeMute, binding.btnPower, binding.btnKeyboard, binding.btnTrackpad, binding.btnBack, binding.btnHome,
            binding.btnVolumeDown, binding.btnVolumeUp, binding.btnNumpad, binding.btnExtension, binding.btnSkipPrevious, binding.btnSkipNext,
        )
    }
}
