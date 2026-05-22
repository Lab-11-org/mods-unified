# AGENT.md

This file is for future coding agents working in this repo.

## Mission
Keep this mod as a dynamic compat bridge between Cooking for Blockheads and pot-based recipe mods.

No static integration hacks, no hard-required optional mods.

## Current Runtime Design
- Entrypoint: `src/main/java/org/lab_11/modsunified/Unifiled.java`
- Bridge target catalog: `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/CookingPotBridgeCatalog.java`
- Shared ids/keys: `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/BridgeKeys.java`
- Marker/tooltip mapping: `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/BridgeMarkerRegistry.java`
- Recipe indexing: `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/CookingPotRecipeIndexer.java`
- Craft handling: `src/main/java/org/lab_11/modsunified/impl/cookingforblockheads/CookingPotKitchenHandler.java`

## Rules
- Always keep target-mod integration optional. Guard by mod loaded checks.
- Do not add static recipe json hacks when runtime bridge is possible.
- Keep comments sparse and only for non-obvious behavior.
- Put user-facing text in lang files, not hardcoded in Java.
- Keep logs useful; avoid spam.

## Validation Before Commit
1. `./gradlew build -x test`
2. Validate local singleplayer cooking table flow.
3. Validate dedicated server recipe sync/login flow (recipe packet must encode cleanly).

## Workspace Boundaries
- Keep edits inside this repo.
- Never commit `ext_src/`, `libs/`, or generated build outputs.
