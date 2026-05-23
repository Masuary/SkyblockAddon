package yorickbm.skyblockaddon.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;

public class ProgressBar {
    private final ServerBossEvent bossBar;
    private final Component baseName;

    private int totalTasks = 0;
    private int completedTasks = 0;

    public ProgressBar(Component baseName) {
        this.baseName = baseName;
        this.bossBar = new ServerBossEvent(
                baseName,
                BossBarColor.YELLOW,
                BossBarOverlay.PROGRESS
        );
        this.bossBar.setVisible(false); // Don't show until started
    }

    /**
     * Add a task to the progress bar.
     */
    public void addTask() {
        totalTasks++;
        updateProgress();
    }

    /**
     * Mark one task as completed.
     */
    public void completeTask() {
        completedTasks++;
        updateProgress();
    }

    /**
     * Update the bossbar progress and name.
     */
    private void updateProgress() {
        float percent = (totalTasks == 0) ? 0f : Math.min((float) completedTasks / totalTasks, 1f);
        bossBar.setProgress(percent);
        updateName();
    }

    /**
     * Update the name with percentage progress.
     */
    public void updateName() {
        int percent = (totalTasks == 0) ? 0 : (completedTasks * 100) / totalTasks;
        Component displayName = new TextComponent("")
                .append(baseName)
                .append(" ")
                .append(new TextComponent(percent + "%").withStyle(style -> style.withBold(true)));

        bossBar.setName(displayName);
    }

    public void sendToast(TextComponent component) {
        for(ServerPlayer player : bossBar.getPlayers()) {
            player.displayClientMessage(
                    component.withStyle(ChatFormatting.GRAY), true
            );
        }
    }

    /**
     * Show the bossbar to a specific player.
     */
    public void start(ServerPlayer player) {
        bossBar.addPlayer(player);
        bossBar.setVisible(true);
    }

    /**
     * Hide and destroy the bossbar.
     */
    public void kill() {
        bossBar.removeAllPlayers();
        bossBar.setVisible(false);
    }
}
