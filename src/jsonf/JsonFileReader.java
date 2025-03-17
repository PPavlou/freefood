package jsonf;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Utility class to read JSON files.
 */
public class JsonFileReader {

    /**
     * Reads the entire content of a JSON file specified by its file path.
     *
     * @param filePath the path to the JSON file.
     * @return a String containing the content of the JSON file.
     */
    public static String readJsonFile(String filePath) {
        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                jsonContent.append(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading JSON file: " + e.getMessage());
        }
        return jsonContent.toString();
    }
}
