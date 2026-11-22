package yorickbm.skyblockaddon.islands;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.mutable.MutableInt;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.util.BiomeUtil;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.NBTSerializable;
import yorickbm.skyblockaddon.util.NBTUtil;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ForgeIsland extends Island implements NBTSerializable {
    private static final String BANNED_PLAYERS_TAG = "bannedPlayers";
    private static final String LAST_ACTIVE_TAG = "lastActive";
    private final List<ChunkPos> playerLoadedChunks = new ArrayList<>();

    public ForgeIsland() {
        super();
    }
    public ForgeIsland(UUID uuid, Vec3i vec) {
        super.setId(UUID.randomUUID());
        super.setOwner(uuid);
        super.setSpawn(ForgeConverter.forgeToInternalVec3i(vec));
        super.setCenter(ForgeConverter.forgeToInternalVec3i(vec));
        super.setVisibility(false);

        this.genBasicGroups();
    }

    /**
     * Generate default groups
     */
    public void genBasicGroups() {
        //Add default members group
        final ItemStack item = new ItemStack(Items.RED_MUSHROOM);
        item.setHoverName(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("gui.group.default.name")).withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.BLUE));
        final ItemStack item2 = new ItemStack(Items.BROWN_MUSHROOM);
        item2.setHoverName(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("gui.group.nonmember.name")).withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.BLUE));


        super.addGroup(new ForgeIslandGroup(SkyblockAddonCore.MEMBERS_GROUP_ID, item, true));
        super.addGroup(new ForgeIslandGroup(SkyblockAddonCore.VISITORS_GROUP_ID, item2, false));
    }

    /**
     * Teleport entity to spawn location of island.
     *
     * @param entity - Entity to teleport
     */
    public void teleportTo(final Entity entity) {
        entity.teleportTo(getSpawn().getX(), getSpawn().getY() + 0.5, getSpawn().getZ());

        if (entity instanceof final ServerPlayer player) {
            ServerHelper.playSongToPlayer(player, SoundEvents.ENDERMAN_TELEPORT, SkyblockAddonCore.EFFECT_SOUND_VOL, 1f);
        }
    }

    /**
     * Kick entity from island
     *
     * @param source - Who is kicking the entity
     * @param entity - UUID of entity to remove
     */
    public void kickMember(final Entity source, final UUID entity) {
        if(isOwner(entity)) {
            handOverOwnershipToNextMember();
        } else {
            removeFromIsland(entity);
        }

        //CLear player from cache
        IslandManager.getInstance().clearCacheForPlayer(entity);

        sendToWorldSpawnIfStandingOn(Objects.requireNonNull(source.getServer()), entity);
    }

    /**
     * Teleport an online player standing on this island to the overworld spawn.
     * @return - If the player was on the island and has been moved
     */
    public boolean sendToWorldSpawnIfStandingOn(final MinecraftServer server, final UUID entity) {
        final ServerPlayer player = server.getPlayerList().getPlayer(entity);
        if (player == null || !isStandingOn(player)) return false;
        sendToWorldSpawn(player);
        return true;
    }

    /**
     * Online players standing on the island who are neither its owner nor a member.
     */
    public List<ServerPlayer> visitorsStandingOn(final MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> !isPartOf(player.getUUID()))
                .filter(this::isStandingOn)
                .toList();
    }

    public boolean isStandingOn(final ServerPlayer player) {
        return player.getLevel().dimension() == Level.OVERWORLD
                && getIslandBoundingBox().isInside(ForgeConverter.forgeToInternalVec3i(player.getOnPos()));
    }

    /**
     * Teleport a player to the overworld spawn. The level-aware teleport dismounts the player first (a mounted player
     * would otherwise snap back to the vehicle) and handles both a same-level and a cross-level move.
     */
    public static void sendToWorldSpawn(final ServerPlayer player) {
        final ServerLevel overworld = Objects.requireNonNull(player.getServer()).overworld();
        final BlockPos worldSpawn = overworld.getSharedSpawnPos();
        player.stopRiding();
        player.teleportTo(overworld, worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    /**
     * Add entity as member to island
     *
     * @param source - Who is adding the entity
     * @param entity - UUID of entity to add
     */
    public boolean addMember(final Entity source, final UUID entity) {
        if(!super.addMember(entity, SkyblockAddonCore.MEMBERS_GROUP_ID)) return false;

        //Teleport entity to island
        final ServerPlayer player = Objects.requireNonNull(source.getServer()).getPlayerList().getPlayer(entity); //Get entity from online player list
        if(player != null) this.teleportTo(player);

        return true;
    }

    /**
     * Set the biome for all loaded overworld chunks of island
     * @param biome - Biome resource location
     * @param server - Server whose overworld holds the island
     */
    public void updateBiome(final String biome, final MinecraftServer server) {
        if (biome == null || biome.isEmpty() || biome.equals("Unknown") || !biome.contains(":")) return;

        setBiome(biome); //Store full namespaced id so modded biomes persist correctly

        final ServerLevel serverlevel = server.overworld();
        final BoundingBox boundingbox = ForgeConverter.internalToForgeBoundingBox(getIslandBoundingBox());
        final Holder<Biome> holder = serverlevel.registryAccess()
                .registryOrThrow(Registry.BIOME_REGISTRY)
                .getOrCreateHolder(ResourceKey.create(ForgeRegistries.BIOMES.getRegistryKey(), ResourceLocation.parse(biome)));

        for(int j = SectionPos.blockToSectionCoord(boundingbox.minZ()); j <= SectionPos.blockToSectionCoord(boundingbox.maxZ()); ++j) {
            for(int k = SectionPos.blockToSectionCoord(boundingbox.minX()); k <= SectionPos.blockToSectionCoord(boundingbox.maxX()); ++k) {
                final ChunkAccess chunkaccess = serverlevel.getChunk(k, j, ChunkStatus.FULL, false);
                if (chunkaccess == null) {
                    continue; //Skip unloaded chunks
                }
                applyConfiguredBiomeToChunk(chunkaccess, serverlevel, holder, boundingbox);
                BiomeUtil.updateChunk(serverlevel.getChunk(k, j), serverlevel);
            }
        }
    }

    /**
     * Reapply this island's configured biome to a freshly loaded chunk if it doesn't already match.
     * Called from ChunkEvent.Load so that chunks unloaded at the time of biome configuration still
     * pick up the correct biome once they come back. Deliberately does NOT send a client packet -
     * the chunk-load path relies on vanilla's natural chunk send to carry the modified biome arrays.
     *
     * @return true if the biome was rewritten, false if skipped.
     */
    public boolean reapplyBiomeIfNeeded(final ChunkAccess chunk, final ServerLevel serverlevel) {
        final String biomeId = getBiome();
        if (biomeId == null || biomeId.isEmpty() || biomeId.equals("Unknown")) return false;

        final Holder<Biome> targetHolder;
        try {
            targetHolder = serverlevel.registryAccess()
                    .registryOrThrow(Registry.BIOME_REGISTRY)
                    .getOrCreateHolder(ResourceKey.create(ForgeRegistries.BIOMES.getRegistryKey(), ResourceLocation.parse(biomeId)));
        } catch (Exception ex) {
            return false;
        }

        final Optional<ResourceKey<Biome>> targetKey = targetHolder.unwrapKey();
        if (targetKey.isEmpty()) return false;

        final ChunkPos pos = chunk.getPos();
        final Holder<Biome> existing = chunk.getNoiseBiome(
                QuartPos.fromBlock(pos.getMiddleBlockX()),
                QuartPos.fromBlock(155),
                QuartPos.fromBlock(pos.getMiddleBlockZ()));
        if (existing.is(targetKey.get())) return false;

        final BoundingBox islandBbox = ForgeConverter.internalToForgeBoundingBox(getIslandBoundingBox());
        applyConfiguredBiomeToChunk(chunk, serverlevel, targetHolder, islandBbox);
        return true;
    }

    private static void applyConfiguredBiomeToChunk(final ChunkAccess chunk, final ServerLevel serverlevel, final Holder<Biome> targetBiomeHolder, final BoundingBox islandBbox) {
        final MutableInt counter = new MutableInt(0);
        chunk.fillBiomesFromNoise(
                BiomeUtil.makeResolver(counter, chunk, islandBbox, targetBiomeHolder, (p) -> true),
                serverlevel.getChunkSource().getGenerator().climateSampler());
        chunk.setUnsaved(true);
    }

    public List<ChunkPos> getModifiedChunks() {
        return Collections.unmodifiableList(playerLoadedChunks);
    }
    public boolean storeChunk(final ChunkAccess chunk) {
        return storeChunk(chunk.getPos());
    }
    public boolean storeChunk(final ChunkPos pos) {
        if(playerLoadedChunks.contains(pos)) return false;
        return playerLoadedChunks.add(pos);
    }
    public boolean removeChunk(final ChunkPos pos) {
        return playerLoadedChunks.remove(pos);
    }

    @Override
    public CompoundTag serializeNBT() {
        final CompoundTag tag = new CompoundTag();

        tag.putUUID("Id", getId());
        tag.putString("owner", getOwner().toString());
        tag.putString("biome", getBiome());
        tag.putString("skullTexture", getSkullTexture());
        tag.putBoolean("travelability", isVisible());
        tag.putLong(LAST_ACTIVE_TAG, getLastActiveEpochMillis());
        tag.put("spawn", NBTUtil.vec3iToNBT(getSpawn()));
        tag.put("center", NBTUtil.vec3iToNBT(getCenter()));

        final CompoundTag groups = new CompoundTag();
        for(final IslandGroup group : getGroups()) {
            groups.put(group.getId().toString(), ((ForgeIslandGroup)group).serializeNBT());
        }
        tag.put("groups", groups);

        final CompoundTag members = new CompoundTag();
        for(int i = 0; i < super.members.size(); i++) {
            members.putUUID(i+"", super.members.get(i));
        }
        tag.put("members", members);

        final CompoundTag bannedPlayers = new CompoundTag();
        final List<UUID> bannedPlayerIds = getBannedPlayers();
        for(int i = 0; i < bannedPlayerIds.size(); i++) {
            bannedPlayers.putUUID(i+"", bannedPlayerIds.get(i));
        }
        tag.put(BANNED_PLAYERS_TAG, bannedPlayers);

        final CompoundTag chunks = new CompoundTag();
        List<ChunkPos> chunksData = this.getModifiedChunks();
        for(int i=0; i < chunksData.size(); i++) {
            chunks.putString(i+"", chunksData.get(i).toString());
        }
        tag.put("chunks", chunks);

        return tag;
    }

    @Override
    public void deserializeNBT(final CompoundTag tag) {
        setId(tag.getUUID("Id"));
        if (tag.getString("owner").length() > 3) setOwner(UUID.fromString(tag.getString("owner")));

        final String storedBiome = tag.getString("biome");
        setBiome(storedBiome.isEmpty() || storedBiome.contains(":") ? storedBiome : "minecraft:" + storedBiome);
        setVisibility(tag.getBoolean("travelability"));
        setLastActiveEpochMillis(tag.getLong(LAST_ACTIVE_TAG));
        setSkullTexture(tag.getString("skullTexture"));
        setSpawn(NBTUtil.nbtToVec3i(tag.getCompound("spawn")));
        setCenter(NBTUtil.nbtToVec3i(tag.getCompound("center")));

        final CompoundTag groups = tag.getCompound("groups");
        for(final String groupUuid : groups.getAllKeys()) {
            final ForgeIslandGroup group = new ForgeIslandGroup();
            group.deserializeNBT(groups.getCompound(groupUuid));
            super.islandGroups.put(group.getId(), group);
        }

        if(super.islandGroups.isEmpty() || super.islandGroups.size() < 2) {
            final ItemStack item = new ItemStack(Items.RED_MUSHROOM);
            item.setHoverName(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("gui.group.default.name")).withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.BLUE));

            final IslandGroup defaultG = new ForgeIslandGroup(SkyblockAddonCore.MEMBERS_GROUP_ID, item, true);
            super.islandGroups.put(defaultG.getId(), defaultG);

            final ItemStack item2 = new ItemStack(Items.BROWN_MUSHROOM);
            item2.setHoverName(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("gui.group.nonmember.name")).withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.BLUE));

            final IslandGroup defaultG2 = new ForgeIslandGroup(SkyblockAddonCore.VISITORS_GROUP_ID, item2, false);
            super.islandGroups.put(defaultG2.getId(), defaultG2);
        }

        final CompoundTag members = tag.getCompound("members");
        for(final String key : members.getAllKeys()) {
            super.members.add(members.getUUID(key));
        }

        final CompoundTag bannedPlayers = tag.getCompound(BANNED_PLAYERS_TAG);
        for(final String key : bannedPlayers.getAllKeys()) {
            super.bannedPlayers.add(bannedPlayers.getUUID(key));
        }

        final CompoundTag chunks = tag.getCompound("chunks");
        for(final String key : chunks.getAllKeys()) {
            this.playerLoadedChunks.add(new ChunkPos(
                    Integer.parseInt(chunks.getString(key).replaceAll("[\\[\\]\\s]", "").split(",")[0]),
                    Integer.parseInt(chunks.getString(key).replaceAll("[\\[\\]\\s]", "").split(",")[1])
            ));
        }

    }
}
