package com.example.test_labyrinthe;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Affiche tous les mots du dictionnaire, triés et groupés par première lettre. */
public class ListOfWords extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_of_words);
        TextView words = findViewById(R.id.words);
        words.setText(readWordsFromFile());
    }

    private String readWordsFromFile() {
        List<String> list = new ArrayList<>();
        try (InputStream in = getResources().openRawResource(R.raw.dictionary);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toUpperCase();
                if (!line.isEmpty() && !list.contains(line)) list.add(line); // ignore lignes vides et doublons
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Collections.sort(list);

        StringBuilder sb = new StringBuilder();
        sb.append(list.size()).append(" words  ·  points = 100 × length\n");
        char current = 0;
        for (String w : list) {
            if (w.charAt(0) != current) {
                current = w.charAt(0);
                sb.append("\n").append(current).append("   ");
            } else {
                sb.append(" · ");
            }
            sb.append(w);
        }
        return sb.toString();
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
