package yorickbm.skyblockaddon.configs;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.HashMap;

public class SkyblockAddonLanguageConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    private static final HashMap<String, ForgeConfigSpec.ConfigValue<String>> values = new HashMap<>();

    static {

        BUILDER.push("language");


        setupWarningLang();
        setupAdminLang();
        setupDefaultCommandsLang();
        setupGUI();

        register("biome.message", "Your islands biome had changed too %s.", "");

        values.put(
            "island.member.kick",
            BUILDER.comment("Message displayed for player who is kicked of their island.")
            .define("island.member.kick", "Sorry, you have been kicked of the island.")
        );
        values.put(
                "island.spawn.needed",
                BUILDER.comment("Notify player that spawn island is still required.")
                .define("island.spawn.needed", "First island that will be created will become spawn island!")
        );
        values.put(
            "toolbar.overlay.nothere",
            BUILDER.comment("Message above toolbar when action is not permitted.")
            .define("toolbar.overlay", "Sorry, you cannot do this here.")
        );
        values.put(
            "chat.hover.copy",
            BUILDER.comment("Message displayed on hover for a copyable message.")
            .define("chat.hover.copy", "Click to copy!")
        );
        values.put(
            "chat.hover.run.invite",
            BUILDER.comment("Message displayed on hover for an island invite.")
            .define("chat.hover.run.invite", "Click to accept invite (Valid for 60 minutes)!")
        );
        values.put(
            "chat.hover.run.teleport",
            BUILDER.comment("Message displayed on hover for teleport request.")
            .define("chat.hover.run.teleport", "Sorry, you cannot do this here.")
        );
        values.put(
            "chat.hover.run.rejoin",
            BUILDER.comment("Message displayed on hover for rejoining left island.")
            .define("chat.hover.run.rejoin", "Click to rejoin your island (Valid for 60 minutes)!")
        );

        BUILDER.pop();
        SPEC = BUILDER.build();

    }

    public static String getForKey(String key) {
        if(!values.containsKey(key))
            return key; //Return lookup value if not found
        return values.get(key).get(); //Return value from config
    }

    private static void register(String key, String value, String comment) {
        values.put(
                key,
                BUILDER.comment(comment)
                        .define(key, value)
        );
    }

    private static void registerGuiPermission(String identifier, String title, String desc) {
        register("guis.permissions." + identifier + ".title", title, "");
        register("guis.permissions." + identifier + ".desc", desc, "");
    }

    private static void registerGuiItem(String identifier, String title, String desc) {
        register("guis." + identifier + ".title", title, "");
        register("guis." + identifier + ".desc", desc, "");
    }

    private static void setupWarningLang() {
        register("commands.not.player", "This command may only be executed by a player.", "");
        register("commands.not.overworld", "You can only execute this command in the overworld.", "");
        register("commands.not.partofisland", "For this command to be executed, you require to be part of an island.", "");
        register("commands.not.online", "Sorry, this player is offline...", "");
        register("commands.not.has.island", "This player is not part of an island, sorry.", "");
        register("commands.not.has.one", "You do not have an island yet!", "");
        register("commands.not.found", "The island %s has not been found.", "");
        register("commands.not.part", "%s is not a member of the island %s.", "");
        register("commands.not.permitted", "You are not permitted to take this action, sorry.", "");
    }

    private static void setupAdminLang() {
        register("commands.admin.getId.success", "You can copy to clipboard the island id from %s by clicking on this message.", "");

        register("commands.admin.setId.success", "%s new island has been set to %s", "");
        register("commands.admin.setId.teleport", "We are teleporting you to your new island...", "");
        register("commands.admin.setId.ispart", "%s is already part of the island %s.", "");
        register("commands.admin.setId.override", "Beware, %s was already part of island %s. This is has been overridden!", "");

        register("commands.admin.where.none", "We could not find an id for the island you are on.", "");
        register("commands.admin.where.success", "You are currently on the island %s. Click on this message to copy.", "");

        register("commands.admin.promote.owner", "%s has been made the new owner of the island %s, %s has been demoted to admin.", "");
        register("commands.admin.promote.admin", "%s has been promoted to admin on the island %s.", "");
        register("commands.admin.promote.already.owner", "%s is already owner role of the island %s.", "");

        register("commands.admin.demote.admin", "%s has been demoted to admin on the island %s, %s is the new owner.", "");
        register("commands.admin.demote.member", "%s has been demoted to member on the island %s.", "");
        register("commands.admin.demote.already.member", "%s is already a member role of the island %s.", "");
        register("commands.admin.demote.not.possible", "%s cannot be demoted, as there is no one else on the island %s to become owner.", "");

        register("commands.admin.island.kick.success", "%s has been kicked of the island %s.", "");

        register("commands.admin.setOwner.already.owner", "%s is already owner of the island %s.", "");
        register("commands.admin.setOwner.success","%s has been made the new owner of the island %s, %s has been demoted to admin.", "");
    }

    private static void setupDefaultCommandsLang() {
        register("commands.spawn.teleport", "Teleporting you to spawn, this may take a second.", "");

        register("commands.create.success", "Created your skyblock island!", "");
        register("commands.create.has.one", "You already have an island, use /island tp to teleport to your island!", "");
        register("commands.create.fail", "We could not create your island! Try again later.", "");
        register("commands.create.delay", "Please wait %s seconds before executing this command again.", "");
        register("commands.create.generating", "We are generating your island, please be patient...", "");

        register("commands.leave.success", "You have left your island...", "");
        register("commands.leave.undo", "If this is a mistake, you may rejoin your island by clicking this message.", "");

        register("commands.invite.has.one", "The player you are trying to invite already has an island!", "");
        register("commands.invite.offline", "The player you are trying to invite went offline!", "");
        register("commands.invite.success", "You have invited %s to your island.", "");
        register("commands.invite.message", "%s has invited you to join their island. Click this message, to join their island!", "");

        register("commands.accept.has.one", "You already have an island!", "");
        register("commands.accept.invalid", "Whoops, seems like your invitation has expired!", "");
        register("commands.accept.success", "You have joined %s's island!", "");

        register("commands.teleport.success", "We have teleported you to your island.", "");
        register("commands.teleport.user.not.found", "We could not find the players island you are trying to teleport to.", ""); //Not used?
        register("commands.teleport.user.has.none", "%s does not have an island to teleport too!", "");
        register("commands.teleport.user.success", "You have teleported to %s's island.", "");
        register("commands.teleport.request.message", "%s wants to teleport to your island. Click this message, to accept teleport request.", "");
        register("commands.teleport.request.send", "You have requested to teleport to %s's island!", "");
        register("commands.teleport.request.success", "We have teleported %s to your island.", "");
        register("commands.teleport.request.not.overworld", "%s is not in the overworld, and cannot be teleported to you!", "");
        register("commands.teleport.request.offline", "The player who's teleport request you accepted is not online.", "");
        register("commands.teleport.request.expired", "The time to accept this teleportation request has expired.", "");

        register("commands.kick.not.allowed", "You are not allowed to kick this member!", "");
        register("commands.kick.not.part", "The player you are trying to kick is not part of your island.", "");
        register("commands.kick.message", "%s has kicked you of their island!", "");
        register("commands.kick.success", "You have kicked %s from your island!", "");

        register("commands.undo.fail", "You are required to leave an island before we can undo it.", "");
        register("commands.undo.expired", "Your time to rejoin the island you left expired.", "");
        register("commands.undo.success.has", "We have left your current island for you, to rejoin your previous one!", "");
        register("commands.undo.success", "You have rejoined your previous island!", "");

        register("commands.admin.toggle.success", "Updated the travel ability of island %s to %s.", "");

        register("commands.set.center.fail", "Cannot modify spawn center location when islands have already been generated.", "");
        register("commands.set.center.success", "Updated island center spawn location. (%s;%s;%s)", "");
    }

    private static void setupGUI() {
        register("guis.island.join", "You have joined %s's island!", "");
        register("guis.island.setspawn.notallowed", "Sorry you are not allowed to set your spawn outside of your own island", "");

        register("guis.default.back", "Back", "");
        register("guis.default.previous", "Previous", "");
        register("guis.default.next", "Next", "");
        register("guis.default.page", "Page", "");
        register("guis.default.unknown", "Unknown", "");
        register("guis.default.addplayer", "Add player", "");
        register("guis.default.true", "TRUE", "");
        register("guis.default.false", "FALSE", "");
        register("guis.default.allowed", "\u2666 Allowed:", "");

        registerGuiItem("teleport", "Teleport", "\u00bb Teleport to your islands spawn location.");
        registerGuiItem("members", "Members", "\u00bb Overview all islands members and invite others.");
        registerGuiItem("settings", "Settings", "\u00bb Change the settings of your island.");
        registerGuiItem("inviteplayer", "%s", "\u00bb Click to invite player to your island");
        registerGuiItem("invite", "Invite", "\u00bb Invite online player to join this island.");
        registerGuiItem("currentowner", "%s", "\u00bb Islands current owner.");
        registerGuiItem("permissions", "Permissions", "\u00bb Click to view all permission groups and modify.");
        registerGuiItem("memberinvite", "%s", "\u00bb Click to add player to this group");
        registerGuiItem("membergroup", "%s", "\u00bb Click to remove this user from the group.");

        register("guis.admin.desc", "\u00bb Island admin.", "");
        register("guis.admin.rightclick.desc", "\u2666 Right-click to demote to member", "");

        register("guis.member.desc", "\u00bb Island member.", "");
        register("guis.member.rightclick.desc", "\u2666 Right-click to kick player from island", "");
        register("guis.member.leftclick.desc", "\u2666 Left-click to promote to admin", "");

        registerGuiItem("biome", "Change Biome", "\u00bb Change Biome of your island.");
        register("guis.biome.current", "\u2666 Current:", "");

        registerGuiItem("spawn", "Change Spawn", "\u00bb Set spawn of your island.");
        register("guis.spawn.current", "\u2666 Current:", "");
        register("guis.spawn.new", "\u2666 New:", "");

        registerGuiItem("travels", "Island Travels", "\u00bb Change visibility of your island.");
        register("guis.travels.current", "\u2666 Current:", "");
        register("guis.travels.onclick", "\u2666 Click to change!", "");
        register("guis.travels.public", "Public", "");
        register("guis.travels.private", "Private", "");

        register("guis.group.other", "\u00bb Click to alter permissions for group, or add players.", "");
        register("guis.group.default", "\u00bb Click to alter permissions that are used by default for players!\n\n\u2666 These permissions are for anyone NOT put within a different group.", "");

        registerGuiPermission("Teleport", "Teleport Requests", "\u2666 Whom may accept teleport requests to your island\n\u2666 Setting this to everyone, will allow teleporting without request.");
        registerGuiPermission("Invite", "Invite New Members", "\u2666 Allow to invite new members to your island\n\u2666 Setting this to everyone, will allow joining without invite.");
        registerGuiPermission("PlaceBlocks", "Place Blocks", "\u2666 Place any form of block on your island");
        registerGuiPermission("BreakBlocks", "Break Blocks", "\u2666 Break any form of block on your island");
        registerGuiPermission("TrampleFarmland", "Trample Farmland", "\u2666 Trample farmland by jumping on top of it");
        registerGuiPermission("OpenBlocks", "Interact With Blocks", "\u2666 Interact with blocks on your island\n\u2666 I.E Chests, Storage Interfaces");
        registerGuiPermission("UseRedstone", "Interact With Redstone", "\u2666 Interact with redstone on your island\n\u2666 I.E Buttons, Levers, Repeaters");
        registerGuiPermission("EnderPearl", "Use EnderPearl", "\u2666 Use an enderpearl on your island to teleport");
        registerGuiPermission("ChorusFruit", "Use ChorusFruit", "\u2666 Use chorusfruit on your island to teleport");
        registerGuiPermission("InteractWithXP", "Collect XP Orbs", "\u2666 Gain XP levels from XP orbs on your island");
        registerGuiPermission("InteractWithGroundItems", "Interact With Ground Items", "\u2666 Pickup and drop items on your island");
        registerGuiPermission("UseBucket", "Use Bucket", "\u2666 Empty and or fill a bucket with fluids on your island\n\u2666 I.E. Water, Lava");
        registerGuiPermission("UseBed", "Sleep in Bed", "\u2666 Sleep within a bed on your island\n\u2666 Will also set their respawn point");
        registerGuiPermission("UseBonemeal", "Use Bonemeal", "\u2666 Use bonemeal, this includes all possible usages of bonemeal\n\u2666 I.E. Crops, Moss blocks");
    }

}
