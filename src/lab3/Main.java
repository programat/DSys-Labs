package lab3;

import mpi.*;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;

public class Main {
    private static final int MIN_PROCESSES = 6;

    public static void main(String[] args) {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        if (size < MIN_PROCESSES) {
            if (rank == 0) {
                System.out.println(new String("Необходимо как минимум ".getBytes(), StandardCharsets.UTF_8) + MIN_PROCESSES + new String(" процессов для выполнения программы.".getBytes(), StandardCharsets.UTF_8));
            }
            MPI.Finalize();
            return;
        }

        ProcessConfig config = new ProcessConfig(size);
        config.printConfig(rank);

        try {
            if (rank == config.finalRank) {
                System.out.println(new String("Процесс 0: Начало работы финального уровня".getBytes(), StandardCharsets.UTF_8));
                processFinalLevel(config);
            } else if (Arrays.binarySearch(config.receivers, rank) >= 0) {
                System.out.println(new String("Процесс ".getBytes(), StandardCharsets.UTF_8) + rank + new String(": Начало работы второго уровня".getBytes(), StandardCharsets.UTF_8));
                processSecondLevel(rank, config);
            } else {
                System.out.println(new String("Процесс ".getBytes(), StandardCharsets.UTF_8) + rank + new String(": Начало работы первого уровня".getBytes(), StandardCharsets.UTF_8));
                sendDataFirstLevel(rank, config);
            }
        } catch (MPIException e) {
            System.err.println(new String("Ошибка MPI в процессе ".getBytes(), StandardCharsets.UTF_8) + rank + ": " + e.getMessage());
        } finally {
            MPI.Finalize();
        }
    }

    private static void sendDataFirstLevel(int rank, ProcessConfig config) throws MPIException {
        int receiver = config.getReceiver(rank);
        int[] dataToSend = (receiver == config.receivers[0]) ? new int[]{3, 1, 5} : new int[]{2, 8, 4};
        Request request = MPI.COMM_WORLD.Isend(dataToSend, 0, dataToSend.length, MPI.INT, receiver, 0);
        request.Wait();
        System.out.println(new String("Процесс ".getBytes(), StandardCharsets.UTF_8) + rank + new String(": Отправлены данные ".getBytes(), StandardCharsets.UTF_8) + Arrays.toString(dataToSend) + new String(" процессу ".getBytes(), StandardCharsets.UTF_8) + receiver);
    }

    private static void processSecondLevel(int rank, ProcessConfig config) throws MPIException {
        int expectedSenders = config.getExpectedSenders(rank);
        System.out.println(new String("Процесс ".getBytes(), StandardCharsets.UTF_8) + rank + new String(": Ожидание данных от ".getBytes(), StandardCharsets.UTF_8) + expectedSenders + new String(" отправителей".getBytes(), StandardCharsets.UTF_8));

        int[][] receivedData = new int[expectedSenders][];
        Request[] requests = new Request[expectedSenders];

        for (int i = 0; i < expectedSenders; i++) {
            Status status = MPI.COMM_WORLD.Probe(MPI.ANY_SOURCE, MPI.ANY_TAG);
            int count = status.Get_count(MPI.INT);
            receivedData[i] = new int[count];
            requests[i] = MPI.COMM_WORLD.Irecv(receivedData[i], 0, count, MPI.INT, status.source, status.tag);
        }

        Request.Waitall(requests);

        int totalLength = Arrays.stream(receivedData).mapToInt(arr -> arr.length).sum();
        int[] mergedData = new int[totalLength];
        int pos = 0;
        for (int[] arr : receivedData) {
            System.arraycopy(arr, 0, mergedData, pos, arr.length);
            pos += arr.length;
        }

        Arrays.sort(mergedData);
        System.out.println(new String("Процесс ".getBytes(), StandardCharsets.UTF_8) + rank + new String(": Отсортированные данные: ".getBytes(), StandardCharsets.UTF_8) + Arrays.toString(mergedData));

        Request sendRequest = MPI.COMM_WORLD.Isend(mergedData, 0, mergedData.length, MPI.INT, config.finalRank, 0);
        sendRequest.Wait();
        System.out.println(new String("Процесс ".getBytes(), StandardCharsets.UTF_8) + rank + new String(": Отправлены отсортированные данные процессу 0".getBytes(), StandardCharsets.UTF_8));
    }

