import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * AI opponent for Othello using minimax search with alpha-beta pruning.
 * The evaluation function considers disc parity, mobility, corner occupancy,
 * stability, and edge control, with weights that shift by game phase.
 */
public class AIPlayer {

    /** Default search depth */
    private static final int DEFAULT_DEPTH = 6;

    /** The AI's color (BLACK or WHITE) */
    private final int color;

    /** Maximum search depth for minimax */
    private int searchDepth;

    /** Counter for nodes explored during a single search */
    private long nodesExplored;

    /**
     * Positional weight table reflecting strategic value of each cell.
     * Corners are highly valued, cells adjacent to corners (X-squares, C-squares)
     * are penalized.
     */
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

    /**
     * Creates an AI player with the default search depth.
     *
     * @param color the AI's disc color (BLACK or WHITE)
     */
    public AIPlayer(int color) {
        this(color, DEFAULT_DEPTH);
    }

    /**
     * Creates an AI player with a specified search depth.
     *
     * @param color the AI's disc color (BLACK or WHITE)
     * @param depth the maximum search depth for minimax
     */
    public AIPlayer(int color, int depth) {
        this.color = color;
        this.searchDepth = depth;
        this.nodesExplored = 0;
    }

    /**
     * Returns the number of nodes explored in the last search.
     *
     * @return nodes explored count
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
     * Sets the search depth for the AI.
     *
     * @param depth the new search depth (must be positive)
     */
    public void setSearchDepth(int depth) {
        if (depth > 0) {
            this.searchDepth = depth;
        }
    }

    /**
     * Selects the best move for the AI using minimax with alpha-beta pruning.
     * Moves are ordered to improve pruning efficiency: corners first, then
     * edges, then interior moves (avoiding X/C-squares when possible).
     *
     * @param board the current board state
     * @return the best move found, or null if no legal move exists
     */
    public Move getBestMove(Board board) {
        nodesExplored = 0;
        List<Move> validMoves = board.getValidMoves(color);
        if (validMoves.isEmpty()) return null;

        // Move ordering: sort by positional weight descending for better pruning
        validMoves.sort(Comparator.comparingInt(
            (Move m) -> POSITION_WEIGHTS[m.row][m.col]).reversed());

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
            alpha = Math.max(alpha, bestScore);
        }

