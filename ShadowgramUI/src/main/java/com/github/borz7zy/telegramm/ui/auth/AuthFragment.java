package com.github.borz7zy.telegramm.ui.auth;

import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.github.borz7zy.shadowgram.shadowgramui.R;
import com.github.borz7zy.shadowgram.shadowgramui.databinding.FragmentAuthBinding;
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment;

public class AuthFragment extends BaseTelegramFragment {

    private FragmentAuthBinding binding;

    private AuthViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAuthBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.phoneEdit.addTextChangedListener(new PhoneNumberFormattingTextWatcher());

        setupListeners();
        observeState();
        observeEvents();
    }

    private void setupListeners() {
        binding.mainBtn.setOnClickListener(v -> viewModel.onMainAction(
                binding.phoneEdit.getText().toString(),
                binding.codeEdit.getText().toString(),
                binding.passwordEdit.getText().toString()
        ));

        binding.secondaryActionBtn.setOnClickListener(v -> viewModel.onSecondaryAction());
    }

    private void observeState() {
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
    }

    private void observeEvents() {
        viewModel.getEvents().observe(getViewLifecycleOwner(), event -> {
            if (event instanceof AuthViewModel.Event.NavigateToMain) {
                NavHostFragment.findNavController(this)
                        .navigate(R.id.frag_auth_to_main);
            } else if (event instanceof AuthViewModel.Event.ShowError) {
                Toast.makeText(
                        requireContext(),
                        ((AuthViewModel.Event.ShowError) event).message,
                        Toast.LENGTH_SHORT
                ).show();
            } else if (event instanceof AuthViewModel.Event.ShowToast) {
                Toast.makeText(
                        requireContext(),
                        ((AuthViewModel.Event.ShowToast) event).message,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void render(AuthViewModel.UiState state) {
        boolean isLoading = state instanceof AuthViewModel.UiState.Loading;

        binding.rootContainer.animate()
                .alpha(isLoading ? 0f : 1f)
                .setDuration(150)
                .start();

        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.mainBtn.setEnabled(!isLoading);
        binding.mainBtn.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        binding.titleText.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        binding.authIcon.setVisibility(isLoading ? View.GONE : View.VISIBLE);

        binding.phoneInputLayout.setVisibility(
                state instanceof AuthViewModel.UiState.Phone ? View.VISIBLE : View.GONE);
        binding.codeInputLayout.setVisibility(
                state instanceof AuthViewModel.UiState.Code ? View.VISIBLE : View.GONE);
        binding.passwordInputLayout.setVisibility(
                state instanceof AuthViewModel.UiState.Password ? View.VISIBLE : View.GONE);

        if (isLoading) {
            return;
        }

        if (state instanceof AuthViewModel.UiState.Phone) {
            binding.titleText.setText("Your Phone");
            binding.mainBtn.setText("Send Code");
            binding.secondaryActionBtn.setVisibility(View.GONE);
        } else if (state instanceof AuthViewModel.UiState.Code) {
            binding.titleText.setText("Enter Code");
            binding.mainBtn.setText("Verify");
            binding.secondaryActionBtn.setVisibility(View.VISIBLE);
            binding.secondaryActionBtn.setText("Wrong number?");
        } else if (state instanceof AuthViewModel.UiState.Password) {
            String hint = ((AuthViewModel.UiState.Password) state).hint;
            binding.titleText.setText("Enter Password");
            binding.mainBtn.setText("Unlock");
            binding.passwordInputLayout.setHelperText(hint != null ? "Hint: " + hint : null);
            binding.secondaryActionBtn.setVisibility(View.VISIBLE);
            binding.secondaryActionBtn.setText("Forgot password?");
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
