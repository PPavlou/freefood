package model;

/**
 * Represents a product available in a store.
 * Contains details such as name, type, available stock, and price.
 */
public class Product {
    private String productName;
    private String productType;
    private int availableAmount;
    private double price;

    /**
     * Constructs a Product with specified details.
     *
     * @param productName   The name of the product.
     * @param productType   The type/category of the product.
     * @param availableAmount The available stock quantity of the product.
     * @param price         The price of the product.
     */
    public Product(String productName, String productType, int availableAmount, double price) {
        this.productName = productName;
        this.productType = productType;
        this.availableAmount = availableAmount;
        this.price = price;
    }

    /**
     * Default constructor for Product.
     */
    public Product() {}

    /**
     * Gets the product name.
     *
     * @return The name of the product.
     */
    public String getProductName() {
        return productName;
    }

    /**
     * Sets the product name.
     *
     * @param productName The new name of the product.
     */
    public void setProductName(String productName) {
        this.productName = productName;
    }

    /**
     * Gets the product type.
     *
     * @return The type/category of the product.
     */
    public String getProductType() {
        return productType;
    }

    /**
     * Sets the product type.
     *
     * @param productType The new type/category of the product.
     */
    public void setProductType(String productType) {
        this.productType = productType;
    }

    /**
     * Gets the available stock quantity of the product.
     *
     * @return The available amount.
     */
    public int getAvailableAmount() {
        return availableAmount;
    }

    /**
     * Sets the available stock quantity.
     *
     * @param availableAmount The new available amount.
     */
    public void setAvailableAmount(int availableAmount) {
        this.availableAmount = availableAmount;
    }

    /**
     * Gets the price of the product.
     *
     * @return The price.
     */
    public double getPrice() {
        return price;
    }

    /**
     * Sets the price of the product.
     *
     * @param price The new price.
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Returns a string representation of the Product object.
     *
     * @return A string containing product details.
     */
    @Override
    public String toString() {
        return "Product{" +
                "productName='" + productName + '\'' +
                ", productType='" + productType + '\'' +
                ", availableAmount=" + availableAmount +
                ", price=" + price +
                '}';
    }
}

