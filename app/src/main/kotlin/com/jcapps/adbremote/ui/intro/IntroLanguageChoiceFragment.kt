package com.jcapps.adbremote.ui.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.IntroLanguageChoiceBinding
import com.jcapps.adbremote.model.Link
import com.jcapps.adbremote.utils.PlayStore
import java.util.Locale

class IntroLanguageChoiceFragment : BaseIntroFragment<IntroLanguageChoiceBinding>() {
    // obf: C2429

    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> IntroLanguageChoiceBinding =
        IntroLanguageChoiceBinding::inflate

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val language = Locale.getDefault().language
        val languageNames = requireContext().resources.getStringArray(R.array.english_myanmar_entries)
        val languageCodes = requireContext().resources.getStringArray(R.array.en_my_entries)
        val selected = languageNames[maxOf(languageCodes.indexOf(language), 0)]
        binding.dropdownLanguage.setSimpleItems(languageNames)
        binding.dropdownLanguage.setText(selected, false)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        // obf: C3843 case 1
        binding.dropdownLanguage.setOnItemClickListener { _, _, position, _ ->
            prefs.edit().putString("language", languageCodes[position]).apply()
        }
        // obf: ViewOnClickListenerC2219 case 11
        binding.btnTranslations.setOnClickListener {
            with(PlayStore) { requireContext().launchLink(Link(Constants.translationsUrl, null)) }
        }
    }
}
