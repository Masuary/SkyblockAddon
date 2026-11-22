package yorickbm.skyblockaddon.core.util;

import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.util.function.Predicate;

/**
 * The modpack whose bundled config defaults are copied into a server's config folder for files it does not have yet.
 * Each pack has its own permission registry; any other bundled file is shared unless the pack bundles its own copy
 * under {@code packs/<id>/}.
 */
public enum BundledDefaultsPack {
    WOLDS_VAULTS("woldsvaults"),
    VAULT_HUNTERS("vaulthunters"),
    DEFAULT("default");

    private final String id;

    BundledDefaultsPack(final String id) {
        this.id = id;
    }

    /**
     * Wold's Vaults (mod {@code woldsvaults}) first, since it also loads Vault Hunters ({@code the_vault}).
     */
    public static BundledDefaultsPack select(final Predicate<String> isModLoaded) {
        if (isModLoaded.test("woldsvaults")) return WOLDS_VAULTS;
        if (isModLoaded.test("the_vault")) return VAULT_HUNTERS;
        return DEFAULT;
    }

    public String id() {
        return id;
    }

    public String permissionRegistryAsset() {
        return "registries/permissions/" + id + ".json";
    }

    /**
     * Item ids (one per line) of the pack's modded items that put an entity into the world: the default of the config
     * key {@code permissions.entity_placer_items}. Packs without the file default to no extra items.
     */
    public String entityPlacerItemsAsset() {
        return "packs/" + id + "/entity_placer_items.txt";
    }

    /**
     * The bundled asset to copy for a shared asset path: the pack's own copy when it bundles one, else the shared one.
     */
    public String assetFor(final String sharedAsset) {
        final String packAsset = "packs/" + id + "/" + sharedAsset;
        return SkyblockAddonCore.class.getResource("/assets/" + SkyblockAddonCore.MOD_ID + "/" + packAsset) != null
                ? packAsset
                : sharedAsset;
    }
}
