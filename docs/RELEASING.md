# Modrinth release procedure

This repository publishes to the existing **Code's Mod Unified** project only:
https://modrinth.com/mod/codes-mod-unified (`fTSZ42IY`). Its source URL is
https://github.com/Lab-11-org/mods-unified.

The existing `.github/workflows/release.yml` publishes GitHub releases on tags.
It does not publish to Modrinth. The explicit Python 3.11+ command below calls
[Modrinth's create-version API](https://docs.modrinth.com/api/operations/createversion/).
No new automatic release trigger or persistent authorization is installed.
Every future release still needs the user's authorization.

## Credentials: user setup only

Create a short-lived Modrinth personal access token with **VERSION_CREATE** only,
using an account already allowed to create versions on this project. The script
uses public reads for project/version verification; PROJECT_WRITE, deletion,
profile edits and project creation are not needed. It does not modify project
metadata or synchronize the project description.

Preferred for one manual invocation: run `publish` in your own terminal and type
the token into its hidden prompt. It is not saved.

For agent execution on ESP-Power, the user may choose to create this private file:
`<repository>/.secrets/modrinth.token` (owner-only mode `600`). On ESP-Power this is
`/home/code/workspace/game/minecraft/mods-unified/.secrets/modrinth.token`.
The root-scoped `/.secrets/` rule in `.gitignore` excludes it from Git.
The script does not fall back to a home-directory token file. Run the following
from inside this repository in your own terminal:

```bash
python3 - <<'PY'
from pathlib import Path
import getpass, os, subprocess
root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
path = root / '.secrets/modrinth.token'
path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
token = getpass.getpass('Modrinth VERSION_CREATE token (hidden): ').strip()
if not token:
    raise SystemExit('Empty token; nothing written')
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as stream:
    stream.write(token + '\n')
print('Private token file created; no token printed.')
PY
```

This intentionally refuses to overwrite an existing file. Revoke/rotate the token
in Modrinth when finished. The agent must not create this credential for the user.
An existing securely injected `MODRINTH_TOKEN` environment variable takes priority.
No token command-line flag is accepted. Do not paste tokens into chat, source,
Gradle properties, changelogs, logs or shell command history. The script reads
credentials only during explicit `publish`, after all gates pass.

If moving this process to CI later, the user can manually add an Actions secret
named `MODRINTH_TOKEN` in **Lab-11-org/mods-unified → Settings → Secrets and variables
→ Actions**. The current workflow does not read that secret. CI integration is
not installed by this change; use the local procedure for this release.

## Prepare exact release artifacts

Review and commit the intended release source first, including the release script.
Preserve unrelated work. Use a clean checkout with the intended `mod_version`, a
reviewed changelog, and an output directory outside the repository:

```bash
python3 scripts/modrinth_release.py prepare \
  --bundle /tmp/mods-unified-release-0.2.3 \
  --changelog /path/to/reviewed-changelog.md
```

`0.2.3` is an example of the next unused version, not a declaration that it is
ready. Forge **0.2.2 already exists on Modrinth and the personal page**. Never
replace its bytes with today's different local build. `prepare` refuses a version
already present on Modrinth, builds both supported loaders, runs the existing
full-runtime GameTests, the exact Forge/Central Kitchen regression profile, and the
newer NeoForge dependency check, then records the
commit, JAR SHA-512 hashes and changelog hash in `manifest.json`.

## Required packaged-JAR E2E

Existing Java checks and `runGameTestServer` use development classes and do not
substitute for release E2E. Install the **exact JARs from the bundle** in real
Minecraft client/integrated-server and dedicated-server/client test instances as
applicable. Preserve runtime mod lists, exact versions, commands, logs, screenshots,
and before/after inventory observations in the bundle. Do not mark a case passed
based on code inspection or a successful build.

For each loader complete the generated `e2e.json` with tester, timestamp, exact
runtime versions and observations. Every passed case needs nonempty evidence
files relative to the bundle directory. Cases required by the publish gate:

- `ice_upgrade_recipes`: Cooking for Blockheads fridge with ice upgrade; Farmer's
  Delight and Kaleidoscope snowball recipes appear and craft repeatedly from the
  configured virtual supply. Check sufficient ingredients and depletion boundaries.
- `ice_upgrade_transfer`: Verify virtual snowball transfer to each appropriate
  pot, no unintended consumption of real fridge stacks, and no blocked transfer
  with the ice upgrade installed. Cover removal/reinsertion of the upgrade.
- `central_kitchen_basket` (Forge): Place, break and replace Farmer's Delight's
  basket with Create + Central Kitchen; access it and reconnect the kitchen.
  Include the reported combination: Minecraft 1.20.1, Forge 47.4.20, Farmer's
  Delight 1.3.4, Create 6.0.8, Central Kitchen 1.5.1, plus its required dependencies.
  Also check without Central Kitchen to detect regressions.
- `core_cooking`: Supported pots, connected stoves, recipe selection, ingredient
  transfer, bowl/container accounting and applicable optional-mod combinations.
- `restart_persistence`: Save/quit, restart client/server, reopen the same world,
  and repeat basket, fridge, recipe and inventory checks; record no loss/duplication.

For NeoForge, record which Central Kitchen combinations are supported instead of
claiming the Forge-only reproducer was tested there. All failures or unavailable
runtime/UI paths block release. The JSON gate checks records and exact hashes;
a human/agent must inspect the real evidence, because a JSON assertion is not
independent proof of behavior.

## Preflight and publish

```bash
python3 scripts/modrinth_release.py preflight --bundle /tmp/mods-unified-release-0.2.3
python3 scripts/modrinth_release.py publish --bundle /tmp/mods-unified-release-0.2.3
```

`preflight` is read-only and never reads credentials. `publish` validates the clean
source commit, files, metadata, changelog and E2E evidence before reading a token.
It creates one version per loader, refuses existing versions with different bytes,
and verifies each public version and downloaded file's SHA-512. It writes receipts
with the returned IDs before further network verification. POSTs are never retried
automatically. After an uncertain network error or partial release, run preflight;
identical already-published versions are safely reused, differing ones block.
A concurrent publisher must not run at the same time.

The personal page is https://blog.lab-11.org/download, owned by the local
`/home/code/workspace/blog` project. It reads Netlify Blobs store `mods-unified`,
site ID `c46b975d-3b54-4379-aad1-1161fc12082f`. Use the existing authorized Netlify
flow separately, upload the same E2E-verified bytes under a new version key,
and verify the public file hashes. This script does not alter that site.

## Current audit blockers (2026-10-09 UTC)

Do not release the current source: the reported Central Kitchen constructor-time
crash still points to the unguarded `blockEntity.getBlockState()` in
`LegacyKitchenConnectorCompat.attach` (line 72 at audit). It is unchanged by the
local stove-connector addition. Snowball virtual-slot handling exists, but actual
recipe/transfer E2E is not yet established. Fix authorization is separate from
this release-configuration change.
