package com.jcapps.adbremote.ui.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.IntroTermsBinding
import com.jcapps.adbremote.model.Link
import com.jcapps.adbremote.utils.PlayStore
import kotlinx.coroutines.Dispatchers
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class IntroTermsFragment : BaseIntroFragment<IntroTermsBinding>() {
    // obf: C2550

    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> IntroTermsBinding =
        IntroTermsBinding::inflate

    // obf: f9629
    private var termsAccepted: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // obf: C2192 case 19
        val onTermsLoaded: (String?) -> Unit = { terms ->
            if (terms == null || terms.isBlank()) {
                binding.textTerms.setText(R.string.failed_connect_server_visit_our)
            } else {
                // obf: C0554 case 1 + case 0 (LinkifyPlugin), as in showMarkdownDialog
                val markwon = Markwon.builder(requireContext())
                    .usePlugin(object : AbstractMarkwonPlugin() {
                        override fun configureTheme(builder: MarkwonTheme.Builder) {
                            builder.bulletWidth(16)
                        }
                    })
                    .usePlugin(LinkifyPlugin.create())
                    .build()
                markwon.setMarkdown(binding.textTerms, terms)
            }
            binding.animationState.visibility = View.GONE
            binding.scrollTerms.visibility = View.VISIBLE
        }
        // obf: C0356
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(Constants.termsMarkdownUrl).build()
                val terms = OkHttpClient().newCall(request).execute().body?.string()
                // obf: C3110
                withContext(Dispatchers.Main) { onTermsLoaded(terms) }
            } catch (e: Exception) {
                // obf: C4053
                withContext(Dispatchers.Main) { onTermsLoaded(null) }
            }
        }
        binding.btnTermsService.setOnClickListener {
            with(PlayStore) { requireContext().launchLink(Link(Constants.appTermsUrl, null)) }
        }
        binding.btnPrivacyPolicy.setOnClickListener {
            with(PlayStore) { requireContext().launchLink(Link(Constants.privacyPolicyUrl, null)) }
        }
    }

    override fun onNextBlocked() {
        if (termsAccepted) {
            pagerController.next()
        }
    }

    override fun canMoveNext(fromSwipe: Boolean): Boolean {
        if (!fromSwipe) {
            termsAccepted = true
        } else if (!termsAccepted) {
            // snap the pager back to the current page
            val currentItem = pagerController.viewPager.currentItem
            pagerController.setCurrentItem(maxOf(currentItem - 1, 0), false)
            pagerController.setCurrentItem(currentItem, false)
        }
        return termsAccepted
    }
}
