package com.github.borz7zy.telegramm.ui.base;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.github.borz7zy.telegramm.App;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.actor.Props;
import com.github.borz7zy.telegramm.core.TdMessages;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import java.util.UUID;

public abstract class BaseTdCustomSheetDialogFragment extends DialogFragment {

    protected ActorRef uiActorRef;
    protected ActorRef clientActorRef;

    protected static class StopActor {}

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        uiActorRef = App.getApplication().getActorSystem().actorOf(
                getClass().getSimpleName() + "-" + UUID.randomUUID(),
                Props.of(this::createActor).dispatcher("ui")
        );

        App.getApplication().getAccountManager()
                .tell(new TdMessages.GetAccount(0), uiActorRef);
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

    protected abstract AbstractActor createActor();

    protected abstract class BaseUiActor extends AbstractActor {

        @Override
        public void onReceive(Object message) {
            if (message instanceof StopActor) {
                context().stop(self());
                return;
            }

            if (message instanceof ActorRef) {
                clientActorRef = (ActorRef) message;
//                TdMediaRepository.get().bindClient(clientActorRef);
                clientActorRef.tell(new TdMessages.Subscribe(self()));
            }
            else if (message instanceof TdMessages.TdError) {
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), ((TdMessages.TdError) message).message, Toast.LENGTH_LONG).show();
                }
            }
            else {
                onReceiveMessage(message);
            }
        }

        protected abstract void onReceiveMessage(Object message);
    }
}
