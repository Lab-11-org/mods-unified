# Mods Unified By LAB-11

NeoForge compat bridge for `cookingforblockheads:cooking_table`.

Current targets:
- FarmersDelight `farmersdelight:cooking_pot`
- DungeonsDelight `dungeonsdelight:monster_pot`
- MinersDelight `minersdelight:copper_pot`

## What This Mod Does
- Registers pot recipes into Cooking for Blockheads at runtime.
- Keeps all integrations optional and dynamic (no hard runtime dependency lock).
- Requires the matching pot above the cooking table before that pot's recipes become craftable.
- Supports extra requirements (example: dungeon oven) as marker-based constraints.
- Handles container consumption and tooltip warnings in the cooking table flow.

## Runtime Notes
- Singleplayer and dedicated server both use the same indexed recipe path.
- Bridge recipe wrappers are injected into RecipeManager by id for CFBH compatibility.
- If a target mod is missing, the bridge for that target is skipped cleanly.

## Build
```bash
./gradlew build -x test
```

## Extend A New Pot Bridge
1. Add ids/keys in `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/BridgeKeys.java`.
2. Add target definition in `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/CookingPotBridgeCatalog.java`.
3. If new markers/requirements are needed:
   - marker item + missing-tooltip mapping in `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/BridgeMarkerRegistry.java`
   - requirement predicate in `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/CookingPotProcessorCapability.java`
   - lang keys in `src/main/resources/assets/lab_11_mods_unified/lang/`
4. Build and validate both integrated server and dedicated server.

## Version Baseline
- Minecraft `1.21.1`
- NeoForge `21.1.219`
- Cooking for Blockheads `21.1.17`
- FarmersDelight `1.2.9`
