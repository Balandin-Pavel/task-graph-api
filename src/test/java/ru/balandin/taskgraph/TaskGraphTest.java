package ru.balandin.taskgraph;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class TaskGraphTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    public static void main(String[] args) throws Exception {
        TaskGraph graph = new TaskGraph();
        graph.addConstant("base", 21);
        graph.addSum("double", "base", "base");
        graph.addSum("total", "double", "base");
        check(graph.result("double") == 42);
        check(graph.result("total") == 63);
        check(graph.size() == 3);
        try {
            graph.addSum("bad", "base", "missing");
            throw new AssertionError("unknown dependency accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            graph.addConstant("base", 1);
            throw new AssertionError("duplicate task accepted");
        } catch (IllegalArgumentException expected) { }

        var server = new TaskGraphServer(new TaskGraph()).start(0);
        try {
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            check(post(client, base, "id=a&type=constant&value=21") == 201);
            check(post(client, base, "id=b&type=sum&left=a&right=a") == 201);
            var request = HttpRequest.newBuilder(URI.create(base + "/tasks/b")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            check(response.statusCode() == 200 && response.body().contains("\"result\":42"));
            check(post(client, base, "id=c&type=sum&left=a&right=missing") == 400);
        } finally {
            server.stop(0);
        }
        System.out.println("TaskGraph tests passed");
    }

    private static int post(HttpClient client, String base, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + "/tasks"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
