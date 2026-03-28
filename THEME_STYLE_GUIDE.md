# Theme Style Guide

This document describes the visual identity of the three branded themes in
the app: **Ephyra**, **Nagare**, and **Atolla**. Each theme goes beyond a
simple color swap — it defines its own shape language, spacing density,
surface transparency, typography weight, and design philosophy through the
`BrandedThemeConfig` system.

---

## How It Works

Every branded theme provides a `BrandedThemeConfig` (defined in
`presentation-core/.../theme/BrandedThemeConfig.kt`) that is exposed to the
entire Compose tree via the `LocalBrandedTheme` CompositionLocal. Components
read design tokens (shape, spacing, alpha, typography weight) from this
config so that the visual identity changes *beyond just color*.

All non-branded themes (Default, Catppuccin, Nord, etc.) use the default
config, which matches the existing `Shapes` radii and standard M3 values.

---

## Ephyra — "Glassmorphic" Aesthetic

| Property            | Value         |
|---------------------|---------------|
| **Feeling**         | Translucent, modern, premium |
| **Colors**          | Electric Indigo (#4F46E5) & Cyan (#0891B2) |
| **Card radius**     | 20 dp (soft, airy) |
| **Cover radius**    | 16 dp |
| **Badge shape**     | Pill (50%) |
| **Sheet radius**    | 32 dp |
| **Dialog radius**   | 32 dp |
| **Card elevation**  | 0 dp (flat / frosted glass) |
| **Card border**     | 0 dp (no border) |
| **Grid spacing**    | 10 dp H × 10 dp V (more breathing room) |
| **Surface alpha**   | 0.85 (translucent glass) |
| **Container alpha** | 0.78 (deeper transparency layers) |
| **Heading weight**  | SemiBold (modern clarity through glass) |
| **Body weight**     | Normal |
| **Card padding**    | 10 dp (spacious) |

### Design Notes

- Dark mode with "frosted glass" overlays and soft purple-to-teal gradients.
- Larger corner radii create an organic, jellyfish-bell-like silhouette.
- **Translucent surface alphas** (0.85/0.78) create depth through layered
  transparency rather than shadows — the hallmark of glassmorphism.
- Zero elevation keeps cards flat — depth comes from alpha layering.
- Wider grid spacing gives the layout an "airy, premium" feel.
- SemiBold headings are readable through glass without being heavy.

### Logo Concept

A minimalist, geometric jellyfish: a simple semi-circle (the bell) with
three clean, vertical lines of varying lengths beneath it.

---

## Nagare — "Minimalist Zen" Aesthetic

| Property            | Value         |
|---------------------|---------------|
| **Feeling**         | Fluid, lightweight, effortless |
| **Colors**          | Charcoal (#374151) & Mint (#059669) |
| **Card radius**     | 12 dp (clean, moderate) |
| **Cover radius**    | 8 dp |
| **Badge shape**     | Pill (50%) |
| **Sheet radius**    | 24 dp |
| **Dialog radius**   | 24 dp |
| **Card elevation**  | 0 dp |
| **Card border**     | 0 dp |
| **Grid spacing**    | 6 dp H × 6 dp V (tight, dense) |
| **Surface alpha**   | 1.0 (fully opaque — no effects) |
| **Container alpha** | 1.0 |
| **Heading weight**  | Medium (understated elegance) |
| **Body weight**     | Normal |
| **Card padding**    | 6 dp (compact) |

### Design Notes

- The most refined Material 3 expression of the three themes.
- Fully opaque surfaces — no transparency gimmicks, just clean M3.
- Tighter grid spacing produces a denser layout that puts content front
  and center without visual clutter.
- **Medium weight headings** are the key differentiator — they create a
  calm, understated hierarchy instead of the typical Bold emphasis.
- Moderate corner radii keep things clean without being overly stylized.

### Logo Concept

A single, continuous "S-curve" line that subtly forms a lowercase "n".
Looks like a brush stroke or a gentle wave.

---

## Atolla — "System Hub" Aesthetic

| Property            | Value         |
|---------------------|---------------|
| **Feeling**         | Bold, stable, industrial |
| **Colors**          | Deep Sea Blue (#1E3A5F) & Amber (#F59E0B) |
| **Card radius**     | 6 dp (crisp, squared) |
| **Cover radius**    | 4 dp |
| **Badge shape**     | 4 dp (nearly square) |
| **Sheet radius**    | 16 dp |
| **Dialog radius**   | 16 dp |
| **Card elevation**  | 2 dp (tactile, raised) |
| **Card border**     | 1 dp |
| **Grid spacing**    | 8 dp H × 8 dp V (standard) |
| **Surface alpha**   | 1.0 (solid, functional panels) |
| **Container alpha** | 1.0 |
| **Heading weight**  | Bold (authority and structure) |
| **Body weight**     | Medium (increased readability) |
| **Card padding**    | 6 dp (dense, efficient) |

### Design Notes

- High contrast with solid blocks of color and crisp borders.
- Small corner radii give cards a structured, "control panel" look.
- Subtle elevation and borders make cards feel tactile and organized.
- **Bold headings + Medium body** text create a strong visual hierarchy
  that prioritizes information density and scannability.
- Library cards look like organized tiles on a dashboard.
- 1dp borders define clear boundaries between elements.

### Logo Concept

Two concentric circles — the inner one is broken at the top (like a power
button or sync symbol). Represents a "Central Hub."

---

## Comparison

| Name   | Logo Concept       | Color Palette            | Primary Feeling       | Surface Style      | Heading Weight |
|--------|--------------------|--------------------------|----------------------|--------------------|----------------|
| Ephyra | Geometric Bell     | Electric Indigo & Cyan   | Translucent / Modern | Glassmorphic (85%) | SemiBold       |
| Nagare | Continuous Wave    | Charcoal & Mint          | Fluid / Lightweight  | Solid / Clean M3   | Medium         |
| Atolla | Concentric Rings   | Deep Sea Blue & Amber    | Reliable / Structured| Solid + Borders    | Bold           |

---

## Token Wiring

The following tokens are **automatically applied** by the theme engine
(in `TachiyomiTheme.kt`) — component authors don't need to read them manually:

| Token | Wired Into | Effect |
|-------|-----------|--------|
| `surfaceAlpha` | `ColorScheme.surface`, `surfaceVariant` | Translucent surface backgrounds |
| `containerAlpha` | `ColorScheme.surfaceContainer*` (all 5 levels) | Translucent container backgrounds |
| `headingWeight` | `Typography` display/headline/title styles | Per-theme heading weight |
| `bodyWeight` | `Typography` body/label styles | Per-theme body text weight |

The Typography extension functions (`header`, `titleEmphasis`, `displayEmphasis`,
`sectionLabel`) also read `headingWeight` from `LocalBrandedTheme` to stay
consistent with the branded typography.

Shape tokens (`ShapeTokens.card`, `ShapeTokens.coverImage`, etc.) delegate to
`LocalBrandedTheme`, so M3 components automatically use per-theme shapes.

## For Developers

To read the current branded theme config in any `@Composable`:

```kotlin
val config = LocalBrandedTheme.current
// Shape tokens
config.cardCornerRadius
config.coverImageCornerRadius
// Surface transparency (applied automatically to ColorScheme)
config.surfaceAlpha
config.containerAlpha
// Typography (applied automatically to MaterialTheme.typography)
config.headingWeight  // FontWeight for titles
config.bodyWeight     // FontWeight for body text
// Spacing & elevation (for custom card components)
config.cardElevation
config.cardBorderWidth
config.cardContentPadding
```

For custom card-like components that need borders/elevation, read
`config.cardElevation` and `config.cardBorderWidth` directly:
