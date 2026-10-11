# LAB-11 Mods Unified

A runtime compatibility bridge between `cookingforblockheads:cooking_table`
and pot-based cooking mods.

## Support Matrix

| Minecraft | Loader | Java | Gradle profile |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge | 21 | default |
| 1.20.1 | Forge | 17 | `-Ptarget_id=forge-1.20.1` |

## Integrations

- Farmer's Delight `farmersdelight:cooking_pot`
- Dungeon's Delight `dungeonsdelight:monster_pot`
- Miner's Delight `minersdelight:copper_pot`
- Kaleidoscope Cookery `kaleidoscope_cookery:pot`
- Kaleidoscope Cookery `kaleidoscope_cookery:stockpot`

## What This Mod Does
- Registers pot recipes into Cooking for Blockheads at runtime.
- Keeps all integrations optional and dynamic (no hard runtime dependency lock).
- Requires the matching pot above the cooking table before that pot's recipes become craftable.
- Supports extra requirements (example: dungeon oven) as marker-based constraints.
- Handles container consumption and tooltip warnings in the cooking table flow.

## Architecture
- Singleplayer and dedicated server both use the same indexed recipe path.
- Bridge recipe wrappers stay in Cooking for Blockheads' runtime index so recipe sync remains serializable.
- If a target mod is missing, the bridge for that target is skipped cleanly.

`RecipeManager` → target catalog → CFBH index → cooking table → connected cookware

## Build
```bash
./gradlew clean build
```

Other targets use the same task with the target properties from
`gradle.properties`, for example:

```bash
./gradlew clean build -Ptarget_id=forge-1.20.1
```

Validate against the full published runtime set for each target with:

```bash
./gradlew clean build -Pfull_runtime=true
./gradlew clean build -Ptarget_id=forge-1.20.1 -Pfull_runtime=true
```

These two full-runtime profiles also run as live loader GameTests in CI.

The NeoForge full-runtime profile excludes Miner's Delight and its Lodestone
dependency because their published 1.21.1 jars declare the incompatible
Minecraft range `[1.21,1.21.1)`. The Forge profile includes Miner's Delight.

Dependency versions live in `gradle.properties`; Gradle downloads them from
Modrinth. Override them without editing files when checking another supported
combination:

```bash
./gradlew clean build \
  -Pcfbh_compile_version=21.1.24 \
  -Pbalm_compile_version=21.0.64 \
  -Pfarmers_delight_compile_version=1.3.2
```

The build workflow checks both the versions in `gradle.properties` and the newer
21.1.24 / 21.0.64 / 1.3.2 combination. Add a matrix row when supporting a new
breaking combination; add compatibility code only when that row fails.

## Extend A New Pot Bridge
1. Add ids/keys in `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/BridgeKeys.java`.
2. Add target definition in `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/CookingPotBridgeCatalog.java`.
3. If new markers/requirements are needed:
   - marker item + missing-tooltip mapping in `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/BridgeMarkerRegistry.java`
   - requirement predicate in `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/CookingPotProcessorCapability.java`
   - lang keys in `src/main/resources/assets/lab_11_mods_unified/lang/`
4. Build and validate both integrated server and dedicated server.
