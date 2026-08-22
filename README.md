# Fallen

A Fabric mod for Minecraft 26.1.2. When you die, your items don't scatter and despawn — your **body stays where you fell**, a corpse wearing your skin, holding everything you were carrying plus your XP. Walk back and right-click to reclaim your gear (sneak-right-click grabs it all at once).

## Features
- Death leaves a lootable corpse instead of loose, despawning item drops (respects the `keepInventory` gamerule — with it on, nothing changes).
- The corpse is rendered with the dead player's actual skin.
- Stored experience is returned when you reclaim the corpse.
- **Owner grace period**, then public: for a while only the owner (and ops) can loot a corpse; afterwards anyone can.
- **Nothing is ever truly lost**: left too long, a corpse despawns and *drops* its contents and XP on the ground.

## Config (`config/fallen.json`)
| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch; when false, death is vanilla. |
| `ownerGraceMinutes` | `5` | Minutes a corpse is owner-only before anyone can loot it. |
| `despawnMinutes` | `30` | Minutes before a corpse despawns and drops its loot (0 = never). |
| `keepExperience` | `true` | Store the player's XP in the corpse and return it. |
| `opsBypassProtection` | `true` | Operators can loot any corpse, ignoring the grace period. |

## License
Original, independent work — a clean-room implementation built on public Minecraft/Fabric APIs. See `LICENSE`.
