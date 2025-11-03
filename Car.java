import java.util.Queue;

public class Car extends Thread {
    private final String carName;
    private final Queue<Car> waitingQueue;
    private final Semaphore mutex;
    private final Semaphore empty;
    private final Semaphore full;

    public Car(String carName, Queue<Car> waitingQueue, Semaphore mutex, Semaphore empty, Semaphore full) {
        this.carName = carName;
        this.waitingQueue = waitingQueue;
        this.mutex = mutex;
        this.empty = empty;
        this.full = full;
        setName("Car-" + carName);
    }

    public String getCarName() {
        return carName;
    }

    @Override
    public void run() {
        try {
            // Wait for a free spot in the waiting area
            empty.acquire();

            // Enter critical section to enqueue
            mutex.acquire();
            try {
                waitingQueue.add(this);
                System.out.println("[ARRIVE] " + carName + " joined the queue. Waiting: " + waitingQueue.size());
            } finally {
                mutex.release();
            }

            // Signal that a car is available
            full.release();
        } catch (InterruptedException e) {
            // Restore interrupt status and exit
            Thread.currentThread().interrupt();
        }
    }
}


