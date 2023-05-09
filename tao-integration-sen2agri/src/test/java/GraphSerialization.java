import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class GraphSerialization {

    public static void main(String[] args) throws IOException {
        final StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(GraphSerialization.class.getResourceAsStream("example.json")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line).append("\n");
            }
        }

        System.exit(0);
    }

}
