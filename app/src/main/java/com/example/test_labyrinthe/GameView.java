package com.example.test_labyrinthe;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/** Écran de jeu : héberge la vue GameViews et le panneau d'informations (HUD). */
public class GameView extends AppCompatActivity implements GameViews.GameListener {
    private GameViews gameView;
    private TextView roundText, scoreText, bestText, movesText, exitText, timeText, wordText, levelText, wordsCountText;
    private ProgressBar timeBar;
    private View timeBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_views);

        String diff = getIntent().getStringExtra("DIFFICULTY");
        if (diff == null) diff = "easy";

        roundText = findViewById(R.id.round_text);
        scoreText = findViewById(R.id.score_text);
        bestText = findViewById(R.id.best_text);
        movesText = findViewById(R.id.moves_text);
        exitText = findViewById(R.id.exit_text);
        timeText = findViewById(R.id.time_text);
        timeBar = findViewById(R.id.time_bar);
        timeBox = findViewById(R.id.time_box);
        wordText = findViewById(R.id.word_text);
        levelText = findViewById(R.id.level_text);
        wordsCountText = findViewById(R.id.words_count_text);

        levelText.setText(diff.substring(0, 1).toUpperCase(Locale.ROOT) + diff.substring(1));

        gameView = findViewById(R.id.gameView);
        gameView.setDimensions(diff);
        gameView.setGameListener(this);

        findViewById(R.id.path_button).setOnClickListener(v -> gameView.calculatePath());
        findViewById(R.id.collect_button).setOnClickListener(v -> gameView.collectCurrentLetter());
        findViewById(R.id.clear_button).setOnClickListener(v -> gameView.clearWord());
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
    }

    @Override
    public void onHudUpdate(int round, int score, int best, int movesLeft, int toExit,
                            int timeLeft, boolean timed, String currentWord, int wordsThisRound) {
        roundText.setText(String.valueOf(round));
        scoreText.setText(String.valueOf(score));
        bestText.setText(getString(R.string.best_fmt, best));
        movesText.setText(String.valueOf(movesLeft));
        movesText.setTextColor(getResources().getColor(movesLeft < 0 ? R.color.danger : R.color.brown_dark, getTheme()));
        exitText.setText(String.valueOf(toExit));

        timeBox.setVisibility(timed ? View.VISIBLE : View.GONE);
        if (timed) {
            timeText.setText(getString(R.string.time_fmt, timeLeft));
            timeBar.setProgress(timeLeft);
            timeText.setTextColor(getResources().getColor(timeLeft <= 10 ? R.color.danger : R.color.brown_dark, getTheme()));
        }
        wordsCountText.setVisibility(timed ? View.VISIBLE : View.GONE);
        wordsCountText.setText(getString(R.string.words_round_fmt, wordsThisRound));
        wordText.setText(currentWord.isEmpty() ? "—" : currentWord);
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pause(); // le timer et les obstacles s'arrêtent quand l'app n'est plus visible
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.resume();
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
