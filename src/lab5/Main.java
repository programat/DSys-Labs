package lab5;

import mpi.*;
import java.util.Arrays;
import java.util.Random;

public class Main {
    private static final int SMALL_MATRIX_SIZE = 4;
    private static final Random RANDOM = new Random(42); // фиксированный seed для воспроизводимости

    public static void main(String[] args) throws Exception {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        System.out.println("Процесс [" + rank + "] запущен.");

        // Основные тесты с жестко заданными матрицами
        TestCase[] mainTestCases = new TestCase[]{
                new TestCase(new int[][]{
                        {0, 1, 0, 1},
                        {1, 0, 1, 0},
                        {0, 1, 0, 1},
                        {1, 0, 1, 0}
                }, "2-регулярный цикл"),
                new TestCase(new int[][]{
                        {0, 1, 1, 1},
                        {1, 0, 1, 1},
                        {1, 1, 0, 1},
                        {1, 1, 1, 0}
                }, "Полный граф K4"),
                new TestCase(new int[][]{
                        {0, 1, 1, 1},
                        {1, 0, 0, 0},
                        {1, 0, 0, 0},
                        {1, 0, 0, 0}
                }, "Нерегулярный граф (звезда)"),
                new TestCase(new int[][]{
                        {0, 1, 0, 0},
                        {1, 0, 1, 0},
                        {0, 1, 0, 1},
                        {0, 0, 1, 0}
                }, "Граф-путь"),
                new TestCase(new int[][]{
                        {0, 0, 0, 0},
                        {0, 0, 0, 0},
                        {0, 0, 0, 0},
                        {0, 0, 0, 0}
                }, "Граф с изолированными вершинами")
        };

        if (rank == 0) {
            System.out.println("\n=== Основные тесты ===\n");
        }

        for (TestCase testCase : mainTestCases) {
            double startTime = MPI.Wtime();
            processTestCase(testCase, rank, size);
            double endTime = MPI.Wtime();
            if (rank == 0) {
                System.out.printf("Время выполнения: %.6f секунд\n", endTime - startTime);
                System.out.println("----------------------------------------\n");
            }
        }

        // Дополнительные тесты с большими и сложными графами
        TestCase[] largeTestCases = generateLargeTestCases();

        if (rank == 0) {
            System.out.println("\n=== Дополнительные тесты для больших графов ===\n");
        }

        for (TestCase testCase : largeTestCases) {
            double startTime = MPI.Wtime();
            processTestCase(testCase, rank, size);
            double endTime = MPI.Wtime();
            if (rank == 0) {
                System.out.printf("Время выполнения: %.6f секунд\n", endTime - startTime);
                System.out.println("----------------------------------------\n");
            }
        }

        MPI.Finalize();
    }

    private static TestCase[] generateLargeTestCases() {
        return new TestCase[]{
                new TestCase(generateLargeRegularGraph(1000, 4), "Большой 4-регулярный граф (1000 вершин)"),
                new TestCase(generateAlmostRegularGraph(1000, 6), "Почти регулярный граф (1000 вершин, около 6 связей)"),
                new TestCase(generateSparseGraph(2000), "Разреженный граф (2000 вершин)"),
                new TestCase(generateDenseGraph(500), "Плотный граф (500 вершин)"),
                new TestCase(generateErdosRenyiGraph(1500, 0.3), "Случайный граф Эрдёша-Реньи (1500 вершин)")
        };
    }

    private static void processTestCase(TestCase testCase, int rank, int size) throws Exception {
        int[][] adjacencyMatrix = testCase.matrix;
        int n = adjacencyMatrix.length;

        if (rank == 0) {
            System.out.println("Тестовый случай: " + testCase.description);
            System.out.printf("Размер матрицы: %d x %d\n", n, n);

            // Отрисовка только для маленьких графов (например, до 10 вершин)
            if (n <= 10) {
                System.out.println("\nМатрица смежности:");
                printMatrixFormatted(adjacencyMatrix);
                System.out.println("\nВизуализация графа:");
                visualizeGraph(adjacencyMatrix);
            }

            printGraphStats(adjacencyMatrix);
        }

        for (int i = 0; i < n; i++) {
            MPI.COMM_WORLD.Bcast(adjacencyMatrix[i], 0, n, MPI.INT, 0);
        }

        int verticesPerProcess = n / size;
        int startVertex = rank * verticesPerProcess;
        int endVertex = (rank == size - 1) ? n : startVertex + verticesPerProcess;

        int[] localDegrees = new int[n];
        for (int i = startVertex; i < endVertex; i++) {
            localDegrees[i] = calculateDegree(adjacencyMatrix[i]);
        }

        int[] globalDegrees = new int[n];
        MPI.COMM_WORLD.Reduce(localDegrees, 0, globalDegrees, 0, n, MPI.INT, MPI.SUM, 0);

        if (rank == 0) {
            printDetailedResults(globalDegrees);
        }
    }

