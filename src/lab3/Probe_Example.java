package lab3;

import mpi.*;

public class Probe_Example {
    public static void main(String[] args) {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int TAG = 0;
        Status st;

        try {
            if (rank == 0) {
                int[] data = new int[1];
                data[0] = 2016;
                MPI.COMM_WORLD.Send(data, 0, 1, MPI.INT, 2, TAG);
            } else if (rank == 1) {
                int[] buf = {1, 3, 5};
                MPI.COMM_WORLD.Send(buf, 0, buf.length, MPI.INT, 2, TAG);
            } else if (rank == 2) {
                st = MPI.COMM_WORLD.Probe(MPI.ANY_SOURCE, TAG);
                int count = st.Get_count(MPI.INT);
                int[] back_buf = new int[count];
                MPI.COMM_WORLD.Recv(back_buf, 0, count, MPI.INT, 0, TAG);
                System.out.print("Rank = 0 ");
                for (int i = 0; i < count; i++)
                    System.out.print(back_buf[i] + " ");
                System.out.println();

                st = MPI.COMM_WORLD.Probe(MPI.ANY_SOURCE, TAG);
                count = st.Get_count(MPI.INT);
                int[] back_buf2 = new int[count];
                MPI.COMM_WORLD.Recv(back_buf2, 0, count, MPI.INT, 1, TAG);
                System.out.print("Rank = 1 ");
                for (int i = 0; i < count; i++)
                    System.out.print(back_buf2[i] + " ");
                System.out.println();
            }
        } catch (MPIException e) {
            System.err.println("MPI error: " + e.getMessage());
        } finally {
            MPI.Finalize();
        }
    }
}