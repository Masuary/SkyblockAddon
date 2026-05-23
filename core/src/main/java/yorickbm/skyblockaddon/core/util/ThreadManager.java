package yorickbm.skyblockaddon.core.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ThreadManager {
    private static final Map<UUID, Thread> activeThreads = new HashMap<>();

    @SuppressWarnings("BusyWait")
    public static UUID startLoopingThread(final RunnableWithParams task, final int delay) {
        final UUID threadId = getNextThreadId();
        final Thread thread = new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    task.run(threadId); // Execute logic
                    Thread.sleep(delay);  // Sleep for delay
                }
            } catch (final InterruptedException e) {
                // Handle interruption if needed
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        activeThreads.put(threadId, thread);
        return threadId;
    }

    public static UUID startThread(final RunnableWithParams task) {
        final UUID threadId = getNextThreadId();
        final Thread thread = new Thread(() -> task.run(threadId));
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        activeThreads.put(threadId, thread);
        return threadId;
    }

    public static void terminateThread(final UUID threadId) {
        final Thread thread = activeThreads.get(threadId);
        if (thread != null) {
            thread.interrupt();
            activeThreads.remove(threadId);
        }
    }

    public static void terminateAllThreads() {
        for (final UUID thread : activeThreads.keySet()) {
            try {
                terminateThread(thread);
            } catch (final Exception ex) {
                //Too late to do anything about it.
            }
        }
    }

    private static synchronized UUID getNextThreadId() {
        return UUID.randomUUID();
    }

    public interface RunnableWithParams {
        void run(UUID threadId);
    }
}