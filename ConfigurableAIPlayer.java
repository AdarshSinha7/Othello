import java.util.Comparator;
import java.util.List;

/**
 * A configurable AI player for Othello that supports pluggable evaluation functions
 * and optional alpha-beta pruning and move ordering.
 *
 * <p>Supports both fixed-depth search ({@link #getBestMove}) and
 * iterative deepening with a time budget ({@link #getBestMoveIterativeDeepening}).</p>
 *
 * <p>Used by the benchmarking system to compare different AI configurations.</p>
 */
public class ConfigurableAIPlayer {

    /** Positional weight table for move ordering (identical to AIPlayer) */
    private static final int[][] POSITION_WEIGHTS = {
        { 100, -20,  10,   5,   5,  10, -20, 100},
        { -20, -50,  -2,  -2,  -2,  -2, -50, -20},
        {  10,  -2,   5,   1,   1,   5,  -2,  10},
        {   5,  -2,   1,   0,   0,   1,  -2,   5},
        {   5,  -2,   1,   0,   0,   1,  -2,   5},
        {  10,  -2,   5,   1,   1,   5,  -2,  10},
        { -20, -50,  -2,  -2,  -2,  -2, -50, -20},
        { 100, -20,  10,   5,   5,  10, -20, 100}
    };

    private final int color;
    private int searchDepth;
    private final EvaluationFunction evalFn;
    private final boolean useAlphaBeta;
    private final boolean useMoveOrdering;
    private long nodesExplored;

    // ── Iterative deepening state ──────────────────────────────────
    /** Depth actually reached by the last iterative deepening search */
    private int depthReached;

    /** Total nodes explored across all iterations of the last ID search */
    private long totalNodesExplored;

    /** Total time in milliseconds for the last ID search */
    private long totalTimeMs;

    /** Search start time (nanos) for timeout checks */
    private long searchStartNano;

    /** Time limit in nanos */
    private long timeLimitNano;

    /** Whether a timed search is active */
    private boolean timedSearchActive;

    /**
     * Thrown internally when a timed search exceeds its time budget.
     */
    private static class SearchTimeoutException extends RuntimeException {
        SearchTimeoutException() { super("search timeout"); }
    }

    /**
     * Creates a configurable AI player.
     *
     * @param color           the AI's disc color (Board.BLACK or Board.WHITE)
     * @param depth           the maximum search depth for minimax
     * @param evalFn          the evaluation function to use for scoring positions
     * @param useAlphaBeta    if true, use alpha-beta pruning; if false, pure minimax
     * @param useMoveOrdering if true, sort moves by positional weight before searching
     */
    public ConfigurableAIPlayer(int color, int depth, EvaluationFunction evalFn,
                                 boolean useAlphaBeta, boolean useMoveOrdering) {
        this.color = color;
        this.searchDepth = depth;
        this.evalFn = evalFn;
        this.useAlphaBeta = useAlphaBeta;
        this.useMoveOrdering = useMoveOrdering;
        this.nodesExplored = 0;
        this.timedSearchActive = false;
    }

    /**
     * Returns the AI's disc color.
     *
     * @return Board.BLACK or Board.WHITE
     */
    public int getColor() {
        return color;
    }

    /**
     * Returns the number of nodes explored in the last search.
     *
     * @return node count
     */
    public long getNodesExplored() {
        return nodesExplored;
    }

    /**
     * Returns the current search depth.
     *
     * @return the search depth
     */
    public int getSearchDepth() {
        return searchDepth;
    }

    /**
     * Sets the search depth.
     *
     * @param depth the new search depth (must be positive)
     */
    public void setSearchDepth(int depth) {
        if (depth > 0) this.searchDepth = depth;
    }

    /**
     * Returns the name of the evaluation function in use.
     *
     * @return evaluation function name
     */
    public String getEvalName() {
        return evalFn.getName();
    }

    /**
     * Returns whether alpha-beta pruning is enabled.
     *
     * @return true if alpha-beta is on
     */
    public boolean isAlphaBetaEnabled() {
        return useAlphaBeta;
    }

    /**
     * Returns whether move ordering is enabled.
     *
     * @return true if move ordering is on
     */
    public boolean isMoveOrderingEnabled() {
        return useMoveOrdering;
    }

    /**
     * Returns the depth actually reached by the last iterative deepening search.
     *
     * @return the depth of the last fully completed iteration
     */
    public int getDepthReached() {
        return depthReached;
    }

    /**
     * Returns the total nodes explored across all iterations of the last
     * iterative deepening search.
     *
     * @return total nodes explored
     */
    public long getTotalNodesExplored() {
        return totalNodesExplored;
    }

    /**
     * Returns the total wall-clock time in milliseconds for the last
     * iterative deepening search.
     *
     * @return total search time in ms
     */
    public long getTotalTimeMs() {
        return totalTimeMs;
    }

    /**
     * Returns a short description of this player's configuration.
     *
     * @return config string like "FullEval d6 AB+MO"
     */
    public String getConfigDescription() {
        String evalShort = evalFn.getName().split(" ")[0];
        String ab = useAlphaBeta ? "AB" : "noAB";
        String mo = useMoveOrdering ? "MO" : "noMO";
        return evalShort + " d" + searchDepth + " " + ab + "+" + mo;
    }

