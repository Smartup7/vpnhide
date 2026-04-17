# Releasing

1. Edit `VERSION` with the new version (e.g. `0.6.1`).
2. Run `./scripts/update-version.py` — propagates `VERSION` to `module.prop`, `Cargo.toml`, and `build.gradle.kts`; rotates the changelog (top-level section → `history[0]`, new empty top-level section for the new version); regenerates `CHANGELOG.md` and `update-json/changelog.md`.
3. Commit, push, tag:
   ```sh
   git commit -am "chore: release v0.6.1"
   git push
   git tag v0.6.1 && git push origin v0.6.1
   ```
4. Wait for CI to build and publish the GitHub release.
5. Run `./scripts/update-json.sh` — generates the `update-json/*.json` files that point to the new release assets.
6. Commit and push:
   ```sh
   git commit -am "chore: update-json for v0.6.1"
   git push
   ```

## Why update-json is a separate commit

Update-json **must** be committed *after* the GitHub release is published. Magisk and KSU fetch these files to decide whether an update is available, then download the zip from the URL inside. If update-json lands before the release exists, users see the new version but the download URL 404s.

## Hotfix order

When adding fixes **on top of** the in-progress version:

1. Bump `VERSION` and run `./scripts/update-version.py` **first** — this rotates the previous top-level changelog section into `history[0]` and creates a fresh empty top-level section for the new version.
2. Then run `./scripts/changelog-add.py` for the fix entries.

If you reverse the order, your entries land in the already-released section. See [changelog.md](changelog.md).
