import com.sun.net.httpserver.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;

public class Main {
    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
            server.createContext("/", new StaticFileHandler("src/main/resources/index.html"));
            server.createContext("/styles.css", new StaticFileHandler("src/main/resources/styles.css"));
            server.createContext("/addPart", new AddPartHandler());
            server.createContext("/deletePart", new DeletePartHandler());
            server.createContext("/listParts", new ListPartsHandler());
            server.setExecutor(null); 
            server.start();
            System.out.println("Server started on port 8081");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class StaticFileHandler implements HttpHandler {
        private final String filePath;

        public StaticFileHandler(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("Serving static file: " + filePath);
            byte[] response = Files.readAllBytes(Paths.get(filePath));
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    static class AddPartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                String[] params = new String(t.getRequestBody().readAllBytes()).split("&");
                String partName = params[0].split("=")[1];
                int partTypeId = Integer.parseInt(params[1].split("=")[1]);
                int quantity = Integer.parseInt(params[2].split("=")[1]);
                double price = Double.parseDouble(params[3].split("=")[1]);

                try (Connection connection = DatabaseConnection.getConnection()) {
                    String query = "INSERT INTO parts (part_name, part_type_id, quantity, price) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(query)) {
                        statement.setString(1, partName);
                        statement.setInt(2, partTypeId);
                        statement.setInt(3, quantity);
                        statement.setDouble(4, price);
                        statement.executeUpdate();
                    }
                    sendResponse(t, "Деталь додано успішно.");
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendResponse(t, "Помилка при додаванні деталі.");
                }
            }
        }
    }

    static class DeletePartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                String[] params = new String(t.getRequestBody().readAllBytes()).split("=");
                int partId = Integer.parseInt(params[1]);

                try (Connection connection = DatabaseConnection.getConnection()) {
                    String query = "DELETE FROM parts WHERE id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(query)) {
                        statement.setInt(1, partId);
                        statement.executeUpdate();
                    }
                    sendResponse(t, "Деталь видалено успішно.");
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendResponse(t, "Помилка при видаленні деталі.");
                }
            }
        }
    }

    static class ListPartsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("Handling /listParts request");
            try (Connection connection = DatabaseConnection.getConnection()) {
                String query = "SELECT * FROM parts";
                List<Part> parts = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(query);
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        parts.add(new Part(
                                resultSet.getInt("id"),
                                resultSet.getString("part_name"),
                                resultSet.getInt("part_type_id"),
                                resultSet.getInt("quantity"),
                                resultSet.getDouble("price")
                        ));
                    }
                }
                String json = new Gson().toJson(parts);
                t.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(t, json);
            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(t, "Помилка при отриманні списку деталей.");
            }
        }
    }

    private static void sendResponse(HttpExchange t, String response) throws IOException {
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}


