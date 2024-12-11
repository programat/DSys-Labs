package lab4;

import mpi.*;

import java.nio.charset.StandardCharsets;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] vectorSizes = {100, 1000, 10000, 100000, 1000000};

        if (rank == 0) {
            System.out.println(new String("Программа вычисления скалярного произведения векторов".getBytes(StandardCharsets.UTF_8)));
            System.out.println(new String("Количество процессов: ".getBytes(StandardCharsets.UTF_8)) + size);
        }

        // последовательное вычисление
        for (int vectorSize : vectorSizes) {
            // размер части для каждого процесса
            int baseChunkSize = vectorSize / size;
            int remainder = vectorSize % size;

            if (rank == 0) {
                // генерируем тестовых векторов
                double[] a = generateVector(vectorSize);
                double[] b = generateVector(vectorSize);

                // замеряем времени последовательного вычисления
                long seqStartTime = System.nanoTime();
                double seqResult = computeScalarProduct(a, b);
                long seqEndTime = System.nanoTime();

                // параллельное
                long parStartTime = System.nanoTime();

                // а это рассылка частей векторов всем процессам
                for (int i = 1; i < size; i++) {
                    // размер части для текущего процесса
                    int currentChunkSize = baseChunkSize + (i < remainder ? 1 : 0);
                    int startIdx = i * baseChunkSize + Math.min(i, remainder);

                    // отправляем размер части
                    MPI.COMM_WORLD.Send(new int[]{currentChunkSize}, 0, 1, MPI.INT, i, 0);

                    // и отправка самих частей векторов
                    MPI.COMM_WORLD.Send(a, startIdx, currentChunkSize, MPI.DOUBLE, i, 1);
                    MPI.COMM_WORLD.Send(b, startIdx, currentChunkSize, MPI.DOUBLE, i, 2);
                }

                // вычисляем часть для мастера
                int masterChunkSize = baseChunkSize + (0 < remainder ? 1 : 0);
                double partialSum = 0.0;
                for (int i = 0; i < masterChunkSize; i++) {
                    partialSum += a[i] * b[i];
                }

                // частиыне суммы от остальных процессов
                double totalSum = partialSum;
                double[] partialSums = new double[1];
                for (int i = 1; i < size; i++) {
                    MPI.COMM_WORLD.Recv(partialSums, 0, 1, MPI.DOUBLE, i, 3);
                    totalSum += partialSums[0];
                }

                long parEndTime = System.nanoTime();

                String seqTimeMessage = formatTime(seqEndTime - seqStartTime);
                String parTimeMessage = formatTime(parEndTime - parStartTime);
                double speedup = (seqEndTime - seqStartTime) / (double) (parEndTime - parStartTime);

                String message = String.format(
                        "Размер векторов: %d\n" +
                                "Последовательное время: %s, Результат: %.2f\n" +
                                "Параллельное время: %s, Результат: %.2f\n" +
                                "Ускорение: %.2f",
                        vectorSize, seqTimeMessage, seqResult, parTimeMessage, totalSum, speedup
                );
                System.out.println(new String(message.getBytes(StandardCharsets.UTF_8)));
                System.out.println();
            } else {
                // размер своей части
                int[] chunkSize = new int[1];
                MPI.COMM_WORLD.Recv(chunkSize, 0, 1, MPI.INT, 0, 0);

                // части векторов
                double[] localA = new double[chunkSize[0]];
                double[] localB = new double[chunkSize[0]];
                MPI.COMM_WORLD.Recv(localA, 0, chunkSize[0], MPI.DOUBLE, 0, 1);
                MPI.COMM_WORLD.Recv(localB, 0, chunkSize[0], MPI.DOUBLE, 0, 2);

                // вычисление частичной суммы
                double partialSum = 0.0;
                for (int i = 0; i < chunkSize[0]; i++) {
                    partialSum += localA[i] * localB[i];
                }

                // отправка результата главному процессу
                MPI.COMM_WORLD.Send(new double[]{partialSum}, 0, 1, MPI.DOUBLE, 0, 3);
            }
        }
        MPI.Finalize();
    }

    private static String formatTime(long nanoTime) {
        if (nanoTime < 1000000) { // меньше 1 мс
            return String.format("%.2f мкс", nanoTime / 1000.0);
        } else if (nanoTime < 1000000000) { // меньше 1 с
            return String.format("%.2f мс", nanoTime / 1000000.0);
        } else { // секунды
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