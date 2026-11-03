package com.example.test_labyrinthe;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Menu principal + musique de fond. */
public class MainActivity extends AppCompatActivity {

    // Correction : la musique était recréée à chaque onCreate (rotation) et ne s'arrêtait
    // jamais, même après avoir quitté l'application. Elle est maintenant partagée (static),
    // jouée en boucle, mise en pause quand l'app passe en arrière-plan et libérée à la fin.
    private static MediaPlayer music;
    private static boolean musicEnabled = true;
    private static int visibleActivities = 0;
    private TextView musicToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (music == null) {
            music = MediaPlayer.create(getApplicationContext(), R.raw.bakcground_music);
            if (music != null) {
                music.setLooping(true);
                music.setVolume(0.5f, 0.5f);
            }
        }

        findViewById(R.id.play_button).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, Difficulty.class)));
        findViewById(R.id.list_of_words_button).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ListOfWords.class)));
        findViewById(R.id.game_rules_button).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, GameRules.class)));

        musicToggle = findViewById(R.id.music_toggle);
        musicToggle.setOnClickListener(v -> {
            musicEnabled = !musicEnabled;
            updateMusic();
        });
        updateMusic();
    }

    private void updateMusic() {
        musicToggle.setText(musicEnabled ? "♪ Music: on" : "♪ Music: off");
        if (music == null) return;
        if (musicEnabled && !music.isPlaying()) music.start();
        if (!musicEnabled && music.isPlaying()) music.pause();
    }

    /** Appelé par chaque écran : la musique joue tant qu'un écran de l'app est visible. */
    static void onScreenStarted() {
        visibleActivities++;
        if (music != null && musicEnabled && !music.isPlaying()) music.start();
    }

    static void onScreenStopped() {
        visibleActivities--;
        if (visibleActivities <= 0 && music != null && music.isPlaying()) music.pause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        onScreenStarted();
    }

    @Override
    protected void onStop() {
        super.onStop();
        onScreenStopped();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Quitter le menu principal (bouton retour) = quitter le jeu : on libère la musique
        if (isFinishing() && music != null) {
            music.release();
            music = null;
        }
    }
}
