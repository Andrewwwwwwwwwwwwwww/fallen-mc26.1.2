# Changelog

## 1.3.0 — 2026-08-31

A fix for a rare but severe item-loss bug, diagnostics for missing bodies, and claim-mod support.

- **Fixed: a body that failed to spawn deleted the inventory.** The corpse was added to the world *after* the player's inventory had already been cleared and the vanilla drop cancelled — and the result was never checked. On the rare occasions the world refused the entity (another mod vetoing entity spawning is the usual reason), the player got no body, no drops, and no items at all. The body is now placed **before** anything is taken from the player, and if the world refuses it everything drops the vanilla way instead. Anything the Trinkets/Traveler's Backpack hooks had already collected is handed back too.
- **A failed body now says so.** It logs the player, the position, the dimension and the likely cause, rather than failing silently.
- **`/fallen debug <true|false>`** — logs every decision the death handler makes, including each reason it declines to create a body (`keepInventory`, spectator, empty inventory, a lava/void setting, or a refused spawn). A missing body otherwise looks exactly like an ordinary death, so this is the way to find out which check declined it.
- **Open Parties and Claims support.** Ships an entity tag, `#fallen:corpses`, so bodies can be excluded from claim protection, and prints the exact config line at startup when OPAC is installed. Without it, claims block looting a body inside them.

To allow it, add this to `forcedEntityProtectionExceptionList` in `<world>/serverconfig/openpartiesandclaims-server.toml` (the server must be stopped to edit the file):

```toml
forcedEntityProtectionExceptionList = ["minecraft:minecart", "anything$#fallen:corpses"]
```

The `anything$` prefix matters — without it a player holding a sword still can't loot their own body. Fallen's own owner-lock applies regardless, so a body stays locked to its owner either way.

## 1.2.0 — 2026-08-23

Operator body tools — no death is ever unrecoverable.

- **Respawn** — on the death-history screen, operators see a Respawn button on any record whose body is gone. One click re-creates the body at the recorded death spot from the record's full at-death snapshot: every item in its original slot, the experience, and any stored backpacks or accessories. The restored body is locked to its owner and ages from zero. Refused while the body still exists, so it can never duplicate one.
- **Move** — records whose body still exists show Move instead: teleport the body to the operator (across dimensions if needed), placed through the normal settle/float logic. The rescue for a body that's stuck somewhere unreachable or invisible.
- **Lost-body reconciliation** — if Move can't find the body in the world, the record is marked lost and its button flips to Respawn, so the items are recoverable in every scenario.
- **Wider history screen** — coordinates, dates and item counts read in full, and the panel shrinks automatically to fit small windows.

## 1.1.1 — 2026-08-22

Flowing fluid, vehicles, knockback, and Traveler's Backpack — all handled properly now.

- **Flowing lava & water** — a body never rides a fall down, never lies hidden under a flow, and is never launched out of one. It settles on the nearest open surface (a dry block or a still-pool surface), searching close first and then wide enough to reach a fall's base.
- **Knockback** — a resting or floating body that gets yanked (fishing rod, wind charge) re-settles under gravity wherever it ends up instead of hanging in the air.
- **Fluid currents** — flowing fluid can't drag or jostle a body at all.
- **Traveler's Backpack support** — a worn backpack goes into the corpse instead of being placed or dropped at the death spot (where a lava death burned it), and returns to your back on recovery. Soft compat; nothing required.
- **Vehicles** — dying in a boat or minecart no longer breaks the body.
- **Full-body hitbox** — clicks and hits register along the whole body, not just at the feet, and the box is aligned to the model.
- **Smoother landings** — no more resting-position jitter.

## 1.1.0 — 2026-08-22

Bodies now behave like real bodies, and loot is never lost to a hazard or a full pack.

- **Gravity** — a body drops and settles on the ground where you died instead of hanging in mid-air.
- **Lava & water** — the body floats on the surface instead of sinking somewhere unreachable.
- **The void** — the body is held just inside the world rather than lost.
- **No spilled loot** — recovery never drops items when your inventory is full; whatever doesn't fit stays in the body until you have room, so it can't fall into lava or the void.
- **Trinkets support (optional)** — equipped accessories are swept into the body on death and handed back on recovery. Does nothing when Trinkets isn't installed.
- **Death history** — the listed location tracks where the body actually comes to rest.
- **Real-day phases** — a body is locked to you for one day, lootable as a skeleton for the next, then it drops its contents.

## 0.1.0 — pre-release development build

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