    /**
     * Selects the best move using minimax, optionally with alpha-beta pruning
     * and move ordering. This method is NOT affected by any timeout mechanism.
     *
     * @param board the current board state
     * @return the best move found, or null if no legal move exists
     */
    public Move getBestMove(Board board) {
        nodesExplored = 0;
        timedSearchActive = false;
        List<Move> validMoves = board.getValidMoves(color);
        if (validMoves.isEmpty()) return null;

        if (useMoveOrdering) {
            validMoves.sort(Comparator.comparingInt(
                (Move m) -> POSITION_WEIGHTS[m.row][m.col]).reversed());
        }

        Move bestMove = validMoves.get(0);
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (Move move : validMoves) {
            Board child = new Board(board);
            child.makeMove(move, color);
            int score = minimax(child, searchDepth - 1, alpha, beta, false);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (useAlphaBeta) {
                alpha = Math.max(alpha, bestScore);
            }
        }

        return bestMove;
    }

    /**
     * Selects the best move using iterative deepening with a time budget.
     * Searches at depth 1, then 2, then 3, etc. Respects the configured
     * useAlphaBeta and useMoveOrdering flags.
     *
     * <p>The best move from the previous depth is tried first at the next
     * depth to maximize alpha-beta cutoffs.</p>
     *
     * @param board       the current board state
     * @param timeLimitMs the time budget in milliseconds
     * @return the best move found, or null if no legal move exists
     */
    public Move getBestMoveIterativeDeepening(Board board, long timeLimitMs) {
        List<Move> validMoves = board.getValidMoves(color);
        if (validMoves.isEmpty()) return null;

        searchStartNano = System.nanoTime();
        timeLimitNano = timeLimitMs * 1_000_000L;
        totalNodesExplored = 0;
        depthReached = 0;

        // Initial move ordering
        if (useMoveOrdering) {
            validMoves.sort(Comparator.comparingInt(
                (Move m) -> POSITION_WEIGHTS[m.row][m.col]).reversed());
        }

        Move overallBestMove = validMoves.get(0);

        for (int depth = 1; depth <= 64; depth++) {
            if (System.nanoTime() - searchStartNano >= timeLimitNano) break;

            nodesExplored = 0;
            timedSearchActive = true;

            try {
                Move bestMoveThisDepth = validMoves.get(0);
                int bestScore = Integer.MIN_VALUE;
                int alpha = Integer.MIN_VALUE;
                int beta = Integer.MAX_VALUE;

                for (Move move : validMoves) {
                    Board child = new Board(board);
                    child.makeMove(move, color);
                    int score = minimax(child, depth - 1, alpha, beta, false);
                    if (score > bestScore) {
                        bestScore = score;
                        bestMoveThisDepth = move;
                    }
                    if (useAlphaBeta) {
                        alpha = Math.max(alpha, bestScore);
                    }
                }

                // Depth completed successfully
                overallBestMove = bestMoveThisDepth;
                depthReached = depth;
                totalNodesExplored += nodesExplored;

                // Reorder moves: best move first for next iteration
                if (useMoveOrdering) {
                    final Move bestForReorder = bestMoveThisDepth;
                    validMoves.sort((a, b) -> {
                        if (a.equals(bestForReorder)) return -1;
                        if (b.equals(bestForReorder)) return 1;
                        return Integer.compare(POSITION_WEIGHTS[b.row][b.col],
                                               POSITION_WEIGHTS[a.row][a.col]);
                    });
                }

            } catch (SearchTimeoutException e) {
                totalNodesExplored += nodesExplored;
                break;
            }
        }

        timedSearchActive = false;
        totalTimeMs = (System.nanoTime() - searchStartNano) / 1_000_000;
        return overallBestMove;
    }

    /**
     * Minimax search with optional alpha-beta pruning.
     * When called during a timed search, checks for timeout periodically.
     *
     * @param board        the current board state
     * @param depth        remaining search depth
     * @param alpha        alpha bound (ignored if alpha-beta is disabled)
     * @param beta         beta bound (ignored if alpha-beta is disabled)
     * @param isMaximizing true if maximizing player's turn
     * @return the evaluated score
     * @throws SearchTimeoutException if a timed search exceeds its budget
     */
    private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing) {
        nodesExplored++;

        // Timeout check — only active during iterative deepening
        if (timedSearchActive && (nodesExplored & 1023) == 0) {
            if (System.nanoTime() - searchStartNano >= timeLimitNano) {
                throw new SearchTimeoutException();
            }
        }

        if (depth == 0 || board.isGameOver()) {
            return evalFn.evaluate(board, color);
        }

        int currentPlayer = isMaximizing ? color : Board.opponent(color);
        List<Move> moves = board.getValidMoves(currentPlayer);

        if (moves.isEmpty()) {
            return minimax(board, depth - 1, alpha, beta, !isMaximizing);
        }

        if (useMoveOrdering) {
            moves.sort(Comparator.comparingInt(
                (Move m) -> POSITION_WEIGHTS[m.row][m.col]).reversed());
        }

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                Board child = new Board(board);
                child.makeMove(move, currentPlayer);

                int a = useAlphaBeta ? alpha : Integer.MIN_VALUE;
                int b = useAlphaBeta ? beta : Integer.MAX_VALUE;
                int eval = minimax(child, depth - 1, a, b, false);

                maxEval = Math.max(maxEval, eval);
                if (useAlphaBeta) {
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) break;
                }
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                Board child = new Board(board);
                child.makeMove(move, currentPlayer);

                int a = useAlphaBeta ? alpha : Integer.MIN_VALUE;
                int b = useAlphaBeta ? beta : Integer.MAX_VALUE;
                int eval = minimax(child, depth - 1, a, b, true);

                minEval = Math.min(minEval, eval);
                if (useAlphaBeta) {
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break;
                }
            }
            return minEval;
        }
    }
}
