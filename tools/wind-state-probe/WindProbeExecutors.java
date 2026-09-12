package windprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Captures only the known FrozenLib cape executor for bounded probe shutdown. */
public final class WindProbeExecutors {
    private static final List<ExecutorService> CAPTURED = new CopyOnWriteArrayList<>();

    private WindProbeExecutors() {
    }

    public static void capture(ExecutorService executor) {
        CAPTURED.add(executor);
    }

    public static ShutdownEvidence shutdown() {
        List<ExecutorService> executors = new ArrayList<>(CAPTURED);
        CAPTURED.clear();
        for (ExecutorService executor : executors) {
            executor.shutdown();
        }

        boolean terminated = true;
        for (ExecutorService executor : executors) {
            try {
                if (!executor.awaitTermination(3L, TimeUnit.SECONDS)) {
                    terminated = false;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                terminated = false;
            }
        }
        return new ShutdownEvidence(executors.size(), terminated);
    }

    public record ShutdownEvidence(int captured, boolean terminated) {
        public String asText() {
            return "cleanup.capeExecutors=" + captured + "\n"
                    + "cleanup.capeExecutorsTerminated=" + terminated + "\n";
        }
    }
}
