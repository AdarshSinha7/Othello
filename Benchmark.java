import java.util.List;

/**
 * Runs a single AI-vs-AI Othello match and collects performance statistics.
 */
public class Benchmark {

    /**
     * Holds the result of a single AI-vs-AI game.
     */
    public static class GameResult {

        /** Winner: Board.BLACK, Board.WHITE, or Board.EMPTY for a draw */
        public final int winner;

        /** Number of black discs at game end */
        public final int blackDiscs;

        /** Number of white discs at game end */
        public final int whiteDiscs;

        /** Total moves played (not counting passes) */
        public final int totalMoves;

        /** Total nodes explored by black across all moves */
        public final long blackNodesTotal;

        /** Total nodes explored by white across all moves */
        public final long whiteNodesTotal;

        /** Total wall-clock time spent by black in milliseconds */
        public final long blackTimeMs;

        /** Total wall-clock time spent by white in milliseconds */
        public final long whiteTimeMs;

        /**
         * Creates a game result record.
         *
         * @param winner          the winning color or EMPTY for draw
         * @param blackDiscs      black disc count at end
         * @param whiteDiscs      white disc count at end
         * @param totalMoves      total moves made
         * @param blackNodesTotal total nodes explored by black
         * @param whiteNodesTotal total nodes explored by white
         * @param blackTimeMs     total thinking time for black in ms
         * @param whiteTimeMs     total thinking time for white in ms
         */
        public GameResult(int winner, int blackDiscs, int whiteDiscs, int totalMoves,
                          long blackNodesTotal, long whiteNodesTotal,
                          long blackTimeMs, long whiteTimeMs) {
            this.winner = winner;
            this.blackDiscs = blackDiscs;
            this.whiteDiscs = whiteDiscs;
            this.totalMoves = totalMoves;
            this.blackNodesTotal = blackNodesTotal;
            this.whiteNodesTotal = whiteNodesTotal;
            this.blackTimeMs = blackTimeMs;
            this.whiteTimeMs = whiteTimeMs;
        }

        /**
         * Returns the winner as a readable string.
         *
         * @return "Black", "White", or "Draw"
         */
        public String winnerString() {
            if (winner == Board.BLACK) return "Black";
            if (winner == Board.WHITE) return "White";
            return "Draw";
        }
    }

    /**
     * Plays a complete game between two configurable AI players.
     * Black moves first. Turns pass automatically when a player has no moves.
     *
     * @param black the AI playing as black
     * @param white the AI playing as white
     * @return the game result with all statistics
     */
    public static GameResult playGame(ConfigurableAIPlayer black, ConfigurableAIPlayer white) {
        Board board = new Board();
        int currentPlayer = Board.BLACK;
        int totalMoves = 0;
        long blackNodes = 0;
        long whiteNodes = 0;
        long blackTimeMs = 0;
        long whiteTimeMs = 0;

        while (!board.isGameOver()) {
            List<Move> moves = board.getValidMoves(currentPlayer);

            if (moves.isEmpty()) {
                // Pass turn
                currentPlayer = Board.opponent(currentPlayer);
                continue;
            }

            ConfigurableAIPlayer player = (currentPlayer == Board.BLACK) ? black : white;
            long startNano = System.nanoTime();
            Move move = player.getBestMove(board);
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;

            if (move != null) {
                board.makeMove(move, currentPlayer);
                totalMoves++;

                if (currentPlayer == Board.BLACK) {
                    blackNodes += player.getNodesExplored();
                    blackTimeMs += elapsedMs;
                } else {
                    whiteNodes += player.getNodesExplored();
                    whiteTimeMs += elapsedMs;
                }
            }

            currentPlayer = Board.opponent(currentPlayer);
        }

        int bDiscs = board.countDiscs(Board.BLACK);
        int wDiscs = board.countDiscs(Board.WHITE);
        int winner;
        if (bDiscs > wDiscs) winner = Board.BLACK;
        else if (wDiscs > bDiscs) winner = Board.WHITE;
        else winner = Board.EMPTY;

        return new GameResult(winner, bDiscs, wDiscs, totalMoves,
                              blackNodes, whiteNodes, blackTimeMs, whiteTimeMs);
    }
}
