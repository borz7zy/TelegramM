package com.github.borz7zy.telegramm.ui.auth;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment;

import org.drinkless.tdlib.TdApi;

public class AuthPasswordFragment extends BaseTelegramFragment {
    private EditText passwordEdit;
    private Button nextBtn;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_auth_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        passwordEdit = view.findViewById(R.id.passwordEdit);
        nextBtn = view.findViewById(R.id.nextBtnPwd);

        if(getArguments() != null){ // TODO
            String hint = getArguments().getString("arg_password_hint");
            if(hint != null && !hint.isEmpty()){
                passwordEdit.setHint(hint);
            }
        }

        nextBtn.setOnClickListener(v -> {
            final String pwd = passwordEdit.getText().toString();
            session.send(new TdApi.CheckAuthenticationPassword(pwd));
        });
    }

    @Override
    protected void onAuthStateChanged(TdApi.AuthorizationState state){
        final NavController nav = Navigation.findNavController(requireView());
        if(state instanceof TdApi.AuthorizationStateReady){
            nav.navigate(R.id.frag_pass_to_main);
        }else{
            // TODO
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

}