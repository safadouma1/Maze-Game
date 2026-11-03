package com.example.test_labyrinthe;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** Écran de choix de la difficulté. */
public class Difficulty extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_difficulty);

        // Les "boutons" sont des cartes cliquables (View) : on n'utilise plus le type Button
        findViewById(R.id.easy_button).setOnClickListener(v -> start("easy"));
        findViewById(R.id.medium_button).setOnClickListener(v -> start("medium"));
        findViewById(R.id.hard_button).setOnClickListener(v -> start("hard"));
    }

    // Envoie la difficulté choisie à l'écran de jeu
    private void start(String difficulty) {
        Intent intent = new Intent(Difficulty.this, GameView.class);
        intent.putExtra("DIFFICULTY", difficulty);
        startActivity(intent);
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
