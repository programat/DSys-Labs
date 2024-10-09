package lab2;

import mpi.*;
import java.nio.charset.StandardCharsets;

public class Main {
    private static final boolean NON_BLOCKING = false; // Флаг для выбора режима

    public static void main(String[] args) throws Exception {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] buf = new int[1];
        int[] s = new int[1];

        buf[0] = rank; // инициализация буфера значением ранга процесса
        s[0] = rank; // инициализация суммы значением ранга процесса

        printSeparator(rank, "Инициализация");
        printWithRank(rank, "Начальное значение: buf = " + buf[0] + ", s = " + s[0]);

        printSeparator(rank, "Начало пересылки данных");

        if (NON_BLOCKING) {
            nonBlockingMode(rank, size, buf, s);
        } else {
            blockingMode(rank, size, buf, s);
        }

        printSeparator(rank, "Завершение");
        printWithRank(rank, "Итоговая сумма: " + s[0]);

        if (rank == 0) {
            System.out.println(new String("Программа завершена".getBytes(), StandardCharsets.UTF_8));
        }

        MPI.Finalize();
    }

    private static void blockingMode(int rank, int size, int[] buf, int[] s) throws Exception {
        int nextRank = (rank + 1) % size;
        int prevRank = (rank - 1 + size) % size;

        for (int i = 0; i < size - 1; i++) {
            printSendReceive(rank, nextRank, prevRank, buf[0]);
            MPI.COMM_WORLD.Sendrecv(buf, 0, 1, MPI.INT, nextRank, 0,
                    buf, 0, 1, MPI.INT, prevRank, 0);

            s[0] += buf[0];
            printWithRank(rank, "Обновленная сумма: " + s[0]);

            if (rank == 0 && s[0] == size * (size - 1) / 2) {
                break;
            }
        }
    }

    private static void nonBlockingMode(int rank, int size, int[] buf, int[] s) throws Exception {
        int nextRank = (rank + 1) % size;
        int prevRank = (rank - 1 + size) % size;
        Request sendRequest, recvRequest;
        Status status;

        for (int i = 0; i < size - 1; i++) {
            printSendReceive(rank, nextRank, prevRank, buf[0]);
            sendRequest = MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, nextRank, 0);
            recvRequest = MPI.COMM_WORLD.Irecv(buf, 0, 1, MPI.INT, prevRank, 0);

            status = recvRequest.Wait();
            sendRequest.Wait();

            s[0] += buf[0];
            printWithRank(rank, "Обновленная сумма: " + s[0]);

            if (rank == 0 && s[0] == size * (size - 1) / 2) {
                break;
            }
        }
    }

    private static void printWithRank(int rank, String message) {
        System.out.println(new String(("Процесс " + rank + ": " + message).getBytes(), StandardCharsets.UTF_8));
    }

    private static void printSeparator(int rank, String title) {
        if (rank == 0) {
            System.out.println("\n" + "=".repeat(50));
            System.out.println(new String(title.getBytes(), StandardCharsets.UTF_8));
            System.out.println("=".repeat(50));
        }
        MPI.COMM_WORLD.Barrier();
    }

    private static void printSendReceive(int rank, int nextRank, int prevRank, int value) {
        String send = String.format("  %d --(%d)--> %d  ", rank, value, nextRank);
        String receive = String.format("  %d <--(??)-- %d  ", rank, prevRank);
        System.out.println(new String((send + "\n" + receive).getBytes(), StandardCharsets.UTF_8));
    }
}
