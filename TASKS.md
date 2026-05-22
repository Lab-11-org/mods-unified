# TASKS.md

## Stage Snapshot
Bridge is active for:
- FarmersDelight cooking pot
- DungeonsDelight monster pot
- MinersDelight copper pot

Container costs and requirement tooltips are wired in cooking table flow.

## Next Work Queue
1. Add optional bridge targets with the same catalog/marker pattern.
2. Add lightweight game tests or scripted validation for:
   - no-pot-above-table gating
   - requirement marker gating
   - container consume + not-enough tooltip behavior
3. Reduce reflection surface where stable APIs exist.
4. Improve dedicated-server diagnostics when recipe sync fails.

## Done Definition For Future Tasks
- Build passes: `./gradlew build -x test`
- Singleplayer behavior valid
- Dedicated server behavior valid
- i18n keys updated for new user-facing strings
- No static hard dependency added for optional target mods
