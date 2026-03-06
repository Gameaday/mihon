# Mihon Fork — What's Different

This fork adds an **authority-first identity system** on top of Mihon's source-based model. Instead of manga only existing as "entries from a source," every manga can have a canonical identity (from MAL, AniList, or MangaUpdates) that persists across sources.

## How it works

When you link a tracker, the app stores a **canonical ID** (e.g., `al:21` for AniList #21) and pulls in **alternative titles** (romaji, native, synonyms). These power smarter migration search and let the app recognize "Shingeki no Kyojin" and "Attack on Titan" as the same series.

**Source health** is automatically tracked during library updates. If a source stops returning chapters, the app detects it, warns you, and suggests migration to a working source.

## User stories

**Authority-only** — You use the app to catalog what you've read (in print, on other apps, etc.) and track it via MAL/AniList. You don't need an active source. The app won't bother you with health warnings for manga that never had chapters.

**Authority + local** — You have local manga files and use trackers to organize them. Local sources are explicitly excluded from health detection — no false DEAD warnings on your local library.

**Authority + source** — The main use case. You read from online sources and use trackers for identity. When a source dies, you get notified and can migrate with one tap.

## What's built

- **Canonical ID** — auto-set from MAL/AniList/MangaUpdates on tracker bind
- **Alternative titles** — pulled from AniList, stored as JSON, used in search
- **Tiered migration search** — canonical ID → primary title → alt titles → near-match → deep search
- **Source health detection** — automatic DEAD/DEGRADED/HEALTHY classification on library update
- **Source health UI** — banner on manga detail, ⚠ badge on library covers, colored source name
- **Notifications** — dead/degraded source alerts, migration prompts after 3 days dead
- **Library filter** — show/hide dead or degraded manga
- **Backup/restore** — canonical ID, health status, and dead_since all survive backup cycles
- **Design tokens** — consistent spacing/sizing via Padding, Navigation, Badge, Pill tokens

135 unit tests across the fork's features.

## What's NOT being built

These ideas were explored but aren't worth the maintenance:

- **Multi-source chapter resolution** — fetching from multiple sources simultaneously. Changes the entire reading pipeline for marginal benefit.
- **Automated cross-source discovery** — searching all sources when one dies. Rate limiting complexity vs. just pressing "Migrate."
- **Source health history table** — tracking every status transition. The `dead_since` column already covers the useful case.

## Architecture

| Aspect | Choice |
|--------|--------|
| Identity | Canonical ID from tracker (`al:21`, `mal:30013`, `mu:12345`) |
| Alt titles | JSON array in DB, backward-compatible with legacy pipe-separated |
| Health detection | Chapter count comparison (70% threshold), zero extra API calls |
| Search | 4-tier: canonical ID (free) → title (1 call) → alt titles → deep search |
