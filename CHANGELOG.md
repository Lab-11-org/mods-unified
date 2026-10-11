# Changelog

## 0.2.3-beta.1 — unreleased

- Defer Forge kitchen capability inspection until block entities finish constructing. This fixes the Farmer’s Delight basket crash with Create: Central Kitchen.
- Preserve virtual fridge ice-upgrade ingredients during Forge cooking-table transfers, without consuming real snowball stacks or restoring virtual ingredients into storage.
- Resolve optional cooking integrations from the installed runtime and keep indexed recipe lookup compatible with supported Cooking for Blockheads versions.
- Improve connected-stove cookware discovery and serving-container accounting.
- Add runtime regressions for virtual and real snowball transfers, Central Kitchen baskets, cabinet capability invalidation, and stove traversal. Isolate Forge and NeoForge test worlds and load the project Mixins during Forge tests.

Release is pending packaged-JAR client/server and restart verification. Existing Forge 0.2.2 artifacts must not be overwritten.
