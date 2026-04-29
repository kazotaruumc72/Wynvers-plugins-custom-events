# WynversCustomEvents

An OreStack addon plugin that lets you declare custom actions (Nexo items, vanilla items, commands) **directly inside your OreStack generator configuration files**, plus a custom **Nexo `wither_properties` mechanic** to protect Nexo blocks from Wither damage.

## Features

- Hook into any OreStack generator event (`on-break`, `on-mine`, `on-harvest`, `on-destroy`, `on-place`, `on-hit`, `on-interact`, `on-growth`)
- Give **Nexo custom items** to players using the `giveItem NexoItems:<id>` syntax
- Give **vanilla Minecraft items** using `giveItem <MATERIAL>`
- Run commands as the player or as the console
- Configuration lives **inside the OreStack generator files themselves** – no parallel actions file to maintain
- New custom Nexo mechanic **`wither_properties`** – per-block control of Wither boss / Wither skull damage (PvP-faction friendly)
- Hot-reload with `/wcereload`

## Requirements

| Dependency | Version | Required? |
|---|---|---|
| Paper / Spigot | 1.21+ | ✅ Required |
| OreStack | any | ⚠️ Optional (needed for the OreStack actions feature) |
| Nexo | 1.16+ (tested on 1.23) | ⚠️ Optional (needed for `giveItem NexoItems:` and `wither_properties`) |

## Installation

1. Drop `WynversCustomEvents.jar` into your `plugins/` folder.
2. Start (or restart) the server.
3. Edit your existing OreStack generator files in `plugins/Orestack/generators/` – simply add the action keys (`on-break`, `on-harvest`, …) inside the relevant stage.
4. Run `/wcereload` (or restart) to apply.

## Usage – inside an OreStack generator file

Just add the action keys directly to the relevant stage of your OreStack generator file. For example, `plugins/Orestack/generators/moonstone_ore.yml`:

```yaml
type: depleted
nx-block: moonstone_stone_ore
growth: 10s
---
type: ripe
block: bedrock
default-drops: true
growth: 20s
on-break:
  - do: giveItem NexoItems:enchanted_cobblestone
---
type: regrown
nx-block: moonstone_stone_ore
default-drops: true
```

The generator name is taken from the file name (here: `moonstone_ore`). Stages are matched in the order they appear in the file, which is the same order OreStack itself uses.

### Supported event keys

| Key | Trigger |
|---|---|
| `on-break` | Player breaks (mines) a generator block (alias: `on-mine`) |
| `on-harvest` | Player right-clicks / harvests a generator block |
| `on-destroy` | Generator health reaches 0 |
| `on-place` | Player places a generator |
| `on-hit` | Player left-clicks a generator block (without breaking) |
| `on-interact` | Player interacts with a generator block |
| `on-growth` | Generator finishes its growth timer (no player – console actions only) |

### Supported actions

| Syntax | Description |
|---|---|
| `giveItem NexoItems:<id>` | Give 1 Nexo custom item |
| `giveItem NexoItems:<id> <qty>` | Give `<qty>` Nexo custom items |
| `giveItem <MATERIAL>` | Give 1 vanilla Minecraft item |
| `giveItem <MATERIAL> <qty>` | Give `<qty>` vanilla items |
| `runCommand <command>` | Dispatch `<command>` as the player |
| `console <command>` | Dispatch `<command>` as the console |

> **Placeholder:** `%player%` is replaced with the triggering player's name in `runCommand` and `console` actions. For `on-growth` (no player), only `console` actions without `%player%` are executed.

> **Note:** the `giveItem NexoItems:...` syntax (and the `on-…` action keys in general) is added **by WynversCustomEvents** – it is not a native OreStack feature. Native OreStack keys such as `type`, `block`, `nx-block`, `growth`, `default-drops` are left untouched and continue to be parsed by OreStack itself.

### Action list format

Both formats are accepted:

```yaml
on-break:
  - do: giveItem NexoItems:enchanted_cobblestone   # map format

on-break:
  - "giveItem NexoItems:enchanted_cobblestone"     # plain string
```

## Configuration – `plugins/WynversCustomEvents/config.yml`

Available options:

```yaml
# Path to the OreStack generators directory.
orestack-generators-path: "plugins/Orestack/generators"

# Path to the Nexo items directory (used to scan wither_properties).
nexo-items-path: "plugins/Nexo/items"

# Verbose per-block logging for the wither_properties mechanic.
# Leave false in production; a single summary line is logged whenever at
# least one block is protected during an explosion.
wither-debug: false
```

## Nexo mechanic – `wither_properties`

