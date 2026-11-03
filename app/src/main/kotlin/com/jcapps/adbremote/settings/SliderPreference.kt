package com.jcapps.adbremote.settings

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.PreferenceSliderBinding

/** Preference with an icon, title, slider and reset button (layout `cs`); persists a Float. */
class SliderPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    // obf: C1394

    // obf: f1590
    private val iconRes: Int?

    // obf: f1589
    private val titleText: String?

    // obf: f1592
    private val defaultValue: Float

    // obf: f1593
    private val valueFrom: Float

    // obf: f1594
    private val valueTo: Float

    // obf: f1591
    private val stepSize: Float

    // obf: f1595
    private var tickVisible: Boolean = true

    init {
        layoutResource = R.layout.preference_slider
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.SliderPreference, 0, 0)
        iconRes = typedArray.getResourceId(R.styleable.SliderPreference_icon, 0)
        titleText = typedArray.getString(R.styleable.SliderPreference_title)
        defaultValue = typedArray.getFloat(R.styleable.SliderPreference_defaultSliderValue, 0f)
        valueFrom = typedArray.getFloat(R.styleable.SliderPreference_valueFrom, 0f)
        valueTo = typedArray.getFloat(R.styleable.SliderPreference_valueTo, 0f)
        stepSize = typedArray.getFloat(R.styleable.SliderPreference_stepSize, 0f)
        // RECON-UNSURE: the original reads the stepSize attr (index 2) here, not tickVisible (index 3) (tick visibility attr);
        //  kept as-is (styleable constant mix-up in the original).
        tickVisible = typedArray.getBoolean(R.styleable.SliderPreference_stepSize, true)
        typedArray.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val binding = PreferenceSliderBinding.bind(holder.itemView)
        iconRes?.let { binding.icon.setImageDrawable(ContextCompat.getDrawable(context, it)) }
        binding.root.isClickable = false
        binding.textTitle.text = titleText
        binding.slider.valueFrom = valueFrom
        binding.slider.valueTo = valueTo
        binding.slider.value = getPersistedFloat(defaultValue)
        binding.slider.stepSize = stepSize
        binding.slider.isTickVisible = tickVisible
        binding.slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                persistFloat(value)
            }
        }
        // obf: ViewOnClickListenerC2376 case 0
        binding.btnReset.setOnClickListener {
            binding.slider.value = defaultValue
            persistFloat(defaultValue)
        }
    }
}
