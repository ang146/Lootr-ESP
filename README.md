# BlockESP

A client-side Minecraft Forge mod that highlights any block with colored outlines visible **through walls**. Works with blocks from any mod.

Built for **Forge 1.20.1**.

---

## Features

- **X-ray outlines** — See tracked blocks through any wall or terrain
- **Any block, any mod** — Works with vanilla and modded blocks (Create, Mekanism, Terramity, etc.)
- **Custom colors** — Set a unique hex color for each block
- **In-game GUI** — Press `J` to manage tracked blocks visually
- **Chat commands** — Quick `/besp` commands for adding, removing, and configuring
- **Smart auto-colors** — Automatically picks colors for common ores
- **Semi-transparent faces** — Optional filled overlays for extra visibility
- **Optimized scanning** — Async chunk-based scanner with dirty-section tracking, won't drop your FPS
- **Instant updates** — Outlines disappear immediately when a block is mined
- **Persistent config** — Your settings save between sessions

---

## Controls

| Key | Action |
|-----|--------|
| `H` | Toggle ESP on/off |
| `J` | Open settings GUI |

---

## Commands

| Command | Description |
|---------|-------------|
| `/besp add "block_id" "#color"` | Add a block to track |
| `/besp remove "block_id"` | Stop tracking a block |
| `/besp list` | Show all tracked blocks |
| `/besp clear` | Remove all tracked blocks |
| `/besp toggle` | Toggle ESP on/off |
| `/besp radius <4-128>` | Set scan radius |
| `/besp help` | Show help |

### Examples

```
/besp add "diamond_ore"
/besp add "deepslate_gold_ore" "#FFD700"
/besp add "terramity:deepslate_iridium_ore" "#9966FF"
/besp add "create:zinc_ore" "#AAAAFF"
/besp remove "diamond_ore"
/besp radius 64
```

> **Tip:** Press F3 and look at a block to find its ID.

---

## Installation

1. Install [Minecraft Forge 1.20.1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
2. Download `BlockESP-1.0.0.jar` from [Releases](https://github.com/mellytimes/Block-ESP/releases)
3. Drop the JAR into your `mods/` folder
4. Launch Minecraft with Forge 1.20.1

---

## Configuration

Settings are saved to `config/blockesp.json` and persist between sessions.

| Setting | Default | Description |
|---------|---------|-------------|
| `scanRadius` | 32 | How far to scan in each direction (blocks) |
| `scanInterval` | 10 | Ticks between scans (10 = 0.5 seconds) |
| `lineWidth` | 2.0 | Outline thickness |
| `filledFaces` | true | Draw semi-transparent face overlays |
| `faceOpacity` | 0.15 | Face overlay opacity (0.0 - 1.0) |

---

## Performance

The scanner runs on a background thread and uses chunk-based caching, so it won't tank your FPS. A few things to keep in mind:

- **Radius 32** scans ~262K blocks — runs smoothly on most systems
- **Radius 64** scans ~2M blocks — still fine thanks to async scanning
- **Radius 128** — works but may use more memory
- Air-only chunk sections are skipped entirely
- Sections only rescan when you move chunks, change config, or a block is mined/placed

---

## License

MIT — do whatever you want with it.

---

Made by [MellyDevs](https://github.com/mellytimes)
