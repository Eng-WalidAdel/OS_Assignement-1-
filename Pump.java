import java.util.Queue;

public class Pump extends Thread {
    private final int pumpId;
    private final Queue<Car> waitingQueue;
    private final Semaphore mutex;
    private final Semaphore empty;
    private final Semaphore full;
    private final Semaphore serviceBays; // Provided by ServiceStation; not strictly necessary with one thread per pump

    public Pump(int pumpId, Queue<Car> waitingQueue, Semaphore mutex, Semaphore empty, Semaphore full, Semaphore serviceBays) {
        this.pumpId = pumpId;
        this.waitingQueue = waitingQueue;
        this.mutex = mutex;
        this.empty = empty;
        this.full = full;
        this.serviceBays = serviceBays;
        setName("Pump-" + pumpId);
        setDaemon(true); // allow JVM to exit once main finishes and cars are done
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Wait until a car is available
                full.acquire();

                // Critical section: remove a car from the queue
                Car car;
                mutex.acquire();
                try {
                    car = waitingQueue.poll();
                } finally {
                    mutex.release();
                }

                // A spot in waiting area is now free
                empty.release();

                if (car == null) {
                    continue;
                }

                // Optionally coordinate with service bay capacity (defensive; with 1 thread/pump this is a no-op)
                boolean acquiredBay = serviceBays.tryAcquire();
                if (!acquiredBay) {
                    // If not acquired (shouldn't happen with 1:1 pump threads), just proceed to avoid deadlock
                }

                System.out.println("[SERVICE] Pump " + pumpId + " is washing " + car.getCarName());

                // Simulate service time
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                System.out.println("[DONE] Pump " + pumpId + " finished " + car.getCarName());

                if (acquiredBay) {
                    serviceBays.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}


