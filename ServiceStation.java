
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class ServiceStation {

    public static void main(String[] args) {

        Scanner input = new Scanner(System.in);

        System.out.println("Welcome to the Car Wash Simulation!");
        System.out.print("Enter Waiting Area Capacity (1-10): ");
        int waitingAreaCapacity = input.nextInt();

        System.out.print("Enter Number of Pumps (Service Bays): ");
        int numberOfPumps = input.nextInt();

        System.out.print("Enter Number of Cars Arriving: ");
        int numberOfCars = input.nextInt();

        System.out.println("\nSimulation starting...\n");

        // ----------- Shared Resources -----------
        Queue<Car> waitingQueue = new LinkedList<>();

        // Semaphores for synchronization
        Semaphore mutex = new Semaphore(1);                  // mutual exclusion
        Semaphore empty = new Semaphore(waitingAreaCapacity); // spaces in waiting area
        Semaphore full = new Semaphore(0);                    // cars waiting
        Semaphore serviceBays = new Semaphore(numberOfPumps); // available pumps

        // ----------- Start Pump Threads (Consumers) -----------
        for (int i = 0; i < numberOfPumps; i++) {
            Pump pump = new Pump(i + 1, waitingQueue, mutex, empty, full, serviceBays);
            pump.start();
        }

        // ----------- Start Car Threads (Producers) -----------
        for (int i = 0; i < numberOfCars; i++) {
            Car car = new Car("C" + (i + 1), waitingQueue, mutex, empty, full);
            car.start();

            // optional delay to simulate arrival timing
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // ----------- End Simulation Info -----------
        System.out.println("\nAll cars have been created and are arriving...\n");
        System.out.println(" Pumps and Cars are running concurrently...");
        System.out.println("=============================================");
    }
}
