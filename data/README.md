# Data

CSV files here are the **source of truth**. The app ships a generated read-only
SQLite database; it is a build artifact, not committed.

- Provenance, licensing, and schema: [`SOURCES.md`](SOURCES.md).
- Data model, i18n, and bundling: [`../docs/DATA.md`](../docs/DATA.md).

## Volumes

412 clubs · 513 players · 945 aliases · 1 905 transfers · 53 nationalities.

## Pipeline (Phase 0/1)

```
data/*.csv  ──build──▶  data/build/out/mercato.db  ──bundle──▶  app
                          (gitignored artifact)
```

The build step validates referential integrity (every transfer references
existing player/club IDs) and is run in CI.

## Editing rules

- Edit the CSVs, never the generated DB.
- Keep Wikidata Q-IDs stable (they dedupe entities across seasons).
- Do not add IP-sensitive fields (no image/crest/logo references).
