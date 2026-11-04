package com.jcapps.adbremote.ui.install

import android.app.Activity
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import coil.ImageLoader
import com.google.android.material.tabs.TabLayoutMediator
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ActivityInstallBinding
import com.jcapps.adbremote.ui.BaseActivity

/**
 * Rebuild addition: sideload apps to the TV — tab "Installed" (apps of this phone) and tab
 * "APK's" (APK files in the Download folders of the phone and of the TV, plus a file picker).
 * Opened from the "+" button of the Apps screen; returns RESULT_OK when something was installed.
 */
class InstallActivity : BaseActivity() {

    private val binding by lazy { ActivityInstallBinding.inflate(layoutInflater) }
    val sideloader by lazy { Sideloader(this) }
    val imageLoader by lazy { ImageLoader(this) }

    /** Set by the APK's tab: the storage dialog is only shown once per visit of this screen. */
    var storageAccessAsked = false

    private val listeners = mutableListOf<() -> Unit>()

    /** [listener] runs after every successful install. */
    fun addOnInstalledListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.installToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.install_title)
        binding.installToolbar.setNavigationOnClickListener { finish() }
        // top/side insets here; the lists add the bottom inset themselves (so they scroll under it)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }
        sideloader.onInstalled = {
            setResult(Activity.RESULT_OK)
            listeners.forEach { it() }
        }
        binding.installPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment =
                if (position == 0) InstalledAppsFragment() else ApkFilesFragment()
        }
        TabLayoutMediator(binding.installTabs, binding.installPager) { tab, position ->
            tab.setText(if (position == 0) R.string.install_tab_installed else R.string.install_tab_apks)
        }.attach()
    }
}
