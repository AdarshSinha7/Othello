/**
 * Evaluation function that considers disc parity and mobility (number of legal moves).
 * Both components are weighted equally.
 */
public class MobilityEval implements EvaluationFunction {

    /**
     * Evaluates the board using disc parity and mobility with equal weights.
     *
     * @param board   the current board state
     * @param aiColor the AI player's color
     * @return combined score of disc parity and mobility components
     */
    @Override
    public int evaluate(Board board, int aiColor) {
        int opp = Board.opponent(aiColor);
        int myDiscs = board.countDiscs(aiColor);
        int oppDiscs = board.countDiscs(opp);

        if (board.isGameOver()) {
            if (myDiscs > oppDiscs) return 100000 + (myDiscs - oppDiscs);
            if (myDiscs < oppDiscs) return -100000 - (oppDiscs - myDiscs);
            return 0;
        }

        // Disc parity
        double discParity = 0;
        if (myDiscs + oppDiscs != 0) {
            discParity = 100.0 * (myDiscs - oppDiscs) / (myDiscs + oppDiscs);
        }

        // Mobility
        int myMoves = board.getValidMoves(aiColor).size();
        int oppMoves = board.getValidMoves(opp).size();
        double mobility = 0;
        if (myMoves + oppMoves != 0) {
            mobility = 100.0 * (myMoves - oppMoves) / (myMoves + oppMoves);
        }

        return (int) (discParity + mobility);
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "Mobility (parity + mobility)";
    }
}