    private static void printMatrixFormatted(int[][] matrix) {
        // Печать номеров столбцов
        System.out.print("    ");
        for (int i = 0; i < matrix.length; i++) {
            System.out.printf("%2d ", i);
        }
        System.out.println("\n    " + "---".repeat(matrix.length));

        // Печать матрицы с номерами строк
        for (int i = 0; i < matrix.length; i++) {
            System.out.printf("%2d | ", i);
            for (int j = 0; j < matrix[i].length; j++) {
                System.out.printf("%2d ", matrix[i][j]);
            }
            System.out.println();
        }
    }

    private static void visualizeGraph(int[][] matrix) {
        // Отображение рёбер
        for (int i = 0; i < matrix.length; i++) {
            for (int j = i + 1; j < matrix.length; j++) {
                if (matrix[i][j] == 1) {
                    String edge = String.format("%d ------ %d", i, j);
                    System.out.println(formatEdge(edge));
                }
            }
        }

        // Отображение изолированных вершин
        for (int i = 0; i < matrix.length; i++) {
            boolean hasEdges = false;
            for (int j = 0; j < matrix.length; j++) {
                if (matrix[i][j] == 1) {
                    hasEdges = true;
                    break;
                }
            }
            if (!hasEdges) {
                System.out.println(formatVertex(String.valueOf(i)));
            }
        }
        System.out.println();
    }

    private static String formatEdge(String edge) {
        return "    " + edge;
    }

    private static String formatVertex(String vertex) {
        return "    (" + vertex + ")  [изолированная вершина]";
    }


    private static void printGraphStats(int[][] matrix) {
        int n = matrix.length;
        int edges = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (matrix[i][j] == 1) edges++;
            }
        }

        System.out.println("\nСтатистика графа:");
        System.out.printf("Количество вершин: %d\n", n);
        System.out.printf("Количество рёбер: %d\n", edges);
        System.out.printf("Плотность графа: %.4f\n", (2.0 * edges) / (n * (n - 1)));
    }

    private static void printDetailedResults(int[] degrees) {
        int min = Arrays.stream(degrees).min().getAsInt();
        int max = Arrays.stream(degrees).max().getAsInt();
        double avg = Arrays.stream(degrees).average().getAsDouble();

        System.out.println("\nАнализ степеней вершин:");
        System.out.printf("Минимальная степень: %d\n", min);
        System.out.printf("Максимальная степень: %d\n", max);
        System.out.printf("Средняя степень: %.2f\n", avg);

        boolean isRegular = checkRegularity(degrees);
        System.out.println("\nРезультат проверки:");
        if (isRegular) {
            System.out.printf("Граф является регулярным (степень %d)\n", degrees[0]);
        } else {
            System.out.println("Граф не является регулярным");
            System.out.printf("Разброс степеней: %d\n", max - min);
        }
    }

    private static int[][] generateLargeRegularGraph(int n, int degree) {
        int[][] matrix = new int[n][n];
        for (int i = 0; i < n; i++) {
            int connections = 0;
            while (connections < degree) {
                int j = (i + connections + 1) % n;
                if (i != j && matrix[i][j] == 0) {
                    matrix[i][j] = matrix[j][i] = 1;
                    connections++;
                }
            }
        }
        return matrix;
    }

    private static int[][] generateAlmostRegularGraph(int n, int targetDegree) {
        int[][] matrix = generateLargeRegularGraph(n, targetDegree);
        int perturbations = n / 20;
        for (int i = 0; i < perturbations; i++) {
            int v1 = RANDOM.nextInt(n);
            int v2 = RANDOM.nextInt(n);
            if (v1 != v2) {
                matrix[v1][v2] = matrix[v2][v1] = RANDOM.nextInt(2);
            }
        }
        return matrix;
    }

    private static int[][] generateSparseGraph(int n) {
        int[][] matrix = new int[n][n];
        int edges = n + RANDOM.nextInt(n);
        while (edges > 0) {
            int i = RANDOM.nextInt(n);
            int j = RANDOM.nextInt(n);
            if (i != j && matrix[i][j] == 0) {
                matrix[i][j] = matrix[j][i] = 1;
                edges--;
            }
        }
        return matrix;
    }

    private static int[][] generateDenseGraph(int n) {
        int[][] matrix = new int[n][n];
        double probability = 0.7;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (RANDOM.nextDouble() < probability) {
                    matrix[i][j] = matrix[j][i] = 1;
                }
            }
        }
        return matrix;
    }

    private static int[][] generateErdosRenyiGraph(int n, double p) {
        int[][] matrix = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (RANDOM.nextDouble() < p) {
                    matrix[i][j] = matrix[j][i] = 1;
                }
            }
        }
        return matrix;
    }

    private static int calculateDegree(int[] row) {
        return Arrays.stream(row).sum();
    }

    private static boolean checkRegularity(int[] degrees) {
        return Arrays.stream(degrees).distinct().count() == 1;
    }
}

class TestCase {
    int[][] matrix;
    String description;

    TestCase(int[][] matrix, String description) {
        this.matrix = matrix;
        this.description = description;
    }
}
