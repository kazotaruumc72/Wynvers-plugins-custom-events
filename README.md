# WynversCustomEvents

An OreStack addon plugin that lets you declare custom actions (Nexo items, vanilla items, commands) **directly inside your OreStack generator configuration files**.

## Features

- Hook into any OreStack generator event (`on-break`, `on-mine`, `on-harvest`, `on-destroy`, `on-place`, `on-hit`, `on-interact`, `on-growth`)
- Give **Nexo custom items** to players using the `giveItem NexoItems:<id>` syntax
- Give **vanilla Minecraft items** using `giveItem <MATERIAL>`
- Run commands as the player or as the console
- Configuration lives **inside the OreStack generator files themselves** – no parallel actions file to maintain
- Hot-reload with `/wcereload`

## Requirements

| Dependency | Version | Required? |
|---|---|---|
| Paper / Spigot | 1.21+ | ✅ Required |
| OreStack | any | ✅ Required |
| Nexo | 1.x | ⚠️ Optional (needed for `giveItem NexoItems:`) |

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

Only one option, used to override the OreStack generators directory if your server uses a non-default location:

```yaml
orestack-generators-path: "plugins/Orestack/generators"
```

## Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/wcereload` | `wynverscustomevents.reload` (default: op) | Re-scan the OreStack generator files |

## Building

```bash
mvn clean package
```

The compiled jar will be in `target/`.
