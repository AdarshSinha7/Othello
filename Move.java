/**
 * Represents a move on the Othello board.
 * A move consists of a row and column coordinate on the 8x8 board.
 */
public class Move {

    /** Row index (0-7) */
    public final int row;

    /** Column index (0-7) */
    public final int col;

    /**
     * Creates a new Move with the specified row and column.
     *
     * @param row the row index (0-7)
     * @param col the column index (0-7)
     */
    public Move(int row, int col) {
        this.row = row;
        this.col = col;
    }

    /**
     * Returns a human-readable string representation of this move.
     * Uses chess-style notation (e.g., "d3" for column d, row 3).
     *
     * @return the move in letter-number notation
     */
    @Override
    public String toString() {
        return "" + (char) ('a' + col) + (row + 1);
    }

    /**
     * Checks equality based on row and column values.
     *
     * @param obj the object to compare
     * @return true if the other object is a Move with the same row and col
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Move)) return false;
        Move other = (Move) obj;
        return this.row == other.row && this.col == other.col;
    }

    /**
     * Returns a hash code based on row and column.
     *
     * @return hash code for this move
     */
    @Override
    public int hashCode() {
        return row * 8 + col;
    }
}
