package net.vulkanmod.render.chunk;

import java.util.ArrayList;
import java.util.List;

public final class ResourceReloadGenerationTest {
    public static void main(String[] args) {
        rejectsNonReloadGeneration();
        rejectsOverlappingGeneration();
        failureSkipsPurgeButRunsRecovery();
        successPurgesBeforeRecovery();
        staleCompletionIsIgnored();
        System.out.println("Resource reload generation tests passed");
    }

    private static void rejectsNonReloadGeneration() {
        ResourceReloadMemoryManager.ReloadGenerationGate gate =
                new ResourceReloadMemoryManager.ReloadGenerationGate();
        require(gate.begin(false) == 0L, "non-reload must not receive a generation");
    }

    private static void rejectsOverlappingGeneration() {
        ResourceReloadMemoryManager.ReloadGenerationGate gate =
                new ResourceReloadMemoryManager.ReloadGenerationGate();
        long generation = gate.begin(true);
        require(generation != 0L, "real reload must receive a generation");
        require(gate.begin(true) == 0L, "only one generation may be active");
        require(gate.complete(generation, false, () -> {
        }, () -> {
        }), "active generation must complete");
    }

    private static void failureSkipsPurgeButRunsRecovery() {
        ResourceReloadMemoryManager.ReloadGenerationGate gate =
                new ResourceReloadMemoryManager.ReloadGenerationGate();
        List<String> events = new ArrayList<>();
        long generation = gate.begin(true);

        require(gate.complete(
                generation,
                false,
                () -> events.add("purge"),
                () -> events.add("recovery")), "failed generation must be accepted");
        require(events.equals(List.of("recovery")),
                "failed reload must skip purge and preserve recovery");
    }

    private static void successPurgesBeforeRecovery() {
        ResourceReloadMemoryManager.ReloadGenerationGate gate =
                new ResourceReloadMemoryManager.ReloadGenerationGate();
        List<String> events = new ArrayList<>();
        long generation = gate.begin(true);

        require(gate.complete(
                generation,
                true,
                () -> events.add("purge"),
                () -> events.add("recovery")), "successful generation must be accepted");
        require(events.equals(List.of("purge", "recovery")),
                "successful reload must purge before recovery");
    }

    private static void staleCompletionIsIgnored() {
        ResourceReloadMemoryManager.ReloadGenerationGate gate =
                new ResourceReloadMemoryManager.ReloadGenerationGate();
        List<String> events = new ArrayList<>();
        long first = gate.begin(true);
        require(gate.complete(first, false, () -> {
        }, () -> {
        }), "first generation must complete");

        long second = gate.begin(true);
        require(second != 0L && second != first, "next reload must receive a new generation");
        require(!gate.complete(
                first,
                true,
                () -> events.add("stale-purge"),
                () -> events.add("stale-recovery")), "stale completion must be rejected");
        require(events.isEmpty(), "stale completion must not run either callback");
        require(gate.complete(
                second,
                true,
                () -> events.add("purge"),
                () -> events.add("recovery")), "current generation must complete");
        require(events.equals(List.of("purge", "recovery")),
                "current generation callbacks must retain their order");
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
