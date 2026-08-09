# STYLE_GUIDE.md

Practical style guide for future agents and contributors.

## Writing Tone
- Be direct.
- Keep it short.
- No filler words.
- Use full mod names like `FarmersDelight`, `DungeonsDelight`, `MinersDelight` (do not shorten to `FD`).

## Code Style
- Prefer small focused classes over one big manager class.
- Keep constants centralized (`BridgeKeys`).
- Keep extension mappings centralized (`BridgeMarkerRegistry`, `CookingPotBridgeCatalog`).
- Use comments only when behavior is not obvious from code.
- Avoid magic strings in multiple files.

## Integration Pattern
- Optional mod integrations must stay dynamic.
- Check mod loaded state before registration.
- Reflection failures should degrade gracefully and log once with context.
- Never force a hard dependency in code paths that should be optional.

## Tooltip + UX Rules
- User-facing strings go to lang json only.
- Tooltip logic should be requirement-driven, not recipe-id hardcoded.
- Missing requirements should explain what is missing, not just fail silently.

## Recipe Bridge Rules
- Indexed recipes must preserve source recipe semantics.
- Container costs must be resolved from recipe/container behavior, not guessed.
- Dedicated server and singleplayer behavior must match.
- Changes to recipe encoding/sync must be validated on dedicated server.

## File Organization
- Entry wiring in `Unifiled`.
- Target definitions in `compat/cookingforblockheads/CookingPotBridgeCatalog`.
- Shared ids in `compat/cookingforblockheads/BridgeKeys`.
- Marker and requirement mapping in `compat/cookingforblockheads/BridgeMarkerRegistry`.
- Keep CFBH integration code in `compat/cookingforblockheads/`.

## Commit Rules
- One concern per commit.
- Build before commit (`./gradlew build -x test`).
- Do not commit `build/`, `ext_src/`, `libs/`, or local tool artifacts.
