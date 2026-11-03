package com.example.test_labyrinthe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;

/**
 * Vue personnalisée qui dessine le labyrinthe et contient toute la logique du jeu :
 * - génération du labyrinthe (recursive backtracking)
 * - plus court chemin (Dijkstra)
 * - collecte des lettres / mots, score, timer, obstacles mobiles.
 */
public class GameViews extends View {

    private enum Direction {UP, DOWN, LEFT, RIGHT}

    // ---------------------------------------------------------------- état du jeu
    private Cell[][] cells;                 // Les cases du labyrinthe
    private Cell player, exit;              // Case du joueur et de la sortie
    private int COLS = 7, ROWS = 5;         // Colonnes et lignes (valeurs par défaut raisonnables)
    private char[][] letters;               // Lettre de chaque case
    private List<Cell> path = new ArrayList<>();                 // Plus court chemin joueur -> sortie
    private final List<Character> collectedLetters = new ArrayList<>(); // Lettres du mot en cours
    private final Set<String> dictionary = new HashSet<>();       // Mots valides
    private final Set<String> prefixes = new HashSet<>();         // Tous les préfixes des mots (pour détecter un mot impossible)
    private final List<String> dictionaryList = new ArrayList<>();
    private final Set<String> usedWords = new HashSet<>();        // Mots déjà collectés
    private boolean[][] collectedCells;     // Cases utilisées pour le mot en cours

    private int totalScore = 0;
    private int bestScore = 0;
    private int currentRound = 1;
    private boolean showPath = false;
    private String diff = "easy";
    private int movesAllowed;
    private int movesMade = 0;
    private int initialShortest = 0;
    private int prevPlayerCol, prevPlayerRow;
    private int wordsCollectedInRound = 0;

    // Timer (niveaux medium / hard)
    private static final int ROUND_TIME = 60;
    private int timeLeft = ROUND_TIME;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private boolean paused = false;

    // Obstacles mobiles (niveau hard). Ce sont des objets indépendants des cases :
    // l'ancienne version déplaçait directement des objets Cell du labyrinthe, ce qui
    // corrompait les coordonnées de la grille.
    private final List<Obstacle> movingObstacles = new ArrayList<>();
    private Runnable obstacleRunnable;

    private final Random random = new Random();
    private long lastClickTime = 0;
    private Toast currentToast;
    private GameListener listener;

    // ---------------------------------------------------------------- dessin
    private float cellSize, hMargin, vMargin, density;
    private final RectF tmpRect = new RectF();
    private final Path tmpPath = new Path();
    private Paint wallPaint, letterPaint, letterOnPlayerPaint, boardPaint, boardShadowPaint,
            cellAltPaint, collectedPaint, playerPaint, playerRingPaint, playerGlowPaint,
            exitPaint, exitHaloPaint, exitDetailPaint, pathPaint, obstaclePaint, obstacleRingPaint;

    // Animation : position affichée du joueur (en unités de case) et pulsation de la sortie
    private static final int COLOR_LETTER = 0xFF9C7A55, COLOR_LETTER_TAKEN = 0xFF4A2E14;
    private float drawPlayerX, drawPlayerY;
    private ValueAnimator moveAnimator;
    private ValueAnimator pulseAnimator;
    private float pulse = 0f;

    /** Interface pour mettre à jour le panneau d'informations (HUD) de l'activité. */
    public interface GameListener {
        void onHudUpdate(int round, int score, int best, int movesLeft, int toExit,
                         int timeLeft, boolean timed, String currentWord, int wordsThisRound);
    }

