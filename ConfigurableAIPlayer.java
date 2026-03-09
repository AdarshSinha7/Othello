import java.util.Comparator;
import java.util.List;

/**
 * A configurable AI player for Othello that supports pluggable evaluation functions
 * and optional alpha-beta pruning and move ordering.
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
     * and move ordering.
     *
     * @param board the current board state
     * @return the best move found, or null if no legal move exists
     */
    public Move getBestMove(Board board) {
        nodesExplored = 0;
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
     * Minimax search with optional alpha-beta pruning.
     *
     * @param board        the current board state
     * @param depth        remaining search depth
     * @param alpha        alpha bound (ignored if alpha-beta is disabled)
     * @param beta         beta bound (ignored if alpha-beta is disabled)
     * @param isMaximizing true if maximizing player's turn
     * @return the evaluated score
     */
    private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing) {
        nodesExplored++;

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
