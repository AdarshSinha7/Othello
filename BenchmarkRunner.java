import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs all benchmark suites for the Othello AI and outputs results
 * as formatted console tables and a CSV file.
 *
 * <p>Suites:</p>
 * <ul>
 *   <li>A — Depth comparison (FullEval at depths 2/4/6/8)</li>
 *   <li>B — Evaluation function comparison (Simple/Mobility/Full at depth 6)</li>
 *   <li>C — Alpha-beta pruning benefit</li>
 *   <li>D — Move ordering benefit</li>
 * </ul>
 *
 * <p>Run with: {@code java BenchmarkRunner}</p>
 */
public class BenchmarkRunner {

    /** CSV output file name */
    private static final String CSV_FILE = "benchmark_results.csv";

    /** Accumulated CSV rows across all suites */
    private static final List<String[]> csvRows = new ArrayList<>();

    // ════════════════════════════════════════════════════════════════
    //  MAIN
    // ════════════════════════════════════════════════════════════════

    /**
     * Entry point. Runs all four benchmark suites sequentially.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        long totalStart = System.currentTimeMillis();

        printBanner();

        suiteA();
        suiteB();
        suiteC();
        suiteD();

        writeCsv();

        long totalElapsed = System.currentTimeMillis() - totalStart;
        System.out.println("════════════════════════════════════════════════════════");
        System.out.printf("Total benchmark runtime: %.1f seconds%n", totalElapsed / 1000.0);
        System.out.println("Results written to " + CSV_FILE);
        System.out.println("════════════════════════════════════════════════════════");
    }

    /**
     * Prints the benchmark header banner.
     */
    private static void printBanner() {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════");
        System.out.println("           OTHELLO AI BENCHMARK SUITE");
        System.out.println("════════════════════════════════════════════════════════");
        System.out.println();
    }

    // ════════════════════════════════════════════════════════════════
    //  SUITE A — Depth Comparison
    // ════════════════════════════════════════════════════════════════