    public GameViews(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setGameListener(GameListener l) {
        this.listener = l;
        notifyHud();
    }

    // ================================================================ initialisation

    private void init() {
        density = getResources().getDisplayMetrics().density;

        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(Color.parseColor("#4A2E14"));
        wallPaint.setStrokeCap(Paint.Cap.ROUND);

        boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardPaint.setColor(Color.parseColor("#FFF8EC"));

        boardShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardShadowPaint.setColor(Color.parseColor("#33000000"));

        cellAltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellAltPaint.setColor(Color.parseColor("#FBEEDA"));

        collectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        collectedPaint.setColor(Color.parseColor("#FFC861"));

        letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        letterPaint.setColor(Color.parseColor("#8A6440"));
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        letterOnPlayerPaint = new Paint(letterPaint);
        letterOnPlayerPaint.setColor(Color.WHITE);

        playerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerPaint.setColor(Color.parseColor("#2E7DDB"));

        playerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerRingPaint.setColor(Color.WHITE);
        playerRingPaint.setStyle(Paint.Style.STROKE);

        playerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerGlowPaint.setColor(Color.parseColor("#402E7DDB"));

        exitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitPaint.setColor(Color.parseColor("#27AE60"));

        exitHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitHaloPaint.setColor(Color.parseColor("#27AE60"));

        exitDetailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitDetailPaint.setColor(Color.WHITE);

        pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pathPaint.setColor(Color.parseColor("#F39C12"));
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeCap(Paint.Cap.ROUND);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);

        obstaclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        obstaclePaint.setColor(Color.parseColor("#E74C3C"));

        obstacleRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        obstacleRingPaint.setColor(Color.parseColor("#80E74C3C"));
        obstacleRingPaint.setStyle(Paint.Style.STROKE);

        loadDictionary();
        if (!isInEditMode()) {
            bestScore = prefs().getInt("best_" + diff, 0);
        }
        createMaze(); // Labyrinthe par défaut (remplacé par setDimensions)
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("maze_game", Context.MODE_PRIVATE);
    }

    /** Ajuste les dimensions de la grille selon le niveau de difficulté. */
    public void setDimensions(String diff) {
        this.diff = diff == null ? "easy" : diff;
        int[] base = baseDimensions();
        COLS = base[0];
        ROWS = base[1];
        currentRound = 1;
        totalScore = 0;
        usedWords.clear();
        if (!isInEditMode()) bestScore = prefs().getInt("best_" + this.diff, 0);
        createMaze();
    }

    private int[] baseDimensions() {
        if ("medium".equals(diff)) return new int[]{8, 10};
        if ("hard".equals(diff)) return new int[]{10, 13};
        return new int[]{6, 8}; // easy
    }

    private int[] maxDimensions() {
        if ("medium".equals(diff)) return new int[]{13, 17};
        if ("hard".equals(diff)) return new int[]{16, 21};
        return new int[]{10, 13};
    }

    private boolean isTimed() {
        return !"easy".equals(diff);
    }

    // Charger les mots du dictionnaire
    private void loadDictionary() {
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.dictionary);
            Scanner scanner = new Scanner(inputStream);
            while (scanner.hasNextLine()) {
                String word = scanner.nextLine().trim().toUpperCase();
                if (word.isEmpty()) continue; // ignorer les lignes vides
                if (dictionary.add(word)) dictionaryList.add(word);
                for (int i = 1; i <= word.length(); i++) prefixes.add(word.substring(0, i));
            }
            scanner.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================================================================ génération du labyrinthe

    // Renvoie une cellule voisine aléatoire non visitée (recursive backtracking)
    private Cell getNeighbour(Cell cell) {
        ArrayList<Cell> neighbours = new ArrayList<>();
        if (cell.col > 0 && !cells[cell.col - 1][cell.row].visited) neighbours.add(cells[cell.col - 1][cell.row]);
        if (cell.col < COLS - 1 && !cells[cell.col + 1][cell.row].visited) neighbours.add(cells[cell.col + 1][cell.row]);
        if (cell.row > 0 && !cells[cell.col][cell.row - 1].visited) neighbours.add(cells[cell.col][cell.row - 1]);
        if (cell.row < ROWS - 1 && !cells[cell.col][cell.row + 1].visited) neighbours.add(cells[cell.col][cell.row + 1]);
        if (!neighbours.isEmpty()) return neighbours.get(random.nextInt(neighbours.size()));
        return null;
    }

    // Supprime le mur entre deux cellules adjacentes
    private void removeWall(Cell current, Cell next) {
        if (current.col == next.col && current.row == next.row + 1) {
            current.topWall = false;
            next.bottomWall = false;
        } else if (current.col == next.col && current.row == next.row - 1) {
            current.bottomWall = false;
            next.topWall = false;
        } else if (current.col == next.col + 1 && current.row == next.row) {
            current.leftWall = false;
            next.rightWall = false;
        } else if (current.col == next.col - 1 && current.row == next.row) {
            current.rightWall = false;
            next.leftWall = false;
        }
    }

