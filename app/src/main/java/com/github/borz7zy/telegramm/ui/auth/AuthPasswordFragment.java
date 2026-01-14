package com.github.borz7zy.telegramm.ui.auth;

import static androidx.navigation.Navigation.findNavController;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.borz7zy.telegramm.App;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.actor.Props;
import com.github.borz7zy.telegramm.core.StopActor;
import com.github.borz7zy.telegramm.core.TdMessages;

import org.drinkless.tdlib.TdApi;

import java.util.UUID;

public class AuthPasswordFragment extends Fragment {

    private ActorRef uiActorRef;
    private ActorRef clientActorRef;
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
        NavController navController = findNavController(view);

        uiActorRef = App.getApplication().getActorSystem().actorOf(
                "auth-pwd-" + UUID.randomUUID(),
                Props.of(()->new UiActor(navController)).dispatcher("ui")
        );

        ActorRef accountManager = App.getApplication().getAccountManager();
        accountManager.tell(new TdMessages.GetAccount(0), uiActorRef);

        if(getArguments() != null){
            String hint = getArguments().getString("arg_password_hint");
            if(hint != null && !hint.isEmpty()){
                passwordEdit.setHint(hint);
            }
        }

        nextBtn.setOnClickListener(v -> {
            String pwd = passwordEdit.getText().toString();
            if (clientActorRef != null && !pwd.isEmpty()) {
                clientActorRef.tell(new TdMessages.SendPassword(pwd));
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (clientActorRef != null && uiActorRef != null) {
            clientActorRef.tell(new TdMessages.Unsubscribe(uiActorRef));
        }
        if (uiActorRef != null) {
            uiActorRef.tell(new StopActor());
        }
    }

    private class UiActor extends AbstractActor {
        private final NavController navController;

        public UiActor(NavController navController) {
            this.navController = navController;
        }

        @Override
        public void onReceive(Object message) {
            if (message instanceof StopActor) {
                context().stop(self());
                return;
            }
            else if(message instanceof ActorRef){
                clientActorRef = (ActorRef) message;
                Log.d("AuthPassword", "Got client actor: " + clientActorRef.path());

                clientActorRef.tell(new TdMessages.Subscribe(self()));
            }
            else if (message instanceof TdMessages.AuthStateChanged) {
                TdApi.AuthorizationState state = ((TdMessages.AuthStateChanged) message).state;

                if (state instanceof TdApi.AuthorizationStateReady) {
                    navController.navigate(R.id.action_authPasswordFragment_to_mainFragment);
                }
            }
            else if (message instanceof TdMessages.TdError) {
                String errorText = ((TdMessages.TdError) message).message;
                Toast.makeText(requireContext(), errorText, Toast.LENGTH_LONG).show();
            }
        }
    }
}