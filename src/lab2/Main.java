package lab2;

import mpi.*;

public class Main {
    public static void main(String[] args) throws Exception {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] buf = new int[1];
        int[] s = new int[1];

        buf[0] = rank;
        s[0] = rank;

        // Блокирующий режим
        for (int i = 0; i < size - 1; i++) {
            int nextRank = (rank + 1) % size;
            int prevRank = (rank - 1 + size) % size;

            MPI.COMM_WORLD.Sendrecv(buf, 0, 1, MPI.INT, nextRank, 0,
                    buf, 0, 1, MPI.INT, prevRank, 0);

            s[0] += buf[0];
        }

        // Неблокирующий режим
        /*
        Request sendRequest, recvRequest;
        Status status;

        for (int i = 0; i < size - 1; i++) {
            int nextRank = (rank + 1) % size;
            int prevRank = (rank - 1 + size) % size;

            sendRequest = MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, nextRank, 0);
            recvRequest = MPI.COMM_WORLD.Irecv(buf, 0, 1, MPI.INT, prevRank, 0);

            status = recvRequest.Wait();
            sendRequest.Wait();

            s[0] += buf[0];
        }
        */

        if (rank == 0) {
            System.out.println("Сумма всех рангов: " + s[0]);
        }

        MPI.Finalize();
    }
}
