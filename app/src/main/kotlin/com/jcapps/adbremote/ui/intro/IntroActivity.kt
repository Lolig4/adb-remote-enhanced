package com.jcapps.adbremote.ui.intro

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ActivityIntroBinding
import com.jcapps.adbremote.ui.BaseActivity

class IntroActivity : BaseActivity() {
    // obf: C1429

    // obf: f1603 / m841
    private val binding: ActivityIntroBinding by lazy { ActivityIntroBinding.inflate(layoutInflater) }

    // obf: f1604 / m840
    lateinit var pagerController: PagerController

    // obf: f1605
    private val pagerAdapter: IntroPagerAdapter by lazy { IntroPagerAdapter(this, fragments) }

    // obf: f1606
    private lateinit var onPageChangeCallback: ViewPager2.OnPageChangeCallback

    // obf: f1607
    private val fragments = ArrayList<Fragment>()

    /** The page currently shown (read by [PagerController] while swiping). */
    val currentPage: BaseIntroFragment<*>
        get() = fragments[binding.pager.currentItem] as BaseIntroFragment<*>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        // obf: C3389 case 0
        onBackPressedDispatcher.addCallback(this) { pagerController.previous() }
        TooltipCompat.setTooltipText(binding.btnArrowRight, getString(R.string.next))
        TooltipCompat.setTooltipText(binding.btnArrowLeft, getString(R.string.previous))
        fragments.add(IntroTermsFragment())
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != 0
        ) {
            fragments.add(IntroNotificationPermissionFragment())
        }
        fragments.add(IntroLanguageChoiceFragment())
        pagerController = PagerController(fragments.size, binding.pager, binding.gestureOverlayIntro)
        // obf: C1039 case 0
        onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> {
                        binding.btnArrowRight.visibility = View.GONE
                        binding.btnArrowLeft.visibility = View.GONE
                        binding.btnAgree.visibility = View.VISIBLE
                        binding.btnDisagree.visibility = View.VISIBLE
                    }
                    fragments.size - 1 -> {
                        binding.btnArrowRight.visibility = View.VISIBLE
                        binding.btnArrowLeft.visibility = View.VISIBLE
                        binding.btnAgree.visibility = View.GONE
                        binding.btnDisagree.visibility = View.GONE
                        binding.btnArrowRight.setIconResource(R.drawable.ic_check)
                    }
                    else -> {
                        binding.btnArrowRight.visibility = View.VISIBLE
                        binding.btnArrowLeft.visibility = View.VISIBLE
                        binding.btnAgree.visibility = View.GONE
                        binding.btnDisagree.visibility = View.GONE
                        binding.btnArrowRight.setIconResource(R.drawable.ic_arrow_right)
                    }
                }
            }
        }
        pagerController.viewPager.registerOnPageChangeCallback(onPageChangeCallback)
        pagerController.onPageChangeCallback = onPageChangeCallback
        pagerController.activity = this
        // RECON-UNSURE: i5(19) is a page transformer with transformType = "Zoom" (data object w3 of a
        //  sealed type also containing Fade/Depth/Flow/SlideOver); its transformPage body was removed by
        //  R8 (interface n3 is empty). Library/owner unidentified — integrator: pick the transformer lib.
        // binding.on.setPageTransformer(...)
        binding.pager.adapter = pagerAdapter
        val next = View.OnClickListener {
            if (!currentPage.canMoveNext(false)) {
                currentPage.onNextBlocked()
            } else {
                pagerController.next()
            }
        }
        binding.btnArrowRight.setOnClickListener(next)
        binding.btnAgree.setOnClickListener(next)
        binding.btnArrowLeft.setOnClickListener { pagerController.previous() }
        binding.btnDisagree.setOnClickListener { pagerController.previous() }
        binding.dotsIntro.attachTo(binding.pager)
    }

    override fun onDestroy() {
        pagerController.viewPager.unregisterOnPageChangeCallback(onPageChangeCallback)
        pagerController.onPageChangeCallback = null
        fragments.clear()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
