package io.github.andrewwwwwwwwwwwwwww.fallen.entity;

import com.mojang.authlib.GameProfile;
import io.github.andrewwwwwwwwwwwwwww.fallen.CorpseConfig;
import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi;
import io.github.andrewwwwwwwwwwwwwww.fallen.deathhistory.DeathHistoryData;
import io.github.andrewwwwwwwwwwwwwww.fallen.menu.CorpseMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.UUID;

/**
 * The body a player leaves behind on death — an armor-stand-style
 * {@link LivingEntity} rendered as the dead player, lying flat. It holds the
 * player's items in a container that mirrors the inventory's slot indices so
 * everything can be handed back to its original slot, plus their experience.
 */
public class CorpseEntity extends LivingEntity {
    private static final int SLOTS = CorpseMenu.CONTAINER_SIZE;

    /**
     * Group id used to re-store addon items (backpacks/trinkets) that couldn't be
     * handed back because the player's inventory was full. No provider owns it, so
     * on the next recovery they're simply retried into the inventory — never
     * dropped, so a body over lava/void can't spill a player's accessories.
     */
    private static final Identifier KEPT_EXTRAS = Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "kept");

    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_OWNER_UUID =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_SKELETON =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);

    private final SimpleContainer contents = new SimpleContainer(SLOTS);
    /** Items from addon slots (backpacks, curios) that live outside the vanilla inventory. */
    private java.util.List<io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.Group> extras = new java.util.ArrayList<>();
    /**
     * The stored addon items shown (and taken) in the corpse screen. This is a
     * view of {@link #extras}, rebuilt from it — {@code extras} stays the source
     * of truth, so taking a backpack here drops it from storage in lock-step and
     * can't duplicate it. When more are stored than there are slots, taking one
     * reveals the next.
     */
    private final SimpleContainer extraDisplay =
            new SimpleContainer(Math.max(0, io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.totalDisplaySlots()));
    private UUID ownerUuid;
    private String ownerName = "";
    private int storedXp;
    private long age;
    /**
     * Pinned bodies stay put where they were placed. Set only when a death in
     * lava or over the void relocated the body to a safe spot, so it can't fall
     * straight back into the hazard. A normal body falls (see
     * {@link #applyCorpseGravity()}) and settles on the ground where it fell.
     */
    private boolean pinned;
    /** Downward speed of the manual fall; reset to 0 once the body lands. */
    private double fallVelocity;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        // Vanilla LivingEntity physics never moves the body (setNoGravity); the
        // fall is driven manually in tick() so a body reliably drops and comes
        // to rest on the ground instead of hanging in the air. Hazard deaths are
        // pinned and don't fall (see pin()).
        this.setNoGravity(true);
    }

    /** Relocated out of a hazard: hold the body in place so it can't fall back in. */
    public void pin() {
        this.pinned = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    // --- setup / population -------------------------------------------------

    public void setOwner(GameProfile profile) {
        this.ownerUuid = profile.id();
        this.ownerName = profile.name() == null ? "" : profile.name();
        this.entityData.set(DATA_OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        this.entityData.set(DATA_OWNER_NAME, ownerName);
        // No floating nameplate — the owner's name is shown in the GUI title
        // instead (a tag at the feet of the lying body looks out of place).
    }

    /**
     * Fill the corpse from an index-aligned snapshot of the dead player's
     * inventory (index i here == inventory slot i), plus experience.
     */
    public void fill(NonNullList<ItemStack> byIndex, int experience) {
        for (int i = 0; i < SLOTS && i < byIndex.size(); i++) {
            // Copy, so later clearing of the player's inventory can never
            // reach back into what the corpse is holding.
            contents.setItem(i, byIndex.get(i).copy());
        }
        this.storedXp = CorpseConfig.get().keepExperience ? experience : 0;
        if (this.tickCount == 0) {
            setHealth(getMaxHealth());
        }
        this.setNoGravity(true);
        this.setPose(Pose.SLEEPING); // makes the vanilla renderer lay the body flat
        this.setBoundingBox(this.makeBoundingBox(this.position()));
    }

    // --- data / persistence -------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_NAME, "");
        builder.define(DATA_OWNER_UUID, "");
        builder.define(DATA_SKELETON, false);
    }

    /** True once the corpse has aged into a skeleton (client-visible). */
    public boolean isSkeleton() {
        return entityData.get(DATA_SKELETON);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (ownerUuid != null) {
            out.store("OwnerUuid", UUIDUtil.CODEC, ownerUuid);
        }
        out.putString("OwnerName", ownerName);
        out.putInt("StoredXp", storedXp);
        out.putLong("Age", age);
        out.putBoolean("Pinned", pinned);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) {
            list.set(i, contents.getItem(i));
        }
        ContainerHelper.saveAllItems(out.child("Contents"), list);
        if (!extras.isEmpty()) {
            out.store("Extras", io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.Group.CODEC.listOf(), extras);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        ownerUuid = in.read("OwnerUuid", UUIDUtil.CODEC).orElse(null);
        ownerName = in.getStringOr("OwnerName", "");
        storedXp = in.getIntOr("StoredXp", 0);
        age = in.getLongOr("Age", 0L);
        pinned = in.getBooleanOr("Pinned", false);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        in.child("Contents").ifPresent(child -> ContainerHelper.loadAllItems(child, list));
        for (int i = 0; i < SLOTS; i++) {
            contents.setItem(i, list.get(i));
        }
        extras = new java.util.ArrayList<>(in.read("Extras", io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.Group.CODEC.listOf()).orElse(java.util.List.of()));
        refreshExtraDisplay();
        this.entityData.set(DATA_OWNER_NAME, ownerName);
        this.entityData.set(DATA_OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        this.setNoGravity(true); // vanilla physics stays off; the manual fall handles a normal body
        this.setPose(Pose.SLEEPING);
    }

    /** Store items captured from addon slots (backpacks, curios) alongside the inventory. */
    public void setExtras(java.util.List<io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.Group> groups) {
        this.extras = new java.util.ArrayList<>(groups);
        refreshExtraDisplay();
    }

    /**
     * Server-authoritative reclaim of one stored item (backpack/curio) shown in
     * {@code displaySlot}: hand it to the looter, drop it from storage, and
     * reveal the next stored item in the freed slot.
     *
     * <p>This is driven by a packet rather than a slot pickup on purpose. Letting
     * the vanilla click machinery take from these slots meant mutating the
     * container mid-click, which desynced the client's click prediction from the
     * server and duplicated the item. Doing the whole thing here, server-side,
     * with the result synced normally, closes that hole.
     */
    public void reclaimOneExtra(Player player, int displaySlot) {
        if (displaySlot < 0 || displaySlot >= extraDisplay.getContainerSize()) {
            return;
        }
        ItemStack stack = extraDisplay.getItem(displaySlot).copy();
        if (stack.isEmpty()) {
            return;
        }
        // Only hand it over if it actually fits. If the inventory is full, take
        // nothing and leave it stored — never drop it (a body over lava/void must
        // not spill the item). Addon items are unstackable, so add() places the
        // whole thing or none of it.
        player.getInventory().add(stack); // mutates to whatever didn't fit
        if (!stack.isEmpty()) {
            return; // no room — keep it in the body
        }
        removeExtraAt(displaySlot); // it was taken — drop it from storage
        refreshExtraDisplay();      // pull the next stored item up into the slot
    }

    /** Remove the {@code flatIndex}-th non-empty stored stack, tidying empty groups. */
    private void removeExtraAt(int flatIndex) {
        int idx = 0;
        for (int g = 0; g < extras.size(); g++) {
            var group = extras.get(g);
            java.util.List<ItemStack> stacks = new java.util.ArrayList<>(group.stacks());
            for (int s = 0; s < stacks.size(); s++) {
                if (stacks.get(s).isEmpty()) {
                    continue;
                }
                if (idx == flatIndex) {
                    stacks.remove(s);
                    if (stacks.isEmpty()) {
                        extras.remove(g);
                    } else {
                        extras.set(g, new io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.Group(
                                group.providerId(), stacks));
                    }
                    return;
                }
                idx++;
            }
        }
    }

    /** Repaint the display slots from the current {@link #extras}. */
    private void refreshExtraDisplay() {
        for (int i = 0; i < extraDisplay.getContainerSize(); i++) {
            extraDisplay.setItem(i, ItemStack.EMPTY);
        }
        int slot = 0;
        for (var group : extras) {
            for (ItemStack stack : group.stacks()) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (slot >= extraDisplay.getContainerSize()) {
                    return; // more stored than the screen pictures — still stored, just not shown
                }
                extraDisplay.setItem(slot++, stack.copy());
            }
        }
    }

    /** Total stored addon items (backpacks/curios), including any not currently shown in a slot. */
    public int totalStoredExtras() {
        int count = 0;
        for (var group : extras) {
            for (ItemStack stack : group.stacks()) {
                if (!stack.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean hasExtras() {
        for (var group : extras) {
            for (ItemStack stack : group.stacks()) {
                if (!stack.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Hand addon items back to their providers; anything refused goes to the player. */
    private void restoreExtrasTo(Player player) {
        if (extras.isEmpty()) {
            return;
        }
        // Providers place back what they can (e.g. re-equip a backpack); whatever
        // they return, try to slot into the inventory WITHOUT ever dropping, and
        // keep anything that still doesn't fit stored in the body so it stays
        // until the player has room.
        java.util.List<ItemStack> leftovers =
                io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.restoreAll(player, extras);
        java.util.List<ItemStack> kept = new java.util.ArrayList<>();
        for (ItemStack stack : leftovers) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            player.getInventory().add(stack); // mutates to whatever didn't fit
            if (!stack.isEmpty()) {
                kept.add(stack); // keep it in the body; never drop
            }
        }
        extras = kept.isEmpty()
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(java.util.List.of(new FallenApi.Group(KEPT_EXTRAS, kept)));
        refreshExtraDisplay();
    }

    /** Lay the body flat facing the direction the player was looking (used by the sleeping renderer). */
    @Override
    public Direction getBedOrientation() {
        return Direction.fromYRot(this.getYRot());
    }

    /**
     * A hitbox that hugs the whole lying body, not just its origin at the feet.
     * The body extends along one cardinal axis (its facing); the box is made
     * long on that axis and narrow across it, so the entire corpse is clickable
     * regardless of which end the head is on.
     */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        // The sleeping body renders entirely on the +facing side of this
        // position (facing points at the head). From top-down F3+B captures the
        // feet sit ~0.3 ahead and the head-top ~1.55 ahead — so BOTH box edges
        // are ahead of the position; starting the box at the position itself
        // just trails empty space behind the feet.
        Direction facing = getBedOrientation();
        double nearFeet = 0.30; // feet-end edge, this far ahead of the position
        double farHead = 1.58;  // head-end edge, this far ahead of the position
        double halfWidth = 0.34;
        double height = 0.45;

        double fx = facing.getStepX();
        double fz = facing.getStepZ();
        double x1 = pos.x + nearFeet * fx;
        double z1 = pos.z + nearFeet * fz;
        double x2 = pos.x + farHead * fx;
        double z2 = pos.z + farHead * fz;

        return new AABB(
                Math.min(x1, x2) - halfWidth, pos.y - 0.05, Math.min(z1, z2) - halfWidth,
                Math.max(x1, x2) + halfWidth, pos.y + height, Math.max(z1, z2) + halfWidth);
    }

    // --- client accessors ---------------------------------------------------

    public String getOwnerName() {
        return entityData.get(DATA_OWNER_NAME);
    }

    public UUID getOwnerProfileId() {
        String raw = entityData.get(DATA_OWNER_UUID);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // --- interaction --------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (!canLoot(server, player)) {
            Component message = Component.literal("This corpse still belongs to " + displayOwner() + ".");
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(message, true);
            } else {
                player.sendSystemMessage(message);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            transferAllTo(player);
        } else {
            openMenu(player);
        }
        return InteractionResult.SUCCESS;
    }

    private boolean canLoot(ServerLevel server, Player player) {
        CorpseConfig cfg = CorpseConfig.get();
        if (ownerUuid == null || player.getUUID().equals(ownerUuid)) {
            return true;
        }
        if (cfg.opsBypassProtection
                && server.getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) {
            return true;
        }
        // Locked to the owner until the body ages into a skeleton — then anyone may loot it.
        // With skeletonStageIsPublic off (or no skeleton timer) it stays the owner's for good.
        if (cfg.skeletonStageIsPublic && cfg.skeletonTicks() > 0 && age >= cfg.skeletonTicks()) {
            return true;
        }
        return false;
    }

    /** Open this corpse's screen for a player (also used for remote recovery). */
    public void openMenuFor(Player player) {
        openMenu(player);
    }

    private void openMenu(Player player) {
        awardExperience(player);
        refreshExtraDisplay(); // make sure the stored-equipment slots are current
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new CorpseMenu(id, inv, contents, extraDisplay, this),
                Component.literal("Corpse of " + displayOwner())));
    }

    /** Hand every stored item back to its original inventory slot; overflow drops. */
    public void transferAllTo(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = contents.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (i < inv.getContainerSize() && inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack); // original slot free — put it right back
                contents.setItem(i, ItemStack.EMPTY);
            } else {
                inv.add(stack); // mutates to whatever didn't fit
                // Keep the remainder in the body rather than dropping it, so a
                // full inventory never spills loot (into lava/void, say).
                contents.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }
        restoreExtrasTo(player); // backpacks/curios go back to their own slots
        awardExperience(player);
    }

    /** Hand addon items back — used by the corpse screen's Transfer button. */
    public void returnExtrasTo(Player player) {
        restoreExtrasTo(player);
    }

    private void awardExperience(Player player) {
        if (storedXp > 0 && CorpseConfig.get().keepExperience) {
            player.giveExperiencePoints(storedXp);
            storedXp = 0;
        }
    }

    private String displayOwner() {
        return ownerName == null || ownerName.isEmpty() ? "Someone" : ownerName;
    }

    // --- lifecycle ----------------------------------------------------------

    /**
     * Drives the body's fall by hand so it reliably drops to the ground.
     * Vanilla {@link LivingEntity} gravity is disabled ({@code setNoGravity}),
     * so this is the only thing that moves a normal body — a sleeping-pose
     * LivingEntity doesn't fall on its own. When it lands it snaps onto the real
     * block surface below and pins there (see {@link #settleOnSurface()}), so it
     * always rests cleanly on top and never ends up sunk into the floor. Pinned
     * bodies (relocated out of lava/void, or already settled) don't fall.
     */
    private void applyCorpseGravity() {
        if (pinned) {
            fallVelocity = 0.0;
            return;
        }
        // If the body is in lava or water, float it on the surface instead of
        // sinking through to somewhere unreachable.
        if (inFluid()) {
            floatOnFluid();
            return;
        }
        if (onGround()) {
            settleOnSurface();
            return;
        }
        double before = getY();
        fallVelocity = Math.min(fallVelocity + 0.08, 3.0); // accelerate toward a terminal speed
        move(MoverType.SELF, new Vec3(0.0, -fallVelocity, 0.0));
        if (inFluid()) {
            floatOnFluid(); // fell into lava/water — surface it
            return;
        }
        if (onGround() || getY() >= before) {
            settleOnSurface(); // landed, or couldn't descend any further
        }
    }

    /**
     * Place the body exactly on the surface it came to rest on and hold it there,
     * so it always sits on top of the floor rather than clipping into it, then
     * pins it so it never drifts or re-sinks. If the resting spot turns out to be
     * submerged, it floats on the fluid instead.
     */
    private void settleOnSurface() {
        if (inFluid()) {
            floatOnFluid();
            return;
        }
        fallVelocity = 0.0;
        setPos(getX(), surfaceBelow(), getZ());
        pin();
    }

    /** True when the body's feet (or head) are inside lava or water. */
    private boolean inFluid() {
        BlockPos p = blockPosition();
        return !level().getFluidState(p).isEmpty() || !level().getFluidState(p.above()).isEmpty();
    }

    /**
     * Float the body on the surface of the fluid it's in — rise to the top of the
     * fluid column and rest on it — then pin it so it stays reachable on top of
     * the lava/water rather than sinking into it.
     */
    private void floatOnFluid() {
        fallVelocity = 0.0;
        setPos(getX(), fluidSurfaceY(level(), Mth.floor(getX()), Mth.floor(getZ()), Mth.floor(getY())), getZ());
        pin();
    }

    /**
     * The Y of the top surface of the fluid column at (x, z): starting from
     * {@code startY}, rise to the highest connected fluid block and return its
     * surface. Used so a body in lava/water rests on the surface no matter how
     * deep it started — never anchored below the surface.
     */
    private static double fluidSurfaceY(Level level, int x, int z, int startY) {
        int top = startY;
        for (int cy = startY; cy <= level.getMaxY(); cy++) {
            if (level.getFluidState(new BlockPos(x, cy, z)).isEmpty()) {
                break;
            }
            top = cy;
        }
        BlockPos topPos = new BlockPos(x, top, z);
        return top + level.getFluidState(topPos).getHeight(level, topPos);
    }

    /**
     * The top surface Y of the first solid block at or below the body's column.
     * Shape-aware, so a body resting on a slab, snow layer or path reads the real
     * top of that block, not the full-block height above it.
     */
    private double surfaceBelow() {
        int x = Mth.floor(getX());
        int z = Mth.floor(getZ());
        int minY = level().getMinY();
        for (int y = Mth.floor(getY() + 0.5); y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            VoxelShape shape = level().getBlockState(pos).getCollisionShape(level(), pos);
            if (!shape.isEmpty()) {
                return y + shape.max(Direction.Axis.Y);
            }
        }
        return getY();
    }

    /**
     * Where a fresh body should come to rest, scanning straight down from the
     * death spot. The first thing it meets decides it: a fluid means float on the
     * surface (held in place); solid ground means fall to it normally; nothing but
     * air to the bottom of the world (the void) means hold it just inside. This is
     * what stops a body dying over lava/water/void from sinking somewhere it can't
     * be reached.
     */
    public static RestSpot computeRestSpot(Level level, BlockPos death) {
        int x = death.getX();
        int z = death.getZ();
        int minY = level.getMinY();
        for (int y = death.getY(); y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty()) {
                // Rise to the top of the fluid column, so dying deep in lava/water
                // still floats the body on the surface rather than under it.
                return new RestSpot(fluidSurfaceY(level, x, z, y), true);
            }
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return new RestSpot(death.getY(), false); // solid below — free-fall onto it
            }
        }
        return new RestSpot(minY + 1, true); // open void — hold it just inside the world
    }

    /** A body's initial resting Y and whether it should be held there (pinned) rather than fall. */
    public record RestSpot(double y, boolean pin) {}

    @Override
    public void tick() {
        super.tick();
        // Keep the body-length hitbox aligned with the (synced) facing on both sides.
        this.setBoundingBox(this.makeBoundingBox(this.position()));
        if (level().isClientSide()) {
            return;
        }
        if (getPose() != Pose.SLEEPING) {
            setPose(Pose.SLEEPING);
        }
        applyCorpseGravity();
        age++;
        // Age into a skeleton after the configured time (cosmetic + unlocks looting).
        long skeletonTicks = CorpseConfig.get().skeletonTicks();
        boolean skeleton = skeletonTicks > 0 && age >= skeletonTicks;
        if (entityData.get(DATA_SKELETON) != skeleton) {
            entityData.set(DATA_SKELETON, skeleton);
        }
        // Safety net: a body that somehow falls out of the bottom of the world
        // (open void) is pinned just inside the floor so its loot stays in the
        // body and reachable, rather than being dropped into the void.
        if (getY() < level().getMinY()) {
            setDeltaMovement(0.0, 0.0, 0.0);
            setPos(getX(), level().getMinY() + 1, getZ());
            pin();
            return;
        }
        long despawn = CorpseConfig.get().despawnTicks();
        if (despawn > 0 && age >= despawn) {
            dropEverythingAndDiscard();
            return;
        }
        if (isLootEmpty() && storedXp <= 0) {
            markGoneInHistory();
            discard();
        }
    }

    /** Flag this corpse as gone in the death history (the record itself is kept). */
    private void markGoneInHistory() {
        if (ownerUuid != null && level() instanceof ServerLevel server && server.getServer() != null) {
            DeathHistoryData.get(server.getServer()).markCorpseGone(ownerUuid, this.getUUID());
        }
    }

    private boolean isLootEmpty() {
        for (int i = 0; i < SLOTS; i++) {
            if (!contents.getItem(i).isEmpty()) {
                return false;
            }
        }
        return !hasExtras(); // a body still holding a backpack isn't empty
    }

    private void dropEverythingAndDiscard() {
        if (level() instanceof ServerLevel server) {
            Containers.dropContents(server, blockPosition(), contents);
            // Addon items (backpacks etc.) drop too — never silently deleted.
            for (var group : extras) {
                for (ItemStack stack : group.stacks()) {
                    if (!stack.isEmpty()) {
                        Containers.dropContents(server, blockPosition(), new SimpleContainer(stack.copy()));
                    }
                }
            }
            extras = new java.util.ArrayList<>();
            refreshExtraDisplay();
            if (storedXp > 0 && CorpseConfig.get().keepExperience) {
                net.minecraft.world.entity.ExperienceOrb.award(server, position(), storedXp);
            }
        }
        markGoneInHistory();
        discard();
    }

    // --- static-body behaviour ---------------------------------------------

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // A body doesn't shove you around — without this the large hitbox nudges
        // players standing near it when they try to open it.
    }

    @Override
    public boolean fireImmune() {
        return true; // can't burn in lava
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // invulnerable — attacking the body must never destroy the loot
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }
}
