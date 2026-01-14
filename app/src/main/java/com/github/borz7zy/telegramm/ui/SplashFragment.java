package com.github.borz7zy.telegramm.ui;

import static androidx.navigation.Navigation.findNavController;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;

import com.github.borz7zy.telegramm.App;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.actor.Props;
import com.github.borz7zy.telegramm.core.StopActor;
import com.github.borz7zy.telegramm.core.TdMessages;

import org.drinkless.tdlib.TdApi;

import java.util.UUID;

public class SplashFragment extends Fragment {

    private TextView splashText;
    private ActorRef uiActorRef;
    private ActorRef clientActorRef;

    private static final int TIMEOUT_SECONDS = 5;
    private static final int MAX_ATTEMPTS = 5;
    private int attemptCount = 0;

    private boolean isAnimationDone = false;
    private TdApi.AuthorizationState pendingState = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private Runnable blinkingRunnable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_splash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        splashText = view.findViewById(R.id.splashText);

        startTypewriterEffect();

        uiActorRef = App.getApplication().getActorSystem().actorOf(
                "splash-" + UUID.randomUUID(),
                Props.of(UiActor::new).dispatcher("ui")
        );

        tryConnectToTdLib();
    }

    private void startTypewriterEffect() {
        String fullText = getString(R.string.app_name);
        splashText.setText("|");

        long totalDuration = 2000;
        long charDelay = totalDuration / fullText.length();

        new Runnable() {
            int index = 0;
            StringBuilder currentText = new StringBuilder();

            @Override
            public void run() {
                if (getContext() == null) return;

                if (index < fullText.length()) {
                    currentText.append(fullText.charAt(index));

                    splashText.setText(currentText.toString() + "|");

                    index++;
                    mainHandler.postDelayed(this, charDelay);
                } else {
                    isAnimationDone = true;
                    startBlinkingCursor(fullText);

                    checkAndNavigate();
                }
            }
        }.run();
    }

    private void startBlinkingCursor(String text) {
        blinkingRunnable = new Runnable() {
            boolean showCursor = false;

            @Override
            public void run() {
                if (getContext() == null) return;

                if (showCursor) {
                    splashText.setText(text + "|");
                } else {
                    splashText.setText(text);
                }
                showCursor = !showCursor;

                mainHandler.postDelayed(this, 500);
            }
        };
        mainHandler.postDelayed(blinkingRunnable, 500);
    }

    private void tryConnectToTdLib() {
        if (pendingState != null) return;

        attemptCount++;
        if (attemptCount > MAX_ATTEMPTS) {
            Toast.makeText(getContext(), "Ошибка инициализации ядра Telegram", Toast.LENGTH_LONG).show();
            mainHandler.postDelayed(() -> {
                if (getActivity() != null) getActivity().finish();
            }, 2000);
            return;
        }

        App.getApplication().getAccountManager()
                .tell(new TdMessages.GetAccount(0), uiActorRef);

        timeoutRunnable = () -> {
            if (pendingState == null) {
                tryConnectToTdLib();
            }
        };
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_SECONDS * 1000);
    }

    private void checkAndNavigate() {
        if (isAnimationDone && pendingState != null) {

            if (blinkingRunnable != null) mainHandler.removeCallbacks(blinkingRunnable);
            if (timeoutRunnable != null) mainHandler.removeCallbacks(timeoutRunnable);

            NavController nav = findNavController(requireView());

            if (pendingState instanceof TdApi.AuthorizationStateReady) {
                nav.navigate(R.id.action_splashFragment_to_mainFragment);
            } else {
                nav.navigate(R.id.action_splashFragment_to_authPhoneFragment);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);

        if (clientActorRef != null && uiActorRef != null) {
            clientActorRef.tell(new TdMessages.Unsubscribe(uiActorRef));
        }
        if (uiActorRef != null) {
            uiActorRef.tell(new StopActor());
        }
    }

    private class UiActor extends AbstractActor {
        @Override
        public void onReceive(Object message) {
            if (message instanceof StopActor) {
                context().stop(self());
                return;
            }
            else if (message instanceof ActorRef) {
                clientActorRef = (ActorRef) message;
                clientActorRef.tell(new TdMessages.Subscribe(self()));
            }
            else if (message instanceof TdMessages.AuthStateChanged) {
                pendingState = ((TdMessages.AuthStateChanged) message).state;

                checkAndNavigate();
            }
        }
    }
}