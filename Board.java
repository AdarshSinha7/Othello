import java.util.ArrayList;
import java.util.List;

/**
 * Represents the Othello game board and contains all game logic
 * including move validation, piece flipping, and game state queries.
 *
 * The board is an 8x8 grid where:
 *   0 = empty cell
 *   1 = black disc
 *   2 = white disc
 */
public class Board {

    /** Board dimension */
    public static final int SIZE = 8;

    /** Empty cell constant */
    public static final int EMPTY = 0;

    /** Black disc constant */
    public static final int BLACK = 1;

    /** White disc constant */
    public static final int WHITE = 2;

    /** The 8 direction vectors: N, NE, E, SE, S, SW, W, NW */
    private static final int[][] DIRECTIONS = {
        {-1, 0}, {-1, 1}, {0, 1}, {1, 1},
        {1, 0}, {1, -1}, {0, -1}, {-1, -1}
    };

    /** The board grid */
    private int[][] grid;

    /**
     * Creates a new board with the standard Othello starting position.
     * Four discs are placed in the center: white on d4/e5, black on d5/e4.
     */
    public Board() {
        grid = new int[SIZE][SIZE];
        // Standard starting position
        grid[3][3] = WHITE;
        grid[3][4] = BLACK;
        grid[4][3] = BLACK;
        grid[4][4] = WHITE;
    }

