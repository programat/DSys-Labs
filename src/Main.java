import mpi.*;

public class Main {
    public static void main(String[] args) throws Exception {
        MPI.Init(args);

        int myrank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        int[] message = new int[1];
        int TAG = 0;
        Status status;

        message[0] = myrank;

        if ((myrank % 2) == 0) {
            if ((myrank + 1) != size) {
                // MPI_Send будет добавлен здесь позже
            }
        } else {
            if (myrank != 0) {
                // MPI_Recv будет добавлен здесь позже
            }
            System.out.println("received: " + message[0]);
        }

        MPI.Finalize();
    }
}