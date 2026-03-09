# Othello (Reversi)

A classic Othello board game with an AI opponent powered by **Minimax with Alpha-Beta Pruning**, built in Java.

![Java](https://img.shields.io/badge/Java-17%2B-orange) ![AI](https://img.shields.io/badge/AI-Minimax%20%2B%20Alpha--Beta-blue)

## How to Play

```bash
# Compile
javac *.java

# Run GUI (recommended)
java OthelloGUI

# Run terminal version
java Game
```

You play as **Black**, the AI plays as **White**. Click a highlighted cell (GUI) or type a move like `d3` (terminal) to place your disc. The AI responds automatically.

## Features

- **Graphical UI** — dark-themed Swing interface with animated discs, hover previews, and move history
- **Terminal UI** — lightweight text-based board with `B`, `W`, `.`, and `*` for valid moves
- **Smart AI** — minimax search with alpha-beta pruning (configurable depth 1–12)
- **Evaluation function** that weighs disc parity, mobility, corner control, edge control, and disc stability — with weights shifting across early, mid, and late game phases
- **Move ordering** for efficient pruning (corners and edges searched first)

## Project Structure

| File | Description |
|------|-------------|
| `Board.java` | Game state, move validation, flipping logic |
| `Move.java` | Simple row/col coordinate pair |
| `AIPlayer.java` | Minimax + alpha-beta pruning, evaluation function |
| `Game.java` | Terminal game loop and input handling |
| `OthelloGUI.java` | Swing-based graphical interface |

## Rules

1. Black moves first
2. Place a disc to outflank opponent discs in any of 8 directions — outflanked discs flip to your color
3. If you can't move, your turn passes
4. Game ends when neither player can move
5. Most discs wins
