package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.util.exceptions.FunctionNotFoundException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FunctionRegistryTest {
    @Test
    void successfulFunctionCanOnlyExecuteOnce() {
        final FunctionRegistry<String> registry = new FunctionRegistry<>();
        final UUID id = UUID.randomUUID();
        final AtomicInteger invocationCount = new AtomicInteger();
        registry.register(id, value -> {
            invocationCount.incrementAndGet();
            return true;
        }, 5);

        registry.execute(id, "executor");

        assertEquals(1, invocationCount.get());
        assertThrows(FunctionNotFoundException.class, () -> registry.execute(id, "executor"));
    }

    @Test
    void rejectsNonPositiveExpiry() {
        final FunctionRegistry<String> registry = new FunctionRegistry<>();
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(UUID.randomUUID(), value -> true, 0));
    }
}
