# Changelog

## 0.1.0 (MC 26.2, Fabric) — in development
- On death (when `keepInventory` is off) your items and experience are swept into a **corpse** at the spot you fell instead of scattering.
- The corpse is rendered as your body — your skin, slim/thick model, and name — lying flat.
- Custom **"Corpse of <name>"** GUI: armor + offhand slots, inventory grid and hotbar, plus a **Transfer Items** button.
- **Transfer returns every item to its original slot** (armor on your body, offhand in offhand, hotbar where it was). Sneak-right-click also grabs everything.
- Reclaiming a corpse returns your stored XP.
- **Owner grace period** then public looting; left too long, a corpse despawns and *drops* its contents and XP (never deleted).
- Corpses don't fall (no gravity) and are fire-immune, so they can't slide into the void or burn in lava.
- After the configured time (default 1 hour) a corpse ages into a **skeleton** (cosmetic); once skeletal, anyone may loot it (configurable).
- **Death History**: press **U** to see your recent deaths. Each entry has a **Location** button (opens chat with a ready teleport command) and an **Items** button (recovers that death's items — creative only). Operators can view any player with **/deathhistory <player>**.
- Config at `config/fallen.json`: `enabled`, `ownerGraceMinutes`, `despawnMinutes`, `keepExperience`, `opsBypassProtection`, `spawnOverVoid`, `spawnInLava`, `voidScanDepth`, `skeletonMinutes`, `skeletonStageIsPublic`, `deathHistorySize`.
