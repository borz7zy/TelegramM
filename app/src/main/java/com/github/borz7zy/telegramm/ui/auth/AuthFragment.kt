package com.github.borz7zy.telegramm.ui.auth

import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.github.borz7zy.telegramm.R
import com.github.borz7zy.telegramm.databinding.FragmentAuthBinding
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment
import kotlinx.coroutines.launch

class AuthFragment : BaseTelegramFragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.phoneEdit.addTextChangedListener(
            PhoneNumberFormattingTextWatcher()
        )

        setupListeners()
        observeState()
        observeEvents()
    }

    private fun setupListeners() {
        binding.mainBtn.setOnClickListener {
            viewModel.onMainAction(
                phone = binding.phoneEdit.text.toString(),
                code = binding.codeEdit.text.toString(),
                password = binding.passwordEdit.text.toString()
            )
        }

        binding.secondaryActionBtn.setOnClickListener {
            viewModel.onSecondaryAction()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                androidx.lifecycle.Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                androidx.lifecycle.Lifecycle.State.STARTED
            ) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AuthViewModel.Event.NavigateToMain ->
                            findNavController()
                                .navigate(R.id.frag_auth_to_main)

                        is AuthViewModel.Event.ShowError ->
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()

                        is AuthViewModel.Event.ShowToast ->
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                    }
                }
            }
        }
    }

    private fun render(state: AuthViewModel.UiState) {
        val isLoading = state is AuthViewModel.UiState.Loading

        binding.rootContainer.animate()
            .alpha(if (isLoading) 0f else 1f)
            .setDuration(150)
            .start()

        binding.progressBar.isVisible =
            state is AuthViewModel.UiState.Loading
        binding.mainBtn.isEnabled =
            state !is AuthViewModel.UiState.Loading
        binding.mainBtn.isVisible =
            state !is AuthViewModel.UiState.Loading
        binding.titleText.isVisible =
            state !is AuthViewModel.UiState.Loading
        binding.authIcon.isVisible =
            state !is AuthViewModel.UiState.Loading

        binding.phoneInputLayout.isVisible =
            state is AuthViewModel.UiState.Phone
        binding.codeInputLayout.isVisible =
            state is AuthViewModel.UiState.Code
        binding.passwordInputLayout.isVisible =
            state is AuthViewModel.UiState.Password

        if (isLoading) return

        when (state) {

            is AuthViewModel.UiState.Phone -> {
                binding.titleText.text = "Your Phone"
                binding.mainBtn.text = "Send Code"
                binding.secondaryActionBtn.isVisible = false
            }

            is AuthViewModel.UiState.Code -> {
                binding.titleText.text = "Enter Code"
                binding.mainBtn.text = "Verify"
                binding.secondaryActionBtn.apply {
                    isVisible = true
                    text = "Wrong number?"
                }
            }

            is AuthViewModel.UiState.Password -> {
                binding.titleText.text = "Enter Password"
                binding.mainBtn.text = "Unlock"
                binding.passwordInputLayout.helperText =
                    state.hint?.let { "Hint: $it" }

                binding.secondaryActionBtn.apply {
                    isVisible = true
                    text = "Forgot password?"
                }
            }

            else -> Unit
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}