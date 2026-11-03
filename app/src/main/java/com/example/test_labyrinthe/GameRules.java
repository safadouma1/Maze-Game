package com.example.test_labyrinthe;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

public class GameRules extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_rules);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MainActivity.onScreenStarted();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MainActivity.onScreenStopped();
    }
}
