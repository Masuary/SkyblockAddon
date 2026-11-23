package yorickbm.skyblockaddon.util;

/**
 * The Create mixins, absent on a pack without Create.
 */
public class CreateMixinPlugin extends InstalledModMixinPlugin {
    public CreateMixinPlugin() {
        super("create");
    }
}
