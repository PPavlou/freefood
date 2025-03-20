package model;

import java.util.List;
import java.util.ArrayList;
import com.google.gson.annotations.SerializedName;

/**
 * Represents a store that sells products.
 * Contains store details such as name, location, category, rating, and a list of products.
 */
public class Store {
    @SerializedName("StoreName")
    private String storeName;

    @SerializedName("Latitude")
    private double latitude;

    @SerializedName("Longitude")
    private double longitude;

    @SerializedName("FoodCategory")
    private String foodCategory;

    @SerializedName("Stars")
    private int stars;

    @SerializedName("NoOfVotes")
    private int noOfVotes;

    // These fields are calculated or maintained internally.
    private double totalRevenue = 0.0;
    private double averagePrice;

    @SerializedName("AveragePriceSymbol")
    private String averagePriceSymbol;

    @SerializedName("StoreLogo")
    private String storeLogo;

    @SerializedName("Products")
    private List<Product> products;

    /**
     * Constructs a Store with specified details.
     *
     * @param storeName    The name of the store.
     * @param latitude     The latitude of the store's location.
     * @param longitude    The longitude of the store's location.
     * @param foodCategory The category of food the store specializes in.
     * @param stars        The rating of the store (1-5 stars).
     * @param noOfVotes    The number of votes received.
     * @param totalRevenue The total revenue generated by the store.
     * @param storeLogo    The file path to the store's logo.
     * @param products     A list of products available in the store.
     */
    public Store(String storeName, double latitude, double longitude, String foodCategory,
                 int stars, int noOfVotes, double totalRevenue, String storeLogo, List<Product> products) {
        this.storeName = storeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.foodCategory = foodCategory;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.totalRevenue = totalRevenue;
        this.storeLogo = storeLogo;
        this.products = products;
        this.setAveragePriceOfStore();
        this.setAveragePriceOfStoreSymbol();
    }

    /**
     * Default constructor for Store.
     * Initializes the product list to prevent null references.
     */
    public Store() {
        this.products = new ArrayList<>();
    }

    /**
     * Gets the store name.
     *
     * @return The name of the store.
     */
    public String getStoreName() {
        return storeName;
    }

    /**
     * Sets the store name.
     *
     * @param storeName The new name of the store.
     */
    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    /**
     * Gets the latitude of the store.
     *
     * @return The latitude.
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Sets the latitude of the store.
     *
     * @param latitude The new latitude.
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * Gets the longitude of the store.
     *
     * @return The longitude.
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * Sets the longitude of the store.
     *
     * @param longitude The new longitude.
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * Gets the food category of the store.
     *
     * @return The food category.
     */
    public String getFoodCategory() {
        return foodCategory;
    }

    /**
     * Sets the food category of the store.
     *
     * @param foodCategory The new food category.
     */
    public void setFoodCategory(String foodCategory) {
        this.foodCategory = foodCategory;
    }

    /**
     * Gets the rating (stars) of the store.
     *
     * @return The rating.
     */
    public int getStars() {
        return stars;
    }

    /**
     * Sets the rating (stars) of the store.
     *
     * @param stars The new rating.
     */
    public void setStars(int stars) {
        this.stars = stars;
    }

    /**
     * Gets the number of votes received by the store.
     *
     * @return The number of votes.
     */
    public int getNoOfVotes() {
        return noOfVotes;
    }

    /**
     * Sets the number of votes for the store.
     *
     * @param noOfVotes The new number of votes.
     */
    public void setNoOfVotes(int noOfVotes) {
        this.noOfVotes = noOfVotes;
    }

    /**
     * Gets the total revenue generated by the store.
     *
     * @return The total revenue.
     */
    public double getTotalRevenue() {
        return totalRevenue;
    }

    /**
     * Sets the total revenue generated by the store.
     *
     * @param totalRevenue The new total revenue.
     */
    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    /**
     * Gets the file path to the store's logo.
     *
     * @return The store logo path.
     */
    public String getStoreLogo() {
        return storeLogo;
    }

