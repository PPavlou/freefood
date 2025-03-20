package Freefooders;

import model.Store;
import model.Product;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PurchaseSimulator {
    public static void main(String[] args) {
        // Set up a store with a product having an initial stock of 100 units.
        Store store = new Store("PizzaWorld", 0.0, 0.0, "pizzeria", 4, 22, 0, "logo.png", new ArrayList<>());
        Product pepperoni = new Product("Pepperoni", "pizza", 100, 10.0);
        store.addProduct(pepperoni);

        // Define simulation parameters:
        // Each thread will try to purchase 30 units.
        // With an initial stock of 100, only three purchases can succeed.
        int numberOfTasks = 10;
        int purchaseQuantity = 20;
//        Thread[] threads = new Thread[numberOfThreads];

        // Create an ExecutorService with a fixed thread pool
        ExecutorService executor = Executors.newFixedThreadPool(numberOfTasks);

        // Submit multiple tasks simulating simultaneous purchase requests.
        for (int i = 0; i < numberOfTasks; i++) {
            executor.execute(() -> {
                boolean success = store.purchaseProduct("Pepperoni", purchaseQuantity);
                System.out.println(Thread.currentThread().getName() + " purchase " +
                        (success ? "successful" : "failed"));
            });
        }

        // Shutdown the executor and wait for all tasks to finish
        executor.shutdown();

        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("Executor interrupted: " + e.getMessage());
            executor.shutdownNow();
        }

        // Print the final results.
        System.out.println("Final total revenue: " + store.getTotalRevenue());
        System.out.println("Remaining stock for Pepperoni: " + pepperoni.getAvailableAmount());
    }
}
