# Sunshine
![Sunshine Banner](https://cdn.modrinth.com/data/cached_images/2682c11a8cb2528985ab7c07c571c1b9b8f31031.png)

**Minecraft Entity Optimiser for 1.21.11**

Sunshine is a purely **client-side** Fabric mod that collapses large piles of
identical dropped item entities into a small, configurable number of visible
renders. The hidden entities remain fully alive, they tick, collide, float,
and can be walked over and picked up exactly as vanilla. Only their drawing is
skipped.

---

## Why it helps

When many identical items land in the same spot (ore veins mined with
Fortune, mob farms, mass crafting output, etc.) Minecraft renders every one
of those item stacks independently, each with its own animated model and
shadow. With 250 dirt stacks on the ground you pay the GPU cost for 250
models even though they look identical and completely overlap. Sunshine
renders only the first few (default: 3) and skips the rest, cutting draw
calls and entity-renderer work proportionally.

---

## Features

| Feature | Detail |
|---|---|
| Rendering-only | Server never contacted; no gameplay change |
| Per-group cap | Default 3 renders per identical-item cluster, 1–64 |
| Adjustable radius | Grouping cell size, default 1.5 blocks |
| Stable selection | Which entities are visible is stable tick-to-tick (no flickering) |
| Live config | `/sunshine` command, changes persist to `config/sunshine.json` |
| Stats overlay | Optional HUD panel; toggle with `/sunshine stats` |
| Auto-adjust | As items are picked up the visible count drops naturally |

---

## Commands

All commands are **client-side** (work on any server, single-player or
multiplayer).

```
/sunshine                      Print current settings
/sunshine enable               Enable the optimisation
/sunshine disable              Disable (vanilla behaviour restored)
/sunshine stats                Toggle the HUD statistics overlay
/sunshine maxvisible <1–64>    Set visible-entity cap per cluster
/sunshine radius <0.25–16.0>   Set grouping cell size in blocks
/sunshine reload               Re-read config/sunshine.json from disk
```

---

## Configuration

Settings are saved automatically after each `/sunshine` command and can also
be edited by hand in `.minecraft/config/sunshine.json`:

```json
{
  "enabled": true,
  "maxVisiblePerGroup": 3,
  "groupingRadius": 1.5,
  "showStatistics": false
}
```

---

## Compatibility

- **Minecraft:** 1.21.11  
- **Mod loader:** Fabric Loader ≥ 0.19.3  
- **Fabric API:** 0.141.4+1.21.11 (or compatible)  
- **Side:** Client-only — install only on the client
- **Other mods:** Compatible with other rendering/culling mods; Sunshine only
  ever adds an additional reason to skip a render, never forces one to appear

---

## Building from source

```bash
git clone https://github.com/vineapple/sunshine.git
cd sunshine
./gradlew build
# Output: build/libs/sunshine-1.0.0.jar
```

---

## Author

Vineapple  
License: CC0-1.0
