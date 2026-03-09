/**
 * Full evaluation function ported from AIPlayer.java.
 * Considers all 6 heuristic components with game-phase-dependent weights:
 * disc parity, mobility, corner occupancy, stability, edge control,
 * and positional weights.
 *
 * <p>Produces identical scores to the original AIPlayer.evaluate() method.</p>
 */
public class FullEval implements EvaluationFunction {

    /** Positional weight table — identical to AIPlayer.POSITION_WEIGHTS */
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
     * Evaluates the board using all 6 heuristic components with phase-dependent weights.
     *
     * <ul>
     *   <li>Disc parity: normalized difference in disc counts</li>
     *   <li>Mobility: normalized difference in legal move counts</li>
     *   <li>Corner occupancy: normalized difference in corner ownership</li>
     *   <li>Stability: normalized difference in stable disc counts</li>
     *   <li>Edge control: normalized difference in edge cell ownership</li>
     *   <li>Positional weight: sum of positional values for each disc</li>
     * </ul>
     *
     * Game phases: Early (≤20 discs), Mid (21–50), Late (>50).
     *
     * @param board   the current board state
     * @param aiColor the AI player's color
     * @return the weighted evaluation score
     */
    @Override
    public int evaluate(Board board, int aiColor) {
        int opp = Board.opponent(aiColor);
        int totalDiscs = board.totalDiscs();

        boolean isEarly = totalDiscs <= 20;
        boolean isLate = totalDiscs > 50;

        // --- Disc Parity ---
        int myDiscs = board.countDiscs(aiColor);
        int oppDiscs = board.countDiscs(opp);
        double discParity = 0;
        if (myDiscs + oppDiscs != 0) {
            discParity = 100.0 * (myDiscs - oppDiscs) / (myDiscs + oppDiscs);
        }

        // --- Mobility ---
        int myMoves = board.getValidMoves(aiColor).size();
        int oppMoves = board.getValidMoves(opp).size();
        double mobility = 0;
        if (myMoves + oppMoves != 0) {
            mobility = 100.0 * (myMoves - oppMoves) / (myMoves + oppMoves);
        }

        // --- Corner Occupancy ---
        int myCorners = board.countCorners(aiColor);
        int oppCorners = board.countCorners(opp);
        double cornerScore = 0;
        if (myCorners + oppCorners != 0) {
            cornerScore = 100.0 * (myCorners - oppCorners) / (myCorners + oppCorners);
        }

        // --- Stability ---
        int myStable = board.countStableDiscs(aiColor);
        int oppStable = board.countStableDiscs(opp);
        double stability = 0;
        if (myStable + oppStable != 0) {
            stability = 100.0 * (myStable - oppStable) / (myStable + oppStable);
        }

        // --- Edge Control ---
        int myEdges = board.countEdges(aiColor);
        int oppEdges = board.countEdges(opp);
        double edgeScore = 0;
        if (myEdges + oppEdges != 0) {
            edgeScore = 100.0 * (myEdges - oppEdges) / (myEdges + oppEdges);
        }

        // --- Positional Weight ---
        int posScore = 0;
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (board.get(r, c) == aiColor) {
                    posScore += POSITION_WEIGHTS[r][c];
                } else if (board.get(r, c) == opp) {
                    posScore -= POSITION_WEIGHTS[r][c];
                }
            }
        }

        // --- Phase-dependent weights ---
        double wDiscParity, wMobility, wCorner, wStability, wEdge, wPosition;

        if (isEarly) {
            wDiscParity = 1;
            wMobility = 50;
            wCorner = 300;
            wStability = 25;
            wEdge = 15;
            wPosition = 30;
        } else if (isLate) {
            wDiscParity = 50;
            wMobility = 5;
            wCorner = 300;
            wStability = 100;
            wEdge = 10;
            wPosition = 5;
        } else {
            wDiscParity = 10;
            wMobility = 30;
            wCorner = 300;
            wStability = 50;
            wEdge = 20;
            wPosition = 15;
        }

        // Game over: definitive score
        if (board.isGameOver()) {
            if (myDiscs > oppDiscs) return 100000 + (myDiscs - oppDiscs);
            if (myDiscs < oppDiscs) return -100000 - (oppDiscs - myDiscs);
            return 0;
        }

        return (int) (wDiscParity * discParity
                     + wMobility * mobility
                     + wCorner * cornerScore
                     + wStability * stability
                     + wEdge * edgeScore
                     + wPosition * posScore);
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "Full (all heuristics, phase-weighted)";
    }
}
