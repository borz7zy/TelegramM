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
import android.widget.Toast;

import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment;

import org.drinkless.tdlib.TdApi;

public class AuthPhoneFragment extends BaseTelegramFragment {
    private static final String TAG = "AuthPhoneFragment";

    private EditText phoneEdit;
    private Button nextBtn;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_auth_phone, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState){
        super.onViewCreated(view, savedInstanceState);

        phoneEdit = view.findViewById(R.id.phoneEdit);
        nextBtn = view.findViewById(R.id.nextBtn);

        nextBtn.setOnClickListener(v -> {
            String phone = phoneEdit.getText().toString();
            if(session == null){
                Toast.makeText(requireContext(),"Session not ready",Toast.LENGTH_SHORT).show();
                return;
            }
            if(phone.isEmpty()){
                return;
            }
            TdApi.SetAuthenticationPhoneNumber req =
                    new TdApi.SetAuthenticationPhoneNumber();
            req.phoneNumber = phone;
            session.send(req);
        });
    }

    @Override
    protected void onAuthStateChanged(TdApi.AuthorizationState state){
        final NavController nav = Navigation.findNavController(requireView());
        if(state instanceof TdApi.AuthorizationStateWaitCode){
            nav.navigate(R.id.frag_phone_to_code);
        }else if(state instanceof TdApi.AuthorizationStateWaitPassword) {
            nav.navigate(R.id.frag_phone_to_password);
        }else if(state instanceof TdApi.AuthorizationStateReady){
            // TODO
            nav.navigate(R.id.frag_phone_to_main);
        }else{
            // TODO
        }
    }

    @Override
    protected void onAuthorized(){
        final NavController nav = Navigation.findNavController(requireView());
        // TODO
        nav.navigate(R.id.frag_phone_to_main);
    }
}
