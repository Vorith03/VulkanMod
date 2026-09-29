package net.vulkanmod.render.profiling;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Legacy on-screen profiler retained for the Alt+F8 overlay.
 *
 * <p>The old implementation allocated a timing tree every frame even when the
 * overlay was hidden. Normal gameplay now only bridges its existing named hooks
 * into {@link PerformanceProfiler}; the allocation-heavy tree is active only
 * while the overlay is actually visible.</p>
 */
public class Profiler2 {
    private static final boolean DEBUG = false;
    private static final float INV_CONVERSION = 1.0f / 1000.0f;
    private static final long POLL_PERIOD = 100_000_000L;
    private static final int SAMPLE_NUM = 200;
    private static final int PERF_STACK_SIZE = 64;

    private static final Profiler2 MAIN_PROFILER = new Profiler2("Main");

    public static Profiler2 getMainProfiler() {
        return MAIN_PROFILER;
    }

    private final String name;
    private Entries entries;
    private final LinkedList<Entries> entriesStack = new LinkedList<>();
    private boolean hasStarted;
    private boolean overlaySampling;

    private List<Result> lastResults;
    private long lastPollTime;

    private final PerformanceProfiler.Stage[] perfStages = new PerformanceProfiler.Stage[PERF_STACK_SIZE];
    private final long[] perfStarts = new long[PERF_STACK_SIZE];
    private int perfDepth;

    public Profiler2(String name) {
        this.name = name;
        this.entries = new Entries(name);
    }

    public void start() {
        syncOverlayState();
        if (!overlaySampling) {
            return;
        }
        if (hasStarted) {
            roundOverlay();
        }
        hasStarted = true;
    }

    public void push(String name) {
        bridgePush(name);
        syncOverlayState();
        if (overlaySampling) {
            entries.push(name);
        }
    }

    public void pop() {
        bridgePop();
        syncOverlayState();
        if (overlaySampling) {
            entries.pop();
        }
    }

    public void round() {
        syncOverlayState();
        if (overlaySampling) {
            roundOverlay();
        }
    }

    public List<Result> getResults() {
        syncOverlayState();
        if (!overlaySampling) {
            return zeroResults();
        }
        if ((System.nanoTime() - lastPollTime) < POLL_PERIOD && lastResults != null) {
            return lastResults;
        }
        if (entriesStack.isEmpty()) {
            return zeroResults();
        }

        Entries template = entriesStack.getFirst();
        List<Result> results = new ArrayList<>();
        results.add(new Result(template.mainNode.name));
        for (Node node : template.mainNode.children) {
            results.add(new Result(node.name));
        }

        int resultSize = results.size();
        for (Entries sample : entriesStack) {
            results.get(0).addValue(sample.mainNode.value);
            List<Node> nodes = sample.mainNode.children;
            for (int i = 0; i < nodes.size() && i + 1 < resultSize; i++) {
                results.get(i + 1).addValue(nodes.get(i).value);
            }
        }

        results.forEach(Result::computeAvg);
        lastPollTime = System.nanoTime();
        return lastResults = results;
    }

    private void bridgePush(String name) {
        if (!PerformanceProfiler.isEnabled()) {
            return;
        }

        int depth = perfDepth++;
        if (depth >= PERF_STACK_SIZE) {
            return;
        }

        PerformanceProfiler.Stage stage = PerformanceProfiler.Stage.fromLegacyName(name);
        perfStages[depth] = stage;
        perfStarts[depth] = stage == null ? 0L : PerformanceProfiler.begin(stage);
    }

    private void bridgePop() {
        if (!PerformanceProfiler.isEnabled() || perfDepth <= 0) {
            return;
        }

        int depth = --perfDepth;
        if (depth >= PERF_STACK_SIZE) {
            return;
        }

        PerformanceProfiler.Stage stage = perfStages[depth];
        long start = perfStarts[depth];
        perfStages[depth] = null;
        perfStarts[depth] = 0L;
        if (stage != null) {
            PerformanceProfiler.end(stage, start);
        }
    }

    private void syncOverlayState() {
        boolean shouldSample = ProfilerOverlay.shouldRender;
        if (shouldSample == overlaySampling) {
            return;
        }

        overlaySampling = shouldSample;
        entriesStack.clear();
        entries = new Entries(name);
        hasStarted = false;
        lastResults = null;
        lastPollTime = 0L;
    }

    private void roundOverlay() {
        entries.round();
        if (entriesStack.size() >= SAMPLE_NUM) {
            entriesStack.pollLast();
        }
        entriesStack.push(entries);
        entries = new Entries(name);
        hasStarted = false;
        lastResults = null;
    }

    private List<Result> zeroResults() {
        Result result = new Result(name);
        result.addValue(0.0f);
        result.computeAvg();
        List<Result> results = new ArrayList<>(1);
        results.add(result);
        return results;
    }

    public static class Result {
        public final String name;
        private float value;
        private int count;

        public Result(String name) {
            this.name = name;
        }

        public void addValue(float value) {
            this.value += value;
            count++;
        }

        public float computeAvg() {
            if (count == 0) {
                value = 0.0f;
            } else {
                value /= count * 1000.0f;
            }
            return value;
        }

        public float getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("%s: %.3f", name, value);
        }
    }

    private static float convert(float nanos) {
        return nanos * INV_CONVERSION;
    }

    private static class Entries {
        private final Node mainNode;
        private Node currentNode;
        private byte level;

        Entries(String name) {
            mainNode = new Node(null, name);
            currentNode = mainNode;
        }

        void push(String name) {
            currentNode = new Node(currentNode, name);
            level++;
        }

        void pop() {
            if (currentNode == mainNode || currentNode.parent == null) {
                if (DEBUG) {
                    System.err.println("Profiler pop with empty stack");
                }
                return;
            }

            Node parent = currentNode.parent;
            currentNode.computeDelta();
            parent.addChild(currentNode);
            currentNode = parent;
            level--;
        }

        void round() {
            if (DEBUG && level != 0) {
                System.err.println("Profiler stack level is not 0");
            }
            mainNode.computeDelta();
        }
    }

    private static class Node {
        private final String name;
        private float value;
        private final long start;
        private final Node parent;
        private final LinkedList<Node> children = new LinkedList<>();

        Node(@Nullable Node parent, String name) {
            this.parent = parent;
            this.name = name;
            this.start = System.nanoTime();
        }

        void addChild(Node node) {
            children.add(node);
        }

        void computeDelta() {
            value = convert(System.nanoTime() - start);
        }
    }
}