A custom Nexo `Mechanic` (registered via `MechanicsManager` exactly like the official [`NexoExampleMechanic`](https://github.com/Nexo-MC/NexoExampleMechanic)) that lets you decide, **per Nexo custom block**, whether the Wither boss can damage it.

### Why it exists

In a faction PvP server, players often want unbreakable base blocks. Vanilla `blast_resistance` doesn't stop the Wither – its body explosion *and* its skull projectiles bypass blast resistance and shred everything. This mechanic gives you fine-grained control directly in your Nexo item file.

### Example item (`plugins/Nexo/items/<file>.yml`)

```yaml
obsidienne_rouge:
  itemname: §6Obsidienne Rouge
  material: OBSIDIAN
  Pack:
    parent_model: block/cube_all
    texture: wynvers/icons/obsidienne_rouge
  Mechanics:
    custom_block:
      block_sounds:
        break_sound: block.stone.break
        hit_sound: block.stone.hit
        place_sound: block.stone.place
      custom_variation: 282
      model: obsidienne_rouge
      hardness: 430
      drop:
        loots: []
      type: NOTEBLOCK
    wither_properties:
      wither_explosion_damage: false   # false = Wither body explosion cannot destroy this block
      wither_damage_throw: false       # false = Wither skull projectile cannot destroy this block
```

### Keys

| Key | Type | Default | Effect |
|---|---|---|---|
| `wither_explosion_damage` | boolean | `true` | All-or-nothing toggle for the **Wither body** explosion. `false` = block is invulnerable. Ignored when `wither_explosion_break_block_percent` is set. |
| `wither_damage_throw` | boolean | `true` | All-or-nothing toggle for the **Wither skull** projectile. `false` = block is invulnerable. Ignored when `wither_damage_throw_break_block_percent` is set. |
| `wither_explosion_break_block_percent` | int 0..100 | *unset* | Probability (in %) for the block to **actually break** when caught in a Wither body explosion. `0` → always protected, `100` → always breaks. Overrides `wither_explosion_damage` when present. |
| `wither_damage_throw_break_block_percent` | int 0..100 | *unset* | Same idea for **Wither skull** projectiles. Overrides `wither_damage_throw` when present. |

Boolean defaults reproduce vanilla behaviour when the section is absent.

### Example – random break chance

```yaml
obsidienne_orange:
  itemname: §6Obsidienne Orange
  material: PAPER
  Pack:
    parent_model: block/cube_all
    texture: wynvers/icons/obsidienne_orange
  Mechanics:
    custom_block:
      type: NOTEBLOCK
      custom_variation: 281
      hardness: 250
      drop:
        loots: []
    wither_properties:
      wither_explosion_break_block_percent: 30   # 30% chance to break per Wither body explosion
      # wither_damage_throw_break_block_percent: 50   # (optional) same idea for skulls
```

With `30`, every time a Wither body explosion would catch this block, the plugin rolls a random number 0–99: a roll `< 30` lets the block break (≈30% of the time), otherwise the block is removed from the explosion list and stays intact.

### How it works (technical)

| Hook | Priority | Purpose |
|---|---|---|
| `EntityExplodeEvent` | `LOWEST` | Runs **before** Nexo's own `CustomBlockListener.onEntityExplosion`, removes protected Nexo blocks from `blockList()` so neither vanilla nor Nexo destroys them. |
| `NexoBlockBreakEvent` | `HIGH` | Safety net – cancels any residual Nexo-driven custom-block break that occurs during an active Wither/skull explosion. |
| `EntityChangeBlockEvent` | `HIGHEST` | Cancels Wither body contact damage (the boss walking through your blocks). |

Two parallel data sources keep things robust:

1. **Nexo `Mechanic`** registered through the official `MechanicsManager.registerMechanicFactory(...)` API (fired in the `NexoMechanicsRegisteredEvent` handler so it lands in the correct timing window).
2. **YAML fallback** – the plugin also scans `nexo-items-path` directly, so protections work even if Nexo hadn't parsed our factory yet (e.g. before a `/nexo reload`).

### Production logs

```
[WynversCustomEvents] [WitherProperties] Loaded wither_properties for 1 item(s).
[WynversCustomEvents] Registered Nexo mechanic 'wither_properties'.
[WynversCustomEvents] [WitherProperties] WITHER explosion: protected 590/590 block(s).
[WynversCustomEvents] [WitherProperties] WITHER_SKULL explosion: protected 4/4 block(s).
```

Switch `wither-debug: true` in `config.yml` to also get one log line per protected block (useful only when something seems wrong).

### Hot-reload

`/wcereload` reloads:
- the OreStack generator action keys
- the YAML scan of `wither_properties`

For the official Nexo `Mechanic` to pick up YAML edits, run `/nexo reload` afterwards.

## Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/wcereload` | `wynverscustomevents.reload` (default: op) | Re-scan the OreStack generator files **and** the Nexo `wither_properties` YAML data |

## Building

```bash
mvn clean package
```

The compiled jar will be in `target/`.
