package com.github.borz7zy.telegramm.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.github.borz7zy.telegramm.AppManager
import com.github.borz7zy.telegramm.R
import com.github.borz7zy.telegramm.ui.MainViewModel

class SettingsFragment : Fragment() {

    companion object {
        fun newInstance() = SettingsFragment()
    }

//    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var mainViewModel: MainViewModel
    private lateinit var settingsRoot: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class]

        applyInsetsPadding()

        val rootFrame = view.findViewById<FrameLayout>(R.id.root_container)
        settingsRoot = createSettingsGroup(requireContext())
        rootFrame.addView(settingsRoot)
    }

    private fun applyInsetsPadding() {
        mainViewModel.topInset.observe(viewLifecycleOwner) { topInset ->
            if (topInset != null) {
                settingsRoot.setPadding(
                    settingsRoot.paddingLeft,
                    topInset,
                    settingsRoot.paddingRight,
                    settingsRoot.paddingBottom
                )
            }
        }

        mainViewModel.bottomInset.observe(viewLifecycleOwner) { bottomInset ->
            if (bottomInset != null) {
                settingsRoot.setPadding(
                    settingsRoot.paddingLeft,
                    settingsRoot.paddingTop,
                    settingsRoot.paddingRight,
                    bottomInset
                )
            }
        }
    }

    private fun createSettingsGroup(context: Context): LinearLayout{
        val root = LinearLayout(context).apply{
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            id = R.id.root_linear_settings
        }

        val title = TextView(context).apply {
            text = "Настройки" // TODO
            textSize = 14f
            alpha = 0.6f
        }
        root.addView(title)

        // TODO:
        root.addView(createSettingsItem(context, R.drawable.ic_message_outline, "Настройки чатов", R.id.chat_settings))
        root.addView(createSettingsItem(context, R.drawable.ic_lock_outline, "Конфиденциальность", R.id.confidentiality_settings))
        root.addView(createSettingsItem(context, R.drawable.ic_notification_outline, "Уведомления и звук", R.id.notifications_settings))
        root.addView(createSettingsItem(context, R.drawable.ic_document_outline, "Данные и память", R.id.datastorage_settings))
        root.addView(createSettingsItem(context, R.drawable.ic_lightbulb_outline, "Энергосбережение", R.id.energy_settings))
        root.addView(createSettingsItem(context, R.drawable.ic_folder_outline, "Папки с чатами", R.id.folders_settings))
        root.addView(createSettingsItem(context, R.drawable.ic_devices_outline, "Устройства", R.id.devices_settings))
        root.addView(createSettingsItem(context, R.drawable.ic_hieroglyph_character_outline, "Язык", R.id.lang_settings))

        return root
    }

    private fun createSettingsItem(
        context: Context,
        iconRes: Int?,
        textValue: String,
        idInt: Int
    ): LinearLayout {

        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 24, 0, 24)
            gravity = Gravity.CENTER_VERTICAL
            id = idInt
        }

        val icon = ImageView(context).apply {
            iconRes?.let { setImageResource(it) }

            layoutParams = LinearLayout.LayoutParams(
                48, 48
            ).apply {
                marginEnd = 32
            }
        }

        val text = TextView(context).apply {
            text = textValue
            textSize = 16f
        }

        item.addView(icon)
        item.addView(text)

        return item
    }

}