    private int[] getRandomPosition() {
        return new int[]{random.nextInt(COLS), random.nextInt(ROWS)};
    }

    // Génère une nouvelle grille et réinitialise l'état de la manche
    private void createMaze() {
        stopObstacleMovement();
        stopTimer();

        Stack<Cell> stack = new Stack<>();
        Cell current, next;
        cells = new Cell[COLS][ROWS];
        for (int x = 0; x < COLS; x++)
            for (int y = 0; y < ROWS; y++)
                cells[x][y] = new Cell(x, y);

        int[] startPosition = getRandomPosition();
        int[] exitPosition = getRandomPosition();
        // La sortie doit être différente du départ (et pas collée à lui)
        while (Math.abs(startPosition[0] - exitPosition[0]) + Math.abs(startPosition[1] - exitPosition[1]) < 2) {
            exitPosition = getRandomPosition();
        }
        player = cells[startPosition[0]][startPosition[1]];
        exit = cells[exitPosition[0]][exitPosition[1]];

        // Recursive backtracking
        current = player;
        current.visited = true;
        do {
            next = getNeighbour(current);
            if (next != null) {
                removeWall(current, next);
                stack.push(current);
                current = next;
                current.visited = true;
            } else {
                current = stack.pop();
            }
        } while (!stack.empty());

        prevPlayerCol = -1;
        prevPlayerRow = -1;
        if (moveAnimator != null) moveAnimator.cancel(); // sinon l'ancienne animation déplacerait le joueur affiché
        drawPlayerX = player.col;
        drawPlayerY = player.row;

        generateRandomLetters();
        plantWords(); // place de vrais mots du dictionnaire le long des couloirs
        collectedCells = new boolean[COLS][ROWS];
        collectedLetters.clear();
        showPath = false;
        movesMade = 0;
        wordsCollectedInRound = 0;

        path = dijkstra(player, exit);
        initialShortest = path.size() - 1;
        int extra = "easy".equals(diff) ? 30 : 20;
        movesAllowed = initialShortest + extra;

        if ("hard".equals(diff)) {
            createMovingObstacles();
            startObstacleMovement();
        }
        if (isTimed()) {
            timeLeft = ROUND_TIME;
            startTimer();
        }
        notifyHud();
        invalidate();
    }

    // Lettres aléatoires (pondérées vers les lettres fréquentes en anglais)
    private static final String LETTER_POOL =
            "EEEEEEAAAAAIIIIOOOOONNNNRRRRTTTTLLLSSSUUUDDDGGBBCCMMPPFFHHVWWYKJXQZ";

    private void generateRandomLetters() {
        letters = new char[COLS][ROWS];
        for (int x = 0; x < COLS; x++)
            for (int y = 0; y < ROWS; y++)
                letters[x][y] = LETTER_POOL.charAt(random.nextInt(LETTER_POOL.length()));
    }

    /**
     * Correction : avec des lettres 100 % aléatoires il était presque impossible de former
     * un mot (et donc de valider la règle "2 mots" en medium/hard). On place quelques mots
     * du dictionnaire lettre par lettre le long de couloirs ouverts du labyrinthe.
     */
    private void plantWords() {
        if (dictionaryList.isEmpty()) return;
        int wanted = "easy".equals(diff) ? 2 : ("medium".equals(diff) ? 3 : 4);
        boolean[][] reserved = new boolean[COLS][ROWS];
        reserved[exit.col][exit.row] = true; // la case sortie termine la manche : pas de lettre utile dessus
        int maxLen = Math.min(7, Math.max(3, (COLS * ROWS) / 8));

        List<String> candidates = new ArrayList<>();
        for (String w : dictionaryList)
            if (w.length() >= 3 && w.length() <= maxLen && !usedWords.contains(w)) candidates.add(w);
        if (candidates.isEmpty()) return;
        Collections.shuffle(candidates, random);

        int planted = 0;
        for (String word : candidates) {
            if (planted >= wanted) break;
            List<Cell> walk = findWalk(word.length(), reserved);
            if (walk == null) continue;
            for (int i = 0; i < walk.size(); i++) {
                Cell c = walk.get(i);
                letters[c.col][c.row] = word.charAt(i);
                reserved[c.col][c.row] = true;
            }
            planted++;
        }
    }

