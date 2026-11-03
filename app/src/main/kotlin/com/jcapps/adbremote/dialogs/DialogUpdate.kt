package com.jcapps.adbremote.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import androidx.transition.TransitionManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.DialogUpdateBinding
import com.jcapps.adbremote.model.AppConfig
import com.jcapps.adbremote.model.Link
import com.jcapps.adbremote.utils.PlayStore
import com.jcapps.adbremote.utils.getParcelableCompat
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * In-app update prompt for [AppConfig.Update] (argument "update"). Non-optional updates are not
 * cancelable and close the app on "later".
 */
class DialogUpdate : BaseBottomSheet<DialogUpdateBinding>() {
    // obf: C1586

    // obf: mo1933 (C1726)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogUpdateBinding
        get() = DialogUpdateBinding::inflate

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // obf: m4416 mask 4 (first arg explicit false, second default)
        return createBottomSheetDialog(savedInstanceState, false, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val update = requireArguments().getParcelableCompat(ARG_UPDATE, AppConfig.Update::class.java)
        if (update == null) {
            dismiss()
            return
        }
        isCancelable = update.optional
        binding.textTitle.text = update.title
        // RECON-UNSURE: inlined PlayStore member extension `Context.isPackageEnabled(packageName)`
        //  ("<this>"/"packageName" checks) — name unknown, body inlined here.
        val isPlayStoreAvailable = try {
            requireContext().packageManager.getApplicationInfo("com.android.vending", 0).enabled
        } catch (e: Exception) {
            false
        }
        val markwon = Markwon.builder(requireContext())
            // obf: C0554 case 1
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.bulletWidth(16)
                }
            })
            // obf: C0554 case 0 (= io.noties.markwon.linkify.LinkifyPlugin, R8-merged into the plugin
            //  group: configure() requires CorePlugin and adds a text-added listener running
            //  Linkify.addLinks(mask 7 = WEB_URLS|EMAIL_ADDRESSES|PHONE_NUMBERS))
            .usePlugin(LinkifyPlugin.create())
            .build()
        markwon.setMarkdown(binding.textAppRequestingApplyIconPack, update.message)
        binding.btnUpdate.setText(if (isPlayStoreAvailable) R.string.update else R.string.download)
        // obf: ViewOnClickListenerC0827 (default case)
        binding.btnUpdate.setOnClickListener {
            if (isPlayStoreAvailable) {
                with(PlayStore) {
                    requireActivity().launchOnPlayStore(update.packageName)
                }
            } else {
                with(PlayStore) {
                    requireActivity().launchLink(Link(update.download, null))
                }
            }
            if (update.optional) {
                dismiss()
            }
        }
        // lambda case 0
        binding.btnClose.setOnClickListener {
            close(update.optional)
        }
        // lambda case 1
        binding.btnCancel.setOnClickListener {
            close(update.optional)
        }
        binding.btnMore.visibility = if (update.optional && update.skippable) View.VISIBLE else View.GONE
        // obf: ViewOnClickListenerC2219 case 8
        binding.btnMore.setOnClickListener {
            // RECON-UNSURE: inlined app Fragment extension (receiver check "<this>")
            TransitionManager.beginDelayedTransition(requireView().parent as ViewGroup)
            binding.btnMore.visibility = View.GONE
            binding.btnIgnoreTime.visibility = View.VISIBLE
        }
        // lambda case 2 (ignore this version)
        binding.btnIgnoreTime.setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putLong("ignored_update_version", update.version)
                .apply()
            dismiss()
        }
    }

    /** Optional update: just dismiss; mandatory: close the app (finishAffinity, else finish). */
    // obf: m3667
    private fun close(optional: Boolean) {
        if (optional) {
            dismiss()
            return
        }
        runCatching {
            requireActivity().finishAffinity()
        }.onFailure {
            requireActivity().finish()
        }
    }

    companion object {
        // obf: f6980 (C1671 = this companion)
        const val ARG_UPDATE = "update"

        /** Inlined at its only call site (C3479). */
        fun show(fragmentManager: FragmentManager, update: AppConfig.Update) {
            val dialog = DialogUpdate()
            dialog.arguments = Bundle().apply { putParcelable(ARG_UPDATE, update) }
            dialog.show(fragmentManager, DialogUpdate::class.java.name)
        }
    }
}
