import java.util.List;
import java.util.Scanner;

/**
 * Main game loop for Othello.
 * Manages turn alternation between a human player (Black) and an AI opponent (White).
 * Handles user input parsing, automatic pass detection, score display, and game end.
 */
public class Game {

    /** The game board */
    private Board board;

    /** The AI opponent */
    private AIPlayer ai;

    /** Scanner for reading user input */
    private Scanner scanner;

    /**
     * Creates a new Othello game with default AI search depth (6).
     */
    public Game() {
        this(6);
    }

    /**
     * Creates a new Othello game with a specified AI search depth.
     *
     * @param aiDepth the search depth for the AI's minimax algorithm
     */
    public Game(int aiDepth) {
        board = new Board();
        ai = new AIPlayer(Board.WHITE, aiDepth);
        scanner = new Scanner(System.in);
    }

    /**
     * Runs the main game loop.
     * Black (human) moves first. Turns alternate, with automatic passing
     * when a player has no legal moves. The game ends when neither player
     * can move. Final scores and the winner are displayed.
     */
    public void play() {
        System.out.println("===========================================");
        System.out.println("          OTHELLO (REVERSI)");
        System.out.println("===========================================");
        System.out.println("You play as Black (B), AI plays as White (W)");
        System.out.println("Enter moves as: d3  or  3 4  (row col)");
        System.out.println("AI search depth: " + ai.getSearchDepth());
        System.out.println("===========================================");

        int currentPlayer = Board.BLACK;

        while (!board.isGameOver()) {
            board.display(currentPlayer);
            displayScore();

            List<Move> validMoves = board.getValidMoves(currentPlayer);

            if (validMoves.isEmpty()) {
                // Current player has no moves — pass
                String name = currentPlayer == Board.BLACK ? "Black" : "White";
                System.out.println(name + " has no valid moves. Turn passes.");
                currentPlayer = Board.opponent(currentPlayer);
                continue;
            }

            if (currentPlayer == Board.BLACK) {
                handleHumanTurn(validMoves);
            } else {
                handleAITurn();
            }

            currentPlayer = Board.opponent(currentPlayer);
        }

        // Game over
        board.display(Board.BLACK);
        displayFinalResult();
    }

    /**
     * Handles the human player's turn.
     * Prompts for input, validates the move, and applies it to the board.
     *
     * @param validMoves the list of legal moves for the human player
     */
    private void handleHumanTurn(List<Move> validMoves) {
        System.out.print("Valid moves: ");
        for (int i = 0; i < validMoves.size(); i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(validMoves.get(i));
        }
        System.out.println();

        while (true) {
            System.out.print("Your move (Black): ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.isEmpty()) continue;

            Move move = parseMove(input);
            if (move == null) {
                System.out.println("Invalid input format. Use letter-number (e.g. d3) or row col (e.g. 3 4).");
                continue;
            }

            if (!board.isValidMove(move.row, move.col, Board.BLACK)) {
                System.out.println("Illegal move. Choose from the valid moves marked with *.");
                continue;
            }

            int flipped = board.makeMove(move, Board.BLACK);
            System.out.println("You placed at " + move + ", flipping " + flipped + " disc(s).");
            break;
        }
    }

    /**
     * Handles the AI player's turn.
     * Computes the best move using minimax, displays timing and node statistics.
     */
    private void handleAITurn() {
        System.out.println("AI (White) is thinking...");

        long startTime = System.nanoTime();
        Move move = ai.getBestMove(board);
        long elapsed = System.nanoTime() - startTime;

        if (move == null) {
            System.out.println("AI has no valid moves (should not reach here).");
            return;
        }

        int flipped = board.makeMove(move, Board.WHITE);
        double seconds = elapsed / 1_000_000_000.0;

        System.out.printf("AI plays %s, flipping %d disc(s).%n", move, flipped);
        System.out.printf("  Time: %.3f s | Nodes explored: %,d | Depth: %d%n",
                seconds, ai.getNodesExplored(), ai.getSearchDepth());
    }

    /**
     * Parses user input into a Move.
     * Supports two formats:
     * <ul>
     *   <li>Letter-number: "d3" — column letter (a-h) followed by row number (1-8)</li>
     *   <li>Row-column numbers: "3 4" — 1-indexed row and column separated by space</li>
     * </ul>
     *
     * @param input the user's input string
     * @return the parsed Move, or null if the input is invalid
     */
    private Move parseMove(String input) {
        // Try letter-number format: e.g. "d3"
        if (input.length() == 2 && Character.isLetter(input.charAt(0)) && Character.isDigit(input.charAt(1))) {
            int col = input.charAt(0) - 'a';
            int row = input.charAt(1) - '1';
            if (row >= 0 && row < Board.SIZE && col >= 0 && col < Board.SIZE) {
                return new Move(row, col);
            }
        }

        // Try row col format: e.g. "3 4"
        String[] parts = input.split("\\s+");
        if (parts.length == 2) {
            try {
                int row = Integer.parseInt(parts[0]) - 1;
                int col = Integer.parseInt(parts[1]) - 1;
                if (row >= 0 && row < Board.SIZE && col >= 0 && col < Board.SIZE) {
                    return new Move(row, col);
                }
            } catch (NumberFormatException e) {
                // Fall through to return null
            }
        }

        return null;
    }

    /**
     * Displays the current score (disc counts for both players).
     */
    private void displayScore() {
        int black = board.countDiscs(Board.BLACK);
        int white = board.countDiscs(Board.WHITE);
        System.out.println("Score — Black: " + black + "  White: " + white);
    }

    /**
     * Displays the final game result including winner and disc counts.
     */
    private void displayFinalResult() {
        int black = board.countDiscs(Board.BLACK);
        int white = board.countDiscs(Board.WHITE);

        System.out.println("===========================================");
        System.out.println("              GAME OVER");
        System.out.println("===========================================");
        System.out.printf("Final Score — Black: %d  White: %d%n", black, white);

        if (black > white) {
            System.out.println("Black wins! Congratulations!");
        } else if (white > black) {
            System.out.println("White (AI) wins!");
        } else {
            System.out.println("It's a draw!");
        }
        System.out.println("===========================================");
    }

    /**
     * Entry point for the Othello game.
     * Accepts an optional command-line argument for AI search depth.
     *
     * @param args optional: first argument sets AI search depth (default 6)
     */
    public static void main(String[] args) {
        int depth = 6;
        if (args.length > 0) {
            try {
                depth = Integer.parseInt(args[0]);
                if (depth < 1) depth = 6;
            } catch (NumberFormatException e) {
                System.out.println("Invalid depth argument, using default depth 6.");
            }
        }
        Game game = new Game(depth);
        game.play();
    }
}
