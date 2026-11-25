package ru.balandin.taskgraph;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class TaskGraphServer {
    private final TaskGraph graph;

    public TaskGraphServer(TaskGraph graph) {
        this.graph = graph;
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/tasks", this::handle);
        server.start();
        return server;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (method.equals("POST") && path.equals("/tasks")) {
                Map<String, String> fields = parseForm(exchange);
                String id = fields.get("id");
                String type = fields.get("type");
                if ("constant".equals(type)) {
                    graph.addConstant(id, Integer.parseInt(fields.get("value")));
                } else if ("sum".equals(type)) {
                    graph.addSum(id, fields.get("left"), fields.get("right"));
                } else {
                    throw new IllegalArgumentException("type must be constant or sum");
                }
                respond(exchange, 201, "{\"id\":\"" + escape(id) + "\"}");
            } else if (method.equals("GET") && path.startsWith("/tasks/")) {
                String id = URLDecoder.decode(path.substring("/tasks/".length()), StandardCharsets.UTF_8);
                int value = graph.result(id);
                respond(exchange, 200, "{\"id\":\"" + escape(id) + "\",\"result\":" + value + "}");
            } else {
                respond(exchange, 404, "{\"error\":\"not found\"}");
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            respond(exchange, 400, "{\"error\":\"invalid task\"}");
        } catch (ArithmeticException e) {
            respond(exchange, 422, "{\"error\":\"integer overflow\"}");
        }
    }

    private static Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8081 : Integer.parseInt(args[0]);
        new TaskGraphServer(new TaskGraph()).start(port);
        System.out.println("TaskGraph API: http://127.0.0.1:" + port);
    }
}
