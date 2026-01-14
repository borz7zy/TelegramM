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

public class AuthPhoneFragment extends Fragment {

    private ActorRef uiActorRef;
    private ActorRef clientActorRef;

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
        NavController navController = findNavController(view);

        uiActorRef = App.getApplication().getActorSystem().actorOf(
                "auth-phone-" + UUID.randomUUID(),
                Props.of(()->new UiActor(navController)).dispatcher("ui")
        );

        ActorRef accountManager = App.getApplication().getAccountManager();
        accountManager.tell(new TdMessages.GetAccount(0), uiActorRef);

        nextBtn.setOnClickListener(v -> {
            String phone = phoneEdit.getText().toString();
            if (clientActorRef != null && !phone.isEmpty()) {
                clientActorRef.tell(new TdMessages.SendPhone(phone));
            }
        });
    }

    @Override
    public void onDestroyView(){
        super.onDestroyView();
        if(clientActorRef != null && uiActorRef != null){
            clientActorRef.tell(new TdMessages.Unsubscribe(uiActorRef));
        }
        if(uiActorRef != null){
            uiActorRef.tell(new StopActor());
        }
    }

    private class UiActor extends AbstractActor{

        private final NavController navController;

        public UiActor(NavController navController){
            this.navController = navController;
        }

        @Override
        public void preStart() throws Exception {
            super.preStart();
        }

        @Override
        public void postStop() throws Exception {
            super.postStop();
        }

        @Override
        public void preRestart(Throwable reason) throws Exception {
            super.preRestart(reason);
        }

        @Override
        public void postRestart(Throwable reason) throws Exception {
            super.postRestart(reason);
        }

        @Override
        public void onReceive(Object message) throws Exception {
            if (message instanceof StopActor) {
                context().stop(self());
                return;
            }
            else if(message instanceof ActorRef){
                clientActorRef = (ActorRef) message;
                Log.d("AuthPhone", "Got client actor: " + clientActorRef.path());

                clientActorRef.tell(new TdMessages.Subscribe(self()));
            }
            else if(message instanceof TdMessages.AuthStateChanged){
                TdApi.AuthorizationState state = ((TdMessages.AuthStateChanged) message).state;
                handleUpdateState(state);
            }
            else if(message instanceof TdMessages.TdError){
                String errorMsg = ((TdMessages.TdError) message).message;
                Log.e("AuthPhone", "TdMessages.TdError: "+errorMsg);
                Toast.makeText(requireContext(), "Error: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        }

        private void handleUpdateState(TdApi.AuthorizationState state){
            if(state instanceof TdApi.AuthorizationStateWaitCode){
                if(navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.authPhoneFragment){
                    navController.navigate(R.id.frag_phone_to_code);
                }
            }
            else if(state instanceof TdApi.AuthorizationStateWaitPassword){

            }
            else if(state instanceof TdApi.AuthorizationStateReady){
                // TODO: main fragment
            }
        }
    }
}