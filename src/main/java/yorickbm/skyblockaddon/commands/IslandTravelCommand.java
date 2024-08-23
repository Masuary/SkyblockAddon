package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.gui.GUIManager;
import yorickbm.skyblockaddon.gui.interfaces.GuiContext;

import java.util.UUID;

public class IslandTravelCommand extends OverWorldCommandStack {
    public IslandTravelCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("island")
                .then(Commands.literal("travel")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(context -> execute(context.getSource(), (ServerPlayer) context.getSource().getEntity()))
                )
        );
    }

    @Override
    public int execute(CommandSourceStack command, ServerPlayer executor) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        GUIManager.getInstance().openMenu("travel", executor, new GuiContext() {
            @Override
            public void teleportTo(Entity entity) {

            }

            @Override
            public boolean kickMember(Entity source, UUID entity) {
                return false;
            }

            @Override
            public void setSpawnPoint(Vec3i point) {

            }

            @Override
            public void toggleVisibility() {

            }

            @Override
            public void updateBiome(String biome, ServerLevel serverlevel) {

            }

            @Override
            public @NotNull Component parseTextComponent(@NotNull Component original) {
                return original;
            }
        });
        return Command.SINGLE_SUCCESS;
    }
}
