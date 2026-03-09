/**
 * Interface for board evaluation functions used by the configurable AI player.
 * Implementations provide different heuristic strategies for scoring board positions.
 */
public interface EvaluationFunction {

    /**
     * Evaluates the board position from the perspective of the specified player.
     *
     * @param board   the current board state
     * @param aiColor the color of the AI player (Board.BLACK or Board.WHITE)
     * @return the evaluation score (positive favors aiColor, negative favors opponent)
     */
    int evaluate(Board board, int aiColor);

    /**
     * Returns a human-readable name for this evaluation function.
     *
     * @return the evaluation function name
     */
    String getName();
}
