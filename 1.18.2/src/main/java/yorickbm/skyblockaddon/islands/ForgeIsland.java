package yorickbm.skyblockaddon.islands;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.geometry.Square;
import yorickbm.skyblockaddon.util.*;

import java.util.*;

public class ForgeIsland extends Island implements NBTSerializable {
    private final List<ChunkPos> playerLoadedChunks = new ArrayList<>();

    public ForgeIsland() {
        super();
    }
    public ForgeIsland(Island island) {
        super.setId(island.getId());
        super.setOwner(island.getOwner());
        super.setSpawn(island.getSpawn());
        super.setCenter(island.getCenter());
        super.setVisibility(island.isVisible());
        super.setSkullTexture(island.getSkullTexture());
        setChunks(((ForgeIsland)island).getModifiedChunks());

        island.getGroups().forEach(super::addGroup);
        island.getMembers().stream()
                .filter(m -> island.getGroupForEntityUUID(m).isPresent())
                .forEach(m -> super.addMember(m, island.getGroupForEntityUUID(m).get().getId()));
    }
    public ForgeIsland(UUID uuid, Vec3i vec) {
        super.setId(UUID.randomUUID());
        super.setOwner(uuid);
        super.setSpawn(ForgeConverter.ForgeToInternalVec3i(vec));
        super.setCenter(ForgeConverter.ForgeToInternalVec3i(vec));
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


        super.addGroup(new ForgeIslandGroup(SkyblockAddonCore.MOD_UUID, item, true));
        super.addGroup(new ForgeIslandGroup(SkyblockAddonCore.MOD_UUID2, item2, false));
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
            if(!getMembers().isEmpty()) super.setOwner(getMembers().get(0));
            else {
                super.setOwner(SkyblockAddonCore.MOD_UUID);
                super.setVisibility(false); //Close island if we are the last one
            }
        } else {
            final Optional<IslandGroup> group = getGroupForEntityUUID(entity);
            group.ifPresentOrElse(
                    g -> super.removeMember(entity, g.getId()),
                    () -> super.removeMember(entity, SkyblockAddonCore.MOD_UUID)
            );
        }

        //CLear player from cache
        IslandManager.getInstance().clearCacheForPlayer(entity);