    /**
     * Creates a deep copy of the given board.
     *
     * @param other the board to copy
     */
    public Board(Board other) {
        grid = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(other.grid[r], 0, grid[r], 0, SIZE);
        }
    }

    /**
     * Returns the disc at the given position.
     *
     * @param row the row index
     * @param col the column index
     * @return EMPTY, BLACK, or WHITE
     */
    public int get(int row, int col) {
        return grid[row][col];
    }

    /**
     * Returns the opponent's color.
     *
     * @param player BLACK or WHITE
     * @return the opponent's color
     */
    public static int opponent(int player) {
        return player == BLACK ? WHITE : BLACK;
    }

    /**
     * Checks whether the given coordinates are within board bounds.
     *
     * @param row the row index
     * @param col the column index
     * @return true if the position is on the board
     */
    private boolean inBounds(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    /**
     * Checks whether a move is valid for the given player.
     * A move is valid if the cell is empty and at least one opponent disc
     * would be flipped in any of the 8 directions.
     *
     * @param row    the row to place the disc
     * @param col    the column to place the disc
     * @param player the player making the move (BLACK or WHITE)
     * @return true if the move is legal
     */
    public boolean isValidMove(int row, int col, int player) {
        if (!inBounds(row, col) || grid[row][col] != EMPTY) {
            return false;
        }
        int opp = opponent(player);
        for (int[] dir : DIRECTIONS) {
            int r = row + dir[0];
            int c = col + dir[1];
            boolean foundOpponent = false;
            while (inBounds(r, c) && grid[r][c] == opp) {
                foundOpponent = true;
                r += dir[0];
                c += dir[1];
            }
            if (foundOpponent && inBounds(r, c) && grid[r][c] == player) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of all legal moves for the given player.
     *
     * @param player the player (BLACK or WHITE)
     * @return list of valid moves; empty if the player must pass
     */
    public List<Move> getValidMoves(int player) {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (isValidMove(r, c, player)) {
                    moves.add(new Move(r, c));
                }
            }
        }
        return moves;
    }

    /**
     * Places a disc and flips all captured opponent discs.
     * This method modifies the board in place.
     *
     * @param move   the move to make
     * @param player the player making the move (BLACK or WHITE)
     * @return the number of discs flipped (0 if the move is invalid)
     */
    public int makeMove(Move move, int player) {
        return makeMove(move.row, move.col, player);
    }

    /**
     * Places a disc and flips all captured opponent discs.
     * This method modifies the board in place.
     *
     * @param row    the row to place the disc
     * @param col    the column to place the disc
     * @param player the player making the move (BLACK or WHITE)
     * @return the number of discs flipped (0 if the move is invalid)
     */
    public int makeMove(int row, int col, int player) {
        if (!isValidMove(row, col, player)) {
            return 0;
        }
        grid[row][col] = player;
        int totalFlipped = 0;
        int opp = opponent(player);

        for (int[] dir : DIRECTIONS) {
            List<int[]> toFlip = new ArrayList<>();
            int r = row + dir[0];
            int c = col + dir[1];
            while (inBounds(r, c) && grid[r][c] == opp) {
                toFlip.add(new int[]{r, c});
                r += dir[0];
                c += dir[1];
            }
            if (!toFlip.isEmpty() && inBounds(r, c) && grid[r][c] == player) {
                for (int[] pos : toFlip) {
                    grid[pos[0]][pos[1]] = player;
                }
                totalFlipped += toFlip.size();
            }
        }
        return totalFlipped;
    }

    /**
     * Counts the number of discs of the given color on the board.
     *
     * @param player BLACK or WHITE
     * @return the disc count
     */
    public int countDiscs(int player) {
        int count = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == player) count++;
            }
        }
        return count;
    }

    /**
     * Checks whether the game is over.
     * The game ends when neither player has a legal move.
     *
     * @return true if the game is over
     */
    public boolean isGameOver() {
        return getValidMoves(BLACK).isEmpty() && getValidMoves(WHITE).isEmpty();
    }

    /**
     * Checks whether the given position is a corner.
     *
     * @param row the row index
     * @param col the column index
     * @return true if the position is one of the four corners
     */
    public static boolean isCorner(int row, int col) {
        return (row == 0 || row == SIZE - 1) && (col == 0 || col == SIZE - 1);
    }

    /**
     * Checks whether the given position is on an edge.
     *
     * @param row the row index
     * @param col the column index
     * @return true if the position is on any edge of the board
     */
    public static boolean isEdge(int row, int col) {
        return row == 0 || row == SIZE - 1 || col == 0 || col == SIZE - 1;
    }

    /**
     * Determines whether a disc at the given position is stable
     * (cannot be flipped for the rest of the game).
     * A disc is stable if it is anchored in all four axis pairs.
     *
     * @param row    the row index
     * @param col    the column index
     * @param player the player's color
     * @return true if the disc is stable
     */
    public boolean isStable(int row, int col, int player) {
        if (grid[row][col] != player) return false;
        if (isCorner(row, col)) return true;

        // Check stability along 4 axes: horizontal, vertical, 2 diagonals.
        // A disc is stable if for each axis, at least one direction reaches
        // the board edge through an unbroken line of same-color discs,
        // OR both directions are filled (no empty cells in either direction).
        int[][] axisPairs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

        for (int[] axis : axisPairs) {
            boolean stableOnAxis = false;

            // Check positive direction
            boolean posEdge = true;
            int r = row + axis[0];
            int c = col + axis[1];
            while (inBounds(r, c)) {
                if (grid[r][c] != player) {
                    posEdge = false;
                    break;
                }
                r += axis[0];
                c += axis[1];
            }

            // Check negative direction
            boolean negEdge = true;
            r = row - axis[0];
            c = col - axis[1];
            while (inBounds(r, c)) {
                if (grid[r][c] != player) {
                    negEdge = false;
                    break;
                }
                r -= axis[0];
                c -= axis[1];
            }

            // Stable on this axis if either direction reaches the edge through
            // same-color discs, or if the entire line is filled (no empty)
            if (posEdge || negEdge) {
                stableOnAxis = true;
            } else {
                // Check if the entire axis line is full (no empty cells)
                boolean allFilled = true;
                r = row + axis[0];
                c = col + axis[1];
                while (inBounds(r, c)) {
                    if (grid[r][c] == EMPTY) { allFilled = false; break; }
                    r += axis[0];
                    c += axis[1];
                }
                if (allFilled) {
                    r = row - axis[0];
                    c = col - axis[1];
                    while (inBounds(r, c)) {
                        if (grid[r][c] == EMPTY) { allFilled = false; break; }
                        r -= axis[0];
                        c -= axis[1];
                    }
                }
                stableOnAxis = allFilled;
            }

            if (!stableOnAxis) return false;
        }
        return true;
    }

    /**
     * Counts the number of stable discs for the given player.
     *
     * @param player BLACK or WHITE
     * @return the number of stable discs
     */
    public int countStableDiscs(int player) {
        int count = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == player && isStable(r, c, player)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Counts the number of corners occupied by the given player.
     *
     * @param player BLACK or WHITE
     * @return the number of corners held (0-4)
     */
    public int countCorners(int player) {
        int count = 0;
        if (grid[0][0] == player) count++;
        if (grid[0][SIZE - 1] == player) count++;
        if (grid[SIZE - 1][0] == player) count++;
        if (grid[SIZE - 1][SIZE - 1] == player) count++;
        return count;
    }

    /**
     * Counts the number of edge cells occupied by the given player.
     *
     * @param player BLACK or WHITE
     * @return the number of edge cells held
     */
    public int countEdges(int player) {
        int count = 0;
        for (int i = 0; i < SIZE; i++) {
            if (grid[0][i] == player) count++;
            if (grid[SIZE - 1][i] == player) count++;
            if (i > 0 && i < SIZE - 1) {
                if (grid[i][0] == player) count++;
                if (grid[i][SIZE - 1] == player) count++;
            }
        }
        return count;
    }

    /**
     * Returns the total number of discs on the board.
     *
     * @return total disc count
     */
    public int totalDiscs() {
        return countDiscs(BLACK) + countDiscs(WHITE);
    }

    /**
     * Displays the board to the console with row/column labels.
     * Empty cells are shown as '.', black as 'B', white as 'W'.
     * Valid moves for the specified player are marked with '*'.
     *
     * @param currentPlayer the player whose valid moves to highlight
     */
    public void display(int currentPlayer) {
        List<Move> validMoves = getValidMoves(currentPlayer);
        boolean[][] isValid = new boolean[SIZE][SIZE];
        for (Move m : validMoves) {
            isValid[m.row][m.col] = true;
        }

        System.out.println();
        System.out.println("    a   b   c   d   e   f   g   h");
        System.out.println("  +---+---+---+---+---+---+---+---+");
        for (int r = 0; r < SIZE; r++) {
            System.out.print((r + 1) + " |");
            for (int c = 0; c < SIZE; c++) {
                char ch;
                if (grid[r][c] == BLACK) {
                    ch = 'B';
                } else if (grid[r][c] == WHITE) {
                    ch = 'W';
                } else if (isValid[r][c]) {
                    ch = '*';
                } else {
                    ch = '.';
                }
                System.out.print(" " + ch + " |");
            }
            System.out.println(" " + (r + 1));
            System.out.println("  +---+---+---+---+---+---+---+---+");
        }
        System.out.println("    a   b   c   d   e   f   g   h");
        System.out.println();
    }
}
