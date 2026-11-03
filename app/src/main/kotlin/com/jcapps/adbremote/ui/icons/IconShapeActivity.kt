package com.jcapps.adbremote.ui.icons

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ActivityIconShapeBinding
import com.jcapps.adbremote.databinding.ItemIconShapeParamBinding
import com.jcapps.adbremote.model.IconShape
import com.jcapps.adbremote.premium.PremiumManager
import com.jcapps.adbremote.ui.BaseActivity
import com.jcapps.adbremote.utils.copyToClipboard
import com.jcapps.adbremote.utils.createShapeAppearanceModel
import com.jcapps.adbremote.utils.getClipboardText
import com.jcapps.adbremote.utils.getCustomIconShape
import com.jcapps.adbremote.utils.loadNativeAd
import com.jcapps.adbremote.utils.parseIconShape

/** "Customize icon shape": per-corner family/size and per-edge type/size editor with copy/paste/save. */
class IconShapeActivity : BaseActivity() {
    // obf: C1575

    // obf: f1654
    private lateinit var iconShape: IconShape

    // obf: f1649 / m859
    private val binding: ActivityIconShapeBinding by lazy { ActivityIconShapeBinding.inflate(layoutInflater) }

    // obf: f1653
    private val previewCornerSize: Float by lazy { resources.getDimension(R.dimen.icon_shape_preview_size) }

    // obf: f1651
    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    // obf: f1650
    private val cornerFamilies: Map<Int, String> by lazy {
        mapOf(0 to getString(R.string.rounded), 1 to getString(R.string.cut))
    }

