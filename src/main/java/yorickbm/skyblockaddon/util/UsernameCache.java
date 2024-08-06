package yorickbm.skyblockaddon.util;

import com.google.common.base.CharMatcher;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gson.stream.JsonReader;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;

import javax.annotation.Nonnull;
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

    public static String getBlocking(UUID uuid) throws IOException {
        try {
            return cache.get(uuid);
        } catch (UncheckedExecutionException | ExecutionException e) {
            throw new IOException("Failed contacting Mojang API", e);
        }
    }

    public static CompletableFuture<String> get(UUID uuid) {
        String name = cache.getIfPresent(uuid);
        if (name != null) {
            return CompletableFuture.completedFuture(name);
        } else {
            CompletableFuture<String> future = new CompletableFuture<>();
            ForkJoinPool.commonPool().execute(() -> {
                try {
                    future.complete(cache.get(uuid));
                } catch (ExecutionException | UncheckedExecutionException x) {
                    future.completeExceptionally(x.getCause());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        }
    }

    public static void invalidate(UUID uuid) {
        cache.invalidate(uuid);
    }

    public static void initCache(int cacheSize) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .build(new Loader());
    }

    public static void onPlayerLogin(Player player) {
        GameProfile profile = player.getGameProfile();
        cache.put(profile.getId(), profile.getName());
    }

    private static final class Loader extends CacheLoader<UUID, String> {

        Loader() {
        }

        private static final CharMatcher DASH_MATCHER = CharMatcher.is('-');

        @Override
        public @NotNull String load(@Nonnull UUID uuid) throws IOException {
            String uuidString = DASH_MATCHER.removeFrom(uuid.toString());
            try (BufferedReader reader = Resources.asCharSource(new URL(String.format(SkyblockAddonConfig.getForKey("mojang.API"), uuidString)), StandardCharsets.UTF_8).openBufferedStream()) {
                JsonReader json = new JsonReader(reader);
                String name = null;

                json.beginObject();
                while (json.hasNext()) {
                    String key = json.nextName();
                    if (key.equals("name")) {
                        name = json.nextString();
                    } else {
                        json.skipValue();
                    }
                }
                json.endObject();

                if (name == null) {
                    throw new IOException("Failed connecting to the Mojang API");
                }

                return name;
            }
        }
    }

}
