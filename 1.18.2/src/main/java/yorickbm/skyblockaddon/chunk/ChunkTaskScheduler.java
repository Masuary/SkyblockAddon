package yorickbm.skyblockaddon.chunk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChunkTaskScheduler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Queue<ScheduledTask> taskQueue = new ConcurrentLinkedQueue<>();

    private static int TASKS_CONCURRENTLY = 2;
    private static int TICKS_PER_BATCH = 40;
    private static int tickCounter = 0;

    public static void init() {
        try {
            ChunkTaskScheduler.TICKS_PER_BATCH = Integer.parseInt(SkyblockAddonConfig.getForKey("purge.batch_time"));
            ChunkTaskScheduler.TASKS_CONCURRENTLY = Integer.parseInt(SkyblockAddonConfig.getForKey("purge.concurrent"));
        } catch (Exception ex) {
            ChunkTaskScheduler.TICKS_PER_BATCH = 200;
            ChunkTaskScheduler.TASKS_CONCURRENTLY = 2;
        }
    }

    public static UUID queue(Runnable task) {
        ScheduledTask scheduled = new ScheduledTask(task);
        taskQueue.offer(scheduled);
        LOGGER.debug("Queued task with ID " + scheduled.id);
        return scheduled.id;
    }

    public static void forwardTicks() {
        tickCounter++;

        if (tickCounter <= TICKS_PER_BATCH) {
            return; // Not time yet to run tasks
        }

        runTick(tickCounter);
        tickCounter = 0;
    }

    /**
     * Call this method every tick.
     * It will only run tasks every TICKS_PER_BATCH ticks.
     */
    public static void runTick(int tick) {
        List<ScheduledTask> tasksToRun = new ArrayList<>();

        // First, collect tasks up to concurrency limit
        for (int i = 0; i < TASKS_CONCURRENTLY; i++) {
            ScheduledTask next = taskQueue.poll();
            if (next == null) break;
            tasksToRun.add(next);
        }

        // Then, execute the collected tasks
        for (ScheduledTask task : tasksToRun) {
            LOGGER.debug("Starting execution of task ID {}", task.id);

            // Run the task on main thread
            task.task.run();
        }
    }

    public static void clear() {
        LOGGER.debug("Clearing all scheduled and running tasks.");
        taskQueue.clear();
        tickCounter = 0; // Reset tick counter as well
    }

    private static class ScheduledTask {
        final UUID id;
        final Runnable task;

        ScheduledTask(Runnable task) {
            this.id = UUID.randomUUID();
            this.task = task;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ScheduledTask other && this.id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}