    // obf: f1652
    private val edgeTypes: Map<Int, String> by lazy {
        mapOf(0 to getString(R.string.normal), 1 to getString(R.string.curved), 2 to getString(R.string.triangle))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarCollapsing)
        applySystemBarInsets(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.customize_icon_shape)
        iconShape = getCustomIconShape(PreferenceManager.getDefaultSharedPreferences(this)).shape
        updatePreview()
        setupViews()
        binding.content.btnExportClipboard.setOnClickListener {
            val json = Gson().toJson(iconShape)
            copyToClipboard(json!!)
            Snackbar.make(binding.root, binding.root.resources.getText(R.string.copied_clipboard), Snackbar.LENGTH_SHORT).show()
        }
        binding.content.btnImportClipboard.setOnClickListener {
            iconShape = parseIconShape(getClipboardText())
            updatePreview()
            setupViews()
            binding.content.root.smoothScrollTo(0, 0)
            Snackbar.make(binding.root, binding.root.resources.getText(R.string.imported_clipboard), Snackbar.LENGTH_SHORT).show()
        }
        binding.content.btnSave.setOnClickListener {
            prefs.edit().putString("custom_icon_shape", Gson().toJson(iconShape)).apply()
            setResult(RESULT_OK)
            finish()
        }
        if (PremiumManager.isPremium(application)) {
            binding.content.frameExtraContent.removeAllViews()
        } else {
            loadNativeAd(
                this,
                true,
                // obf: C1920 case 0 (R8-inlined NativeAdCardBinding.populate(nativeAd) helper)
                { ad, _ ->
                    val adLayout = binding.content.bk
                    adLayout.bl.background = null
                    adLayout.bf.background = null
                    adLayout.bj.background = null
                    adLayout.bh.isEnabled = true
                    // obf: ViewGroupOnHierarchyChangeListenerC0419
                    adLayout.bl.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                        override fun onChildViewAdded(parent: View?, child: View?) {
                            if (child is ImageView) {
                                child.adjustViewBounds = true
                            }
                        }

                        override fun onChildViewRemoved(parent: View?, child: View?) {}
                    })
                    val nativeAdView = adLayout.root
                    nativeAdView.mediaView = adLayout.bl
                    nativeAdView.headlineView = adLayout.bj
                    nativeAdView.bodyView = adLayout.bg
                    nativeAdView.callToActionView = adLayout.bh
                    nativeAdView.iconView = adLayout.bf
                    nativeAdView.priceView = adLayout.bm
                    nativeAdView.starRatingView = adLayout.bn
                    nativeAdView.storeView = adLayout.bo
                    nativeAdView.advertiserView = adLayout.be
                    adLayout.bj.text = ad.headline
                    ad.mediaContent?.let { adLayout.bl.mediaContent = it }
                    if (ad.body == null) {
                        adLayout.bg.visibility = View.INVISIBLE
                    } else {
                        adLayout.bg.visibility = View.VISIBLE
                        adLayout.bg.text = ad.body
                    }
                    if (ad.callToAction == null) {
                        adLayout.bh.visibility = View.INVISIBLE
                    } else {
                        adLayout.bh.visibility = View.VISIBLE
                        adLayout.bh.text = ad.callToAction
                    }
                    if (ad.icon == null) {
                        adLayout.bf.visibility = View.GONE
                    } else {
                        adLayout.bf.setImageDrawable(ad.icon?.drawable)
                        adLayout.bf.visibility = View.VISIBLE
                    }
                    if (ad.price == null) {
                        adLayout.bm.visibility = View.INVISIBLE
                    } else {
                        adLayout.bm.visibility = View.VISIBLE
                        adLayout.bm.text = ad.price
                    }
                    if (ad.store == null) {
                        adLayout.bo.visibility = View.INVISIBLE
                    } else {
                        adLayout.bo.visibility = View.VISIBLE
                        adLayout.bo.text = ad.store
                    }
                    if (ad.starRating == null) {
                        adLayout.bn.visibility = View.INVISIBLE
                    } else {
                        adLayout.bn.rating = ad.starRating!!.toFloat()
                        adLayout.bn.visibility = View.VISIBLE
                    }
                    if (ad.advertiser == null) {
                        adLayout.be.visibility = View.INVISIBLE
                    } else {
                        adLayout.be.text = ad.advertiser
                        adLayout.be.visibility = View.VISIBLE
                    }
                    nativeAdView.setNativeAd(ad)
                },
                { binding.content.frameExtraContent.removeAllViews() },
            )
        }
    }

    // obf: m856
    private fun setupViews() {
        val corners = iconShape.corners
        val edges = iconShape.edges
        setupCorner(binding.content.includeCornerTopLeft, R.string.top_left_corner, corners.topLeft) {
            iconShape = iconShape.copy(corners = iconShape.corners.copy(topLeft = it))
        }
        setupCorner(binding.content.includeCornerTopRight, R.string.top_right_corner, corners.topRight) {
            iconShape = iconShape.copy(corners = iconShape.corners.copy(topRight = it))
        }
        setupCorner(binding.content.includeCornerBottomLeft, R.string.bottom_left_corner, corners.bottomLeft) {
            iconShape = iconShape.copy(corners = iconShape.corners.copy(bottomLeft = it))
        }
        setupCorner(binding.content.includeCornerBottomRight, R.string.bottom_right_corner, corners.bottomRight) {
            iconShape = iconShape.copy(corners = iconShape.corners.copy(bottomRight = it))
        }
        setupEdge(binding.content.includeEdgeLeft, R.string.left_edge, edges.left) {
            iconShape = iconShape.copy(edges = iconShape.edges.copy(left = it))
        }
        setupEdge(binding.content.includeEdgeTop, R.string.top_edge, edges.top) {
            iconShape = iconShape.copy(edges = iconShape.edges.copy(top = it))
        }
        setupEdge(binding.content.includeEdgeRight, R.string.right_edge, edges.right) {
            iconShape = iconShape.copy(edges = iconShape.edges.copy(right = it))
        }
        setupEdge(binding.content.includeEdgeBottom, R.string.bottom_edge, edges.bottom) {
            iconShape = iconShape.copy(edges = iconShape.edges.copy(bottom = it))
        }
    }

    // obf: m857
    private fun setupCorner(binding: ItemIconShapeParamBinding, @StringRes title: Int, corner: IconShape.Corner, onChanged: (IconShape.Corner) -> Unit) {
        binding.title.setText(title)
        binding.textParamValue.text = "${corner.size}%"
        binding.slider.value = corner.size.toFloat()
        binding.inputArrowDropDown.text = cornerFamilies[corner.family]
        // obf: ViewOnClickListenerC2344 case 0
        binding.inputArrowDropDown.setOnClickListener {
            val family = corner.family
            // obf: C0582 case 4
            val selectedFamily: (Int) -> Unit = {
                onChanged(corner.copy(family = it))
                binding.inputArrowDropDown.text = cornerFamilies[it]
                updatePreview()
            }
            val names = cornerFamilies.values.toTypedArray()
            val families = cornerFamilies.keys.toList()
            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                // obf: DialogInterfaceOnClickListenerC0807 case 2
                .setSingleChoiceItems(names, families.indexOf(family)) { dialog, which ->
                    selectedFamily(families[which])
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        // obf: C2207 case 0
        binding.slider.addOnChangeListener { _, value, _ ->
            val size = value.toInt()
            onChanged(corner.copy(size = size))
            binding.textParamValue.text = "$size%"
            updatePreview()
        }
    }

    // obf: m858
    private fun setupEdge(binding: ItemIconShapeParamBinding, @StringRes title: Int, edge: IconShape.Edge, onChanged: (IconShape.Edge) -> Unit) {
        binding.title.setText(title)
        binding.textParamValue.text = "${edge.size}%"
        binding.slider.value = edge.size.toFloat()
        binding.inputArrowDropDown.text = edgeTypes[edge.type]
        // obf: ViewOnClickListenerC2344 case 1
        binding.inputArrowDropDown.setOnClickListener {
            val type = edge.type
            // obf: C0582 case 5
            val selectedType: (Int) -> Unit = {
                onChanged(edge.copy(type = it))
                binding.inputArrowDropDown.text = edgeTypes[it]
                updatePreview()
            }
            val names = edgeTypes.values.toTypedArray()
            val types = edgeTypes.keys.toList()
            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                // obf: DialogInterfaceOnClickListenerC0807 case 1
                .setSingleChoiceItems(names, types.indexOf(type)) { dialog, which ->
                    selectedType(types[which])
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        // obf: C2207 case 1
        binding.slider.addOnChangeListener { _, value, _ ->
            val size = value.toInt()
            onChanged(edge.copy(size = size))
            binding.textParamValue.text = "$size%"
            updatePreview()
        }
    }

    // obf: m860
    private fun updatePreview() {
        binding.content.icon.shapeAppearanceModel = createShapeAppearanceModel(iconShape, previewCornerSize)
    }
}
