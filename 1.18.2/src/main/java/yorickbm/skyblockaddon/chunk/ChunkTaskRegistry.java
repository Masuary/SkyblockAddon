package yorickbm.skyblockaddon.chunk;

import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkTaskRegistry {
    private static final Map<ChunkPos, UUID> chunkToTaskMap = new ConcurrentHashMap<>();
    private static final Map<UUID, IslandChunkManager.Task> taskMap = new ConcurrentHashMap<>();

    public static void registerTask(IslandChunkManager.Task task) {
        taskMap.put(task.getId(), task);
    }

    public static void mapChunkToTask(ChunkPos pos, UUID taskId) {
        chunkToTaskMap.put(pos, taskId);
    }

    public static Optional<IslandChunkManager.Task> getTaskForChunk(ChunkPos pos) {
        UUID id = chunkToTaskMap.get(pos);
        return Optional.ofNullable(taskMap.get(id));
    }

    public static void removeTask(UUID taskId) {
        taskMap.remove(taskId);
        chunkToTaskMap.entrySet().removeIf(e -> e.getValue().equals(taskId));
    }
}