        return bestMove;
    }

    /**
     * Minimax algorithm with alpha-beta pruning.
     *
     * @param board          the current board state
     * @param depth          remaining search depth
     * @param alpha          the alpha bound (best score for maximizer)
     * @param beta           the beta bound (best score for minimizer)
     * @param isMaximizing   true if this is the maximizing player's turn
     * @return the evaluated score of the board position
     */
    private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing) {
        nodesExplored++;

        if (depth == 0 || board.isGameOver()) {
            return evaluate(board);
        }

        int currentPlayer = isMaximizing ? color : Board.opponent(color);
        List<Move> moves = board.getValidMoves(currentPlayer);

        // If current player has no moves, pass the turn to opponent
        if (moves.isEmpty()) {
            return minimax(board, depth - 1, alpha, beta, !isMaximizing);
        }

        // Move ordering for better pruning
        moves.sort(Comparator.comparingInt(
            (Move m) -> POSITION_WEIGHTS[m.row][m.col]).reversed());

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                Board child = new Board(board);
                child.makeMove(move, currentPlayer);
                int eval = minimax(child, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break; // Beta cutoff
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                Board child = new Board(board);
                child.makeMove(move, currentPlayer);
                int eval = minimax(child, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break; // Alpha cutoff
            }
            return minEval;
        }
    }

    /**
     * Evaluates the board position from the AI's perspective.
     * The evaluation considers multiple heuristics with weights that
     * shift based on the game phase (early, mid, late).
     *
     * <ul>
     *   <li>Disc parity: difference in disc counts</li>
     *   <li>Mobility: difference in number of legal moves</li>
     *   <li>Corner occupancy: corners are extremely valuable</li>
     *   <li>Stability: discs that can never be flipped</li>
     *   <li>Edge control: occupation of edge cells</li>
     *   <li>Positional weight: static positional value of each disc</li>
     * </ul>
     *
     * @param board the board to evaluate
     * @return the evaluation score (positive favors AI, negative favors opponent)
     */
    private int evaluate(Board board) {
        int opp = Board.opponent(color);
        int totalDiscs = board.totalDiscs();

        // Determine game phase
        // Early: <= 20 discs, Mid: 21-50, Late: > 50
        double phaseWeight;
        boolean isEarly = totalDiscs <= 20;
        boolean isLate = totalDiscs > 50;

        // --- Disc Parity ---
        int myDiscs = board.countDiscs(color);
        int oppDiscs = board.countDiscs(opp);
        double discParity = 0;
        if (myDiscs + oppDiscs != 0) {
            discParity = 100.0 * (myDiscs - oppDiscs) / (myDiscs + oppDiscs);
        }

        // --- Mobility ---
        int myMoves = board.getValidMoves(color).size();
        int oppMoves = board.getValidMoves(opp).size();
        double mobility = 0;
        if (myMoves + oppMoves != 0) {
            mobility = 100.0 * (myMoves - oppMoves) / (myMoves + oppMoves);
        }

        // --- Corner Occupancy ---
        int myCorners = board.countCorners(color);
        int oppCorners = board.countCorners(opp);
        double cornerScore = 0;
        if (myCorners + oppCorners != 0) {
            cornerScore = 100.0 * (myCorners - oppCorners) / (myCorners + oppCorners);
        }

        // --- Stability ---
        int myStable = board.countStableDiscs(color);
        int oppStable = board.countStableDiscs(opp);
        double stability = 0;
        if (myStable + oppStable != 0) {
            stability = 100.0 * (myStable - oppStable) / (myStable + oppStable);
        }

        // --- Edge Control ---
        int myEdges = board.countEdges(color);
        int oppEdges = board.countEdges(opp);
        double edgeScore = 0;
        if (myEdges + oppEdges != 0) {
            edgeScore = 100.0 * (myEdges - oppEdges) / (myEdges + oppEdges);
        }

        // --- Positional Weight ---
        int posScore = 0;
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (board.get(r, c) == color) {
                    posScore += POSITION_WEIGHTS[r][c];
                } else if (board.get(r, c) == opp) {
                    posScore -= POSITION_WEIGHTS[r][c];
                }
            }
        }

        // --- Phase-dependent weights ---
        double wDiscParity, wMobility, wCorner, wStability, wEdge, wPosition;

        if (isEarly) {
            // Early game: prioritize mobility and position, avoid disc maximization
            wDiscParity = 1;
            wMobility = 50;
            wCorner = 300;
            wStability = 25;
            wEdge = 15;
            wPosition = 30;
        } else if (isLate) {
            // Late game: disc count matters most, stability is critical
            wDiscParity = 50;
            wMobility = 5;
            wCorner = 300;
            wStability = 100;
            wEdge = 10;
            wPosition = 5;
        } else {
            // Mid game: balanced approach
            wDiscParity = 10;
            wMobility = 30;
            wCorner = 300;
            wStability = 50;
            wEdge = 20;
            wPosition = 15;
        }

        // If the game is over, return a definitive score
        if (board.isGameOver()) {
            if (myDiscs > oppDiscs) return 100000 + (myDiscs - oppDiscs);
            if (myDiscs < oppDiscs) return -100000 - (oppDiscs - myDiscs);
            return 0; // Draw
        }

        return (int) (wDiscParity * discParity
                     + wMobility * mobility
                     + wCorner * cornerScore
                     + wStability * stability
                     + wEdge * edgeScore
                     + wPosition * posScore);
    }
}
