import mpi.*;

import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        MPI.Init(args);

        int myRank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        printUTF8("Процесс " + myRank + " запущен.");

        if (size % 2 != 0) {
            if (myRank == 0) {
                printUTF8("Ошибка: Необходимо четное количество процессов.");
            }
            MPI.Finalize();
            return;
        }

        if (myRank % 2 == 0) {
            if (myRank + 1 < size) {
                sendMessage(myRank, myRank + 1);
            }
        } else {
            receiveMessage(myRank, myRank - 1);
        }

        MPI.Finalize();
        printUTF8("Процесс " + myRank + " завершил работу.");
    }

    // Процедура отправки сообщения (Send)
    private static void sendMessage(int sender, int receiver) throws MPIException {
        int[] message = new int[]{sender};
        int tag = 0;

        printUTF8("Процесс " + sender + " начинает отправку сообщения процессу " + receiver);
        MPI.COMM_WORLD.Send(message, 0, 1, MPI.INT, receiver, tag);
        printUTF8("Процесс " + sender + " завершил отправку сообщения процессу " + receiver);
    }

    // Процедура получения сообщения (Recv)
    private static void receiveMessage(int receiver, int sender) throws MPIException {
        int[] message = new int[1];
        int tag = 0;
        Status status;

        printUTF8("Процесс " + receiver + " ожидает сообщение от процесса " + sender);
        status = MPI.COMM_WORLD.Recv(message, 0, 1, MPI.INT, sender, tag);
        printUTF8("Процесс " + receiver + " получил сообщение от процесса " + sender + ": " + message[0]);
    }

    private static void printUTF8(String message) {
        System.out.println(new String(message.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }
}