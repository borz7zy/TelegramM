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
import androidx.navigation.fragment.findNavController
import com.github.borz7zy.telegramm.R
import com.github.borz7zy.telegramm.databinding.FragmentAuthBinding
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class AuthFragment : BaseTelegramFragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.setSession(session)

        binding.phoneEdit.addTextChangedListener(PhoneNumberFormattingTextWatcher())

        setupListeners()
        observeState()
        observeEvents()
    }

    private fun setupListeners() {
        binding.mainBtn.setOnClickListener {
            when (val state = viewModel.uiState.value) {
                is AuthViewModel.UiState.Phone ->
                    viewModel.sendPhone(binding.phoneEdit.text.toString())

                is AuthViewModel.UiState.Code ->
                    viewModel.sendCode(binding.codeEdit.text.toString())

                is AuthViewModel.UiState.Password ->
                    viewModel.sendPassword(binding.passwordEdit.text.toString())

                else -> Unit
            }
        }

        binding.secondaryActionBtn.setOnClickListener {
            when (viewModel.uiState.value) {
                is AuthViewModel.UiState.Code -> viewModel.onWrongNumber()
                is AuthViewModel.UiState.Password -> viewModel.onForgotPassword()
                else -> Unit
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { render(it) }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is AuthViewModel.Event.NavigateToMain ->
                        findNavController().navigate(R.id.frag_auth_to_main)

                    is AuthViewModel.Event.ShowError ->
                        Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()

                    is AuthViewModel.Event.ShowToast ->
                        Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun render(state: AuthViewModel.UiState) {
        binding.progressBar.isVisible = state is AuthViewModel.UiState.Loading
        binding.mainBtn.isEnabled = state !is AuthViewModel.UiState.Loading

        binding.phoneInputLayout.isVisible = state is AuthViewModel.UiState.Phone
        binding.codeInputLayout.isVisible = state is AuthViewModel.UiState.Code
        binding.passwordInputLayout.isVisible = state is AuthViewModel.UiState.Password

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
                    state.hint?.takeIf { it.isNotBlank() }?.let { "Hint: $it" }
                binding.secondaryActionBtn.apply {
                    isVisible = true
                    text = "Forgot password?"
                }
            }

            else -> Unit
        }
    }

    override fun onAuthStateChanged(state: TdApi.AuthorizationState?) {
        state?.let { viewModel.onAuthStateChanged(it) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "AuthFragment"
    }
}