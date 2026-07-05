package yorickbm.skyblockaddon.core.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ThreadManager {
    private static final Map<UUID, Thread> activeThreads = new ConcurrentHashMap<>();

    @SuppressWarnings("BusyWait")
    public static UUID startLoopingThread(final RunnableWithParams task, final int delay) {
        final UUID threadId = getNextThreadId();
        final Thread thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    task.run(threadId); // Execute logic
                    Thread.sleep(delay);  // Sleep for delay
                }
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                activeThreads.remove(threadId);
            }
        }, "SkyblockAddon-Worker-" + threadId);
        thread.setPriority(Thread.MIN_PRIORITY);
        activeThreads.put(threadId, thread);
        thread.start();
        return threadId;
    }

    public static UUID startThread(final RunnableWithParams task) {
        final UUID threadId = getNextThreadId();
        final Thread thread = new Thread(() -> {
            try {
                task.run(threadId);
            } finally {
                activeThreads.remove(threadId);
            }
        }, "SkyblockAddon-Worker-" + threadId);
        thread.setPriority(Thread.MIN_PRIORITY);
        activeThreads.put(threadId, thread);
        thread.start();
        return threadId;
    }

    public static void terminateThread(final UUID threadId) {
        if (threadId == null) return;
        final Thread thread = activeThreads.remove(threadId);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public static void terminateAllThreads() {
        final Thread currentThread = Thread.currentThread();
        final Thread[] threads = activeThreads.values().toArray(Thread[]::new);
        activeThreads.clear();
        for (final Thread thread : threads) thread.interrupt();

        for (final Thread thread : threads) {
            if (thread == currentThread) continue;
            try {
                thread.join(5_000L);
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping SkyblockAddon worker threads", exception);
            }
            if (thread.isAlive()) {
                throw new IllegalStateException("SkyblockAddon worker did not stop: " + thread.getName());
            }
        }
    }

    private static UUID getNextThreadId() {
        return UUID.randomUUID();
    }

    public interface RunnableWithParams {
        void run(UUID threadId);
    }
}