    /**
     * Sets the file path for the store's logo.
     *
     * @param storeLogo The new store logo path.
     */
    public void setStoreLogo(String storeLogo) {
        this.storeLogo = storeLogo;
    }

    /**
     * Gets the list of products available in the store.
     *
     * @return The product list.
     */
    public List<Product> getProducts() {
        return products;
    }

    /**
     * Sets the list of products available in the store.
     *
     * @param products The new product list.
     */
    public void setProducts(List<Product> products) {
        this.products = products;
    }

    /**
     * Gets the symbol of the store's average price.
     *
     * @return The average price symbol.
     */
    public String getAveragePriceOfStoreSymbol() {
        return averagePriceSymbol;
    }

    /**
     * Sets the symbol of the store's average price.
     * For example, if averagePrice > 15, symbol could be "$$$".
     */
    public void setAveragePriceOfStoreSymbol() {
        if (products.isEmpty()) {
            averagePriceSymbol = "Not any products in the store";
            return;
        }
        if (averagePrice < 5.0) {
            averagePriceSymbol = "$";
        } else if (averagePrice < 15.0) {
            averagePriceSymbol = "$$";
        } else {
            averagePriceSymbol = "$$$";
        }
    }

    /**
     * Gets the average price of the store.
     *
     * @return The average price.
     */
    public double getAveragePriceOfStore() {
        return averagePrice;
    }

    /**
     * Sets the average price of the store.
     * This is calculated based on the prices of products.
     */
    public void setAveragePriceOfStore() {
        if (products.isEmpty()) {
            averagePrice = 0.0;
            return;
        }
        double sum = 0.0;
        for (Product product : products) {
            sum += product.getPrice();
        }
        averagePrice = sum / products.size();
    }

    /**
     * Purchases a product from the store.
     *
     * @param productName The name of the product to purchase.
     * @param quantity    The quantity of the product to purchase.
     * @return True if the purchase was successful, false otherwise.
     */
    public synchronized boolean purchaseProduct(String productName, int quantity) {
        for (Product product : products) {
            if (product.getProductName().equals(productName)) {
                if (product.getAvailableAmount() >= quantity) {
                    product.setAvailableAmount(product.getAvailableAmount() - quantity);
                    totalRevenue += quantity * product.getPrice();
                    return true;
                } else {
                    return false; // Not enough stock available.
                }
            }
        }
        return false; // Product not found.
    }

    /**
     * Adds a product to the store's inventory.
     *
     * @param product The product to be added.
     */
    public void addProduct(Product product) {
        if (this.products == null) {
            this.products = new ArrayList<>();
        }
        this.products.add(product);
    }

    /**
     * Removes a product from the store's inventory based on the product name.
     *
     * @param productName The name of the product to remove.
     * @return True if the product was found and removed, false otherwise.
     */
    public boolean removeProduct(String productName) {
        if (this.products != null) {
            return this.products.removeIf(p -> p.getProductName().equals(productName));
        }
        return false;
    }

    public void updateStoreReviews(int review)
    {
        int reviewSum = this.stars*this.noOfVotes;
        reviewSum += review;
        this.stars = reviewSum / (noOfVotes+1);
        this.noOfVotes += 1;
    }

    public void updateStorePrices()
    {
        setAveragePriceOfStore();
        setAveragePriceOfStoreSymbol();
    }

    /**
     * Returns a string representation of the Store object.
     *
     * @return A string containing store details.
     */
    @Override
    public String toString() {
        return "Store{" +
                "storeName='" + storeName + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", foodCategory='" + foodCategory + '\'' +
                ", stars=" + stars +
                ", noOfVotes=" + noOfVotes +
                ", storeLogo='" + storeLogo + '\'' +
                ", products=" + products + '\''+
                ", AveragePrices=" + averagePriceSymbol +
                '}';
    }
}
