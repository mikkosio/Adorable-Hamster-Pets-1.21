package net.dawson.adorablehamsterpets.mixin.server;

import net.dawson.adorablehamsterpets.AdorableHamsterPets;
import net.dawson.adorablehamsterpets.attachment.HamsterShoulderData;
import net.dawson.adorablehamsterpets.attachment.ModEntityAttachments;
import net.dawson.adorablehamsterpets.config.ModConfig;
import net.dawson.adorablehamsterpets.entity.custom.HamsterEntity;
import net.dawson.adorablehamsterpets.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity {

    // --- 1. Fields (Constants and State Trackers) ---
    @Unique
    private static final int CHECK_INTERVAL_TICKS = 20;

    @Unique
    private int adorablehamsterpets$diamondCheckTimer = 0;
    @Unique
    private int adorablehamsterpets$creeperCheckTimer = 0;

    @Unique
    private int adorablehamsterpets$diamondSoundCooldownTicks = 0;
    @Unique
    private int adorablehamsterpets$creeperSoundCooldownTicks = 0;

    // --- New fields for dismount messages ---
    @Unique
    private static final List<String> DISMOUNT_MESSAGE_KEYS = Arrays.asList(
            "message.adorablehamsterpets.dismount.1",
            "message.adorablehamsterpets.dismount.2",
            "message.adorablehamsterpets.dismount.3",
            "message.adorablehamsterpets.dismount.4",
            "message.adorablehamsterpets.dismount.5",
            "message.adorablehamsterpets.dismount.6"
            // Add more keys here if you add more messages in en_us.json
    );
    @Unique
    private String adorablehamsterpets$lastDismountMessageKey = "";
    // --- End new fields ---
    // --- End 1. Fields ---

    // --- 2. Constructor ---
    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }
    // --- End 2. Constructor ---

    // --- 3. Injected Methods ---
    @Inject(method = "tick", at = @At("TAIL"))
    private void adorablehamsterpets$onTick(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        World world = self.getWorld();
        if (world.isClient) {
            return;
        }
        Random random = world.getRandom();
        final ModConfig config = AdorableHamsterPets.CONFIG;

        if (adorablehamsterpets$diamondSoundCooldownTicks > 0) adorablehamsterpets$diamondSoundCooldownTicks--;
        if (adorablehamsterpets$creeperSoundCooldownTicks > 0) adorablehamsterpets$creeperSoundCooldownTicks--;

        HamsterShoulderData shoulderData = self.getAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA);
        if (shoulderData != null) {
            if (self.isSneaking()) {
                AdorableHamsterPets.LOGGER.debug("[HamsterDismount] Player {} is sneaking. Dismounting hamster.", self.getName().getString());
                HamsterEntity.spawnFromShoulderData((ServerWorld) world, self, shoulderData);
                self.removeAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA);

                // --- Send Dismount Message ---
                // Corrected config access:
                if (AdorableHamsterPets.CONFIG.uiTweaks.enableShoulderDismountMessages() && !DISMOUNT_MESSAGE_KEYS.isEmpty()) {
                    String chosenKey;
                    if (DISMOUNT_MESSAGE_KEYS.size() == 1) {
                        chosenKey = DISMOUNT_MESSAGE_KEYS.get(0);
                    } else {
                        List<String> availableKeys = new ArrayList<>(DISMOUNT_MESSAGE_KEYS);
                        availableKeys.remove(this.adorablehamsterpets$lastDismountMessageKey);
                        if (availableKeys.isEmpty()) {
                            chosenKey = this.adorablehamsterpets$lastDismountMessageKey; // Fallback if all were used once
                        } else {
                            chosenKey = availableKeys.get(random.nextInt(availableKeys.size()));
                        }
                    }
                    self.sendMessage(Text.translatable(chosenKey), true);
                    this.adorablehamsterpets$lastDismountMessageKey = chosenKey;
                    // TODO: Play random HAMSTER_DISMOUNT_SOUND here
                }
                // --- End Send Dismount Message ---

                adorablehamsterpets$diamondCheckTimer = 0;
                adorablehamsterpets$creeperCheckTimer = 0;
                adorablehamsterpets$diamondSoundCooldownTicks = 0;
                adorablehamsterpets$creeperSoundCooldownTicks = 0;
                return;
            }

            // Shoulder Detection Features (Diamond/Creeper)
            if (config.features.enableShoulderDiamondDetection()) {
                adorablehamsterpets$diamondCheckTimer++;
                if (adorablehamsterpets$diamondCheckTimer >= CHECK_INTERVAL_TICKS) {
                    adorablehamsterpets$diamondCheckTimer = 0;
                    if (isDiamondNearby(self, config.features.shoulderDiamondDetectionRadius())) {
                        if (adorablehamsterpets$diamondSoundCooldownTicks == 0) {
                            world.playSound(null, self.getBlockPos(),
                                    ModSounds.getRandomSoundFrom(ModSounds.HAMSTER_DIAMOND_SNIFF_SOUNDS, random),
                                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
                            self.sendMessage(Text.translatable("message.adorablehamsterpets.diamond_nearby").formatted(Formatting.AQUA), true);
                            adorablehamsterpets$diamondSoundCooldownTicks = random.nextBetween(140, 200);
                        }
                    }
                }
            }

            if (config.features.enableShoulderCreeperDetection()) {
                adorablehamsterpets$creeperCheckTimer++;
                if (adorablehamsterpets$creeperCheckTimer >= CHECK_INTERVAL_TICKS) {
                    adorablehamsterpets$creeperCheckTimer = 0;
                    if (creeperSeesPlayer(self, config.features.shoulderCreeperDetectionRadius())) {
                        if (adorablehamsterpets$creeperSoundCooldownTicks == 0) {
                            world.playSound(null, self.getBlockPos(),
                                    ModSounds.getRandomSoundFrom(ModSounds.HAMSTER_CREEPER_DETECT_SOUNDS, random),
                                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
                            self.sendMessage(Text.translatable("message.adorablehamsterpets.creeper_detected").formatted(Formatting.RED), true);
                            adorablehamsterpets$creeperSoundCooldownTicks = random.nextBetween(100, 160);
                        }
                    }
                }
            }
        }
    }
    // --- End 3. Injected Methods ---

    // --- 4. Unique Helper Methods (Unchanged) ---
    @Unique
    private boolean isDiamondNearby(PlayerEntity player, double radius) {
        // ... (existing code)
        World world = player.getWorld();
        BlockPos center = player.getBlockPos();
        int intRadius = (int) Math.ceil(radius);
        for (BlockPos checkPos : BlockPos.iterate(center.add(-intRadius, -intRadius, -intRadius), center.add(intRadius, intRadius, intRadius))) {
            if (checkPos.getSquaredDistance(center) <= radius * radius) {
                BlockState state = world.getBlockState(checkPos);
                if (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private boolean creeperSeesPlayer(PlayerEntity player, double radius) {
        // ... (existing code)
        World world = player.getWorld();
        Box searchBox = new Box(player.getPos().subtract(radius, radius, radius), player.getPos().add(radius, radius, radius));
        List<CreeperEntity> nearbyCreepers = world.getEntitiesByClass(
                CreeperEntity.class,
                searchBox,
                creeper -> creeper.isAlive() && creeper.getTarget() == player && EntityPredicates.VALID_ENTITY.test(creeper)
        );
        return !nearbyCreepers.isEmpty();
    }
    // --- End 4. Unique Helper Methods ---
}