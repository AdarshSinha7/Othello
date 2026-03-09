/**
 * Simple evaluation function that only considers disc parity.
 * Score is normalized to the range [-100, 100].
 */
public class SimpleEval implements EvaluationFunction {

    /**
     * Evaluates the board based solely on the difference in disc counts.
     *
     * @param board   the current board state
     * @param aiColor the AI player's color
     * @return 100 * (myDiscs - oppDiscs) / (myDiscs + oppDiscs), or +/-100000 at game end
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

        if (myDiscs + oppDiscs == 0) return 0;
        return (int) (100.0 * (myDiscs - oppDiscs) / (myDiscs + oppDiscs));
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "Simple (disc parity only)";
    }
}
