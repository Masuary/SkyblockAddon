package yorickbm.skyblockaddon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class BiomeUtil {
    public static int quantize(final int p_261998_) {
        return QuartPos.toBlock(QuartPos.fromBlock(p_261998_));
    }

    public static BlockPos quantize(final BlockPos p_262148_) {
        return new BlockPos(quantize(p_262148_.getX()), quantize(p_262148_.getY()), quantize(p_262148_.getZ()));
    }

    public static BiomeResolver makeResolver(final MutableInt p_262615_, final ChunkAccess p_262698_, final BoundingBox p_262622_, final Holder<Biome> p_262705_, final Predicate<Holder<Biome>> p_262695_) {
        return (p_262550_, p_262551_, p_262552_, p_262553_) -> {
            final int i = QuartPos.toBlock(p_262550_);
            final int j = QuartPos.toBlock(p_262551_);
            final int k = QuartPos.toBlock(p_262552_);
            final Holder<Biome> holder = p_262698_.getNoiseBiome(p_262550_, p_262551_, p_262552_);
            if (p_262622_.isInside(new Vec3i(i, j, k)) && p_262695_.test(holder)) {
                p_262615_.increment();
                return p_262705_;
            } else {
                return holder;
            }
        };
    }

    /**
     * Send chunk update to player client
     * @param levelChunk Chunk to update
     * @param serverlevel Server level chunk is part off
     */
    public static void updateChunk(@NotNull final LevelChunk levelChunk, final ServerLevel serverlevel) {
        final ChunkPos chunkPos = levelChunk.getPos();
        final MutableObject<ClientboundLevelChunkWithLightPacket> mutableObject = new MutableObject<>();

        for(final ServerPlayer serverPlayer : serverlevel.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            if(mutableObject.getValue() == null) {
                mutableObject.setValue(new ClientboundLevelChunkWithLightPacket(levelChunk, serverlevel.getLightEngine(), null, null, true));
            }
            //serverPlayer.untrackChunk(chunkPos);
            serverPlayer.trackChunk(chunkPos, mutableObject.getValue());
        }
    }
}