    // Cherche un chemin simple de longueur `len` dans les couloirs, sans case réservée
    private List<Cell> findWalk(int len, boolean[][] reserved) {
        for (int attempt = 0; attempt < 60; attempt++) {
            Cell start = cells[random.nextInt(COLS)][random.nextInt(ROWS)];
            if (reserved[start.col][start.row]) continue;
            List<Cell> walk = new ArrayList<>();
            walk.add(start);
            boolean[][] used = new boolean[COLS][ROWS];
            used[start.col][start.row] = true;
            Cell cur = start;
            while (walk.size() < len) {
                List<Cell> options = new ArrayList<>();
                for (Cell n : getNeighbors(cur))
                    if (!used[n.col][n.row] && !reserved[n.col][n.row]) options.add(n);
                if (options.isEmpty()) break;
                cur = options.get(random.nextInt(options.size()));
                used[cur.col][cur.row] = true;
                walk.add(cur);
            }
            if (walk.size() == len) return walk;
        }
        return null;
    }

    // ================================================================ obstacles (hard)

    private void createMovingObstacles() {
        movingObstacles.clear();
        int count = 3 + Math.max(0, (COLS * ROWS - 130) / 80); // un peu plus d'obstacles sur les grandes grilles
        for (int i = 0; i < count; i++) {
            int col, row, guard = 0;
            do {
                col = random.nextInt(COLS);
                row = random.nextInt(ROWS);
                guard++;
            } while (guard < 100 && (Math.abs(player.col - col) + Math.abs(player.row - row) < 3
                    || (exit.col == col && exit.row == row)));
            movingObstacles.add(new Obstacle(col, row));
        }
    }

    // Les obstacles se déplacent dans les couloirs (ils respectent les murs)
    private void moveObstacles() {
        for (Obstacle o : movingObstacles) {
            List<Cell> options = new ArrayList<>();
            for (Cell n : getNeighbors(cells[o.col][o.row]))
                if (n != exit) options.add(n);
            if (!options.isEmpty()) {
                Cell n = options.get(random.nextInt(options.size()));
                o.col = n.col;
                o.row = n.row;
            }
        }
        checkObstacleCollision();
        invalidate();
    }

    private void startObstacleMovement() {
        stopObstacleMovement();
        obstacleRunnable = new Runnable() {
            @Override
            public void run() {
                if (!paused) moveObstacles();
                handler.postDelayed(this, 600);
            }
        };
        handler.postDelayed(obstacleRunnable, 600);
    }

    private void stopObstacleMovement() {
        if (obstacleRunnable != null) handler.removeCallbacks(obstacleRunnable);
        obstacleRunnable = null;
    }

    private void checkObstacleCollision() {
        for (Obstacle o : movingObstacles) {
            if (player.col == o.col && player.row == o.row) {
                int[] p;
                int guard = 0;
                do {
                    p = getRandomPosition();
                    guard++;
                } while (guard < 100 && ((p[0] == exit.col && p[1] == exit.row) || isObstacleAt(p[0], p[1])));
                player = cells[p[0]][p[1]];
                prevPlayerCol = -1;
                prevPlayerRow = -1;
                if (moveAnimator != null) moveAnimator.cancel();
                drawPlayerX = player.col;
                drawPlayerY = player.row;
                path = dijkstra(player, exit); // le chemin doit être recalculé après la téléportation
                showMessage("Hit an obstacle! Position reset.");
                notifyHud();
                return;
            }
        }
    }

    private boolean isObstacleAt(int col, int row) {
        for (Obstacle o : movingObstacles) if (o.col == col && o.row == row) return true;
        return false;
    }

    // ================================================================ timer

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (paused) {
                    handler.postDelayed(this, 1000);
                    return;
                }
                if (timeLeft > 0) {
                    timeLeft--;
                    notifyHud();
                    if (timeLeft == 0) {
                        endRound();
                        return;
                    }
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.postDelayed(timerRunnable, 1000);
    }

    private void stopTimer() {
        if (timerRunnable != null) handler.removeCallbacks(timerRunnable);
        timerRunnable = null;
    }

