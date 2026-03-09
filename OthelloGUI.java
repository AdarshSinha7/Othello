import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;

/**
 * Graphical user interface for the Othello game using Java Swing.
 * Provides a visually rich board with clickable cells, score tracking,
 * move history, and a responsive AI opponent running on a background thread.
 *
 * <p>Run with: {@code javac *.java && java OthelloGUI}</p>
 */
public class OthelloGUI extends JFrame {

    // ── Color Palette ──────────────────────────────────────────────
    private static final Color BOARD_GREEN       = new Color(0, 120, 60);
    private static final Color BOARD_GREEN_LIGHT = new Color(0, 140, 70);
    private static final Color GRID_LINE         = new Color(0, 80, 40);
    private static final Color VALID_MOVE_COLOR  = new Color(255, 255, 100, 90);
    private static final Color HOVER_COLOR       = new Color(255, 255, 100, 160);
    private static final Color LAST_MOVE_COLOR   = new Color(255, 80, 80, 150);
    private static final Color BG_DARK           = new Color(30, 30, 30);
    private static final Color BG_PANEL          = new Color(40, 40, 40);
    private static final Color TEXT_PRIMARY      = new Color(230, 230, 230);
    private static final Color TEXT_SECONDARY    = new Color(160, 160, 160);
    private static final Color ACCENT_GOLD       = new Color(255, 200, 60);
    private static final Color BLACK_DISC        = new Color(20, 20, 20);
    private static final Color BLACK_SHINE       = new Color(80, 80, 80);
    private static final Color WHITE_DISC        = new Color(240, 240, 240);
    private static final Color WHITE_SHINE       = new Color(255, 255, 255);
    private static final Color SCORE_BLACK_BG    = new Color(50, 50, 50);
    private static final Color SCORE_WHITE_BG    = new Color(220, 220, 220);
    private static final Color FLIP_HIGHLIGHT    = new Color(255, 220, 50, 120);

    // ── Game State ─────────────────────────────────────────────────
    private Board board;
    private AIPlayer ai;
    private int currentPlayer;
    private boolean humanTurn;
    private boolean gameOver;
    private Move lastMove;
    private List<Move> currentValidMoves;

    // ── UI Components ──────────────────────────────────────────────
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private JLabel blackScoreLabel;
    private JLabel whiteScoreLabel;
    private JLabel blackCountLabel;
    private JLabel whiteCountLabel;
    private JTextArea historyArea;
    private JSpinner depthSpinner;
    private JLabel aiStatsLabel;
    private JCheckBox idCheckbox;
    private JComboBox<String> timeLimitCombo;
    private JLabel depthLabelRef;
    private int moveNumber;

    /** Time limit options in milliseconds, matching the combo box entries */
    private static final long[] TIME_LIMITS_MS = {1000, 2000, 5000, 10000};

    /**
     * Creates and displays the Othello GUI.
     */
    public OthelloGUI() {
        super("Othello — Reversi");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(BG_DARK);

        initGame(6);
        buildUI();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * Initializes or resets the game state.
     *
     * @param depth AI search depth
     */
    private void initGame(int depth) {
        board = new Board();
        ai = new AIPlayer(Board.WHITE, depth);
        currentPlayer = Board.BLACK;
        humanTurn = true;
        gameOver = false;
        lastMove = null;
        moveNumber = 0;
        currentValidMoves = board.getValidMoves(Board.BLACK);
    }

    // ════════════════════════════════════════════════════════════════
    //  UI CONSTRUCTION
    // ════════════════════════════════════════════════════════════════

    /**
     * Builds the complete user interface layout.
     */
    private void buildUI() {
        setLayout(new BorderLayout(0, 0));

        // ── Header ─────────────────────────────────────────────────
        add(createHeader(), BorderLayout.NORTH);

        // ── Center: board ──────────────────────────────────────────
        boardPanel = new BoardPanel();
        JPanel boardWrapper = new JPanel(new BorderLayout());
        boardWrapper.setBackground(BG_DARK);
        boardWrapper.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 5));
        boardWrapper.add(boardPanel, BorderLayout.CENTER);
        add(boardWrapper, BorderLayout.CENTER);

        // ── Right side panel ───────────────────────────────────────
        add(createSidePanel(), BorderLayout.EAST);

