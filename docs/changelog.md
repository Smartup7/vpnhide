# Changelog

## Source of truth

`lsposed/app/src/main/assets/changelog.json` — bilingual (en/ru), full history. The top-level `version`/`changes` block is the upcoming (unreleased) version. The `history[]` array holds all previously released versions.

## Generated files (do NOT edit by hand)

Two markdown files are regenerated from the JSON by `scripts/_changelog.py`:

- `CHANGELOG.md` at repo root — full history, Keep a Changelog header. The canonical human-facing changelog. CI extracts the current tag's section from here to use as the **GitHub release body**, so don't manually edit release notes either.
- `update-json/changelog.md` — last 5 versions only, shown by Magisk/KSU in the update popup inside the manager app.

## Adding an entry

```sh
./scripts/changelog-add.py <type> "<EN text>" "<RU text>"
```

Types: `added`, `changed`, `fixed`, `removed`, `deprecated`, `security`.

The entry lands in the upcoming version's top-level section of `changelog.json`. Both markdown files are regenerated automatically.

## When to add an entry

Add one for user-visible changes:

- new features / behaviour changes
- bug fixes that affect released versions
- security fixes
- breaking changes (also bump major/minor as appropriate)

Skip for: internal refactors with no behaviour change, documentation-only changes, CI/build tweaks, test additions.

See [releasing.md](releasing.md) for the version-bump and tag flow.