    /**
     * Suite A: Round-robin between depths 2, 4, 6, 8 using FullEval.
     * 10 games per matchup (5 each way).
     */
    private static void suiteA() {
        System.out.println("──────────────────────────────────────────────────────");
        System.out.println("  SUITE A: Depth Comparison (FullEval)");
        System.out.println("──────────────────────────────────────────────────────");

        int[] depths = {2, 4, 6, 8};
        int n = depths.length;
        int gamesPerMatchup = 10;
        // wins[i][j] = how many games depth[i] won against depth[j]
        int[][] wins = new int[n][n];
        int[][] draws = new int[n][n];

        int totalMatchups = n * (n - 1) / 2;
        int matchup = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                matchup++;
                String label = "d" + depths[i] + " vs d" + depths[j];
                System.out.printf("%nMatchup %d/%d: %s (%d games)%n", matchup, totalMatchups, label, gamesPerMatchup);

                for (int g = 0; g < gamesPerMatchup; g++) {
                    System.out.printf("  Game %d/%d...", g + 1, gamesPerMatchup);

                    // Alternate colors: even games => i=Black, j=White; odd => j=Black, i=White
                    int blackIdx = (g % 2 == 0) ? i : j;
                    int whiteIdx = (g % 2 == 0) ? j : i;

                    ConfigurableAIPlayer black = new ConfigurableAIPlayer(
                        Board.BLACK, depths[blackIdx], new FullEval(), true, true);
                    ConfigurableAIPlayer white = new ConfigurableAIPlayer(
                        Board.WHITE, depths[whiteIdx], new FullEval(), true, true);

                    Benchmark.GameResult result = Benchmark.playGame(black, white);
                    System.out.printf(" %s (B:%d W:%d)%n", result.winnerString(), result.blackDiscs, result.whiteDiscs);

                    // Map winner back to depth indices
                    if (result.winner == Board.BLACK) {
                        wins[blackIdx][whiteIdx]++;
                    } else if (result.winner == Board.WHITE) {
                        wins[whiteIdx][blackIdx]++;
                    } else {
                        draws[i][j]++;
                    }

                    addCsvRow("A", label, g + 1,
                              "d" + depths[blackIdx], "d" + depths[whiteIdx], result);
                }
            }
        }

        // Print win-rate matrix
        System.out.println("\nWin-rate matrix (row vs column):");
        System.out.printf("%-8s", "");
        for (int d : depths) System.out.printf("  d%-6d", d);
        System.out.println();

        for (int i = 0; i < n; i++) {
            System.out.printf("d%-7d", depths[i]);
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    System.out.printf("  %-7s", "---");
                } else {
                    int total = wins[i][j] + wins[j][i] + draws[i][j] + draws[j][i];
                    if (total == 0) {
                        System.out.printf("  %-7s", "---");
                    } else {
                        double rate = 100.0 * wins[i][j] / total;
                        System.out.printf("  %5.1f%% ", rate);
                    }
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    // ════════════════════════════════════════════════════════════════
    //  SUITE B — Evaluation Function Comparison
    // ════════════════════════════════════════════════════════════════

    /**
     * Suite B: Round-robin between SimpleEval, MobilityEval, and FullEval at depth 6.
     * 10 games per matchup (5 each way).
     */
    private static void suiteB() {
        System.out.println("──────────────────────────────────────────────────────");
        System.out.println("  SUITE B: Evaluation Function Comparison (depth 6)");
        System.out.println("──────────────────────────────────────────────────────");

        EvaluationFunction[] evals = {new SimpleEval(), new MobilityEval(), new FullEval()};
        String[] names = {"Simple", "Mobility", "Full"};
        int n = evals.length;
        int depth = 6;
        int gamesPerMatchup = 10;
        int[][] wins = new int[n][n];
        int[][] draws = new int[n][n];

        int totalMatchups = n * (n - 1) / 2;
        int matchup = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                matchup++;
                String label = names[i] + " vs " + names[j];
                System.out.printf("%nMatchup %d/%d: %s (%d games)%n", matchup, totalMatchups, label, gamesPerMatchup);

                for (int g = 0; g < gamesPerMatchup; g++) {
                    System.out.printf("  Game %d/%d...", g + 1, gamesPerMatchup);

                    int blackIdx = (g % 2 == 0) ? i : j;
                    int whiteIdx = (g % 2 == 0) ? j : i;

                    ConfigurableAIPlayer black = new ConfigurableAIPlayer(
                        Board.BLACK, depth, evals[blackIdx], true, true);
                    ConfigurableAIPlayer white = new ConfigurableAIPlayer(
                        Board.WHITE, depth, evals[whiteIdx], true, true);

                    Benchmark.GameResult result = Benchmark.playGame(black, white);
                    System.out.printf(" %s (B:%d W:%d)%n", result.winnerString(), result.blackDiscs, result.whiteDiscs);

                    if (result.winner == Board.BLACK) {
                        wins[blackIdx][whiteIdx]++;
                    } else if (result.winner == Board.WHITE) {
                        wins[whiteIdx][blackIdx]++;
                    } else {
                        draws[i][j]++;
                    }

                    addCsvRow("B", label, g + 1,
                              names[blackIdx], names[whiteIdx], result);
                }
            }
        }

        // Print win-rate matrix
        System.out.println("\nWin-rate matrix (row vs column):");
        System.out.printf("%-12s", "");
        for (String name : names) System.out.printf("  %-10s", name);
        System.out.println();

        for (int i = 0; i < n; i++) {
            System.out.printf("%-12s", names[i]);
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    System.out.printf("  %-10s", "---");
                } else {
                    int total = wins[i][j] + wins[j][i] + draws[i][j] + draws[j][i];
                    if (total == 0) {
                        System.out.printf("  %-10s", "---");
                    } else {
                        double rate = 100.0 * wins[i][j] / total;
                        System.out.printf("  %6.1f%%   ", rate);
                    }
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    // ════════════════════════════════════════════════════════════════
    //  SUITE C — Alpha-Beta Pruning Benefit
    // ════════════════════════════════════════════════════════════════

    /**
     * Suite C: Compares alpha-beta ON vs OFF at depths 2, 4, and 6.
     * 2 games per depth (one each way). Prints nodes and time comparison.
     */
    private static void suiteC() {
        System.out.println("──────────────────────────────────────────────────────");
        System.out.println("  SUITE C: Alpha-Beta Pruning Benefit (FullEval)");
        System.out.println("──────────────────────────────────────────────────────");

        int[] depths = {2, 4, 6};

        System.out.printf("%n%-8s %16s %16s %16s %16s%n",
                "Depth", "AB Nodes/move", "NoAB Nodes/move", "AB Time/move", "NoAB Time/move");
        System.out.println("─".repeat(84));

        for (int depth : depths) {
            long abNodesTotal = 0, noAbNodesTotal = 0;
            long abTimeTotal = 0, noAbTimeTotal = 0;
            int abMoveCount = 0, noAbMoveCount = 0;

            for (int g = 0; g < 2; g++) {
                System.out.printf("  Depth %d, game %d/2...%n", depth, g + 1);

                ConfigurableAIPlayer abPlayer, noAbPlayer;
                if (g == 0) {
                    abPlayer = new ConfigurableAIPlayer(Board.BLACK, depth, new FullEval(), true, true);
                    noAbPlayer = new ConfigurableAIPlayer(Board.WHITE, depth, new FullEval(), false, true);
                } else {
                    noAbPlayer = new ConfigurableAIPlayer(Board.BLACK, depth, new FullEval(), false, true);
                    abPlayer = new ConfigurableAIPlayer(Board.WHITE, depth, new FullEval(), true, true);
                }

                Benchmark.GameResult result = Benchmark.playGame(
                    g == 0 ? abPlayer : noAbPlayer,
                    g == 0 ? noAbPlayer : abPlayer
                );

                if (g == 0) {
                    abNodesTotal += result.blackNodesTotal;
                    abTimeTotal += result.blackTimeMs;
                    noAbNodesTotal += result.whiteNodesTotal;
                    noAbTimeTotal += result.whiteTimeMs;
                    abMoveCount += result.totalMoves / 2 + (result.totalMoves % 2);
                    noAbMoveCount += result.totalMoves / 2;
                } else {
                    noAbNodesTotal += result.blackNodesTotal;
                    noAbTimeTotal += result.blackTimeMs;
                    abNodesTotal += result.whiteNodesTotal;
                    abTimeTotal += result.whiteTimeMs;
                    noAbMoveCount += result.totalMoves / 2 + (result.totalMoves % 2);
                    abMoveCount += result.totalMoves / 2;
                }

                String blackCfg = g == 0 ? "AB" : "noAB";
                String whiteCfg = g == 0 ? "noAB" : "AB";
                addCsvRow("C", "AB_vs_noAB_d" + depth, g + 1, blackCfg, whiteCfg, result);
            }

            long abAvgNodes = abMoveCount > 0 ? abNodesTotal / abMoveCount : 0;
            long noAbAvgNodes = noAbMoveCount > 0 ? noAbNodesTotal / noAbMoveCount : 0;
            double abAvgTime = abMoveCount > 0 ? (double) abTimeTotal / abMoveCount : 0;
            double noAbAvgTime = noAbMoveCount > 0 ? (double) noAbTimeTotal / noAbMoveCount : 0;

            System.out.printf("d%-7d %,16d %,16d %13.1f ms %13.1f ms%n",
                    depth, abAvgNodes, noAbAvgNodes, abAvgTime, noAbAvgTime);
        }
        System.out.println();
    }

    // ════════════════════════════════════════════════════════════════
    //  SUITE D — Move Ordering Benefit
    // ════════════════════════════════════════════════════════════════

    /**
     * Suite D: Compares move ordering ON vs OFF at depth 6 with FullEval and alpha-beta.
     * 2 games (one each way).
     */
    private static void suiteD() {
        System.out.println("──────────────────────────────────────────────────────");
        System.out.println("  SUITE D: Move Ordering Benefit (FullEval, depth 6)");
        System.out.println("──────────────────────────────────────────────────────");

        int depth = 6;
        long moNodesTotal = 0, noMoNodesTotal = 0;
        long moTimeTotal = 0, noMoTimeTotal = 0;
        int moMoveCount = 0, noMoMoveCount = 0;

        for (int g = 0; g < 2; g++) {
            System.out.printf("  Game %d/2...%n", g + 1);

            ConfigurableAIPlayer moPlayer, noMoPlayer;
            if (g == 0) {
                moPlayer = new ConfigurableAIPlayer(Board.BLACK, depth, new FullEval(), true, true);
                noMoPlayer = new ConfigurableAIPlayer(Board.WHITE, depth, new FullEval(), true, false);
            } else {
                noMoPlayer = new ConfigurableAIPlayer(Board.BLACK, depth, new FullEval(), true, false);
                moPlayer = new ConfigurableAIPlayer(Board.WHITE, depth, new FullEval(), true, true);
            }

            Benchmark.GameResult result = Benchmark.playGame(
                g == 0 ? moPlayer : noMoPlayer,
                g == 0 ? noMoPlayer : moPlayer
            );

            if (g == 0) {
                moNodesTotal += result.blackNodesTotal;
                moTimeTotal += result.blackTimeMs;
                noMoNodesTotal += result.whiteNodesTotal;
                noMoTimeTotal += result.whiteTimeMs;
                moMoveCount += result.totalMoves / 2 + (result.totalMoves % 2);
                noMoMoveCount += result.totalMoves / 2;
            } else {
                noMoNodesTotal += result.blackNodesTotal;
                noMoTimeTotal += result.blackTimeMs;
                moNodesTotal += result.whiteNodesTotal;
                moTimeTotal += result.whiteTimeMs;
                noMoMoveCount += result.totalMoves / 2 + (result.totalMoves % 2);
                moMoveCount += result.totalMoves / 2;
            }

            String blackCfg = g == 0 ? "MO" : "noMO";
            String whiteCfg = g == 0 ? "noMO" : "MO";
            addCsvRow("D", "MO_vs_noMO_d6", g + 1, blackCfg, whiteCfg, result);
        }

        long moAvgNodes = moMoveCount > 0 ? moNodesTotal / moMoveCount : 0;
        long noMoAvgNodes = noMoMoveCount > 0 ? noMoNodesTotal / noMoMoveCount : 0;
        double moAvgTime = moMoveCount > 0 ? (double) moTimeTotal / moMoveCount : 0;
        double noMoAvgTime = noMoMoveCount > 0 ? (double) noMoTimeTotal / noMoMoveCount : 0;

        System.out.printf("%n%-18s %16s %16s%n", "", "Move Ordering ON", "Move Ordering OFF");
        System.out.println("─".repeat(52));
        System.out.printf("%-18s %,16d %,16d%n", "Avg nodes/move", moAvgNodes, noMoAvgNodes);
        System.out.printf("%-18s %13.1f ms %13.1f ms%n", "Avg time/move", moAvgTime, noMoAvgTime);

        double reduction = noMoAvgNodes > 0 ? 100.0 * (1.0 - (double) moAvgNodes / noMoAvgNodes) : 0;
        System.out.printf("%nNode reduction with move ordering: %.1f%%%n", reduction);
        System.out.println();
    }

    // ════════════════════════════════════════════════════════════════
    //  CSV OUTPUT
    // ════════════════════════════════════════════════════════════════

    /**
     * Adds a row to the CSV buffer.
     *
     * @param suite     the suite identifier (A/B/C/D)
     * @param matchup   description of the matchup
     * @param gameNum   game number within the matchup
     * @param blackCfg  black player's configuration label
     * @param whiteCfg  white player's configuration label
     * @param result    the game result
     */
    private static void addCsvRow(String suite, String matchup, int gameNum,
                                   String blackCfg, String whiteCfg,
                                   Benchmark.GameResult result) {
        csvRows.add(new String[]{
            suite,
            matchup,
            String.valueOf(gameNum),
            blackCfg,
            whiteCfg,
            result.winnerString(),
            String.valueOf(result.blackDiscs),
            String.valueOf(result.whiteDiscs),
            String.valueOf(result.totalMoves),
            String.valueOf(result.blackNodesTotal),
            String.valueOf(result.whiteNodesTotal),
            String.valueOf(result.blackTimeMs),
            String.valueOf(result.whiteTimeMs)
        });
    }

    /**
     * Writes all accumulated results to the CSV file.
     */
    private static void writeCsv() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE))) {
            pw.println("suite,matchup,game_number,black_config,white_config,winner,"
                     + "black_discs,white_discs,total_moves,black_nodes,white_nodes,"
                     + "black_time_ms,white_time_ms");
            for (String[] row : csvRows) {
                pw.println(String.join(",", row));
            }
        } catch (IOException e) {
            System.err.println("Error writing CSV: " + e.getMessage());
        }
    }
}
