package lab4;

import mpi.*;

import java.nio.charset.StandardCharsets;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        // размеры тестовых векторов (используем long для больших размеров)
        long[] vectorSizes = {
                100L,         // 100
                10_000L,      // 10K
                100_000L,     // 100K
                1_000_000L,    // 1M
                5_000_000L,    // 5M
                10_000_000L,   // 10M
                50_000_000L    // 50M
        };

        if (rank == 0) {
            System.out.println(new String("Программа вычисления скалярного произведения векторов".getBytes(StandardCharsets.UTF_8)));
            System.out.println(new String("Количество процессов: ".getBytes(StandardCharsets.UTF_8)) + size);
        }

        // прогрев JVM на небольшом размере
        warmup(rank, size);

        // основные вычисления
        for (long vectorSize : vectorSizes) {

            int intSize = (int) vectorSize;
            int baseChunkSize = intSize / size;
            int remainder = intSize % size;

            if (rank == 0) {
                try {
                    // генерация векторов с проверкой памяти
                    double[] a = generateVector(intSize);
                    double[] b = generateVector(intSize);

                    // последовательное вычисление
                    long seqStartTime = System.nanoTime();
                    double seqResult = computeScalarProduct(a, b);
                    long seqEndTime = System.nanoTime();

                    // параллельное вычисление
                    long parStartTime = System.nanoTime();

                    // рассылка данных другим процессам
                    for (int i = 1; i < size; i++) {
                        int currentChunkSize = baseChunkSize + (i < remainder ? 1 : 0);
                        int startIdx = i * baseChunkSize + Math.min(i, remainder);

                        // отправляем размер части
                        MPI.COMM_WORLD.Send(new int[]{currentChunkSize}, 0, 1, MPI.INT, i, 0);

                        // отправляем части векторов одним сообщением
                        double[] combinedData = new double[currentChunkSize * 2];
                        System.arraycopy(a, startIdx, combinedData, 0, currentChunkSize);
                        System.arraycopy(b, startIdx, combinedData, currentChunkSize, currentChunkSize);
                        MPI.COMM_WORLD.Send(combinedData, 0, combinedData.length, MPI.DOUBLE, i, 1);
                    }

                    // вычисление части мастера
                    int masterChunkSize = baseChunkSize + (0 < remainder ? 1 : 0);
                    double partialSum = 0.0;
                    for (int i = 0; i < masterChunkSize; i++) {
                        partialSum += a[i] * b[i];
                    }

                    // очищаем память
                    System.gc();

                    // сбор результатов
                    double totalSum = partialSum;
                    double[] partialSums = new double[1];
                    for (int i = 1; i < size; i++) {
                        MPI.COMM_WORLD.Recv(partialSums, 0, 1, MPI.DOUBLE, i, 2);
                        totalSum += partialSums[0];
                    }

                    long parEndTime = System.nanoTime();

                    // вывод результатов
                    String seqTimeMessage = formatTime(seqEndTime - seqStartTime);
                    String parTimeMessage = formatTime(parEndTime - parStartTime);
                    double speedup = (seqEndTime - seqStartTime) / (double) (parEndTime - parStartTime);

                    String message = String.format(
                            "Размер векторов: %d\n" +
                                    "Последовательное время: %s, Результат: %.2f\n" +
                                    "Параллельное время: %s, Результат: %.2f\n" +
                                    "Ускорение: %.2f",
                            intSize, seqTimeMessage, seqResult, parTimeMessage, totalSum, speedup
                    );
                    System.out.println(new String(message.getBytes(StandardCharsets.UTF_8)));
                    System.out.println();

                } catch (OutOfMemoryError e) {
                    System.out.println("Недостаточно памяти для размера: " + intSize);
                }
            } else {
                try {
                    // получение размера части
                    int[] chunkSize = new int[1];
                    MPI.COMM_WORLD.Recv(chunkSize, 0, 1, MPI.INT, 0, 0);

                    // получение данных одним сообщением
                    double[] combinedData = new double[chunkSize[0] * 2];
                    MPI.COMM_WORLD.Recv(combinedData, 0, combinedData.length, MPI.DOUBLE, 0, 1);

                    // вычисление частичной суммы
                    double partialSum = 0.0;
                    for (int i = 0; i < chunkSize[0]; i++) {
                        partialSum += combinedData[i] * combinedData[i + chunkSize[0]];
                    }

                    // отправка результата
                    MPI.COMM_WORLD.Send(new double[]{partialSum}, 0, 1, MPI.DOUBLE, 0, 2);

                    // очищаем память
                    System.gc();

                } catch (OutOfMemoryError e) {
                    System.out.println("Недостаточно памяти для размера: " + intSize);
                }
            }
        }

        MPI.Finalize();
    }

    // прогрев JVM
    private static void warmup(int rank, int size) {
        int warmupSize = 1000000;
        if (rank == 0) {
            double[] a = generateVector(warmupSize);
            double[] b = generateVector(warmupSize);
            for (int i = 0; i < 5; i++) {
                computeScalarProduct(a, b);
            }
        }
    }

    private static String formatTime(long nanoTime) {
        if (nanoTime < 1000000) {
            return String.format("%.2f мкс", nanoTime / 1000.0);
        } else if (nanoTime < 1000000000) {
            return String.format("%.2f мс", nanoTime / 1000000.0);
        } else {
            return String.format("%.2f с", nanoTime / 1000000000.0);
        }
    }

    private static double[] generateVector(int size) {
        double[] vector = new double[size];
        Random random = new Random();
        for (int i = 0; i < size; i++) {
            vector[i] = random.nextDouble();
        }
        return vector;
    }

    private static double computeScalarProduct(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Векторы должны быть одинаковой длины");
        }

        double result = 0.0;
        for (int i = 0; i < a.length; i++) {
            result += a[i] * b[i];
        }
        return result;
    }
}