    // Temps écoulé : -300 points et retour au round 1
    private void endRound() {
        totalScore = Math.max(0, totalScore - 300);
        currentRound = 1;
        int[] base = baseDimensions();
        COLS = base[0];
        ROWS = base[1];
        createMaze();
        showMessage("Time's up! You lost 300 points.");
    }

    /** Met le jeu en pause (appelé par l'activité dans onPause). */
    public void pause() {
        paused = true;
        if (pulseAnimator != null) pulseAnimator.pause();
    }

    /** Reprend le jeu (appelé par l'activité dans onResume). */
    public void resume() {
        paused = false;
        if (pulseAnimator != null) pulseAnimator.resume();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(1400);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.addUpdateListener(a -> {
            pulse = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopTimer();
        stopObstacleMovement();
        if (pulseAnimator != null) pulseAnimator.cancel();
        if (moveAnimator != null) moveAnimator.cancel();
        if (currentToast != null) currentToast.cancel();
    }

    // ================================================================ Dijkstra

    private List<Cell> dijkstra(Cell start, Cell end) {
        final Map<Cell, Integer> distances = new HashMap<>();
        Map<Cell, Cell> previous = new HashMap<>();
        Set<Cell> settled = new HashSet<>();
        PriorityQueue<Cell> queue = new PriorityQueue<>((a, b) -> Integer.compare(distances.get(a), distances.get(b)));

        for (int x = 0; x < COLS; x++)
            for (int y = 0; y < ROWS; y++)
                distances.put(cells[x][y], Integer.MAX_VALUE);
        distances.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty()) {
            Cell current = queue.poll();
            if (!settled.add(current)) continue; // déjà traitée (entrée obsolète)
            if (current == end) break;
            for (Cell neighbor : getNeighbors(current)) {
                int newDistance = distances.get(current) + 1;
                if (newDistance < distances.get(neighbor)) {
                    distances.put(neighbor, newDistance);
                    previous.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }
        List<Cell> result = new ArrayList<>();
        if (start != end && !previous.containsKey(end)) {
            result.add(start); // pas de chemin (ne devrait pas arriver dans un labyrinthe parfait)
            return result;
        }
        for (Cell at = end; at != null; at = previous.get(at)) result.add(at);
        Collections.reverse(result);
        return result;
    }

    // Voisins accessibles (pas de mur entre les deux cases)
    private List<Cell> getNeighbors(Cell cell) {
        List<Cell> neighbors = new ArrayList<>();
        if (!cell.topWall && cell.row > 0) neighbors.add(cells[cell.col][cell.row - 1]);
        if (!cell.bottomWall && cell.row < ROWS - 1) neighbors.add(cells[cell.col][cell.row + 1]);
        if (!cell.leftWall && cell.col > 0) neighbors.add(cells[cell.col - 1][cell.row]);
        if (!cell.rightWall && cell.col < COLS - 1) neighbors.add(cells[cell.col + 1][cell.row]);
        return neighbors;
    }

    // ================================================================ actions du joueur

    /** Bouton "Show Path" : coûte 200 points, une fois par manche. */
    public void calculatePath() {
        path = dijkstra(player, exit);
        if (showPath) {
            showMessage("Path already shown");
        } else if (totalScore >= 200) {
            totalScore -= 200;
            showPath = true;
            showMessage("Path shown (-200 points)");
        } else {
            showMessage("You need at least 200 points to show the path");
        }
        notifyHud();
        invalidate();
    }

    /** Bouton "Collect" : ramasse la lettre de la case du joueur. */
    public void collectCurrentLetter() {
        int col = player.col, row = player.row;
        if (collectedCells[col][row]) {
            showMessage("Letter already taken for this word");
            return;
        }
        collectedLetters.add(letters[col][row]);
        collectedCells[col][row] = true;
        checkWord();
        notifyHud();
        invalidate();
    }

    /** Bouton "Clear" : efface le mot en cours. */
    public void clearWord() {
        collectedLetters.clear();
        collectedCells = new boolean[COLS][ROWS];
        notifyHud();
        invalidate();
    }

    private void movePlayer(Direction direction) {
        Cell before = player;
        switch (direction) {
            case UP:
                if (!player.topWall && player.row > 0) player = cells[player.col][player.row - 1];
                break;
            case DOWN:
                if (!player.bottomWall && player.row < ROWS - 1) player = cells[player.col][player.row + 1];
                break;
            case LEFT:
                if (!player.leftWall && player.col > 0) player = cells[player.col - 1][player.row];
                break;
            case RIGHT:
                if (!player.rightWall && player.col < COLS - 1) player = cells[player.col + 1][player.row];
                break;
        }
        if (player == before) return; // mur : rien ne change

        // Retour sur la case précédente : -5
        if (player.col == prevPlayerCol && player.row == prevPlayerRow) {
            totalScore = Math.max(0, totalScore - 5);
            showMessage("You went back! -5 points");
        }
        prevPlayerCol = before.col;
        prevPlayerRow = before.row;

        movesMade++;
        movesAllowed--;
        if (movesAllowed < 0) totalScore = Math.max(0, totalScore - 10);

        animatePlayer(before.col, before.row);
        path = dijkstra(player, exit);

        checkObstacleCollision();
        checkExit();
        notifyHud();
        invalidate();
    }

    private void animatePlayer(float fromX, float fromY) {
        if (moveAnimator != null) {
            moveAnimator.cancel();
            fromX = drawPlayerX;
            fromY = drawPlayerY;
        }
        final float sx = fromX, sy = fromY, ex = player.col, ey = player.row;
        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.setDuration(110);
        moveAnimator.setInterpolator(new DecelerateInterpolator());
        moveAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            drawPlayerX = sx + (ex - sx) * t;
            drawPlayerY = sy + (ey - sy) * t;
            invalidate();
        });
        moveAnimator.start();
    }

    private void checkExit() {
        if (player != exit) return;

        StringBuilder msg = new StringBuilder("You reached the exit! +300");
        totalScore += 300;
        playSound(R.raw.victory);

        // Bonus : exactement le plus court chemin (corrigé : avant, le niveau hard utilisait ">=")
        if (movesMade == initialShortest) {
            totalScore += 200;
            msg.append("\nPerfect path! +200");
        }

        boolean replay = false;
        if (isTimed() && wordsCollectedInRound < 2) {
            totalScore = Math.max(0, totalScore - 100);
            msg.append("\nCollect 2 words before the exit! -100 (round replayed)");
            replay = true;
        }

        if (!replay) {
            currentRound++;
            int[] max = maxDimensions();
            COLS = Math.min(COLS + 1, max[0]);
            ROWS = Math.min(ROWS + 1, max[1]);
        }
        saveBest();
        createMaze();
        showMessage(msg.toString());
    }

    private void checkWord() {
        StringBuilder sb = new StringBuilder();
        for (Character c : collectedLetters) sb.append(c);
        String word = sb.toString();

        if (dictionary.contains(word)) {
            if (!usedWords.contains(word)) {
                usedWords.add(word);
                int score = word.length() * 100;
                totalScore += score;
                wordsCollectedInRound++;
                showMessage("Word found: " + word + " (+" + score + " points)");
                playSound(R.raw.word_found);
                saveBest();
            } else {
                showMessage("Word already used: " + word);
            }
            // Correction : on vide toujours le mot après une validation (avant, les lettres restaient en cas de mot déjà utilisé)
            collectedLetters.clear();
            collectedCells = new boolean[COLS][ROWS];
        } else if (!prefixes.contains(word)) {
            // Correction : aucun mot ne commence ainsi -> on repart à zéro au lieu de bloquer indéfiniment
            showMessage("No word starts with \"" + word + "\" — word cleared");
            collectedLetters.clear();
            collectedCells = new boolean[COLS][ROWS];
        }
    }

    private void saveBest() {
        if (totalScore > bestScore) {
            bestScore = totalScore;
            if (!isInEditMode()) prefs().edit().putInt("best_" + diff, bestScore).apply();
        }
    }

    // ================================================================ utilitaires

    // Correction fuite mémoire : chaque MediaPlayer est libéré à la fin du son
    private void playSound(int resId) {
        try {
            MediaPlayer mp = MediaPlayer.create(getContext(), resId);
            if (mp == null) return;
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
        } catch (Exception ignored) {
        }
    }

    // Un seul Toast à la fois (évite la file d'attente de messages)
    private void showMessage(String text) {
        if (isInEditMode()) return;
        if (currentToast != null) currentToast.cancel();
        currentToast = Toast.makeText(getContext(), text, Toast.LENGTH_SHORT);
        currentToast.show();
    }

    private void notifyHud() {
        if (listener == null || path == null) return;
        StringBuilder sb = new StringBuilder();
        for (Character c : collectedLetters) sb.append(c);
        listener.onHudUpdate(currentRound, totalScore, bestScore, movesAllowed,
                Math.max(0, path.size() - 1), timeLeft, isTimed(), sb.toString(), wordsCollectedInRound);
    }

    // ================================================================ dessin

    private void computeLayout() {
        float pad = 12 * density;
        float w = getWidth() - 2 * pad;
        float h = getHeight() - 2 * pad;
        // Correction : division en float (avant, width/height entiers donnait une mauvaise taille)
        cellSize = Math.min(w / COLS, h / ROWS);
        hMargin = (getWidth() - COLS * cellSize) / 2f;
        vMargin = (getHeight() - ROWS * cellSize) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cells == null || getWidth() == 0) return;
        computeLayout();

        float boardW = COLS * cellSize, boardH = ROWS * cellSize;
        float radius = Math.min(cellSize * 0.35f, 18 * density);
        float wall = Math.max(3 * density, cellSize * 0.1f);
        wallPaint.setStrokeWidth(wall);
        float inset = wall * 0.9f;

        canvas.save();
        canvas.translate(hMargin, vMargin);

        // Plateau : ombre + fond arrondi
        tmpRect.set(-inset, -inset + 4 * density, boardW + inset, boardH + inset + 4 * density);
        canvas.drawRoundRect(tmpRect, radius, radius, boardShadowPaint);
        tmpRect.set(-inset, -inset, boardW + inset, boardH + inset);
        canvas.drawRoundRect(tmpRect, radius, radius, boardPaint);

        // Damier léger + cases collectées
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                tmpRect.set(x * cellSize, y * cellSize, (x + 1) * cellSize, (y + 1) * cellSize);
                if (collectedCells[x][y]) {
                    tmpRect.inset(cellSize * 0.08f, cellSize * 0.08f);
                    canvas.drawRoundRect(tmpRect, cellSize * 0.2f, cellSize * 0.2f, collectedPaint);
                } else if ((x + y) % 2 == 1) {
                    canvas.drawRect(tmpRect, cellAltPaint);
                }
            }
        }

        // Sortie : halo pulsant + porte
        float ecx = (exit.col + 0.5f) * cellSize, ecy = (exit.row + 0.5f) * cellSize;
        exitHaloPaint.setAlpha((int) (110 * (1f - pulse)));
        canvas.drawCircle(ecx, ecy, cellSize * (0.35f + 0.3f * pulse), exitHaloPaint);
        float m = cellSize * 0.18f;
        tmpRect.set(exit.col * cellSize + m, exit.row * cellSize + m * 0.8f,
                (exit.col + 1) * cellSize - m, (exit.row + 1) * cellSize - m * 0.6f);
        canvas.drawRoundRect(tmpRect, cellSize * 0.25f, cellSize * 0.25f, exitPaint);
        canvas.drawCircle(tmpRect.right - cellSize * 0.14f, tmpRect.centerY(), cellSize * 0.05f, exitDetailPaint);

        // Chemin le plus court (pointillés arrondis)
        if (showPath && path.size() > 1) {
            pathPaint.setStrokeWidth(Math.max(3 * density, cellSize * 0.12f));
            pathPaint.setPathEffect(new android.graphics.ComposePathEffect(
                    new DashPathEffect(new float[]{cellSize * 0.18f, cellSize * 0.16f}, -pulse * cellSize * 0.68f),
                    new CornerPathEffect(cellSize * 0.3f)));
            tmpPath.reset();
            tmpPath.moveTo((drawPlayerX + 0.5f) * cellSize, (drawPlayerY + 0.5f) * cellSize);
            for (int i = 1; i < path.size(); i++) {
                Cell c = path.get(i);
                tmpPath.lineTo((c.col + 0.5f) * cellSize, (c.row + 0.5f) * cellSize);
            }
            canvas.drawPath(tmpPath, pathPaint);
        }

        // Lettres
        letterPaint.setTextSize(cellSize * 0.42f);
        letterOnPlayerPaint.setTextSize(cellSize * 0.42f);
        float textOffset = -(letterPaint.descent() + letterPaint.ascent()) / 2;
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                if (cells[x][y] == exit) continue;
                if (x == player.col && y == player.row) continue; // dessinée sur le joueur
                letterPaint.setColor(collectedCells[x][y] ? COLOR_LETTER_TAKEN : COLOR_LETTER);
                canvas.drawText(String.valueOf(letters[x][y]),
                        (x + 0.5f) * cellSize, (y + 0.5f) * cellSize + textOffset, letterPaint);
            }
        }

        // Murs
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                Cell c = cells[x][y];
                float l = x * cellSize, t = y * cellSize, r = (x + 1) * cellSize, b = (y + 1) * cellSize;
                if (c.topWall) canvas.drawLine(l, t, r, t, wallPaint);
                if (c.leftWall) canvas.drawLine(l, t, l, b, wallPaint);
                if (c.bottomWall && y == ROWS - 1) canvas.drawLine(l, b, r, b, wallPaint);
                if (c.rightWall && x == COLS - 1) canvas.drawLine(r, t, r, b, wallPaint);
            }
        }

        // Obstacles (hard)
        if ("hard".equals(diff)) {
            obstacleRingPaint.setStrokeWidth(cellSize * 0.06f);
            for (Obstacle o : movingObstacles) {
                float cx = (o.col + 0.5f) * cellSize, cy = (o.row + 0.5f) * cellSize;
                canvas.drawCircle(cx, cy, cellSize * (0.3f + 0.04f * pulse), obstacleRingPaint);
                canvas.drawCircle(cx, cy, cellSize * 0.22f, obstaclePaint);
                canvas.drawCircle(cx - cellSize * 0.07f, cy - cellSize * 0.04f, cellSize * 0.045f, exitDetailPaint);
                canvas.drawCircle(cx + cellSize * 0.07f, cy - cellSize * 0.04f, cellSize * 0.045f, exitDetailPaint);
            }
        }

        // Joueur : halo + disque + anneau blanc + lettre de la case
        float pcx = (drawPlayerX + 0.5f) * cellSize, pcy = (drawPlayerY + 0.5f) * cellSize;
        canvas.drawCircle(pcx, pcy, cellSize * 0.44f, playerGlowPaint);
        canvas.drawCircle(pcx, pcy, cellSize * 0.34f, playerPaint);
        playerRingPaint.setStrokeWidth(Math.max(2 * density, cellSize * 0.05f));
        canvas.drawCircle(pcx, pcy, cellSize * 0.34f, playerRingPaint);
        canvas.drawText(String.valueOf(letters[player.col][player.row]), pcx, pcy + textOffset, letterOnPlayerPaint);

        canvas.restore();
    }

    // ================================================================ gestes

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (cells == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                long clickTime = System.currentTimeMillis();
                // Double-tap sur le joueur -> collecter la lettre
                if (clickTime - lastClickTime < 300) {
                    int col = (int) Math.floor((event.getX() - hMargin) / cellSize);
                    int row = (int) Math.floor((event.getY() - vMargin) / cellSize);
                    if (col == player.col && row == player.row) {
                        collectCurrentLetter();
                        lastClickTime = 0;
                        return true;
                    }
                }
                lastClickTime = clickTime;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                // Le joueur suit le doigt case par case
                float dx = event.getX() - (hMargin + (player.col + 0.5f) * cellSize);
                float dy = event.getY() - (vMargin + (player.row + 0.5f) * cellSize);
                float absDx = Math.abs(dx), absDy = Math.abs(dy);
                if (absDx > cellSize * 0.8f || absDy > cellSize * 0.8f) {
                    if (absDx > absDy) movePlayer(dx > 0 ? Direction.RIGHT : Direction.LEFT);
                    else movePlayer(dy > 0 ? Direction.DOWN : Direction.UP);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // ================================================================ classes internes

    private static class Cell {
        boolean topWall = true, leftWall = true, bottomWall = true, rightWall = true, visited = false;
        final int col, row; // final : une case ne change jamais de position

        Cell(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }

    private static class Obstacle {
        int col, row;

        Obstacle(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }
}
