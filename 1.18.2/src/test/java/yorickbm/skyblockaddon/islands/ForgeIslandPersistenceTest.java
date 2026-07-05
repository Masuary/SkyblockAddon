package yorickbm.skyblockaddon.islands;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.util.geometry.ChunkRef;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeIslandPersistenceTest {
    @BeforeAll
    static void initializeVanillaRegistriesWithoutForgeNetwork() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        // Forge's patched Bootstrap initializes NetworkHooks, whose access transformer is not
        // applied by the plain JUnit worker. Marking bootstrap active lets vanilla registries
        // initialize without entering that unrelated network path.
        final var bootstrapField = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapField.setAccessible(true);
        bootstrapField.setBoolean(null, true);
    }

    @Test
    void snapshotIsDeepAndPreservesAllPersistedIslandState() {
        final IslandFixture fixture = createIslandFixture();

        final ForgeIsland snapshot = new ForgeIsland(fixture.island());

        assertPersistedState(snapshot, fixture);
        final IslandGroup sourceGroup = fixture.island().getGroup(fixture.customGroupId());
        final IslandGroup copiedGroup = snapshot.getGroup(fixture.customGroupId());
        assertNotSame(sourceGroup, copiedGroup);

        sourceGroup.removeMember(fixture.member());
        sourceGroup.setPermission("place_blocks", false);
        fixture.island().setChunks(List.of());

        assertTrue(copiedGroup.hasMember(fixture.member()));
        assertTrue(copiedGroup.canDo("place_blocks"));
        assertEquals(fixture.chunks(), snapshot.getLoadedChunks());
    }

    @Test
    void nbtRoundTripPreservesAllPersistedIslandStateAndMembershipInvariant() {
        final IslandFixture fixture = createIslandFixture();
        final TestForgeIsland restored = new TestForgeIsland();

        restored.deserializeNBT(fixture.island().serializeNBT());

        assertPersistedState(restored, fixture);
        assertEquals(1, restored.getGroups().stream()
                .filter(group -> group.hasMember(fixture.member()))
                .count());
    }

    @Test
    void legacyNamespacedUnknownBiomeIsNormalizedDuringLoad() {
        final CompoundTag tag = createIslandFixture().island().serializeNBT();
        tag.putString("biome", "minecraft:Unknown");
        final TestForgeIsland restored = new TestForgeIsland();

        restored.deserializeNBT(tag);

        assertEquals("Unknown", restored.getBiome());
        assertTrue(ForgeIsland.isUnconfiguredBiomeId(restored.getBiome()));
    }

    @Test
    void modifiedChunkTrackingIsUniqueRemovableAndPersisted() {
        final TestForgeIsland island = createIslandFixture().island();
        final net.minecraft.world.level.ChunkPos added =
                new net.minecraft.world.level.ChunkPos(20, -30);

        assertTrue(island.storeChunk(added));
        assertFalse(island.storeChunk(added));
        assertTrue(island.getModifiedChunks().contains(added));

        final TestForgeIsland restored = new TestForgeIsland();
        restored.deserializeNBT(island.serializeNBT());
        assertTrue(restored.getModifiedChunks().contains(added));

        assertTrue(restored.removeChunk(added));
        assertFalse(restored.removeChunk(added));
        assertFalse(restored.getModifiedChunks().contains(added));
    }

    private static IslandFixture createIslandFixture() {
        final TestForgeIsland island = new TestForgeIsland();
        final UUID islandId = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();
        final UUID member = UUID.randomUUID();
        final UUID customGroupId = UUID.randomUUID();
        final Vec3i spawn = new Vec3i(120, 81, -240);
        final Vec3i center = new Vec3i(128, 64, -256);
        final List<ChunkRef> chunks = List.of(new ChunkRef(7, -16), new ChunkRef(8, -16));

        island.setId(islandId);
        island.setOwner(owner);
        island.setSpawn(spawn);
        island.setTestCenter(center);
        island.setBiome("regions_unexplored:maple_forest");
        island.setVisibility(true);
        island.setSkullTexture("0123456789abcdef");
        island.setChunks(chunks);

        island.addGroup(group(SkyblockAddonCore.MOD_UUID, "Members", false));
        island.addGroup(group(SkyblockAddonCore.MOD_UUID2, "Visitors", false));
        final ForgeIslandGroup customGroup = group(customGroupId, "Builders", false);
        customGroup.setPermission("place_blocks", true);
        island.addGroup(customGroup);
        assertTrue(island.addMember(member, customGroupId));

        return new IslandFixture(island, islandId, owner, member, customGroupId, spawn, center, chunks);
    }

    private static ForgeIslandGroup group(final UUID id, final String name, final boolean allowAll) {
        final ItemStack icon = new ItemStack(Items.PAPER);
        icon.setHoverName(new TextComponent(name));
        return new ForgeIslandGroup(id, icon, allowAll);
    }

    private static void assertPersistedState(final ForgeIsland island, final IslandFixture expected) {
        assertEquals(expected.islandId(), island.getId());
        assertEquals(expected.owner(), island.getOwner());
        assertEquals(expected.spawn(), island.getSpawn());
        assertEquals(expected.center(), island.getCenter());
        assertEquals("regions_unexplored:maple_forest", island.getBiome());
        assertTrue(island.isVisible());
        assertEquals("0123456789abcdef", island.getSkullTexture());
        assertEquals(expected.chunks(), island.getLoadedChunks());
        assertEquals(List.of(expected.member()), island.getMembers());
        final ForgeIslandGroup customGroup = (ForgeIslandGroup) island.getGroup(expected.customGroupId());
        assertEquals("[Builders]", customGroup.getName());
        assertSame(Items.PAPER, customGroup.getItem().getItem());
        assertTrue(customGroup.hasMember(expected.member()));
        assertTrue(customGroup.canDo("place_blocks"));
        assertFalse(island.getDefaultGroup().hasMember(expected.member()));
    }

    private record IslandFixture(
            TestForgeIsland island,
            UUID islandId,
            UUID owner,
            UUID member,
            UUID customGroupId,
            Vec3i spawn,
            Vec3i center,
            List<ChunkRef> chunks
    ) {}

    private static final class TestForgeIsland extends ForgeIsland {
        private void setTestCenter(final Vec3i center) {
            setCenter(center);
        }
    }
}
