package Freefooders;

import model.Store;
import model.Product;
import java.util.ArrayList;

public class PurchaseSimulator {
    public static void main(String[] args) {
        // Set up a store with a product having an initial stock of 100 units.
        Store store = new Store("PizzaWorld", 0.0, 0.0, "pizzeria", 4, 22, 0, "logo.png", new ArrayList<>());
        Product pepperoni = new Product("Pepperoni", "pizza", 100, 10.0);
        store.addProduct(pepperoni);

        // Define simulation parameters:
        // Each thread will try to purchase 30 units.
        // With an initial stock of 100, only three purchases can succeed.
        int numberOfThreads = 10;
        int purchaseQuantity = 20;
        Thread[] threads = new Thread[numberOfThreads];

        // Create multiple threads simulating simultaneous purchase requests.
        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                boolean success = store.purchaseProduct("Pepperoni", purchaseQuantity);
                System.out.println(Thread.currentThread().getName() + " purchase " + (success ? "successful" : "failed"));
            });
            threads[i].start();
        }

        // Wait for all threads to finish.
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.err.println("Thread interrupted: " + e.getMessage());
            }
        }

        // Print the final results.
        System.out.println("Final total revenue: " + store.getTotalRevenue());
        System.out.println("Remaining stock for Pepperoni: " + pepperoni.getAvailableAmount());
    }
}
