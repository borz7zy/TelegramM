package com.github.borz7zy.telegramm;

import static androidx.core.view.WindowCompat.enableEdgeToEdge;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle b){
        super.onCreate(b);
        enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
    }
}
