# WynversCustomEvents

An OreStack addon plugin that enables custom Nexo item actions in generator events via a simple YAML configuration.

## Features

- Hook into any OreStack generator event (`on-mine`, `on-harvest`, `on-destroy`, `on-place`, `on-hit`, `on-interact`)
- Give **Nexo custom items** to players using the `giveItem NexoItems:<id>` syntax
- Give **vanilla Minecraft items** using `giveItem <MATERIAL>`
- Run commands as the player or as the console
- Hot-reload configuration with `/wcereload`

## Requirements

| Dependency | Version | Required? |
|---|---|---|
| Paper / Spigot | 1.21+ | ✅ Required |
| OreStack | any | ✅ Required |
| Nexo | 1.x | ⚠️ Optional (needed for `giveItem NexoItems:`) |

## Installation

1. Drop `WynversCustomEvents.jar` into your `plugins/` folder.
2. Start (or restart) the server.
3. Edit `plugins/WynversCustomEvents/orestack/events/actions.yml` (created automatically on first run).
4. Restart or run `/wcereload` to apply changes.

## Configuration — `orestack/events/actions.yml`

```yaml
generators:

  # Name must match your OreStack generator name exactly
  my_cobblestone_generator:

    on-mine:
      - do: giveItem NexoItems:enchanted_cobblestone       # give 1 Nexo item
      - do: giveItem NexoItems:enchanted_cobblestone 3     # give 3 Nexo items

    on-harvest:
      - do: giveItem DIAMOND 2                             # give 2 vanilla diamonds

    on-destroy:
      - do: console say %player% destroyed the generator!  # console command

    on-place:
      - do: runCommand me placed a generator!              # command as player
```

### Supported event keys

| Key | Trigger |
|---|---|
| `on-mine` | Player left-breaks a generator block |
| `on-harvest` | Player right-clicks / harvests a generator block |
| `on-destroy` | Generator health reaches 0 |
| `on-place` | Player places a generator |
| `on-hit` | Player left-clicks a generator block (without breaking) |
| `on-interact` | Player interacts with a generator block |

### Supported actions

| Syntax | Description |
|---|---|
| `giveItem NexoItems:<id>` | Give 1 Nexo custom item |
| `giveItem NexoItems:<id> <qty>` | Give `<qty>` Nexo custom items |
| `giveItem <MATERIAL>` | Give 1 vanilla Minecraft item |
| `giveItem <MATERIAL> <qty>` | Give `<qty>` vanilla items |
| `runCommand <command>` | Dispatch `<command>` as the player |
| `console <command>` | Dispatch `<command>` as the console |

> **Placeholder:** `%player%` is replaced with the triggering player's name in `runCommand` and `console` actions.

## Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/wcereload` | `wynverscustomevents.reload` (default: op) | Reload the actions configuration |

## Building

```bash
mvn clean package
```

The compiled jar will be in `target/`.