    private static void processFinalLevel(ProcessConfig config) throws MPIException {
        int[][] sortedDataFromReceivers = new int[config.numSecondLevel][];
        Request[] requests = new Request[config.numSecondLevel];

        for (int i = 0; i < config.numSecondLevel; i++) {
            Status status = MPI.COMM_WORLD.Probe(MPI.ANY_SOURCE, MPI.ANY_TAG);
            int count = status.Get_count(MPI.INT);
            sortedDataFromReceivers[i] = new int[count];
            requests[i] = MPI.COMM_WORLD.Irecv(sortedDataFromReceivers[i], 0, count, MPI.INT, status.source, status.tag);
        }

        Request.Waitall(requests);

        for (int i = 0; i < config.numSecondLevel; i++) {
            System.out.println(new String("Процесс 0: Получены отсортированные данные от процесса ".getBytes(), StandardCharsets.UTF_8) + config.receivers[i] + ": " + Arrays.toString(sortedDataFromReceivers[i]));
        }

        int totalLength = Arrays.stream(sortedDataFromReceivers).mapToInt(arr -> arr.length).sum();
        int[] finalData = new int[totalLength];
        int pos = 0;
        for (int[] arr : sortedDataFromReceivers) {
            System.arraycopy(arr, 0, finalData, pos, arr.length);
            pos += arr.length;
        }
        Arrays.sort(finalData);
        System.out.println(new String("Процесс 0: Финальные отсортированные данные: ".getBytes(), StandardCharsets.UTF_8) + Arrays.toString(finalData));
    }

    private static class ProcessConfig {
        final int[] receivers;
        final int numFirstLevel;
        final int numSecondLevel;
        final int finalRank;

        ProcessConfig(int totalProcesses) {
            this.finalRank = 0;

            // динамическое вычисление количества процессов второго уровня
            this.numSecondLevel = Math.max(2, (int) Math.sqrt(totalProcesses - 1));

            // вычисление количества процессов первого уровня
            this.numFirstLevel = totalProcesses - this.numSecondLevel - 1;

            // вычисление процессов-получателей
            this.receivers = new int[numSecondLevel];
            for (int i = 0; i < numSecondLevel; i++) {
                receivers[i] = numFirstLevel + i + 1;
            }
        }

        void printConfig(int rank) {
            if (rank == finalRank) {
                System.out.println(new String("Конфигурация процессов:".getBytes(), StandardCharsets.UTF_8));
                System.out.println(new String("  Всего процессов: ".getBytes(), StandardCharsets.UTF_8) + (numFirstLevel + numSecondLevel + 1));
                System.out.println(new String("  Процессов первого уровня: ".getBytes(), StandardCharsets.UTF_8) + numFirstLevel);
                System.out.println(new String("  Процессов второго уровня: ".getBytes(), StandardCharsets.UTF_8) + numSecondLevel);
                System.out.println(new String("  Процессы-получатели: ".getBytes(), StandardCharsets.UTF_8) + Arrays.toString(receivers));
            }
        }

        int getReceiver(int senderRank) {
            return receivers[(senderRank - 1) % receivers.length];
        }

        int getExpectedSenders(int receiverRank) {
            int baseCount = numFirstLevel / numSecondLevel;
            int extraSenders = numFirstLevel % numSecondLevel;
            int receiverIndex = Arrays.binarySearch(receivers, receiverRank);
            return baseCount + (receiverIndex < extraSenders ? 1 : 0);
        }
    }
}