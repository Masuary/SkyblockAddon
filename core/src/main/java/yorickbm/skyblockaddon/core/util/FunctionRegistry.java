package yorickbm.skyblockaddon.core.util;

import yorickbm.skyblockaddon.core.util.exceptions.FunctionNotFoundException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class FunctionRegistry<T> {
    private final Map<UUID, RegisteredFunction<T>> functionMap = new ConcurrentHashMap<>();

    public void register(final UUID hash, final Function<T, Boolean> function, final int durationMinutes) {
        if (durationMinutes <= 0) throw new IllegalArgumentException("Function duration must be positive");
        final long expirationNanos = System.nanoTime() + java.time.Duration.ofMinutes(durationMinutes).toNanos();
        functionMap.entrySet().removeIf(entry -> entry.getValue().isExpired());
        functionMap.put(hash, new RegisteredFunction<>(function, expirationNanos));
    }

    public void execute(final UUID hash, final T executor) {
        final RegisteredFunction<T> registeredFunction = functionMap.get(hash);
        if (registeredFunction == null) {
            throw new FunctionNotFoundException(hash);
        }
        if (registeredFunction.isExpired()) {
            functionMap.remove(hash, registeredFunction);
            throw new FunctionNotFoundException(hash);
        }
        if (!functionMap.remove(hash, registeredFunction)) {
            throw new FunctionNotFoundException(hash);
        }

        boolean consumed = false;
        try {
            consumed = registeredFunction.function.apply(executor);
        } finally {
            if (!consumed && !registeredFunction.isExpired()) {
                functionMap.putIfAbsent(hash, registeredFunction);
            }
        }
    }

    public static String getCommand(final UUID uuid) {
        return "/island registry %s".formatted(uuid.toString());
    }

    private static final class RegisteredFunction<T> {
        private final Function<T, Boolean> function;
        private final long expirationNanos;

        private RegisteredFunction(final Function<T, Boolean> function, final long expirationNanos) {
            this.function = function;
            this.expirationNanos = expirationNanos;
        }

        private boolean isExpired() {
            return System.nanoTime() >= expirationNanos;
        }
    }
}
