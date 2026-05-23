package yorickbm.skyblockaddon.core.util;

import com.google.common.base.CharMatcher;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

/**
 * @author diesieben07
 * Source: https://github.com/diesieben07/SevenCommons/blob/1.7/src/main/java/de/take_weiland/mods/commons/internal/UsernameCache.java
 */
public final class UsernameCache {
    private static LoadingCache<UUID, String> cache;

    public static String getBlocking(final UUID uuid) {
        return get(uuid).exceptionally(ex -> {
            //LOGGER.warn("Failure while contacting Mojang API (" + uuid.toString() + ").");
            return "-"; // Default value if there's an error
        }).getNow("...");
    }

    public static CompletableFuture<String> get(final UUID uuid) {
        final String name = cache.getIfPresent(uuid);
        if (name != null) {
            return CompletableFuture.completedFuture(name);
        } else {
            final CompletableFuture<String> future = new CompletableFuture<>();
            ForkJoinPool.commonPool().execute(() -> {
                try {
                    future.complete(cache.get(uuid));
                } catch (final ExecutionException | UncheckedExecutionException x) {
                    future.completeExceptionally(x.getCause());
                } catch (final Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        }
    }

    public static void invalidate(final UUID uuid) {
        cache.invalidate(uuid);
    }

    public static void initCache(final int cacheSize) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .build(new Loader());
    }

    public static void registerPlayername(UUID uuid, String name) {
        cache.put(uuid, name);
    }

    private static final class Loader extends CacheLoader<UUID, String> {

        Loader() {
        }

        private static final String USERNAME_API_URL = "https://api.mojang.com/user/profile/%s";
        private static final CharMatcher DASH_MATCHER = CharMatcher.is('-');

        @Override
        public String load(final UUID uuid) throws IOException {
            final String uuidString = DASH_MATCHER.removeFrom(uuid.toString());
            try (final BufferedReader reader = Resources.asCharSource(new URL(String.format(USERNAME_API_URL, uuidString)), StandardCharsets.UTF_8).openBufferedStream()) {
                final JsonReader json = new JsonReader(reader);
                String name = "";

                json.beginObject();
                while (json.hasNext()) {
                    final String key = json.nextName();
                    if (key.equals("name")) {
                        name = json.nextString();
                    } else {
                        json.skipValue();
                    }
                }
                json.endObject();

                return name;
            }
        }
    }
}