        // ── Bottom status bar ──────────────────────────────────────
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    /**
     * Creates the header panel with title and score display.
     */
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_DARK);
        header.setBorder(BorderFactory.createEmptyBorder(12, 20, 8, 20));

        // Title
        JLabel title = new JLabel("OTHELLO");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(ACCENT_GOLD);
        header.add(title, BorderLayout.WEST);

        // Score panel
        JPanel scorePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        scorePanel.setOpaque(false);

        // Black score
        JPanel blackScore = createScoreChip(SCORE_BLACK_BG, Color.WHITE, "B");
        blackCountLabel = (JLabel) ((JPanel) blackScore.getComponent(0)).getComponent(1);
        blackScoreLabel = (JLabel) ((JPanel) blackScore.getComponent(0)).getComponent(0);
        scorePanel.add(blackScore);

        // White score
        JPanel whiteScore = createScoreChip(SCORE_WHITE_BG, Color.BLACK, "W");
        whiteCountLabel = (JLabel) ((JPanel) whiteScore.getComponent(0)).getComponent(1);
        whiteScoreLabel = (JLabel) ((JPanel) whiteScore.getComponent(0)).getComponent(0);
        scorePanel.add(whiteScore);

        header.add(scorePanel, BorderLayout.EAST);
        updateScoreLabels();
        return header;
    }

    /**
     * Creates a rounded score chip for the header.
     */
    private JPanel createScoreChip(Color bg, Color fg, String symbol) {
        JPanel chip = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setLayout(new BorderLayout());
        chip.setPreferredSize(new Dimension(100, 40));

        JPanel inner = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 5));
        inner.setOpaque(false);

        JLabel symbolLabel = new JLabel(symbol);
        symbolLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        symbolLabel.setForeground(fg);

        JLabel countLabel = new JLabel("2");
        countLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        countLabel.setForeground(fg);

        inner.add(symbolLabel);
        inner.add(countLabel);
        chip.add(inner, BorderLayout.CENTER);
        return chip;
    }

    /**
     * Creates the side panel with controls, move history, and AI stats.
     */
    private JPanel createSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(BG_DARK);
        side.setBorder(BorderFactory.createEmptyBorder(5, 5, 15, 15));
        side.setPreferredSize(new Dimension(220, 0));

        // ── Controls ───────────────────────────────────────────────
        JPanel controls = createStyledPanel("CONTROLS");
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        // Depth selector
        JPanel depthRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        depthRow.setOpaque(false);
        depthLabelRef = new JLabel("AI Depth:");
        depthLabelRef.setForeground(TEXT_PRIMARY);
        depthLabelRef.setFont(new Font("SansSerif", Font.PLAIN, 13));
        depthSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 12, 1));
        depthSpinner.setPreferredSize(new Dimension(55, 26));
        depthSpinner.addChangeListener(e -> {
            int d = (int) depthSpinner.getValue();
            ai.setSearchDepth(d);
        });
        depthRow.add(depthLabelRef);
        depthRow.add(depthSpinner);
        controls.add(depthRow);

        // Time limit selector (shown when iterative deepening is on)
        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        timeRow.setOpaque(false);
        JLabel timeLabel = new JLabel("Time limit:");
        timeLabel.setForeground(TEXT_PRIMARY);
        timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        timeLimitCombo = new JComboBox<>(new String[]{"1 s", "2 s", "5 s", "10 s"});
        timeLimitCombo.setSelectedIndex(1); // default 2s
        timeLimitCombo.setPreferredSize(new Dimension(65, 26));
        timeRow.add(timeLabel);
        timeRow.add(timeLimitCombo);
        timeRow.setVisible(false); // hidden by default
        controls.add(timeRow);

        // Iterative deepening checkbox
        JPanel idRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        idRow.setOpaque(false);
        idCheckbox = new JCheckBox("Iterative Deepening");
        idCheckbox.setForeground(TEXT_PRIMARY);
        idCheckbox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        idCheckbox.setOpaque(false);
        idCheckbox.setFocusPainted(false);
        idCheckbox.addActionListener(e -> {
            boolean on = idCheckbox.isSelected();
            depthRow.setVisible(!on);
            timeRow.setVisible(on);
            controls.revalidate();
        });
        idRow.add(idCheckbox);
        controls.add(idRow);

        controls.add(Box.createVerticalStrut(6));

        // New Game button
        JButton newGameBtn = new JButton("New Game");
        newGameBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        newGameBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        newGameBtn.setFocusPainted(false);
        newGameBtn.setBackground(ACCENT_GOLD);
        newGameBtn.setForeground(Color.BLACK);
        newGameBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newGameBtn.setPreferredSize(new Dimension(180, 32));
        newGameBtn.setMaximumSize(new Dimension(180, 32));
        newGameBtn.addActionListener(e -> resetGame());
        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnWrap.setOpaque(false);
        btnWrap.add(newGameBtn);
        controls.add(btnWrap);

        side.add(controls);
        side.add(Box.createVerticalStrut(10));

        // ── AI Stats ───────────────────────────────────────────────
        JPanel statsPanel = createStyledPanel("AI STATS");
        statsPanel.setLayout(new BorderLayout());
        aiStatsLabel = new JLabel("<html><br>Waiting...<br><br></html>");
        aiStatsLabel.setForeground(TEXT_SECONDARY);
        aiStatsLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        aiStatsLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
        statsPanel.add(aiStatsLabel, BorderLayout.CENTER);
        side.add(statsPanel);
        side.add(Box.createVerticalStrut(10));

        // ── Move History ───────────────────────────────────────────
        JPanel histPanel = createStyledPanel("MOVE HISTORY");
        histPanel.setLayout(new BorderLayout());
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        historyArea.setBackground(new Color(25, 25, 25));
        historyArea.setForeground(TEXT_SECONDARY);
        historyArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JScrollPane scroll = new JScrollPane(historyArea);
        scroll.setPreferredSize(new Dimension(190, 300));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(25, 25, 25));
        histPanel.add(scroll, BorderLayout.CENTER);
        side.add(histPanel);

        return side;
    }

    /**
     * Creates a styled panel with a titled border.
     */
    private JPanel createStyledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(BG_PANEL);
        TitledBorder tb = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
            title
        );
        tb.setTitleFont(new Font("SansSerif", Font.BOLD, 11));
        tb.setTitleColor(ACCENT_GOLD);
        panel.setBorder(BorderFactory.createCompoundBorder(
            tb,
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        return panel;
    }

    /**
     * Creates the bottom status bar.
     */
    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(35, 35, 35));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 60)),
            BorderFactory.createEmptyBorder(8, 20, 8, 20)
        ));

        statusLabel = new JLabel("Your turn (Black) — click a highlighted cell");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        statusLabel.setForeground(TEXT_PRIMARY);
        bar.add(statusLabel, BorderLayout.WEST);

        JLabel credit = new JLabel("Minimax + Alpha-Beta Pruning");
        credit.setFont(new Font("SansSerif", Font.ITALIC, 11));
        credit.setForeground(TEXT_SECONDARY);
        bar.add(credit, BorderLayout.EAST);

        return bar;
    }

    // ════════════════════════════════════════════════════════════════
    //  GAME LOGIC
    // ════════════════════════════════════════════════════════════════

    /**
     * Updates score display labels.
     */
    private void updateScoreLabels() {
        int b = board.countDiscs(Board.BLACK);
        int w = board.countDiscs(Board.WHITE);
        if (blackCountLabel != null) blackCountLabel.setText(String.valueOf(b));
        if (whiteCountLabel != null) whiteCountLabel.setText(String.valueOf(w));
    }

    /**
     * Handles a human player clicking on the board.
     *
     * @param row the row clicked
     * @param col the column clicked
     */
    private void handleHumanMove(int row, int col) {
        if (!humanTurn || gameOver) return;

        Move move = new Move(row, col);
        if (!board.isValidMove(row, col, Board.BLACK)) return;

        int flipped = board.makeMove(move, Board.BLACK);
        lastMove = move;
        moveNumber++;
        appendHistory(moveNumber + ". B " + move + " (+" + flipped + ")");
        updateScoreLabels();

        currentPlayer = Board.WHITE;
        checkGameStateAndContinue();
    }

    /**
     * Checks the game state after a move and triggers the next turn.
     * Handles automatic passing and game-over detection.
     */
    private void checkGameStateAndContinue() {
        if (board.isGameOver()) {
            endGame();
            return;
        }

        List<Move> moves = board.getValidMoves(currentPlayer);
        if (moves.isEmpty()) {
            // Current player must pass
            String name = currentPlayer == Board.BLACK ? "Black" : "White (AI)";
            appendHistory("   " + name + " passes");
            statusLabel.setText(name + " has no moves — turn passes");
            currentPlayer = Board.opponent(currentPlayer);

            // Check again after pass
            if (board.isGameOver()) {
                endGame();
                return;
            }

            moves = board.getValidMoves(currentPlayer);
            if (moves.isEmpty()) {
                endGame();
                return;
            }
        }

        if (currentPlayer == Board.BLACK) {
            humanTurn = true;
            currentValidMoves = board.getValidMoves(Board.BLACK);
            statusLabel.setText("Your turn (Black) — click a highlighted cell");
            boardPanel.repaint();
        } else {
            humanTurn = false;
            currentValidMoves = null;
            statusLabel.setText("AI is thinking...");
            boardPanel.repaint();
            triggerAIMove();
        }
    }

    /**
     * Runs the AI move computation on a background thread using SwingWorker
     * to keep the UI responsive. Uses iterative deepening when enabled.
     */
    private void triggerAIMove() {
        final boolean useID = idCheckbox != null && idCheckbox.isSelected();
        final long timeBudgetMs = useID ? TIME_LIMITS_MS[timeLimitCombo.getSelectedIndex()] : 0;

        SwingWorker<Move, Void> worker = new SwingWorker<>() {
            long nodesExplored;
            int depth;
            double seconds;

            @Override
            protected Move doInBackground() {
                Move move;
                if (useID) {
                    move = ai.getBestMoveIterativeDeepening(board, timeBudgetMs);
                    nodesExplored = ai.getTotalNodesExplored();
                    depth = ai.getDepthReached();
                    seconds = ai.getTotalTimeMs() / 1000.0;
                } else {
                    long startTime = System.nanoTime();
                    move = ai.getBestMove(board);
                    nodesExplored = ai.getNodesExplored();
                    depth = ai.getSearchDepth();
                    seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
                }
                return move;
            }

            @Override
            protected void done() {
                try {
                    Move move = get();

                    if (move != null) {
                        int flipped = board.makeMove(move, Board.WHITE);
                        lastMove = move;
                        moveNumber++;
                        appendHistory(moveNumber + ". W " + move + " (+" + flipped + ")");
                        updateScoreLabels();

                        String modeLabel = useID ? "ID depth" : "Depth";
                        aiStatsLabel.setText(String.format(
                            "<html>" +
                            "Time: %.3f s<br>" +
                            "Nodes: %,d<br>" +
                            "%s: %d<br>" +
                            "Move: %s" +
                            "</html>",
                            seconds, nodesExplored, modeLabel, depth, move
                        ));
                    }

                    currentPlayer = Board.BLACK;
                    checkGameStateAndContinue();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    /**
     * Ends the game and displays the result.
     */
    private void endGame() {
        gameOver = true;
        humanTurn = false;
        currentValidMoves = null;
        boardPanel.repaint();
        updateScoreLabels();

        int b = board.countDiscs(Board.BLACK);
        int w = board.countDiscs(Board.WHITE);

        String result;
        if (b > w) {
            result = "Black wins!  " + b + " – " + w;
            statusLabel.setText("Game Over — You win!");
        } else if (w > b) {
            result = "White (AI) wins!  " + w + " – " + b;
            statusLabel.setText("Game Over — AI wins!");
        } else {
            result = "It's a draw!  " + b + " – " + w;
            statusLabel.setText("Game Over — Draw!");
        }

        appendHistory("\n--- GAME OVER ---");
        appendHistory(result);

        // Show dialog after a short delay so the board finishes painting
        Timer t = new Timer(300, e -> {
            int choice = JOptionPane.showOptionDialog(
                this,
                result + "\n\nPlay again?",
                "Game Over",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                new String[]{"New Game", "Close"},
                "New Game"
            );
            if (choice == 0) resetGame();
        });
        t.setRepeats(false);
        t.start();
    }

    /**
     * Resets the game to the initial state.
     */
    private void resetGame() {
        int depth = (int) depthSpinner.getValue();
        initGame(depth);
        historyArea.setText("");
        aiStatsLabel.setText("<html><br>Waiting...<br><br></html>");
        statusLabel.setText("Your turn (Black) — click a highlighted cell");
        updateScoreLabels();
        boardPanel.repaint();
    }

    /**
     * Appends a line to the move history text area.
     *
     * @param text the line to append
     */
    private void appendHistory(String text) {
        historyArea.append(text + "\n");
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
    }

    // ════════════════════════════════════════════════════════════════
    //  BOARD PANEL (Inner Class)
    // ════════════════════════════════════════════════════════════════

    /**
     * Custom JPanel that renders the Othello board with discs,
     * valid move indicators, hover effects, and coordinate labels.
     */
    private class BoardPanel extends JPanel implements MouseListener, MouseMotionListener {

        private static final int MARGIN = 30;
        private static final int CELL_SIZE = 64;
        private static final int BOARD_PX = CELL_SIZE * Board.SIZE;
        private static final int TOTAL = BOARD_PX + 2 * MARGIN;

        private int hoverRow = -1;
        private int hoverCol = -1;

        /**
         * Creates the board panel and registers mouse listeners.
         */
        public BoardPanel() {
            setPreferredSize(new Dimension(TOTAL, TOTAL));
            setBackground(BG_DARK);
            addMouseListener(this);
            addMouseMotionListener(this);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            drawBoard(g2);
            drawCoordinates(g2);
            drawDiscs(g2);
            drawValidMoves(g2);
            drawLastMove(g2);
            drawHover(g2);

            g2.dispose();
        }

        /**
         * Draws the board background and grid lines.
         */
        private void drawBoard(Graphics2D g2) {
            // Board background with subtle gradient
            GradientPaint gp = new GradientPaint(
                MARGIN, MARGIN, BOARD_GREEN_LIGHT,
                MARGIN + BOARD_PX, MARGIN + BOARD_PX, BOARD_GREEN
            );
            g2.setPaint(gp);
            g2.fillRoundRect(MARGIN - 2, MARGIN - 2, BOARD_PX + 4, BOARD_PX + 4, 8, 8);

            // Board border
            g2.setColor(new Color(0, 60, 30));
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(MARGIN - 2, MARGIN - 2, BOARD_PX + 4, BOARD_PX + 4, 8, 8);

            // Grid lines
            g2.setColor(GRID_LINE);
            g2.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i <= Board.SIZE; i++) {
                int pos = MARGIN + i * CELL_SIZE;
                g2.drawLine(pos, MARGIN, pos, MARGIN + BOARD_PX);
                g2.drawLine(MARGIN, pos, MARGIN + BOARD_PX, pos);
            }

            // Small dots at star points (like a Go board)
            g2.setColor(new Color(0, 60, 30));
            int[] stars = {2, 6};
            for (int r : stars) {
                for (int c : stars) {
                    int cx = MARGIN + c * CELL_SIZE;
                    int cy = MARGIN + r * CELL_SIZE;
                    g2.fillOval(cx - 4, cy - 4, 8, 8);
                }
            }
        }

        /**
         * Draws row and column coordinate labels around the board.
         */
        private void drawCoordinates(Graphics2D g2) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.setColor(TEXT_SECONDARY);
            for (int i = 0; i < Board.SIZE; i++) {
                String col = String.valueOf((char) ('a' + i));
                int x = MARGIN + i * CELL_SIZE + CELL_SIZE / 2 - 4;
                g2.drawString(col, x, MARGIN - 10);
                g2.drawString(col, x, MARGIN + BOARD_PX + 20);

                String row = String.valueOf(i + 1);
                int y = MARGIN + i * CELL_SIZE + CELL_SIZE / 2 + 5;
                g2.drawString(row, MARGIN - 20, y);
                g2.drawString(row, MARGIN + BOARD_PX + 10, y);
            }
        }

        /**
         * Draws all discs on the board with gradient shading for a 3D effect.
         */
        private void drawDiscs(Graphics2D g2) {
            int padding = 6;
            int discSize = CELL_SIZE - 2 * padding;

            for (int r = 0; r < Board.SIZE; r++) {
                for (int c = 0; c < Board.SIZE; c++) {
                    int piece = board.get(r, c);
                    if (piece == Board.EMPTY) continue;

                    int cx = MARGIN + c * CELL_SIZE + padding;
                    int cy = MARGIN + r * CELL_SIZE + padding;

                    // Shadow
                    g2.setColor(new Color(0, 0, 0, 50));
                    g2.fillOval(cx + 2, cy + 2, discSize, discSize);

                    // Disc body
                    if (piece == Board.BLACK) {
                        GradientPaint gp = new GradientPaint(
                            cx, cy, new Color(60, 60, 60),
                            cx + discSize, cy + discSize, BLACK_DISC
                        );
                        g2.setPaint(gp);
                    } else {
                        GradientPaint gp = new GradientPaint(
                            cx, cy, WHITE_SHINE,
                            cx + discSize, cy + discSize, new Color(200, 200, 200)
                        );
                        g2.setPaint(gp);
                    }
                    g2.fillOval(cx, cy, discSize, discSize);

                    // Disc border
                    g2.setColor(piece == Board.BLACK ? new Color(10, 10, 10) : new Color(150, 150, 150));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(cx, cy, discSize, discSize);

                    // Shine highlight
                    Color shine = piece == Board.BLACK ? BLACK_SHINE : WHITE_SHINE;
                    g2.setColor(new Color(shine.getRed(), shine.getGreen(), shine.getBlue(), 80));
                    g2.fillOval(cx + discSize / 4, cy + discSize / 6, discSize / 3, discSize / 4);
                }
            }
        }

        /**
         * Draws translucent indicators for valid moves.
         */
        private void drawValidMoves(Graphics2D g2) {
            if (currentValidMoves == null || gameOver) return;
            int padding = 20;
            int dotSize = CELL_SIZE - 2 * padding;

            for (Move m : currentValidMoves) {
                int cx = MARGIN + m.col * CELL_SIZE + padding;
                int cy = MARGIN + m.row * CELL_SIZE + padding;
                g2.setColor(VALID_MOVE_COLOR);
                g2.fillOval(cx, cy, dotSize, dotSize);
                g2.setColor(new Color(255, 255, 100, 140));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(cx, cy, dotSize, dotSize);
            }
        }

        /**
         * Draws a highlight on the last move played.
         */
        private void drawLastMove(Graphics2D g2) {
            if (lastMove == null) return;
            int x = MARGIN + lastMove.col * CELL_SIZE;
            int y = MARGIN + lastMove.row * CELL_SIZE;
            g2.setColor(LAST_MOVE_COLOR);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(x + 2, y + 2, CELL_SIZE - 4, CELL_SIZE - 4, 6, 6);
        }

        /**
         * Draws a hover effect when the mouse is over a valid move cell.
         */
        private void drawHover(Graphics2D g2) {
            if (hoverRow < 0 || !humanTurn || gameOver) return;
            if (!board.isValidMove(hoverRow, hoverCol, Board.BLACK)) return;

            int x = MARGIN + hoverCol * CELL_SIZE;
            int y = MARGIN + hoverRow * CELL_SIZE;
            g2.setColor(HOVER_COLOR);
            g2.fillRoundRect(x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2, 4, 4);

            // Show a ghost disc
            int padding = 6;
            int discSize = CELL_SIZE - 2 * padding;
            g2.setColor(new Color(20, 20, 20, 100));
            g2.fillOval(x + padding, y + padding, discSize, discSize);
        }

        /**
         * Converts pixel coordinates to board cell coordinates.
         */
        private int[] pixelToCell(int px, int py) {
            int col = (px - MARGIN) / CELL_SIZE;
            int row = (py - MARGIN) / CELL_SIZE;
            if (row >= 0 && row < Board.SIZE && col >= 0 && col < Board.SIZE) {
                return new int[]{row, col};
            }
            return null;
        }

        // ── Mouse Events ──────────────────────────────────────────

        @Override
        public void mouseClicked(MouseEvent e) {
            int[] cell = pixelToCell(e.getX(), e.getY());
            if (cell != null) {
                handleHumanMove(cell[0], cell[1]);
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            int[] cell = pixelToCell(e.getX(), e.getY());
            if (cell != null) {
                hoverRow = cell[0];
                hoverCol = cell[1];
            } else {
                hoverRow = -1;
                hoverCol = -1;
            }
            repaint();
        }

        @Override public void mousePressed(MouseEvent e) {}
        @Override public void mouseReleased(MouseEvent e) {}
        @Override public void mouseEntered(MouseEvent e) {}
        @Override public void mouseExited(MouseEvent e) { hoverRow = -1; hoverCol = -1; repaint(); }
        @Override public void mouseDragged(MouseEvent e) {}
    }

    // ════════════════════════════════════════════════════════════════
    //  MAIN
    // ════════════════════════════════════════════════════════════════

    /**
     * Entry point for the graphical Othello game.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        // Use system look and feel for native appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(OthelloGUI::new);
    }
}
