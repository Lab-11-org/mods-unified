package org.lab_11.modsunified.impl.cookingforblockheads;

public final class BridgeKeys {
    public static final String MOD_LAB11_UNIFIED = "lab_11_mods_unified";
    public static final String MOD_COOKING_FOR_BLOCKHEADS = "cookingforblockheads";
    public static final String MOD_FARMERS_DELIGHT = "farmersdelight";
    public static final String MOD_DUNGEONS_DELIGHT = "dungeonsdelight";
    public static final String MOD_MINERS_DELIGHT = "minersdelight";
    public static final String MOD_MINERS_DELIGHT_ALT = "miners_delight";
    public static final String MOD_KALEIDOSCOPE_COOKERY = "kaleidoscope_cookery";

    public static final String TARGET_FARMERS_DELIGHT_COOKING_POT = "farmersdelight_cooking_pot";
    public static final String TARGET_DUNGEONS_DELIGHT_MONSTER_POT = "dungeonsdelight_monster_pot";
    public static final String TARGET_MINERS_DELIGHT_COPPER_POT = "minersdelight_copper_pot";
    public static final String TARGET_KALEIDOSCOPE_COOKERY_POT = "kaleidoscope_cookery_pot";
    public static final String TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT = "kaleidoscope_cookery_stockpot";
    public static final String ITEM_KALEIDOSCOPE_STOCKPOT_LID = "stockpot_lid";
    public static final String BLOCK_LAVA_SINK = "lava_sink";

    public static final String MARKER_DUNGEON_OVEN = "dungeon_oven";
    public static final String MARKER_LAVA_SINK = "lava_sink";
    public static final String INDEXED_CFBH_RECIPE_PATH_PREFIX = "cfbh_indexed/";
    public static final String MIRRORED_DD_CUP_RECIPE_PATH_PREFIX = "compat/dd_monster_to_fd/";
    public static final String MIRRORED_DD_CUP_RECIPE_ID_PREFIX =
            MOD_LAB11_UNIFIED + ":" + MIRRORED_DD_CUP_RECIPE_PATH_PREFIX;

    public static final String TOOLTIP_MISSING_DUNGEON_OVEN =
            "lab_11_mods_unified.tooltip.cooking_table.missing_dungeon_oven";

    public static String minersDelightModId() {
        if (org.lab_11.modsunified.impl.platform.LoaderApiCompat.isModLoaded(MOD_MINERS_DELIGHT)) {
            return MOD_MINERS_DELIGHT;
        }
        if (org.lab_11.modsunified.impl.platform.LoaderApiCompat.isModLoaded(MOD_MINERS_DELIGHT_ALT)) {
            return MOD_MINERS_DELIGHT_ALT;
        }
        return MOD_MINERS_DELIGHT;
    }

    private BridgeKeys() {
    }
}
