# Fallen

A Fabric mod for Minecraft 26.1.2. When you die, your items don't scatter and despawn — your **body stays where you fell**, a corpse wearing your skin, holding everything you were carrying plus your XP. Walk back and right-click to reclaim your gear (sneak-right-click grabs it all at once).

Install on **both the client and the server** — this is not a server-only mod. For a server-only alternative that works with completely vanilla clients, see [Server Sided Corpse](https://github.com/Andrewwwwwwwwwwwwwww/ssc).

## Features

- Death leaves a lootable corpse instead of loose, despawning item drops (respects the `keepInventory` gamerule — with it on, nothing changes).
- The corpse is rendered with the dead player's actual skin, wide or slim, lying where they fell.
- **Items go back where they belong** — reclaiming returns each item to its original slot (armor to armor, offhand to offhand). You can only ever take *out* of a body, never put things in.
- Stored experience is returned when you reclaim the corpse.
- **Settles like a real body** — it drops to the ground where you died, floats on still lava or water, and over the void is held just inside the world. In flowing fluid it rests on the nearest open surface instead of riding the fall down.
- **Yours until it's bone** — for a day only you (and operators) can loot your body; once it ages into a skeleton anyone may loot it. Both times are configurable, and it can stay yours for good.
- **Never spills your loot** — reclaiming only takes what fits. If your pack is full, the rest stays in the body until you have room.
- **Nothing is ever truly lost** — a body left far too long drops its contents instead of vanishing.
- Optional **Trinkets** and **Traveler's Backpack** support: equipped accessories and a worn backpack go into the body too, and return to their own slots on recovery. Neither mod is required.

## Death History (press U)

Every death is recorded permanently. A green check means the body is still out there; a red X means it's gone. Open any record for a read-only snapshot of exactly what was carried at the moment of death, accurate even after the body was looted.

Operators can review any player with `/deathhistory <player>`, and get two recovery tools: **Respawn** re-creates a lost body from its record, and **Move** teleports an existing body to them — so no death is unrecoverable.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/deathhistory` | everyone | Your recent deaths (the U screen). |
| `/deathhistory <player>` | operators | Another player's death history. |
| `/fallen debug <true\|false>` | operators | Log every decision the death handler makes — see below. |

## Config (`config/fallen.json`)

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch; when false, death is vanilla. |
| `skeletonMinutes` | `1440` | Minutes before a body ages into a skeleton and unlocks (1 day). 0 = never. |
| `skeletonStageIsPublic` | `true` | Once skeletal, anyone may loot it. false = owner-only forever. |
| `despawnMinutes` | `2880` | Minutes before a body despawns and drops its contents (2 days). 0 = never. |
| `keepExperience` | `true` | Store the player's XP in the body and return it. |
| `opsBypassProtection` | `true` | Operators can loot any body, ignoring the owner lock. |
| `spawnInLava` / `spawnOverVoid` | `true` | Whether to place a body at a lava/void death (else items drop as vanilla). |
| `voidScanDepth` | `12` | How far down to scan for ground before a spot counts as "over the void". |
| `deathHistorySize` | `20` | How many past deaths to keep per player. |
| `debugLogging` | `false` | Log every death-handler decision (see below). Also toggled with `/fallen debug`. |

The owner lock is driven entirely by the skeleton timers: a body is yours until it skeletonises, then it's public. Set `skeletonStageIsPublic` to false (or `skeletonMinutes` to 0) to keep bodies owner-only for good.

## Claim mods (Open Parties and Claims)

A corpse is an entity, so claim mods treat looting one as "interacting with an entity" and refuse it inside claims. Fallen ships an entity tag for exactly this. Add it to OPAC's forced exceptions in `<world>/serverconfig/openpartiesandclaims-server.toml` — the server has to be stopped to edit that file:

```toml
forcedEntityProtectionExceptionList = ["minecraft:minecart", "anything$#fallen:corpses"]
```

The `anything$` prefix matters: without it the exception only applies when the item in your hand isn't itself blocked, so a player holding a sword still couldn't loot their own body. Because the tag names `fallen:corpse` specifically, nothing else on your server loses protection.

Claims never make a body public — Fallen's own owner-lock applies regardless. Fallen prints this config line to the console at startup whenever it detects OPAC.

## Diagnosing a missing body

A body that never appears looks exactly like an ordinary death from the outside. `/fallen debug true` logs the reasoning: every path that declines to create a body says why (`keepInventory` on, spectator, empty inventory, a lava/void setting, or the world refusing the corpse entity).

A body that can't be placed logs a warning naming the player, position and dimension. The usual cause is another mod vetoing entity spawning there; the player's items drop the vanilla way instead.

Bodies are ordinary entities, so vanilla selectors find the ones that exist:

```
/execute as @e[type=fallen:corpse] run tp @s ~ ~ ~
```

## Add-ons

- **[Fallen: Backpacked](https://github.com/Andrewwwwwwwwwwwwwww/fallen-backpacked)** — sends MrCrayfish's Backpacked backpacks into your corpse instead of dropping them. Built on Fallen's compatibility API, so other backpack/curio-style mods can add support too.

## License

Original, independent work — a clean-room implementation built on public Minecraft/Fabric APIs. See `LICENSE`.
