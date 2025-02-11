package lab4;

import mpi.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.nio.ByteBuffer;

public class Main {
    private enum ExchangeType {
        SEND_RECV,           // Обычный Send/Recv
        SYNC_SEND_RECV,      // Синхронный Ssend/Recv
        READY_SEND_RECV,     // Ready Rsend/Recv
        BUFFERED_SEND_RECV,  // Буферизованный Bsend/Recv
        BROADCAST_REDUCE,    // Broadcast/Reduce
        SCATTER_GATHER       // Scatter/Gather
    }

    private static final ExchangeType EXCHANGE_TYPE = ExchangeType.BUFFERED_SEND_RECV; // выбор типа обмена

    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        // выделяем буфер для нашего буф обмена
        if (EXCHANGE_TYPE == ExchangeType.BUFFERED_SEND_RECV) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1000000);
            MPI.Buffer_attach(buffer);
        }

        long[] vectorSizes = {100L, 10_000L, 100_000L, 1_000_000L, 5_000_000L, 10_000_000L};

        if (rank == 0) {
            System.out.println("Программа вычисления скалярного произведения векторов");
            System.out.println("Количество процессов: " + size);
            System.out.println("Тип обмена: " + EXCHANGE_TYPE);
        }

        warmup(rank, size);

        for (long vectorSize : vectorSizes) {
            int intSize = (int) vectorSize;
            try {
                switch (EXCHANGE_TYPE) {
                    case SEND_RECV:
                        computeSendRecv(intSize, rank, size, false, false, false);
                        break;
                    case SYNC_SEND_RECV:
                        computeSendRecv(intSize, rank, size, true, false, false);
                        break;
                    case READY_SEND_RECV:
                        computeSendRecv(intSize, rank, size, false, true, false);
                        break;
                    case BUFFERED_SEND_RECV:
                        computeSendRecv(intSize, rank, size, false, false, true);
                        break;
                    case BROADCAST_REDUCE:
                        computeBroadcastReduce(intSize, rank, size);
                        break;
                    case SCATTER_GATHER:
                        computeScatterGather(intSize, rank, size);
                        break;
                }
            } catch (Exception e) {
                if (rank == 0) {
                    System.out.println("Ошибка при размере: " + intSize + ": " + e.getMessage());
                }
            }
        }

        // Освобождаем буфер если использовался
        if (EXCHANGE_TYPE == ExchangeType.BUFFERED_SEND_RECV) {
            MPI.Buffer_detach();
        }

        finalizeJsonFile();
        MPI.Finalize();
    }

    // получение названия типа обмена
    private static String getExchangeTypeName() {
        switch (EXCHANGE_TYPE) {
            case SEND_RECV:
                return "Send/Recv";
            case SYNC_SEND_RECV:
                return "Sync Send/Recv";
            case READY_SEND_RECV:
                return "Ready Send/Recv";
            case BUFFERED_SEND_RECV:
                return "Buffered Send/Recv";
            case BROADCAST_REDUCE:
                return "Broadcast/Reduce";
            case SCATTER_GATHER:
                return "Scatter/Gather";
            default:
                return "Неизвестный тип";
        }
    }


    // метод с использованием Send/Recv
    // основной метод для вычисления с разными типами send/recv
    private static void computeSendRecv(int vectorSize, int rank, int size,
                                        boolean sync, boolean ready, boolean buffered) throws Exception {
        int baseChunkSize = vectorSize / size;
        int remainder = vectorSize % size;

        if (rank == 0) {
            // генерация исходных векторов
            double[] a = generateVector(vectorSize);
            double[] b = generateVector(vectorSize);

            // последовательное вычисление для сравнения
            long seqStartTime = System.nanoTime();
            double seqResult = computeScalarProduct(a, b);
            long seqEndTime = System.nanoTime();

            // параллельное вычисление
            long parStartTime = System.nanoTime();

            // рассылка частей векторов процессам
            for (int i = 1; i < size; i++) {
                int currentChunkSize = baseChunkSize + (i < remainder ? 1 : 0);
                int startIdx = i * baseChunkSize + Math.min(i, remainder);

                // отправка размера части
                if (sync) {
                    MPI.COMM_WORLD.Ssend(new int[]{currentChunkSize}, 0, 1, MPI.INT, i, 0);
                } else if (ready) {
                    MPI.COMM_WORLD.Rsend(new int[]{currentChunkSize}, 0, 1, MPI.INT, i, 0);
                } else if (buffered) {
                    MPI.COMM_WORLD.Bsend(new int[]{currentChunkSize}, 0, 1, MPI.INT, i, 0);
                } else {
                    MPI.COMM_WORLD.Send(new int[]{currentChunkSize}, 0, 1, MPI.INT, i, 0);
                }

                // отправка частей векторов
                double[] combinedData = new double[currentChunkSize * 2];
                System.arraycopy(a, startIdx, combinedData, 0, currentChunkSize);
                System.arraycopy(b, startIdx, combinedData, currentChunkSize, currentChunkSize);

                if (sync) {
                    MPI.COMM_WORLD.Ssend(combinedData, 0, combinedData.length, MPI.DOUBLE, i, 1);
                } else if (ready) {
                    MPI.COMM_WORLD.Rsend(combinedData, 0, combinedData.length, MPI.DOUBLE, i, 1);
                } else if (buffered) {
                    MPI.COMM_WORLD.Bsend(combinedData, 0, combinedData.length, MPI.DOUBLE, i, 1);
                } else {
                    MPI.COMM_WORLD.Send(combinedData, 0, combinedData.length, MPI.DOUBLE, i, 1);
                }
            }

            // вычисление части мастера
            int masterChunkSize = baseChunkSize + (0 < remainder ? 1 : 0);
            double partialSum = 0.0;
            for (int i = 0; i < masterChunkSize; i++) {
                partialSum += a[i] * b[i];
            }

            // сбор результатов
            double totalSum = partialSum;
            double[] partialSums = new double[1];
            for (int i = 1; i < size; i++) {
                MPI.COMM_WORLD.Recv(partialSums, 0, 1, MPI.DOUBLE, i, 2);
                totalSum += partialSums[0];
            }

            long parEndTime = System.nanoTime();
            printResults(vectorSize, seqStartTime, seqEndTime, parStartTime, parEndTime, seqResult, totalSum);

        } else {
            // получение размера части
            int[] chunkSize = new int[1];
            MPI.COMM_WORLD.Recv(chunkSize, 0, 1, MPI.INT, 0, 0);

            // получение данных
            double[] combinedData = new double[chunkSize[0] * 2];
            MPI.COMM_WORLD.Recv(combinedData, 0, combinedData.length, MPI.DOUBLE, 0, 1);

            // вычисление частичной суммы
            double partialSum = 0.0;
            for (int i = 0; i < chunkSize[0]; i++) {
                partialSum += combinedData[i] * combinedData[i + chunkSize[0]];
            }

            // отправка результата
            MPI.COMM_WORLD.Send(new double[]{partialSum}, 0, 1, MPI.DOUBLE, 0, 2);
        }
    }

    private static void computeBroadcastReduce(int vectorSize, int rank, int size) throws Exception {
        System.out.println("Процесс " + rank + " начал работу BR");

        int baseChunkSize = vectorSize / size;
        int remainder = vectorSize % size;

        if (rank == 0) {
            System.out.println("Мастер генерирует векторы");
            // генерация векторов
            double[] a = generateVector(vectorSize);
            double[] b = generateVector(vectorSize);

            // последовательное вычисление
            long seqStartTime = System.nanoTime();
            double seqResult = computeScalarProduct(a, b);
            long seqEndTime = System.nanoTime();

            System.out.println("Мастер начинает параллельное вычисление");
            long parStartTime = System.nanoTime();

            // рассылка данных другим процессам
            for (int i = 1; i < size; i++) {
                int currentChunkSize = baseChunkSize + (i < remainder ? 1 : 0);
                int startIdx = i * baseChunkSize + Math.min(i, remainder);

                System.out.println("Мастер отправляет данные процессу " + i);
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

            System.out.println("Мастер собирает результаты");
            // сбор результатов с редукцией
            double[] totalSum = new double[1];
            totalSum[0] = partialSum;
            for (int i = 1; i < size; i++) {
                double[] partialSums = new double[1];
                MPI.COMM_WORLD.Recv(partialSums, 0, 1, MPI.DOUBLE, i, 2);
                totalSum[0] += partialSums[0];
            }

            long parEndTime = System.nanoTime();
            printResults(vectorSize, seqStartTime, seqEndTime, parStartTime, parEndTime, seqResult, totalSum[0]);

        } else {
            System.out.println("Процесс " + rank + " ждет данные");
            // получение размера части
            int[] chunkSize = new int[1];
            MPI.COMM_WORLD.Recv(chunkSize, 0, 1, MPI.INT, 0, 0);

            System.out.println("Процесс " + rank + " получил размер части: " + chunkSize[0]);
            // получение данных
            double[] combinedData = new double[chunkSize[0] * 2];
            MPI.COMM_WORLD.Recv(combinedData, 0, combinedData.length, MPI.DOUBLE, 0, 1);

            System.out.println("Процесс " + rank + " начинает вычисления");
            // вычисление частичной суммы
            double partialSum = 0.0;
            for (int i = 0; i < chunkSize[0]; i++) {
                partialSum += combinedData[i] * combinedData[i + chunkSize[0]];
            }

            System.out.println("Процесс " + rank + " отправляет результат: " + partialSum);
            // отправка результата
            MPI.COMM_WORLD.Send(new double[]{partialSum}, 0, 1, MPI.DOUBLE, 0, 2);
        }
        System.out.println("Процесс " + rank + " завершил работу BR");
    }

    private static void computeScatterGather(int vectorSize, int rank, int size) throws Exception {
        System.out.println("Процесс " + rank + " начал работу SG");

        // базовые расчеты размеров
        int baseChunkSize = vectorSize / size;
        int myChunkSize = baseChunkSize;
        if (rank < vectorSize % size) {
            myChunkSize++;
        }

        // буферы для частей векторов
        double[] localA = new double[myChunkSize];
        double[] localB = new double[myChunkSize];

        if (rank == 0) {
            // генерация и последовательные вычисления
            double[] a = generateVector(vectorSize);
            double[] b = generateVector(vectorSize);

            long seqStartTime = System.nanoTime();
            double seqResult = computeScalarProduct(a, b);
            long seqEndTime = System.nanoTime();
            long parStartTime = System.nanoTime();

            // распределение данных
            for (int i = 0; i < size; i++) {
                int currentChunkSize = baseChunkSize + (i < vectorSize % size ? 1 : 0);
                int startIdx = i * baseChunkSize + Math.min(i, vectorSize % size);

                if (i == 0) {
                    // копируем свою часть
                    System.arraycopy(a, startIdx, localA, 0, currentChunkSize);
                    System.arraycopy(b, startIdx, localB, 0, currentChunkSize);
                } else {
                    // отправляем части другим процессам
                    double[] chunk = new double[currentChunkSize * 2];
                    System.arraycopy(a, startIdx, chunk, 0, currentChunkSize);
                    System.arraycopy(b, startIdx, chunk, currentChunkSize, currentChunkSize);
                    MPI.COMM_WORLD.Send(chunk, 0, chunk.length, MPI.DOUBLE, i, 0);
                }
            }

            // вычисление своей части
            double localSum = 0.0;
            for (int i = 0; i < myChunkSize; i++) {
                localSum += localA[i] * localB[i];
            }

            // сбор всех результатов
            double[] allResults = new double[size];
            allResults[0] = localSum;

            for (int i = 1; i < size; i++) {
                double[] partialSum = new double[1];
                MPI.COMM_WORLD.Recv(partialSum, 0, 1, MPI.DOUBLE, i, 1);
                allResults[i] = partialSum[0];
            }

            // подсчет общей суммы
            double totalSum = 0.0;
            for (double result : allResults) {
                totalSum += result;
            }

            long parEndTime = System.nanoTime();
            printResults(vectorSize, seqStartTime, seqEndTime, parStartTime, parEndTime, seqResult, totalSum);

        } else {
            // получение своей части данных
            double[] chunk = new double[myChunkSize * 2];
            MPI.COMM_WORLD.Recv(chunk, 0, chunk.length, MPI.DOUBLE, 0, 0);

            // распаковка данных
            System.arraycopy(chunk, 0, localA, 0, myChunkSize);
            System.arraycopy(chunk, myChunkSize, localB, 0, myChunkSize);

            // вычисление
            double localSum = 0.0;
            for (int i = 0; i < myChunkSize; i++) {
                localSum += localA[i] * localB[i];
            }

            // отправка результата
            MPI.COMM_WORLD.Send(new double[]{localSum}, 0, 1, MPI.DOUBLE, 0, 1);
        }

        System.out.println("Процесс " + rank + " завершил работу SG");
    }

    private static void printResults(int vectorSize, long seqStartTime, long seqEndTime,
                                     long parStartTime, long parEndTime, double seqResult, double parResult) {
        String seqTimeMessage = formatTime(seqEndTime - seqStartTime);
        String parTimeMessage = formatTime(parEndTime - parStartTime);
        double speedup = (seqEndTime - seqStartTime) / (double) (parEndTime - parStartTime);

        // только для процесса с рангом 0
        if (MPI.COMM_WORLD.Rank() == 0) {
            // формируем JSON
            String jsonResult = String.format(
                    "{\"exchangeType\": \"%s\", " +
                            "\"processCount\": %d, " +
                            "\"vectorSize\": %d, " +
                            "\"seqTimeNanos\": %d, " +
                            "\"parTimeNanos\": %d, " +
                            "\"speedup\": %.4f, " +
                            "\"seqResult\": %.2f, " +
                            "\"parResult\": %.2f}",
                    getExchangeTypeName(),
                    MPI.COMM_WORLD.Size(),
                    vectorSize,
                    (seqEndTime - seqStartTime),
                    (parEndTime - parStartTime),
                    speedup,
                    seqResult,
                    parResult
            );

            // записываем в файл
            try {
                File file = new File("results.json");
                FileWriter writer;

                // если файл существует, добавляем запятую перед новой записью
                if (file.exists() && file.length() > 0) {
                    writer = new FileWriter(file, true);
                    writer.write(",\n");
                } else {
                    writer = new FileWriter(file);
                    writer.write("[\n");
                }

                writer.write(jsonResult);
                writer.flush();
                writer.close();
            } catch (IOException e) {
                System.err.println("Ошибка записи в файл: " + e.getMessage());
            }

            // человекочитаемый вывод в консоль
            String message = String.format(
                    "Размер векторов: %d\n" +
                            "Последовательное время: %s, Результат: %.2f\n" +
                            "Параллельное время: %s, Результат: %.2f\n" +
                            "Ускорение: %.2f",
                    vectorSize, seqTimeMessage, seqResult, parTimeMessage, parResult, speedup
            );
            System.out.println(new String(message.getBytes(StandardCharsets.UTF_8)));
            System.out.println();
        }
    }

    // добавим метод для закрытия JSON массива в конце работы программы
    private static void finalizeJsonFile() {
        if (MPI.COMM_WORLD.Rank() == 0) {
            try {
                FileWriter writer = new FileWriter("results.json", true);
                writer.write("\n]");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                System.err.println("Ошибка закрытия JSON файла: " + e.getMessage());
            }
        }
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

    // форматирование времени
    private static String formatTime(long nanoTime) {
        if (nanoTime < 1000000) {
            return String.format("%.2f мкс", nanoTime / 1000.0);
        } else if (nanoTime < 1000000000) {
            return String.format("%.2f мс", nanoTime / 1000000.0);
        } else {
            return String.format("%.2f с", nanoTime / 1000000000.0);
        }
    }

    // генерация случайного вектора
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