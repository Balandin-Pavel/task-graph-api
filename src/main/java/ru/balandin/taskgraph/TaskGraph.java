package ru.balandin.taskgraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A small, synchronous DAG executor.
 * Nodes are immutable; results are cached after their first evaluation.
 */
public final class TaskGraph {
    private sealed interface Operation permits Constant, Add {
        int evaluate(List<Integer> arguments);
    }

    private record Constant(int value) implements Operation {
        @Override public int evaluate(List<Integer> arguments) { return value; }
    }

    private record Add() implements Operation {
        @Override public int evaluate(List<Integer> arguments) {
            return Math.addExact(arguments.get(0), arguments.get(1));
        }
    }

    private record Task(List<String> dependencies, Operation operation) {
        Task {
            dependencies = List.copyOf(dependencies);
            Objects.requireNonNull(operation);
        }
    }

    private final Map<String, Task> tasks = new HashMap<>();
    private final Map<String, Integer> results = new HashMap<>();

    public synchronized void addConstant(String id, int value) {
        put(id, new Task(List.of(), new Constant(value)));
    }

    public synchronized void addSum(String id, String left, String right) {
        requireExisting(left);
        requireExisting(right);
        put(id, new Task(List.of(left, right), new Add()));
    }

    public synchronized int result(String id) {
        requireExisting(id);
        return evaluate(id, new HashSet<>());
    }

    public synchronized int size() { return tasks.size(); }

    private void put(String id, Task task) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("task id is required");
        }
        if (tasks.putIfAbsent(id, task) != null) {
            throw new IllegalArgumentException("task id already exists");
        }
    }

    private void requireExisting(String id) {
        if (!tasks.containsKey(id)) {
            throw new IllegalArgumentException("unknown task: " + id);
        }
    }

    private int evaluate(String id, Set<String> visiting) {
        Integer cached = results.get(id);
        if (cached != null) return cached;
        if (!visiting.add(id)) throw new IllegalStateException("cycle detected");
        Task task = tasks.get(id);
        List<Integer> arguments = task.dependencies().stream()
                .map(dependency -> evaluate(dependency, visiting)).toList();
        int value = task.operation().evaluate(arguments);
        visiting.remove(id);
        results.put(id, value);
        return value;
    }
}
