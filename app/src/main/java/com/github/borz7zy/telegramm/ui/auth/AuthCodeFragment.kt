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

public class AuthCodeFragment extends BaseTelegramFragment {

    private EditText codeEdit;
    private Button nextBtn;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_auth_code, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        codeEdit = view.findViewById(R.id.codeEdit);
        nextBtn = view.findViewById(R.id.nextBtn);

        nextBtn.setOnClickListener(v -> {
            final String phone = codeEdit.getText().toString();
            session.send(new TdApi.CheckAuthenticationCode(phone));
        });
    }

    @Override
    protected void onAuthStateChanged(TdApi.AuthorizationState state){
        final NavController nav = Navigation.findNavController(requireView());
        if(state instanceof TdApi.AuthorizationStateWaitPassword){
            nav.navigate(R.id.frag_code_to_password);
        }else if(state instanceof TdApi.AuthorizationStateReady){
            nav.navigate(R.id.frag_code_to_password);
        }else{
            // TODO
        }
    }

    @Override
    public void onDestroyView(){
        super.onDestroyView();
    }
}