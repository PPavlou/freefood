package Manager;

import java.io.*;
import java.net.Socket;
import java.util.*;
import com.google.gson.Gson;
import model.Store;
import model.Product;

public class Manager {
    private String masterHost;
    private int masterPort;

    public Manager(String masterHost, int masterPort) {
        this.masterHost = masterHost;
        this.masterPort = masterPort;
    }

    /**
     * Sends a command along with its data to the Master server.
     */
    private String sendCommand(String command, String data) {
        String response = "";
        try (Socket socket = new Socket(masterHost, masterPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            // Send the command and data (each on a separate line)
            out.println(command);
            out.println(data);
            response = in.readLine();
        } catch (IOException e) {
            System.err.println("Error communicating with Master: " + e.getMessage());
        }
        return response;
    }

    /**
     * Provides an interactive console for Manager operations.
     */
    public void interactiveMenu() {
        Scanner scanner = new Scanner(System.in);
        Gson gson = new Gson();
        while (true) {
            System.out.println("=== Manager Console ===");
            System.out.println("Select an option:");
            System.out.println("1. Add Store");
            System.out.println("2. Remove Store");
            System.out.println("3. Add Product");
            System.out.println("4. Remove Product");
            System.out.println("5. Increase Product Amount");
            System.out.println("6. Decrease Product Amount");
            System.out.println("7. Show Deleted Products Report");
            System.out.println("8. List Stores");
            System.out.println("9. Exit");
            System.out.print("Choice: ");
            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Invalid option. Please enter a number.");
                continue;
            }
            switch (choice) {
                case 1:
                    // Add Store
                    System.out.println("Enter store details:");
                    System.out.print("Store Name: ");
                    String storeName = scanner.nextLine();
                    System.out.print("Latitude: ");
                    double latitude = Double.parseDouble(scanner.nextLine());
                    System.out.print("Longitude: ");
                    double longitude = Double.parseDouble(scanner.nextLine());
                    System.out.print("Food Category: ");
                    String foodCategory = scanner.nextLine();
                    System.out.print("Rating (stars, 1-5): ");
                    int stars = Integer.parseInt(scanner.nextLine());
                    System.out.print("Number of Votes: ");
                    int noOfVotes = Integer.parseInt(scanner.nextLine());
                    System.out.print("Logo Path: ");
                    String logoPath = scanner.nextLine();

                    List<Product> products = new ArrayList<>();
                    System.out.print("Number of products to add: ");
                    int productCount = Integer.parseInt(scanner.nextLine());
                    for (int i = 0; i < productCount; i++) {
                        System.out.println("Enter details for product " + (i + 1) + ":");
                        System.out.print("  Product Name: ");
                        String prodName = scanner.nextLine();
                        System.out.print("  Product Type: ");
                        String prodType = scanner.nextLine();
                        System.out.print("  Available Amount: ");
                        int available = Integer.parseInt(scanner.nextLine());
                        System.out.print("  Price: ");
                        double price = Double.parseDouble(scanner.nextLine());
                        products.add(new Product(prodName, prodType, available, price));
                    }
                    Store store = new Store();
                    store.setStoreName(storeName);
                    store.setLatitude(latitude);
                    store.setLongitude(longitude);
                    store.setFoodCategory(foodCategory);
                    store.setStars(stars);
                    store.setNoOfVotes(noOfVotes);
                    store.setStoreLogo(logoPath);
                    store.setProducts(products);
                    store.setAveragePriceOfStore();
                    store.setAveragePriceOfStoreSymbol();

                    String storeJson = gson.toJson(store);
                    System.out.println("Sending ADD_STORE command...");
                    System.out.println("Response: " + sendCommand("ADD_STORE", storeJson));
                    break;
                case 2:
                    // Remove Store
                    System.out.print("Enter the exact store name to remove: ");
                    String removeStoreName = scanner.nextLine();
                    System.out.println("Sending REMOVE_STORE command...");
                    System.out.println("Response: " + sendCommand("REMOVE_STORE", removeStoreName));
                    break;
                case 3:
                    // Add Product
                    System.out.print("Enter store name to add a product: ");
                    String storeNameForAdd = scanner.nextLine();
                    System.out.println("Enter product details:");
                    System.out.print("Product Name: ");
                    String productName = scanner.nextLine();
                    System.out.print("Product Type: ");
                    String productType = scanner.nextLine();
                    System.out.print("Available Amount: ");
                    int availableAmount = Integer.parseInt(scanner.nextLine());
                    System.out.print("Price: ");
                    double productPrice = Double.parseDouble(scanner.nextLine());
                    Product product = new Product(productName, productType, availableAmount, productPrice);
                    String productJson = gson.toJson(product);
                    System.out.println("Sending ADD_PRODUCT command...");
                    System.out.println("Response: " + sendCommand("ADD_PRODUCT", storeNameForAdd + "|" + productJson));
                    break;
                case 4:
                    // Remove Product
                    System.out.print("Enter store name to remove a product: ");
                    String storeNameForRemove = scanner.nextLine();
                    System.out.print("Enter product name to remove: ");
                    String prodToRemove = scanner.nextLine();
                    System.out.println("Sending REMOVE_PRODUCT command...");
                    System.out.println("Response: " + sendCommand("REMOVE_PRODUCT", storeNameForRemove + "|" + prodToRemove));
                    break;
                case 5:
                    // Increase Product Amount
                    System.out.print("Enter store name: ");
                    String storeNameForIncrease = scanner.nextLine();
                    System.out.print("Enter product name: ");
                    String prodNameForIncrease = scanner.nextLine();
                    System.out.print("Enter amount to add: ");
                    String incrementValue = scanner.nextLine();
                    System.out.println("Sending INCREMENT_PRODUCT_AMOUNT command...");
                    System.out.println("Response: " + sendCommand("INCREMENT_PRODUCT_AMOUNT", storeNameForIncrease + "|" + prodNameForIncrease + "|" + incrementValue));
                    break;
                case 6:
                    // Decrease Product Amount
                    System.out.print("Enter store name: ");
                    String storeNameForDecrease = scanner.nextLine();
                    System.out.print("Enter product name: ");
                    String prodNameForDecrease = scanner.nextLine();
                    System.out.print("Enter amount to remove: ");
                    String decrementValue = scanner.nextLine();
                    System.out.println("Sending DECREMENT_PRODUCT_AMOUNT command...");
                    System.out.println("Response: " + sendCommand("DECREMENT_PRODUCT_AMOUNT", storeNameForDecrease + "|" + prodNameForDecrease + "|" + decrementValue));
                    break;
                case 7:
                    // Show Deleted Products Report
                    System.out.println("Sending DELETED_PRODUCTS command...");
                    System.out.println("Deleted Products Report: " + sendCommand("DELETED_PRODUCTS", ""));
                    break;
                case 8:
                    // List Stores
                    System.out.println("Sending LIST_STORES command...");
                    System.out.println("Stores: " + sendCommand("LIST_STORES", ""));
                    break;
                case 9:
                    System.out.println("Exiting Manager Console.");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    public static void main(String[] args) {
        // Manager connects to Master on localhost:12345.
        Manager manager = new Manager("localhost", 12345);
        manager.interactiveMenu();
    }
}
