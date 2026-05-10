package com.github.borz7zy.telegramm;

import static androidx.core.view.WindowCompat.enableEdgeToEdge;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

public class MainActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle b){
        super.onCreate(b);
        enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // If the activity was recreated by the system due to a uiMode flip
        // (typical Android behavior unless android:configChanges declares
        // uiMode), re-derive Monet for follow-system users.
        AppManager.getInstance().refreshNightModeFromSystem();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // If the user opted into follow-system dark, repaint Monet against
        // the new system uiMode. AppManager bails out cheaply when the user
        // has an explicit isDark preference.
        AppManager.getInstance().refreshNightModeFromSystem();
    }
}
