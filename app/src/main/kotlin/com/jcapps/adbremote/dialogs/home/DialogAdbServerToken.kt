package com.jcapps.adbremote.dialogs.home

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.DialogAdbServerTokenBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.utils.copyToClipboard
import com.jcapps.adbremote.utils.getClipboardText
import java.util.UUID
import java.util.regex.Pattern

/** Edit the on-device ADB server token ("adb_server_token"): save / copy / generate / paste. */
class DialogAdbServerToken : BaseBottomSheet<DialogAdbServerTokenBinding>() {
    // obf: C0868

    // obf: f5006 (C2247 case 7)
    private val dataStore: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    /** Called with the saved token (SettingsFragment, C2192 case 8). RECON-UNSURE: name. */
    // obf: f5007
    var onTokenSaved: ((String) -> Unit)? = null

    // obf: mo1933 (C1378)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogAdbServerTokenBinding
        get() = DialogAdbServerTokenBinding::inflate

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // obf: m4416 mask 6 (both defaults)
        return createBottomSheetDialog(savedInstanceState, true, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val token = dataStore.getString("adb_server_token", null) ?: UUID.randomUUID().toString()
        binding.inputAdbServerToken.setText(token)
        // save (lambda case 0)
        binding.btnOk.setOnClickListener {
            val newToken = binding.inputAdbServerToken.text.toString()
            val pattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            if (!pattern.matcher(newToken).matches()) {
                binding.inputLayoutAdbServerToken.error = getString(R.string.token_invalid)
            } else {
                dataStore.edit().putString("adb_server_token", newToken).apply()
                onTokenSaved?.invoke(newToken)
                dismiss()
            }
        }
        // copy (lambda case 1)
        binding.btnCopy.setOnClickListener {
            val text = binding.inputAdbServerToken.text.toString()
            requireContext().copyToClipboard(text)
            binding.inputLayoutAdbServerToken.helperText = getString(R.string.copied_clipboard)
        }
        // generate (lambda case 2)
        binding.btnRandomize.setOnClickListener {
            val newToken = UUID.randomUUID().toString()
            setToken(newToken)
        }
        // paste (lambda default case)
        binding.inputLayoutAdbServerToken.setEndIconOnClickListener {
            setToken(requireContext().getClipboardText())
        }
        // obf: C3076 case 2
        binding.inputAdbServerToken.doAfterTextChanged {
            binding.inputLayoutAdbServerToken.error = null
        }
    }

    /** Validates [token] (UUID) and saves + shows it, or shows the "invalid" error. */
    // obf: m2709
    private fun setToken(token: String?) {
        if (token != null) {
            val pattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            if (pattern.matcher(token).matches()) {
                dataStore.edit().putString("adb_server_token", token).apply()
                binding.inputAdbServerToken.setText(token)
                return
            }
        }
        binding.inputLayoutAdbServerToken.error = getString(R.string.token_invalid)
    }

}
