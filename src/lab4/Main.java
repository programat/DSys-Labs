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
            if (rank == 0) {
                // генерируем тестовых векторов
                double[] a = generateVector(vectorSize);
                double[] b = generateVector(vectorSize);

                // замеряем времени последовательного вычисления
                long startTime = System.nanoTime();
                double result = computeScalarProduct(a, b);
                long endTime = System.nanoTime();
                long duration = endTime - startTime;

                String timeMessage;
                if (duration < 1000000) { // меньше 1 мс
                    timeMessage = String.format("%.2f мкс", duration / 1000.0);
                } else if (duration < 1000000000) { // меньше 1 с
                    timeMessage = String.format("%.2f мс", duration / 1000000.0);
                } else { // секунды
                    timeMessage = String.format("%.2f с", duration / 1000000000.0);
                }

                String message = String.format(
                        "Размер векторов: %d, Последовательное время: %s, Результат: %.2f",
                        vectorSize, timeMessage, result
                );
                System.out.println(new String(message.getBytes(StandardCharsets.UTF_8)));
            }
        }
        MPI.Finalize();
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