        //Teleport entity to world spawn
        final BlockPos worldSpawn = Objects.requireNonNull(Objects.requireNonNull(source.getServer()).getLevel(Level.OVERWORLD)).getSharedSpawnPos();
        final ServerPlayer player = source.getServer().getPlayerList().getPlayer(entity); //Get entity from online player list
        if(player != null //Check if we found entity as an online player
                && player.getLevel().dimension() == Level.OVERWORLD //Check player is found
                && getIslandBoundingBox().isInside(ForgeConverter.ForgeToInternalVec3i(player.getOnPos())) //Determine if player is on the island
        ) {
            player.teleportTo(worldSpawn.getX(), worldSpawn.getY(), worldSpawn.getZ()); //Teleport player
        }
    }

    /**
     * Add entity as member to island
     *
     * @param source - Who is adding the entity
     * @param entity - UUID of entity to add
     */
    public boolean addMember(final Entity source, final UUID entity) {
        if(!super.addMember(entity, SkyblockAddonCore.MOD_UUID)) return false;

        //Teleport entity to island
        final ServerPlayer player = Objects.requireNonNull(source.getServer()).getPlayerList().getPlayer(entity); //Get entity from online player list
        if(player != null) this.teleportTo(player);

        return true;
    }

    /**
     * Set the biome for all loaded chunks of island
     * @param biome - Biome resource location
     * @param serverlevel - Over-world of server
     */
    public void updateBiome(final String biome, final ServerLevel serverlevel) {
        setBiome(biome.split(":")[1]); //Set biome for configuration to part without mod id

        final BoundingBox boundingbox = ForgeConverter.InternalToForgeBoundingBox(getIslandBoundingBox());
        final MutableInt mutableint = new MutableInt(0);
        final Holder<Biome> holder = serverlevel.registryAccess()
                .registryOrThrow(Registry.BIOME_REGISTRY)
                .getOrCreateHolder(ResourceKey.create(ForgeRegistries.BIOMES.getRegistryKey(), new ResourceLocation(biome)));

        for(int j = SectionPos.blockToSectionCoord(boundingbox.minZ()); j <= SectionPos.blockToSectionCoord(boundingbox.maxZ()); ++j) {
            for(int k = SectionPos.blockToSectionCoord(boundingbox.minX()); k <= SectionPos.blockToSectionCoord(boundingbox.maxX()); ++k) {
                final ChunkAccess chunkaccess = serverlevel.getChunk(k, j, ChunkStatus.FULL, false);
                if (chunkaccess == null) {
                    continue; //Skip unloaded chunks
                }
                chunkaccess.fillBiomesFromNoise(
                        BiomeUtil.makeResolver(mutableint, chunkaccess, boundingbox, holder, (p_262543_) -> true),
                        serverlevel.getChunkSource().getGenerator().climateSampler());
                BiomeUtil.updateChunk(serverlevel.getChunk(k, j), serverlevel);
            }
        }
    }

    public List<ChunkPos> getModifiedChunks() {
        return Collections.unmodifiableList(playerLoadedChunks);
    }
    public boolean storeChunk(final ChunkAccess chunk) {
        if(playerLoadedChunks.contains(chunk.getPos())) return false;
        return playerLoadedChunks.add(chunk.getPos());
    }
    public void setChunks(Collection<ChunkPos> data) {
        this.playerLoadedChunks.addAll(data);
    }

    @Override
    public Square getIslandBoundingBoxAsSquare() {
        final yorickbm.skyblockaddon.core.util.geometry.BoundingBox box = getIslandBoundingBox();
        return new yorickbm.skyblockaddon.util.geometry.Square(new Vec3i(box.minX, 0, box.minZ), new Vec3i(box.maxX, 0, box.maxZ));
    }

    @Override
    public Optional<UUID> getGroupByName(String groupName) {
        return getGroups().stream().filter(g -> g.getName().equalsIgnoreCase(groupName)).map(IslandGroup::getId).findFirst();
    }

    @Override
    public CompoundTag serializeNBT() {
        final CompoundTag tag = new CompoundTag();

        tag.putUUID("Id", getId());
        tag.putString("owner", getOwner().toString());
        tag.putString("biome", getBiome());
        tag.putString("skullTexture", getSkullTexture());
        tag.putBoolean("travelability", isVisible());
        tag.put("spawn", NBTUtil.Vec3iToNBT(getSpawn()));
        tag.put("center", NBTUtil.Vec3iToNBT(getCenter()));

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

        setBiome(tag.getString("biome"));
        setVisibility(tag.getBoolean("travelability"));
        setSkullTexture(tag.getString("skullTexture"));
        setSpawn(NBTUtil.NBTToVec3i(tag.getCompound("spawn")));
        setCenter(NBTUtil.NBTToVec3i(tag.getCompound("center")));

        final CompoundTag groups = tag.getCompound("groups");
        for(final String groupUuid : groups.getAllKeys()) {
            final ForgeIslandGroup group = new ForgeIslandGroup();
            group.deserializeNBT(groups.getCompound(groupUuid));
            super.islandGroups.put(group.getId(), group);
        }

        if(super.islandGroups.isEmpty() || super.islandGroups.size() < 2) {
            final ItemStack item = new ItemStack(Items.RED_MUSHROOM);
            item.setHoverName(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("gui.group.default.name")).withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.BLUE));

            final IslandGroup defaultG = new ForgeIslandGroup(SkyblockAddonCore.MOD_UUID, item, true);
            super.islandGroups.put(defaultG.getId(), defaultG);

            final ItemStack item2 = new ItemStack(Items.BROWN_MUSHROOM);
            item2.setHoverName(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("gui.group.nonmember.name")).withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.BLUE));

            final IslandGroup defaultG2 = new ForgeIslandGroup(SkyblockAddonCore.MOD_UUID2, item2, true);
            super.islandGroups.put(defaultG2.getId(), defaultG2);
        }

        final CompoundTag members = tag.getCompound("members");
        for(final String key : members.getAllKeys()) {
            super.members.add(members.getUUID(key));
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
