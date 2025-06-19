package net.dawson.adorablehamsterpets.entity.custom;

import net.dawson.adorablehamsterpets.AdorableHamsterPets;
import net.dawson.adorablehamsterpets.advancement.criterion.ModCriteria;
import net.dawson.adorablehamsterpets.attachment.HamsterRenderState;
import net.dawson.adorablehamsterpets.attachment.HamsterShoulderData;
import net.dawson.adorablehamsterpets.attachment.ModEntityAttachments;
import net.dawson.adorablehamsterpets.client.sound.HamsterCleaningSoundInstance;
import net.dawson.adorablehamsterpets.config.AhpConfig;
import net.dawson.adorablehamsterpets.config.Configs;
import net.dawson.adorablehamsterpets.entity.AI.*;
import net.dawson.adorablehamsterpets.entity.ImplementedInventory;
import net.dawson.adorablehamsterpets.entity.ModEntities;
import net.dawson.adorablehamsterpets.item.ModItems;
import net.dawson.adorablehamsterpets.networking.payload.StartHamsterFlightSoundPayload;
import net.dawson.adorablehamsterpets.networking.payload.StartHamsterThrowSoundPayload;
import net.dawson.adorablehamsterpets.screen.HamsterEntityScreenHandlerFactory;
import net.dawson.adorablehamsterpets.sound.ModSounds;
import net.dawson.adorablehamsterpets.tag.ModItemTags;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.EntityEffectParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import net.minecraft.client.MinecraftClient;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.client.sound.PositionedSoundInstance;
import java.util.*;
import static net.dawson.adorablehamsterpets.sound.ModSounds.HAMSTER_CELEBRATE_SOUNDS;
import static net.dawson.adorablehamsterpets.sound.ModSounds.getRandomSoundFrom;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

public class HamsterEntity extends TameableEntity implements GeoEntity, ImplementedInventory {


    /* ──────────────────────────────────────────────────────────────────────────────
     *                    1. Constants and Static Utilities
     * ────────────────────────────────────────────────────────────────────────────*/

    // --- Constants ---
    private static final int INVENTORY_SIZE = 6;
    private static final int REFUSE_FOOD_TIMER_TICKS = 40;            // 2 seconds
    private static final int CUSTOM_LOVE_TICKS = 600;                 // 30 seconds
    private static final float THROW_DAMAGE = 20.0f;
    private static final double THROWN_GRAVITY = -0.05;
    private static final double HAMSTER_ATTACK_BOX_EXPANSION = 0.65D;  // Expand by 0.5 blocks horizontally (vanilla is 0.83 blocks, so really this is shrinking it)
    public enum DozingPhase {
        NONE,                  // Not in any part of the sleep sequence
        QUIESCENT_SITTING,     // Tamed, sitting by command, waiting for drowsiness timer
        DRIFTING_OFF,          // Playing the 90sec anim_hamster_drifting_off animation
        SETTLING_INTO_SLUMBER, // Playing a short anim_hamster_settle_sleepX transition
        DEEP_SLEEP             // Looping one of the anim_hamster_sleep_poseX animations
    }
    public static final int CELEBRATION_PARTICLE_DURATION_TICKS = 600;    // 3 seconds
    private static final float DEFAULT_FOOTSTEP_VOLUME = 0.10F;
    private static final float GRAVEL_VOLUME_MODIFIER = 0.60F;

    // --- Item Restriction Sets ---
    private static final Set<TagKey<Item>> DISALLOWED_ITEM_TAGS = Set.of(
            // --- Tool Tags ---
            net.minecraft.registry.tag.ItemTags.AXES,
            net.minecraft.registry.tag.ItemTags.HOES,
            net.minecraft.registry.tag.ItemTags.PICKAXES,
            net.minecraft.registry.tag.ItemTags.SHOVELS,
            net.minecraft.registry.tag.ItemTags.SWORDS,
            // Armor
            net.minecraft.registry.tag.ItemTags.TRIMMABLE_ARMOR,
            // Large Blocks/Structures
            net.minecraft.registry.tag.ItemTags.BEDS,
            net.minecraft.registry.tag.ItemTags.BANNERS,
            net.minecraft.registry.tag.ItemTags.DOORS,
            // Vehicles
            net.minecraft.registry.tag.ItemTags.BOATS, // Covers Boats & Chest Boats
            net.minecraft.registry.tag.ItemTags.CREEPER_DROP_MUSIC_DISCS

    );

    private static final Set<Item> DISALLOWED_ITEMS = Set.of(
            // Specific Tools/Weapons not covered by tags
            Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.FISHING_ROD,
            // Specific Armor/Wearables
            Items.SHIELD, Items.ELYTRA,
            Items.TURTLE_HELMET,
            Items.CARVED_PUMPKIN,
            Items.PLAYER_HEAD, Items.ZOMBIE_HEAD, Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL, Items.CREEPER_HEAD, Items.DRAGON_HEAD, Items.PIGLIN_HEAD,
            // Vehicles/Mounts
            Items.MINECART, Items.CHEST_MINECART, Items.FURNACE_MINECART, Items.TNT_MINECART, Items.HOPPER_MINECART, Items.COMMAND_BLOCK_MINECART,
            Items.SADDLE,
            // Buckets
            Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.MILK_BUCKET, Items.POWDER_SNOW_BUCKET,
            Items.AXOLOTL_BUCKET, Items.TADPOLE_BUCKET, Items.COD_BUCKET, Items.PUFFERFISH_BUCKET, Items.SALMON_BUCKET, Items.TROPICAL_FISH_BUCKET,
            // Complex/Utility/Special
            Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME,
            Items.PAINTING,
            Items.ARMOR_STAND,
            Items.END_CRYSTAL,
            Items.SPYGLASS,
            Items.NETHER_STAR, Items.DRAGON_EGG,
            Items.BUNDLE,
            // Mod Items
            ModItems.HAMSTER_GUIDE_BOOK
    );
    // --- End Item Restriction Sets ---

    // Define food sets as static final fields
    private static final Set<Item> HAMSTER_FOODS = new HashSet<>(Arrays.asList(
            ModItems.HAMSTER_FOOD_MIX, ModItems.SUNFLOWER_SEEDS, ModItems.GREEN_BEANS,
            ModItems.CUCUMBER, ModItems.GREEN_BEAN_SEEDS, ModItems.CUCUMBER_SEEDS,
            Items.APPLE, Items.CARROT, Items.MELON_SLICE, Items.SWEET_BERRIES,
            Items.BEETROOT, Items.WHEAT, Items.WHEAT_SEEDS
    ));
    private static final Set<Item> REPEATABLE_FOODS = new HashSet<>(Arrays.asList(
            ModItems.HAMSTER_FOOD_MIX, ModItems.STEAMED_GREEN_BEANS
    ));

    // --- Auto-Feedable Healing Foods ---
    private static final Set<Item> AUTO_HEAL_FOODS = new HashSet<>(List.of(
            ModItems.HAMSTER_FOOD_MIX // Only allow Hamster Food Mix
    ));
    // --- End Auto-Feedable Healing Foods ---

    // --- Variant Pool Definitions ---
    private static final List<HamsterVariant> ORANGE_VARIANTS = List.of(
            HamsterVariant.ORANGE, HamsterVariant.ORANGE_OVERLAY1, HamsterVariant.ORANGE_OVERLAY2,
            HamsterVariant.ORANGE_OVERLAY3, HamsterVariant.ORANGE_OVERLAY4, HamsterVariant.ORANGE_OVERLAY5,
            HamsterVariant.ORANGE_OVERLAY6, HamsterVariant.ORANGE_OVERLAY7, HamsterVariant.ORANGE_OVERLAY8
    );
    private static final List<HamsterVariant> BLACK_VARIANTS = List.of(
            HamsterVariant.BLACK, HamsterVariant.BLACK_OVERLAY1, HamsterVariant.BLACK_OVERLAY2,
            HamsterVariant.BLACK_OVERLAY3, HamsterVariant.BLACK_OVERLAY4, HamsterVariant.BLACK_OVERLAY5,
            HamsterVariant.BLACK_OVERLAY6, HamsterVariant.BLACK_OVERLAY7, HamsterVariant.BLACK_OVERLAY8
    );
    private static final List<HamsterVariant> BLUE_VARIANTS = List.of(
            HamsterVariant.BLUE, HamsterVariant.BLUE_OVERLAY1, HamsterVariant.BLUE_OVERLAY2,
            HamsterVariant.BLUE_OVERLAY3, HamsterVariant.BLUE_OVERLAY4, HamsterVariant.BLUE_OVERLAY5,
            HamsterVariant.BLUE_OVERLAY6, HamsterVariant.BLUE_OVERLAY7, HamsterVariant.BLUE_OVERLAY8
    );
    private static final List<HamsterVariant> CHOCOLATE_VARIANTS = List.of(
            HamsterVariant.CHOCOLATE, HamsterVariant.CHOCOLATE_OVERLAY1, HamsterVariant.CHOCOLATE_OVERLAY2,
            HamsterVariant.CHOCOLATE_OVERLAY3, HamsterVariant.CHOCOLATE_OVERLAY4, HamsterVariant.CHOCOLATE_OVERLAY5,
            HamsterVariant.CHOCOLATE_OVERLAY6, HamsterVariant.CHOCOLATE_OVERLAY7, HamsterVariant.CHOCOLATE_OVERLAY8
    );
    private static final List<HamsterVariant> CREAM_VARIANTS = List.of(
            HamsterVariant.CREAM, HamsterVariant.CREAM_OVERLAY1, HamsterVariant.CREAM_OVERLAY2,
            HamsterVariant.CREAM_OVERLAY3, HamsterVariant.CREAM_OVERLAY4, HamsterVariant.CREAM_OVERLAY5,
            HamsterVariant.CREAM_OVERLAY6, HamsterVariant.CREAM_OVERLAY7, HamsterVariant.CREAM_OVERLAY8
    );
    private static final List<HamsterVariant> DARK_GRAY_VARIANTS = List.of(
            HamsterVariant.DARK_GRAY, HamsterVariant.DARK_GRAY_OVERLAY1, HamsterVariant.DARK_GRAY_OVERLAY2,
            HamsterVariant.DARK_GRAY_OVERLAY3, HamsterVariant.DARK_GRAY_OVERLAY4, HamsterVariant.DARK_GRAY_OVERLAY5,
            HamsterVariant.DARK_GRAY_OVERLAY6, HamsterVariant.DARK_GRAY_OVERLAY7, HamsterVariant.DARK_GRAY_OVERLAY8
    );
    private static final List<HamsterVariant> LAVENDER_VARIANTS = List.of(
            HamsterVariant.LAVENDER, HamsterVariant.LAVENDER_OVERLAY1, HamsterVariant.LAVENDER_OVERLAY2,
            HamsterVariant.LAVENDER_OVERLAY3, HamsterVariant.LAVENDER_OVERLAY4, HamsterVariant.LAVENDER_OVERLAY5,
            HamsterVariant.LAVENDER_OVERLAY6, HamsterVariant.LAVENDER_OVERLAY7, HamsterVariant.LAVENDER_OVERLAY8
    );
    private static final List<HamsterVariant> LIGHT_GRAY_VARIANTS = List.of(
            HamsterVariant.LIGHT_GRAY, HamsterVariant.LIGHT_GRAY_OVERLAY1, HamsterVariant.LIGHT_GRAY_OVERLAY2,
            HamsterVariant.LIGHT_GRAY_OVERLAY3, HamsterVariant.LIGHT_GRAY_OVERLAY4, HamsterVariant.LIGHT_GRAY_OVERLAY5,
            HamsterVariant.LIGHT_GRAY_OVERLAY6, HamsterVariant.LIGHT_GRAY_OVERLAY7, HamsterVariant.LIGHT_GRAY_OVERLAY8
    );
    private static final List<HamsterVariant> WHITE_VARIANTS = List.of(HamsterVariant.WHITE); // White has no overlays

    // --- End Variant Pool Definitions ---

    // --- Hamster Spawning In Different Biomes ---
    // Helper to check if a biome key matches any key in a list
    private static boolean matchesAnyBiomeKey(RegistryEntry<Biome> biomeEntry, RegistryKey<Biome>... keysToMatch) {
        for (RegistryKey<Biome> key : keysToMatch) {
            if (biomeEntry.matchesKey(key)) {
                return true;
            }
        }
        return false;
    }

    // Biome Key Helper Methods
    private static boolean isSnowyBiome(RegistryEntry<Biome> biomeEntry) {
        return matchesAnyBiomeKey(biomeEntry,
                BiomeKeys.SNOWY_PLAINS, BiomeKeys.SNOWY_TAIGA, BiomeKeys.SNOWY_SLOPES,
                BiomeKeys.FROZEN_PEAKS, BiomeKeys.JAGGED_PEAKS, // ICE_SPIKES handled separately
                BiomeKeys.GROVE, BiomeKeys.FROZEN_RIVER, BiomeKeys.SNOWY_BEACH,
                BiomeKeys.FROZEN_OCEAN, BiomeKeys.DEEP_FROZEN_OCEAN
        );
    }
    private static boolean isIceSpikesBiome(RegistryEntry<Biome> biomeEntry) {
        return biomeEntry.matchesKey(BiomeKeys.ICE_SPIKES);
    }
    private static boolean isCherryGroveBiome(RegistryEntry<Biome> biomeEntry) {
        return biomeEntry.matchesKey(BiomeKeys.CHERRY_GROVE);
    }
    private static boolean isDesertBiome(RegistryEntry<Biome> biomeEntry) {
        // Deserts don't have a tag, check the specific key
        return biomeEntry.matchesKey(BiomeKeys.DESERT);
    }
    private static boolean isPlainsBiome(RegistryEntry<Biome> biomeEntry) {
        // Plains don't have a tag, check specific keys
        return matchesAnyBiomeKey(biomeEntry, BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.MEADOW);
        // Including Meadow here as it's plains-like
    }
    private static boolean isSwampBiome(RegistryEntry<Biome> biomeEntry) {
        // Swamps don't have a tag, check specific keys
        return matchesAnyBiomeKey(biomeEntry, BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP);
    }
    private static boolean isCaveBiome(RegistryEntry<Biome> biomeEntry) {
        // Check specific cave keys, now including Deep Dark
        return matchesAnyBiomeKey(biomeEntry, BiomeKeys.LUSH_CAVES, BiomeKeys.DRIPSTONE_CAVES, BiomeKeys.DEEP_DARK);
    }
    private static boolean isOldGrowthBirchForest(RegistryEntry<Biome> biomeEntry) {
        return biomeEntry.matchesKey(BiomeKeys.OLD_GROWTH_BIRCH_FOREST);
    }
    private static boolean isWindsweptOrStonyPeaks(RegistryEntry<Biome> biomeEntry) {
        return matchesAnyBiomeKey(biomeEntry,
                BiomeKeys.WINDSWEPT_HILLS,
                BiomeKeys.WINDSWEPT_GRAVELLY_HILLS,
                BiomeKeys.WINDSWEPT_FOREST,
                BiomeKeys.WINDSWEPT_SAVANNA,
                BiomeKeys.STONY_PEAKS
        );
    }
    private static boolean isJungleBiome(RegistryEntry<Biome> biomeEntry) {
        return matchesAnyBiomeKey(biomeEntry,
                BiomeKeys.JUNGLE,
                BiomeKeys.SPARSE_JUNGLE,
                BiomeKeys.BAMBOO_JUNGLE
        );
    }

    // Determine Which Hamster Variant for Each Biome
    private static HamsterVariant determineVariantForBiome(RegistryEntry<Biome> biomeEntry, net.minecraft.util.math.random.Random random) {
        String biomeKeyStr = biomeEntry.getKey().map(key -> key.getValue().toString()).orElse("UNKNOWN");
        AdorableHamsterPets.LOGGER.trace("[DetermineVariant] Checking biome: {}", biomeKeyStr);


        // --- 1. Specific Rare Biomes First ---
        if (isIceSpikesBiome(biomeEntry)) {
            AdorableHamsterPets.LOGGER.trace("  - Matched: Ice Spikes Key");
            // 30% chance for White, 70% chance for Blue (including overlays)
            if (random.nextInt(10) < 3) { // 0, 1, 2 (3 out of 10)
                AdorableHamsterPets.LOGGER.trace("    - Ice Spikes Roll: White");
                return getRandomVariant(WHITE_VARIANTS, random); // WHITE_VARIANTS only contains HamsterVariant.WHITE
            } else {
                AdorableHamsterPets.LOGGER.trace("    - Ice Spikes Roll: Blue");
                return getRandomVariant(BLUE_VARIANTS, random);
            }
        } else if (isCherryGroveBiome(biomeEntry)) {
            AdorableHamsterPets.LOGGER.trace("  - Matched: Cherry Grove Key");
            return getRandomVariant(LAVENDER_VARIANTS, random);
        }
        // --- End 1. Specific Rare Biomes First ---


        // --- 2. General Biome Categories ---
        else if (isSnowyBiome(biomeEntry)) { // Excludes Ice Spikes due to earlier check
            AdorableHamsterPets.LOGGER.trace("  - Matched: Snowy Keys (excluding Ice Spikes)");
            return getRandomVariant(WHITE_VARIANTS, random);
        } else if (isOldGrowthBirchForest(biomeEntry)) { // Specific check for Old Growth Birch
            AdorableHamsterPets.LOGGER.trace("  - Matched: Old Growth Birch Forest Key");
            return getRandomVariant(CREAM_VARIANTS, random);
        } else if (isCaveBiome(biomeEntry)) { // Now includes Deep Dark
            AdorableHamsterPets.LOGGER.trace("  - Matched: Cave Keys (Lush/Dripstone/DeepDark)");
            int chance = random.nextInt(4); // 0, 1, 2, 3
            AdorableHamsterPets.LOGGER.trace("  - Cave chance roll: {}", chance);
            if (chance < 2) { return getRandomVariant(BLACK_VARIANTS, random); }
            else if (chance == 2) { return getRandomVariant(DARK_GRAY_VARIANTS, random); }
            else { return getRandomVariant(LIGHT_GRAY_VARIANTS, random); }
        } else if (isSwampBiome(biomeEntry)) {
            AdorableHamsterPets.LOGGER.trace("  - Matched: Swamp Keys");
            return getRandomVariant(BLACK_VARIANTS, random);
        } else if (isDesertBiome(biomeEntry)) {
            AdorableHamsterPets.LOGGER.trace("  - Matched: Desert Key");
            return getRandomVariant(CREAM_VARIANTS, random);
        } else if (biomeEntry.isIn(BiomeTags.IS_BADLANDS)) {
            AdorableHamsterPets.LOGGER.trace("  - Matched: BiomeTags.IS_BADLANDS");
            return getRandomVariant(ORANGE_VARIANTS, random);
        } else if (biomeEntry.isIn(BiomeTags.IS_BEACH) && !isSnowyBiome(biomeEntry)) {
            AdorableHamsterPets.LOGGER.trace("  - Matched: BiomeTags.IS_BEACH (non-snowy)");
            return getRandomVariant(CREAM_VARIANTS, random);
        } else if ((biomeEntry.isIn(BiomeTags.IS_FOREST) || biomeEntry.isIn(BiomeTags.IS_TAIGA) || isJungleBiome(biomeEntry)) && !isSnowyBiome(biomeEntry) && !isCherryGroveBiome(biomeEntry) && !isOldGrowthBirchForest(biomeEntry)) {
            AdorableHamsterPets.LOGGER.trace("  - Matched: BiomeTags.IS_FOREST/IS_TAIGA/IS_JUNGLE (non-snowy, non-cherry, non-old-birch)");
            return getRandomVariant(CHOCOLATE_VARIANTS, random);
        } else if (biomeEntry.isIn(BiomeTags.IS_SAVANNA) || isPlainsBiome(biomeEntry)) {
            AdorableHamsterPets.LOGGER.trace("  - Matched: BiomeTags.IS_SAVANNA or Plains Keys");
            return getRandomVariant(ORANGE_VARIANTS, random);
        } else if ((biomeEntry.isIn(BiomeTags.IS_MOUNTAIN) || biomeEntry.matchesKey(BiomeKeys.STONY_SHORE) || isWindsweptOrStonyPeaks(biomeEntry)) && !isSnowyBiome(biomeEntry) && !isIceSpikesBiome(biomeEntry)) {
            AdorableHamsterPets.LOGGER.trace("  - Matched: BiomeTags.IS_MOUNTAIN or specific stony/windswept keys (non-snowy, non-ice-spikes)");
            boolean lightOrDarkGrayChance = random.nextBoolean();
            AdorableHamsterPets.LOGGER.trace("  - Mountain roll (true=DarkGray, false=LightGray): {}", lightOrDarkGrayChance);
            if (lightOrDarkGrayChance) { return getRandomVariant(DARK_GRAY_VARIANTS, random); }
            else { return getRandomVariant(LIGHT_GRAY_VARIANTS, random); }
        }
        // --- End 2. General Biome Categories ---


        // --- 3. Default Fallback ---
        else {
            AdorableHamsterPets.LOGGER.trace("  - No specific tags/keys matched. Using default ORANGE.");
            return getRandomVariant(ORANGE_VARIANTS, random);
        }
        // --- End 3. Default Fallback ---
    }
    // --- End Hamster Spawning In Different Biomes ---

    private static HamsterVariant getRandomVariant(List<HamsterVariant> variantPool, net.minecraft.util.math.random.Random random) {
        if (variantPool == null || variantPool.isEmpty()) {
            // Fallback
            return HamsterVariant.ORANGE;
        }
        return variantPool.get(random.nextInt(variantPool.size()));
    }

    // Heler Method for Choosing Baby Variant
    private static List<HamsterVariant> getPoolForBaseVariant(HamsterVariant baseVariant) {
        return switch (baseVariant) {
            case ORANGE -> ORANGE_VARIANTS;
            case BLACK -> BLACK_VARIANTS;
            case BLUE -> BLUE_VARIANTS;
            case CHOCOLATE -> CHOCOLATE_VARIANTS;
            case CREAM -> CREAM_VARIANTS;
            case DARK_GRAY -> DARK_GRAY_VARIANTS;
            case LAVENDER -> LAVENDER_VARIANTS;
            case LIGHT_GRAY -> LIGHT_GRAY_VARIANTS;
            case WHITE -> WHITE_VARIANTS;
            // Default case should not be reachable if baseVariant is always one of the above
            default -> ORANGE_VARIANTS; // Fallback
        };
    }

    /**
     * Creates the attribute container for the Hamster entity.
     * @return The attribute container builder.
     */
    public static DefaultAttributeContainer.Builder createHamsterAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, THROW_DAMAGE)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, Configs.AHP.meleeDamage.get())
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0D);
    }


    /**
     * Creates a HamsterEntity instance from shoulder data, loading variant, health, age, inventory, effects, and custom name.
     * Does NOT set position or spawn the entity.
     * @param world The server world.
     * @param player The player the hamster belongs to.
     * @param data The shoulder data.
     * @return The created HamsterEntity, or null if creation failed.
     */
    @Nullable
    public static HamsterEntity createFromShoulderData(ServerWorld world, PlayerEntity player, HamsterShoulderData data) {
        // --- 1. Create Hamster Instance ---
        AdorableHamsterPets.LOGGER.debug("[HamsterEntity] createFromShoulderData called for player {} with data: {}", player.getName().getString(), data);
        HamsterEntity hamster = ModEntities.HAMSTER.create(world);
        // --- End 1. Create Hamster Instance ---


        if (hamster != null) {
            // --- 2. Load Core Data ---
            hamster.setVariant(data.variantId());
            hamster.setHealth(data.health());
            hamster.setOwnerUuid(player.getUuid());
            hamster.setTamed(true, true);
            hamster.setBreedingAge(data.breedingAge());
            hamster.throwCooldownEndTick = data.throwCooldownEndTick();
            hamster.steamedBeansCooldownEndTick = data.steamedBeansCooldownEndTick();
            hamster.autoEatCooldownTicks = data.autoEatCooldownTicks();
            hamster.dataTracker.set(PINK_PETAL_TYPE, data.pinkPetalType());
            hamster.dataTracker.set(CHEEK_POUCH_UNLOCKED, data.cheekPouchUnlocked());
            hamster.dataTracker.set(ANIMATION_PERSONALITY_ID, data.animationPersonalityId());
            // --- End 2. Load Core Data ---


            // --- 2b. Load Custom Name ---
            data.customName().ifPresent(name -> {
                if (!name.isEmpty()) { // Check if the name string is actually present
                    hamster.setCustomName(Text.literal(name));
                    AdorableHamsterPets.LOGGER.debug("[HamsterEntity] Restored custom name '{}' from shoulder data.", name);
                } else {
                    AdorableHamsterPets.LOGGER.debug("[HamsterEntity] Custom name in shoulder data was empty, not setting name.");
                }
            });
            // --- End 2b. Load Custom Name ---


            // --- 3. Load Inventory ---
            NbtCompound inventoryNbt = data.inventoryNbt();
            RegistryWrapper.WrapperLookup registries = world.getRegistryManager();
            if (!inventoryNbt.isEmpty()) {
                Inventories.readNbt(inventoryNbt, hamster.items, registries);
                AdorableHamsterPets.LOGGER.debug("[HamsterEntity] Loaded inventory from NBT: {}", inventoryNbt);
                hamster.updateCheekTrackers();
            } else {
                AdorableHamsterPets.LOGGER.debug("[HamsterEntity] No inventory NBT found in shoulder data, clearing inventory.");
                hamster.items.clear();
                hamster.updateCheekTrackers();
            }
            // --- End 3. Load Inventory ---


            // --- 4. Load and Apply Active Status Effects ---
            NbtList effectsList = data.activeEffectsNbt();
            if (!effectsList.isEmpty()) {
                AdorableHamsterPets.LOGGER.debug("[HamsterEntity] Loading {} active effects from NBT list.", effectsList.size());
                for (NbtElement effectElement : effectsList) {
                    if (effectElement instanceof NbtCompound effectNbt) {
                        StatusEffectInstance effectInstance = StatusEffectInstance.fromNbt(effectNbt);
                        if (effectInstance != null) {
                            hamster.addStatusEffect(effectInstance);
                        } else {
                            AdorableHamsterPets.LOGGER.warn("[HamsterEntity] Failed to deserialize StatusEffectInstance from NBT: {}", effectNbt);
                        }
                    }
                }
            } else {
                AdorableHamsterPets.LOGGER.debug("[HamsterEntity] No active effects NBT found in shoulder data.");
            }
            // --- End 4. Load Active Status Effects ---


            // --- 5. Reset Eating State ---
            hamster.isAutoEating = false;
            hamster.autoEatProgressTicks = 0;
            // --- End 5. Reset Eating State ---

            // --- 6. Load Seeking and Sulking Data from Nested Record ---
            HamsterShoulderData.SeekingBehaviorData seekingData = data.seekingBehaviorData();
            if (seekingData != null) { // Should always be non-null due to orElse(SeekingBehaviorData.empty()) in codec
                hamster.isPrimedToSeekDiamonds = seekingData.isPrimedToSeekDiamonds();
                hamster.foundOreCooldownEndTick = seekingData.foundOreCooldownEndTick();
                hamster.currentOreTarget = seekingData.currentOreTarget().orElse(null);
                hamster.dataTracker.set(IS_SULKING, seekingData.isSulking());
            } else {
                // Fallback if seekingData is somehow null, though orElse should prevent this.
                hamster.isPrimedToSeekDiamonds = false;
                hamster.foundOreCooldownEndTick = 0L;
                hamster.currentOreTarget = null;
                hamster.dataTracker.set(IS_SULKING, false);
                AdorableHamsterPets.LOGGER.warn("[HamsterEntity] SeekingBehaviorData was null when loading from shoulder data. This should not happen.");
            }
            // --- End 6. Load Seeking and Sulking Data ---

        } else {
            AdorableHamsterPets.LOGGER.error("[HamsterEntity] Failed to create HamsterEntity instance in createFromShoulderData.");
        }
        return hamster;
    }

    /**
     * Spawns a HamsterEntity from shoulder data near the player, handling position and spawning.
     * Uses createFromShoulderData to load the entity's state.
     * @param world The server world.
     * @param player The player dismounting the hamster.
     * @param data The shoulder data.
     */
    public static void spawnFromShoulderData(ServerWorld world, PlayerEntity player, HamsterShoulderData data, boolean wasDiamondAlertActive) {
        // --- Create and Configure Hamster ---
        AdorableHamsterPets.LOGGER.debug("[HamsterEntity] spawnFromShoulderData called for player {} with data: {}, wasDiamondAlertActive: {}", player.getName().getString(), data, wasDiamondAlertActive);
        HamsterEntity hamster = createFromShoulderData(world, player, data);
        // --- End Create and Configure Hamster ---

        if (hamster != null) {
            // --- Prime for Diamond Seeking (if applicable) ---
            if (wasDiamondAlertActive && Configs.AHP.enableIndependentDiamondSeeking) {
                hamster.isPrimedToSeekDiamonds = true;
                // hamster.primedToSeekTimer was removed, so no need to set it.
                AdorableHamsterPets.LOGGER.debug("[HamsterEntity {}] Primed for diamond seeking.", hamster.getId());
            }
            // --- End Prime for Diamond Seeking ---

            // --- Set Position for Normal Dismount ---
            double angle = Math.toRadians(player.getYaw());
            double offsetX = -Math.sin(angle) * 0.7;
            double offsetZ = Math.cos(angle) * 0.7;
            hamster.refreshPositionAndAngles(player.getX() + offsetX, player.getY() + 0.1, player.getZ() + offsetZ, player.getYaw(), player.getPitch());
            // --- End Position Setting ---

            // --- Spawn Entity ---
            AdorableHamsterPets.LOGGER.debug("[HamsterEntity] Spawning hamster entity from shoulder data...");
            world.spawnEntityAndPassengers(hamster); // Spawn the fully configured hamster
            AdorableHamsterPets.LOGGER.debug("[HamsterEntity] Spawned Hamster ID {} from shoulder data near Player {}", hamster.getId(), player.getName().getString());
            // --- End Spawn Entity ---
        }
    }

    /**
     * Attempts to throw the hamster from the player's shoulder.
     * Called server-side when the throw packet is received.
     * @param player The player attempting the throw.
     */
    public static void tryThrowFromShoulder(ServerPlayerEntity player) {
        // --- 1. Initial Setup ---
        World world = player.getWorld();
        UUID playerUuid = player.getUuid();
        HamsterShoulderData shoulderData = player.getAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA);
        final AhpConfig config = AdorableHamsterPets.CONFIG; // Access static config
        // --- End 1. Initial Setup ---

        // --- Check Config Toggle ---
        if (!config.enableHamsterThrowing) {
            AdorableHamsterPets.LOGGER.debug("[HamsterEntity] tryThrowFromShoulder: Throwing disabled in config for player {}.", player.getName().getString());
            // Send message
            player.sendMessage(Text.literal("Hamster throwing is disabled in config."), true);
            return;
        }
        // --- End Check Config Toggle ---

        if (shoulderData != null) {
            AdorableHamsterPets.LOGGER.debug("[HamsterEntity] tryThrowFromShoulder: Player {} has shoulder data, proceeding.", player.getName().getString());

            // --- 2. Create Hamster Instance ---
            ServerWorld serverWorld = (ServerWorld) world;
            HamsterEntity hamster = HamsterEntity.createFromShoulderData(serverWorld, player, shoulderData);
            // --- End 2. Create Hamster Instance ---

            if (hamster != null) {
                AdorableHamsterPets.LOGGER.debug("[HamsterEntity] tryThrowFromShoulder: Hamster instance created with cooldownEndTick {} and isBaby={}.", hamster.throwCooldownEndTick, hamster.isBaby());

                // --- 3. Check if Hamster is Baby ---
                if (hamster.isBaby()) {
                    player.sendMessage(
                            Text.translatable("message.adorablehamsterpets.baby_throw_refusal")
                                    .formatted(Formatting.RED),
                            true // Send to action bar
                    );
                    AdorableHamsterPets.LOGGER.debug("[HamsterEntity] Hamster instance ID {} (from player {}) is a baby. Aborting throw.", hamster.getId(), player.getName().getString());
                    // Re-attach data since the throw is aborted
                    player.setAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA, shoulderData);
                    return; // Stop the throw
                }
                // --- End 3. Baby Check ---

                // --- 4. Check Specific Hamster's Cooldown ---
                long currentTime = world.getTime();
                // Use Config Value for Throw Cooldown Check
                if (hamster.throwCooldownEndTick > currentTime) {
                    long remainingTicks = hamster.throwCooldownEndTick - currentTime;
                    long totalSecondsRemaining = remainingTicks / 20;
                    long minutes = totalSecondsRemaining / 60;
                    long seconds = totalSecondsRemaining % 60;
                    player.sendMessage(Text.translatable("message.adorablehamsterpets.throw_cooldown", minutes, seconds).formatted(Formatting.RED), true);
                    player.setAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA, shoulderData); // Re-attach data
                    return;
                }
                // --- End 4. Hamster Cooldown Check ---

                // --- 5. Proceed with Throw ---
                player.removeAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA); // Now remove shoulder data

                // --- 5a. Set Position and Velocity ---
                hamster.refreshPositionAndAngles(player.getX(), player.getEyeY() - 0.1, player.getZ(), player.getYaw(), player.getPitch());
                hamster.setThrown(true);
                hamster.interactionCooldown = 10;
                hamster.throwTicks = 0;

                // --- Set Cooldown on the Hamster Instance ---
                hamster.throwCooldownEndTick = currentTime + config.hamsterThrowCooldown.get();
                AdorableHamsterPets.LOGGER.debug("[HamsterEntity] Set throw cooldown for hamster instance ID {} ending at tick {}.", hamster.getId(), hamster.throwCooldownEndTick);
                // --- End Set Cooldown ---

                float throwSpeed = 1.5f;
                Vec3d lookVec = player.getRotationVec(1.0f);
                Vec3d throwVec = new Vec3d(lookVec.x, lookVec.y + 0.1f, lookVec.z).normalize();
                hamster.setVelocity(throwVec.multiply(throwSpeed));
                hamster.velocityDirty = true;
                // --- End 5a. Set Position and Velocity ---

                // --- 5b. Spawn the entity ---
                serverWorld.spawnEntity(hamster);
                AdorableHamsterPets.LOGGER.debug("[HamsterEntity] tryThrowFromShoulder: Spawned thrown Hamster ID {}.", hamster.getId());
                // --- End 5b. Spawn the entity ---

                // --- 5c. Send S2C Packets ---
                StartHamsterFlightSoundPayload flightPayload = new StartHamsterFlightSoundPayload(hamster.getId());
                StartHamsterThrowSoundPayload throwPayload = new StartHamsterThrowSoundPayload(hamster.getId());

                // Send to thrower and nearby players (existing logic)
                ServerPlayNetworking.send(player, flightPayload);
                ServerPlayNetworking.send(player, throwPayload);
                double radius = 64.0;
                Vec3d hamsterPos = hamster.getPos();
                Box searchBox = new Box(hamsterPos.subtract(radius, radius, radius), hamsterPos.add(radius, radius, radius));
                List<ServerPlayerEntity> nearbyPlayers = serverWorld.getPlayers(p -> p != player && searchBox.contains(p.getPos()));
                for (ServerPlayerEntity nearbyPlayer : nearbyPlayers) {
                    ServerPlayNetworking.send(nearbyPlayer, flightPayload);
                    ServerPlayNetworking.send(nearbyPlayer, throwPayload);
                }
                // --- End 5c. Send S2C Packets ---

                // Receive Throw Hamster Advancement
                ModCriteria.HAMSTER_THROWN.trigger(player);
                // --- End 5. Proceed with Throw ---

            } else {
                AdorableHamsterPets.LOGGER.error("[HamsterEntity] tryThrowFromShoulder: Failed to create HamsterEntity instance. Re-attaching data (attempt).");
                // Ensure data is re-attached if hamster creation fails
                player.setAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA, shoulderData);
            }
        } else {
            AdorableHamsterPets.LOGGER.warn("[HamsterEntity] tryThrowFromShoulder: Player {} received throw packet but had no shoulder data.", player.getName().getString());
        }
    }

    /**
     * Checks if the given item stack is considered a standard hamster food item.
     * @param stack The item stack to check.
     * @return True if the item is in the HAMSTER_FOODS set, false otherwise.
     */
    private static boolean isIsFood(ItemStack stack) {
        return HAMSTER_FOODS.contains(stack.getItem());
    }

    // --- Data Trackers ---
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Integer> ANIMATION_PERSONALITY_ID = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Boolean> IS_SLEEPING = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> IS_SITTING = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> IS_BEGGING = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> IS_IN_LOVE = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> IS_REFUSING_FOOD = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> IS_THROWN = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> LEFT_CHEEK_FULL = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> RIGHT_CHEEK_FULL = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> IS_KNOCKED_OUT = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Integer> PINK_PETAL_TYPE = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Boolean> CHEEK_POUCH_UNLOCKED = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> IS_CONSIDERING_AUTO_EAT = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Integer> DOZING_PHASE = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<String> CURRENT_DEEP_SLEEP_ANIM_ID = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<Boolean> IS_SULKING = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> IS_CELEBRATING_DIAMOND = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<Boolean> IS_CLEANING = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<String> ACTIVE_CUSTOM_GOAL_NAME_DEBUG = DataTracker.registerData(HamsterEntity.class, TrackedDataHandlerRegistry.STRING);



    // --- Animation Constants ---
    private static final RawAnimation CRASH_ANIM = RawAnimation.begin().thenPlay("anim_hamster_crash");
    private static final RawAnimation KNOCKED_OUT_ANIM = RawAnimation.begin().thenPlay("anim_hamster_ko");
    private static final RawAnimation WAKE_UP_ANIM = RawAnimation.begin().thenPlay("anim_hamster_wakeup");
    private static final RawAnimation FLYING_ANIM = RawAnimation.begin().thenPlay("anim_hamster_flying");
    private static final RawAnimation NO_ANIM = RawAnimation.begin().thenPlay("anim_hamster_no");
    private static final RawAnimation SLEEP_POSE1_ANIM = RawAnimation.begin().thenPlay("anim_hamster_sleep_pose1");
    private static final RawAnimation SLEEP_POSE2_ANIM = RawAnimation.begin().thenPlay("anim_hamster_sleep_pose2");
    private static final RawAnimation SLEEP_POSE3_ANIM = RawAnimation.begin().thenPlay("anim_hamster_sleep_pose3");
    private static final RawAnimation SETTLE_SLEEP1_ANIM = RawAnimation.begin().thenPlay("anim_hamster_settle_sleep1");
    private static final RawAnimation SETTLE_SLEEP2_ANIM = RawAnimation.begin().thenPlay("anim_hamster_settle_sleep2");
    private static final RawAnimation SETTLE_SLEEP3_ANIM = RawAnimation.begin().thenPlay("anim_hamster_settle_sleep3");
    private static final RawAnimation WILD_SETTLE_SLEEP_ANIM = RawAnimation.begin().thenPlay("anim_hamster_wild_settle_sleep");
    private static final RawAnimation SITTING_POSE1_ANIM = RawAnimation.begin().thenPlay("anim_hamster_sitting_pose1");
    private static final RawAnimation SITTING_POSE2_ANIM = RawAnimation.begin().thenPlay("anim_hamster_sitting_pose2");
    private static final RawAnimation SITTING_POSE3_ANIM = RawAnimation.begin().thenPlay("anim_hamster_sitting_pose3");
    private static final RawAnimation DRIFTING_OFF_POSE1_ANIM = RawAnimation.begin().thenPlay("anim_hamster_drifting_off_pose1");
    private static final RawAnimation DRIFTING_OFF_POSE2_ANIM = RawAnimation.begin().thenPlay("anim_hamster_drifting_off_pose2");
    private static final RawAnimation DRIFTING_OFF_POSE3_ANIM = RawAnimation.begin().thenPlay("anim_hamster_drifting_off_pose3");
    private static final RawAnimation CLEANING_ANIM = RawAnimation.begin().thenPlay("anim_hamster_cleaning");
    private static final RawAnimation RUNNING_ANIM = RawAnimation.begin().thenPlay("anim_hamster_running");
    private static final RawAnimation WALKING_ANIM = RawAnimation.begin().thenPlay("anim_hamster_walking");
    private static final RawAnimation BEGGING_ANIM = RawAnimation.begin().thenPlay("anim_hamster_begging");
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenPlay("anim_hamster_idle");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("anim_hamster_attack");
    private static final RawAnimation SULK_ANIM = RawAnimation.begin().thenPlay("anim_hamster_sulk");
    private static final RawAnimation SULKING_ANIM = RawAnimation.begin().thenPlay("anim_hamster_sulking");
    public static final RawAnimation SEEKING_DIAMOND_ANIM = RawAnimation.begin().thenPlay("anim_hamster_seeking_diamond");
    public static final RawAnimation WANTS_TO_SEEK_DIAMOND_ANIM = RawAnimation.begin().thenPlay("anim_hamster_wants_to_seek_diamond");



    /* ──────────────────────────────────────────────────────────────────────────────
     *                                  2. Fields
     * ────────────────────────────────────────────────────────────────────────────*/

    // --- Unique Instance Fields ---
    @Unique private int interactionCooldown = 0;
    @Unique private int throwTicks = 0;
    @Unique public int wakingUpTicks = 0;
    @Unique private int ejectionCheckCooldown = 20;
    @Unique private int preAutoEatDelayTicks = 0;
    @Unique private int quiescentSitDurationTimer = 0;
    @Unique private int driftingOffTimer = 0;
    @Unique private int settleSleepAnimationCooldown = 0;
    @Unique private String activeCustomGoalDebugName = "None";
    @Unique public boolean isPrimedToSeekDiamonds = false;
    @Unique public long foundOreCooldownEndTick = 0L;
    @Unique public BlockPos currentOreTarget = null;
    @Unique private int celebrationParticleTicks = 0;
    @Unique private int sulkingSoundTicks = 0;
    @Unique private int diamondCelebrationSoundTicks = 0;
    @Unique private int sulkOrchestraHitDelayTicks = 0;
    @Unique private int sulkFailParticleTicks = 0;
    @Unique private int sulkEntityEffectTicks = 0;
    @Unique private int sulkShockedSoundDelayTicks = 0;
    @Unique private int diamondSparkleSoundDelayTicks = 0;
    @Nullable private HamsterCleaningSoundInstance cleaningSoundInstance;



    // --- Inventory ---
    private final DefaultedList<ItemStack> items = ImplementedInventory.create(INVENTORY_SIZE);

    // --- Animation ---
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // --- State Variables ---
    private int refuseTimer = 0;
    private ItemStack lastFoodItem = ItemStack.EMPTY;
    public int customLoveTimer;
    private int tamingCooldown = 0;
    private long throwCooldownEndTick = 0L;
    private long steamedBeansCooldownEndTick = 0L;

    // --- Auto-Eating State/Cooldown Fields ---
    private boolean isAutoEating = false; // Flag for potential animation hook
    private int autoEatProgressTicks = 0; // Ticks remaining for the current eating action
    private int autoEatCooldownTicks = 0; // Ticks remaining before it can start eating again
    // --- End Auto-Eating Fields ---

    public int cleaningTimer = 0;
    private int cleaningCooldownTimer = 0;



    /* ──────────────────────────────────────────────────────────────────────────────
     *                             3. Constructor
     * ────────────────────────────────────────────────────────────────────────────*/

    // --- Make Hamsters Avoid Water and Fire ---
    public HamsterEntity(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 3;
        this.setPathfindingPenalty(PathNodeType.WATER, -1.0F);
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, 8.0F);   // Standard penalty for fire
        this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, -1.0F);  // Strongly avoid nodes that cause fire damage
    }


    /* ──────────────────────────────────────────────────────────────────────────────
     *                             4. Public Methods
     * ────────────────────────────────────────────────────────────────────────────*/

    // --- Data Tracker Getters/Setters ---
    public int getVariant() { return this.dataTracker.get(VARIANT); }
    public void setVariant(int variantId) { this.dataTracker.set(VARIANT, variantId); }
    public boolean isSleeping() { return this.dataTracker.get(IS_SLEEPING); }
    public void setSleeping(boolean sleeping) { this.dataTracker.set(IS_SLEEPING, sleeping); }
    @Override public boolean isSitting() {  return this.dataTracker.get(IS_SITTING) || this.dataTracker.get(IS_SLEEPING) || this.dataTracker.get(IS_KNOCKED_OUT); }
    public boolean isBegging() { return this.dataTracker.get(IS_BEGGING); }
    public void setBegging(boolean value) { this.dataTracker.set(IS_BEGGING, value); }
    public boolean isInLove() { return this.dataTracker.get(IS_IN_LOVE); }
    public void setInLove(boolean value) { this.dataTracker.set(IS_IN_LOVE, value); }
    public boolean isRefusingFood() { return this.dataTracker.get(IS_REFUSING_FOOD); }
    public void setRefusingFood(boolean value) { this.dataTracker.set(IS_REFUSING_FOOD, value); }
    public boolean isThrown() { return this.dataTracker.get(IS_THROWN); }
    public void setThrown(boolean thrown) { this.dataTracker.set(IS_THROWN, thrown); }
    public boolean isLeftCheekFull() { return this.dataTracker.get(LEFT_CHEEK_FULL); }
    public void setLeftCheekFull(boolean full) { this.dataTracker.set(LEFT_CHEEK_FULL, full); }
    public boolean isRightCheekFull() { return this.dataTracker.get(RIGHT_CHEEK_FULL); }
    public void setRightCheekFull(boolean full) { this.dataTracker.set(RIGHT_CHEEK_FULL, full); }
    public boolean isKnockedOut() { return this.dataTracker.get(IS_KNOCKED_OUT); }
    public void setKnockedOut(boolean knocked_out) { this.dataTracker.set(IS_KNOCKED_OUT, knocked_out); }
    public String getCurrentDeepSleepAnimationIdFromTracker() {return this.dataTracker.get(CURRENT_DEEP_SLEEP_ANIM_ID);}
    public boolean isAutoEating() {return this.isAutoEating;}
    public boolean isConsideringAutoEat() {return this.dataTracker.get(IS_CONSIDERING_AUTO_EAT);}
    public DozingPhase getDozingPhase() {return DozingPhase.values()[this.dataTracker.get(DOZING_PHASE)];}
    public void setDozingPhase(DozingPhase phase) {this.dataTracker.set(DOZING_PHASE, phase.ordinal());}
    public void setActiveCustomGoalDebugName(String name) {this.dataTracker.set(ACTIVE_CUSTOM_GOAL_NAME_DEBUG, name);}
    public String getActiveCustomGoalDebugName() {String goalName = this.dataTracker.get(ACTIVE_CUSTOM_GOAL_NAME_DEBUG);return goalName;}
    public boolean isSulking() {return this.dataTracker.get(IS_SULKING);}
    public boolean isCelebratingDiamond() {return this.dataTracker.get(IS_CELEBRATING_DIAMOND);}
    public void setCelebratingDiamond(boolean celebrating) {
        this.dataTracker.set(IS_CELEBRATING_DIAMOND, celebrating);
        if (celebrating) {
            this.setBegging(false); // Ensure not also in normal begging state
            if (!this.getWorld().isClient()) { // Only initialize timer on server
                this.celebrationParticleTicks = HamsterEntity.CELEBRATION_PARTICLE_DURATION_TICKS;
                this.diamondSparkleSoundDelayTicks = 10; // 10-tick delay for sparkle sound
            }
        } else {
            // If stopping celebration, ensure all associated timers are also stopped/reset
            this.celebrationParticleTicks = 0;
            this.diamondSparkleSoundDelayTicks = 0;
            this.diamondCelebrationSoundTicks = 0;
        }
    }
    public void setSulking(boolean sulking) {
        this.dataTracker.set(IS_SULKING, sulking);
        if (sulking) {
            if (!this.getWorld().isClient()) {
                this.sulkOrchestraHitDelayTicks = 10; // 10-tick delay for orchestra hit
                this.sulkShockedSoundDelayTicks = 44; // 2.2 seconds * 20 ticks/second = 44 ticks
                this.sulkFailParticleTicks = 600;     // Duration for fail particles
                this.sulkEntityEffectTicks = 600;     // Duration for entity effect particles
            }
        } else {
            // If stopping sulking, ensure all associated timers are also stopped/reset
            this.sulkOrchestraHitDelayTicks = 0;
            this.sulkFailParticleTicks = 0;
            this.sulkEntityEffectTicks = 0;
        }
    }


    // --- Inventory Implementation ---
    @Override
    public DefaultedList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    public void markDirty() {
        if (!this.getWorld().isClient()) {
            this.updateCheekTrackers();
        }
    }


    // --- Override isValid for Hopper Interaction ---
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        // --- 1. Check if the item is allowed based on the disallowed logic ---
        // Ensure the slot index is valid for the hamster inventory (0-5)
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return false;
        }
        // Use the helper method to determine if the item is allowed
        return !this.isItemDisallowed(stack);
        // --- End 1. Check if the item is allowed based on the disallowed logic ---
    }
    // --- End isValid Override ---

    /**
     * Updates the DataTrackers for cheek fullness based on the inventory content.
     * Also triggers the "Chipmunk Aspirations" advancement if all pouch slots become full.
     */
    public void updateCheekTrackers() {
        // --- Update Left Cheek ---
        boolean leftFull = false;
        for (int i = 0; i < 3; i++) {
            if (!this.items.get(i).isEmpty()) {
                leftFull = true;
                break;
            }
        }

        // --- Update Right Cheek ---
        boolean rightFull = false;
        for (int i = 3; i < INVENTORY_SIZE; i++) {
            if (!this.items.get(i).isEmpty()) {
                rightFull = true;
                break;
            }
        }

        // --- Set Data Trackers ---
        if (this.isLeftCheekFull() != leftFull) this.setLeftCheekFull(leftFull);
        if (this.isRightCheekFull() != rightFull) this.setRightCheekFull(rightFull);
        // --- End Set Data Trackers ---

        // --- Trigger "Chipmunk Aspirations" Advancement ---
        if (!this.getWorld().isClient() && this.getOwner() instanceof ServerPlayerEntity serverPlayerOwner) {
            boolean allSlotsFilled = true;
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                if (this.items.get(i).isEmpty()) {
                    allSlotsFilled = false;
                    break;
                }
            }
            if (allSlotsFilled) {
                ModCriteria.HAMSTER_POUCH_FILLED.trigger(serverPlayerOwner, this);
            }
        }
        // --- End Trigger ---
    }

    // --- NBT Saving/Loading ---
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        // --- 1. Write Core Data ---
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("HamsterVariant", this.getVariant());

        // Only save "Sitting" NBT if tamed
        if (this.isTamed()) {
            nbt.putBoolean("Sitting", this.dataTracker.get(IS_SITTING));
        }

        nbt.putBoolean("KnockedOut", this.isKnockedOut());
        nbt.putLong("ThrowCooldownEnd", this.throwCooldownEndTick);
        nbt.putLong("SteamedBeansCooldownEnd", this.steamedBeansCooldownEndTick);
        nbt.putInt("AutoEatCooldown", this.autoEatCooldownTicks);
        nbt.putInt("EjectionCheckCooldown", this.ejectionCheckCooldown);
        nbt.putInt("PinkPetalType", this.dataTracker.get(PINK_PETAL_TYPE));
        nbt.putBoolean("CheekPouchUnlocked", this.dataTracker.get(CHEEK_POUCH_UNLOCKED));
        nbt.putInt("AnimationPersonalityId", this.dataTracker.get(ANIMATION_PERSONALITY_ID));

        // --- 2. Write Sleep State Data ---
        nbt.putInt("DozingPhase", this.getDozingPhase().ordinal());
        nbt.putString("CurrentDeepSleepAnimId", this.dataTracker.get(CURRENT_DEEP_SLEEP_ANIM_ID));
        nbt.putInt("QuiescentSitTimer", this.quiescentSitDurationTimer);
        nbt.putInt("DriftingOffTimer", this.driftingOffTimer);
        nbt.putInt("SettleSleepCooldown", this.settleSleepAnimationCooldown);

        // --- 3. Write Inventory ---
        RegistryWrapper.WrapperLookup registries = getRegistryLookup();
        NbtCompound inventoryWrapperNbt = new NbtCompound();
        Inventories.writeNbt(inventoryWrapperNbt, this.items, registries);
        nbt.put("Inventory", inventoryWrapperNbt);

        // --- 4. Write Seeking and Sulking Data ---
        nbt.putBoolean("IsPrimedToSeekDiamonds", this.isPrimedToSeekDiamonds);
        nbt.putLong("FoundOreCooldownEndTick", this.foundOreCooldownEndTick);
        if (this.currentOreTarget != null) {
            nbt.putInt("OreTargetX", this.currentOreTarget.getX());
            nbt.putInt("OreTargetY", this.currentOreTarget.getY());
            nbt.putInt("OreTargetZ", this.currentOreTarget.getZ());
        }
        nbt.putBoolean("IsSulking", this.dataTracker.get(IS_SULKING));
        nbt.putBoolean("IsCelebratingDiamond", this.dataTracker.get(IS_CELEBRATING_DIAMOND));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        // --- 1. Read Core Data ---
        super.readCustomDataFromNbt(nbt);
        AdorableHamsterPets.LOGGER.debug("[NBT Read {}] Start reading NBT data.", this.getId());
        this.setVariant(nbt.getInt("HamsterVariant"));

        // Default wild hamsters to not sitting
        if (this.isTamed()) {
            boolean wasSittingNbt = nbt.getBoolean("Sitting");
            this.setSitting(wasSittingNbt, true);
        } else {
            this.setSitting(false, true);
        }
        this.setKnockedOut(nbt.getBoolean("KnockedOut"));
        this.throwCooldownEndTick = nbt.getLong("ThrowCooldownEnd");
        this.steamedBeansCooldownEndTick = nbt.getLong("SteamedBeansCooldownEnd");
        this.autoEatCooldownTicks = nbt.getInt("AutoEatCooldown");
        this.ejectionCheckCooldown = nbt.contains("EjectionCheckCooldown", NbtElement.INT_TYPE) ? nbt.getInt("EjectionCheckCooldown") : 20;
        this.dataTracker.set(PINK_PETAL_TYPE, nbt.getInt("PinkPetalType"));
        this.dataTracker.set(CHEEK_POUCH_UNLOCKED, nbt.getBoolean("CheekPouchUnlocked"));
        this.dataTracker.set(ANIMATION_PERSONALITY_ID, nbt.getInt("AnimationPersonalityId"));

        // --- 2. Read Sleep State Data ---
        if (nbt.contains("DozingPhase", NbtElement.INT_TYPE)) {
            int phaseOrdinal = nbt.getInt("DozingPhase");
            if (phaseOrdinal >= 0 && phaseOrdinal < DozingPhase.values().length) {
                this.setDozingPhase(DozingPhase.values()[phaseOrdinal]);
            } else {
                this.setDozingPhase(DozingPhase.NONE); // Fallback for invalid ordinal
            }
        } else {
            this.setDozingPhase(DozingPhase.NONE); // Default if tag is missing
        }
        this.dataTracker.set(CURRENT_DEEP_SLEEP_ANIM_ID, nbt.getString("CurrentDeepSleepAnimId"));
        this.quiescentSitDurationTimer = nbt.getInt("QuiescentSitTimer");
        this.driftingOffTimer = nbt.getInt("DriftingOffTimer");
        this.settleSleepAnimationCooldown = nbt.getInt("SettleSleepCooldown");

        // --- 3. Read Inventory ---
        this.items.clear();
        RegistryWrapper.WrapperLookup registries = getRegistryLookup();
        if (nbt.contains("Inventory", NbtElement.COMPOUND_TYPE)) {
            Inventories.readNbt(nbt.getCompound("Inventory"), this.items, registries);
        }
        this.updateCheekTrackers();

        // --- 4. Read Seeking and Sulking Data ---
        this.isPrimedToSeekDiamonds = nbt.getBoolean("IsPrimedToSeekDiamonds");
        this.foundOreCooldownEndTick = nbt.getLong("FoundOreCooldownEndTick");
        if (nbt.contains("OreTargetX") && nbt.contains("OreTargetY") && nbt.contains("OreTargetZ")) {
            this.currentOreTarget = new BlockPos(nbt.getInt("OreTargetX"), nbt.getInt("OreTargetY"), nbt.getInt("OreTargetZ"));
        } else {
            this.currentOreTarget = null;
        }
        this.dataTracker.set(IS_SULKING, nbt.getBoolean("IsSulking"));
        this.dataTracker.set(IS_CELEBRATING_DIAMOND, nbt.getBoolean("IsCelebratingDiamond"));
    }


    // --- Shoulder Riding Data Handling ---
    public HamsterShoulderData saveToShoulderData() {
        // --- 1. Save Shoulder Data ---
        AdorableHamsterPets.LOGGER.trace("[HamsterEntity {}] saveToShoulderData called", this.getId());
        this.updateCheekTrackers();


        // --- 1a. Save Inventory NBT ---
        NbtCompound inventoryNbt = new NbtCompound();
        RegistryWrapper.WrapperLookup registries = null;
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            registries = serverWorld.getRegistryManager();
            Inventories.writeNbt(inventoryNbt, this.items, registries);
            AdorableHamsterPets.LOGGER.trace("[HamsterEntity {}] Saved inventory NBT: {}", this.getId(), inventoryNbt);
        } else {
            AdorableHamsterPets.LOGGER.warn("[HamsterEntity {}] Cannot save inventory NBT on client side or in non-server world!", this.getId());
        }
        // --- End 1a. Save Inventory NBT ---


        // --- 1b. Save Active Status Effects ---
        NbtList effectsList = new NbtList();
        for (StatusEffectInstance effectInstance : this.getStatusEffects()) {
            NbtElement effectNbt = effectInstance.writeNbt();
            effectsList.add(effectNbt);
        }
        AdorableHamsterPets.LOGGER.trace("[HamsterEntity {}] Saved {} active effects to NBT list.", this.getId(), effectsList.size());
        // --- End 1b. Save Active Status Effects ---


        // --- 1c. Get Custom Name ---
        Optional<String> nameOptional = Optional.ofNullable(this.getCustomName()).map(Text::getString);
        AdorableHamsterPets.LOGGER.trace("[HamsterEntity {}] Custom name to save: {}", this.getId(), nameOptional.orElse("None"));
        // --- End 1c. Get Custom Name ---


        // --- 1d. Create SeekingBehaviorData instance ---
        HamsterShoulderData.SeekingBehaviorData seekingData = new HamsterShoulderData.SeekingBehaviorData(
                this.isPrimedToSeekDiamonds,
                this.foundOreCooldownEndTick,
                Optional.ofNullable(this.currentOreTarget),
                this.dataTracker.get(IS_SULKING)
        );
        // --- End 1d. ---

        // --- 1e. Create main HamsterShoulderData record ---
        HamsterShoulderData data = new HamsterShoulderData(
                this.getVariant(),
                this.getHealth(),
                inventoryNbt,
                this.isLeftCheekFull(),
                this.isRightCheekFull(),
                this.getBreedingAge(),
                this.throwCooldownEndTick,
                this.steamedBeansCooldownEndTick,
                effectsList,
                this.autoEatCooldownTicks,
                nameOptional,
                this.dataTracker.get(PINK_PETAL_TYPE),
                this.dataTracker.get(CHEEK_POUCH_UNLOCKED),
                this.dataTracker.get(ANIMATION_PERSONALITY_ID),
                seekingData // Pass the created seekingData instance
        );
        AdorableHamsterPets.LOGGER.trace("[HamsterEntity {}] Returning shoulder data: {}", this.getId(), data);
        return data;
        // --- End 1e. ---
        // --- End 1. Save Shoulder Data ---
    }

    // --- Entity Behavior ---
    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) { return false; }

    @Override
    public void changeLookDirection(double cursorX, double cursorY) {
        if (this.isSleeping()) return;
        super.changeLookDirection(cursorX, cursorY);
    }

    @Override
    public void setSitting(boolean sitting) {
        // Calls the overload below. We want player-initiated sits to NOT play the sleep sound.
        // So, suppressSound should always be true when called from here.
        this.setSitting(sitting, true); // Always suppress sound for this basic toggle
    }

    // --- Overload for setSitting (ONLY controls IS_SITTING) ---
    /**
     * Sets the player-commanded sitting state of the hamster.
     * This method updates the {@code IS_SITTING} DataTracker and the vanilla sitting pose.
     * If the hamster is being told to stand up while it was in a dozing/sleep sequence,
     * the sleep sequence will be reset.
     *
     * @param sitting True to make the hamster sit, false to make it stand.
     * @param suppressSound True to suppress any sound normally associated with this action (parameter exists for API compatibility, not actively used for sound suppression within this method currently).
     */
    public void setSitting(boolean sitting, boolean suppressSound) {
        // --- 1. Reset Sleep Sequence if Standing Up from a Doze/Sleep ---
        if (!sitting && this.isTamed() && this.getDozingPhase() != DozingPhase.NONE) {
            resetSleepSequence("Player commanded hamster to stand up.");
        }
        // --- End 1. Reset ---

        // --- 2. Update Core Sitting State ---
        this.dataTracker.set(IS_SITTING, sitting);
        this.setInSittingPose(sitting); // Vanilla flag, also calls the IS_SITTING override in setInSittingPose
        // --- End 2. Update ---

        // --- 3. Manage Cleaning Timers and Quiescent Sit Timer on State Change ---
        if (sitting) {
            // When commanded to sit, ensure the cleaning timer is reset.
            this.cleaningTimer = 0;
            // quiescentSitDurationTimer will be set by the tick method when DozingPhase becomes QUIESCENT_SITTING.
        } else {
            // If standing up, reset the quiescent sit timer to prevent immediate re-entry into sleep sequence.
            this.quiescentSitDurationTimer = 0;
            // Also ensure cleaning stops if it was active.
            this.cleaningTimer = 0;
        }
        // --- End 3. Manage Timers ---
    }

    // --- Override isInAttackRange ---
    /**
     * Checks if the target entity is within the hamster's shorter melee attack range.
     * Overrides the default MobEntity check which uses a larger expansion.
     * @param entity The entity to check range against.
     * @return True if the entity is within the custom attack range, false otherwise.
     */
    @Override
    public boolean isInAttackRange(LivingEntity entity) {
        // --- Description: Calculate and check intersection with a smaller attack box ---
        // Get the hamster's current bounding box
        Box hamsterBox = this.getBoundingBox();
        // Expand it horizontally by the custom smaller amount
        Box attackBox = hamsterBox.expand(HAMSTER_ATTACK_BOX_EXPANSION, 0.0D, HAMSTER_ATTACK_BOX_EXPANSION);
        // Check if this smaller attack box intersects the target's hitbox
        boolean intersects = attackBox.intersects(entity.getBoundingBox());
        return intersects;
        // --- End Description ---
    }
    // --- End Override ---

    // --- Target Exclusion Override ---
    @Override
    public boolean canAttackWithOwner(LivingEntity target, LivingEntity owner) {
        UUID ownerUuid = owner.getUuid();
        // AdorableHamsterPets.LOGGER.debug("[canAttackWithOwner] Hamster: {}, Target: {}, Owner: {}", this.getName().getString(), target.getName().getString(), owner.getName().getString());

        // --- 1. Basic Exclusions (Self, Owner) ---
        if (target == this || target == owner) {
            return false;
        }
        if (target instanceof PlayerEntity && target.getUuid().equals(ownerUuid)) {
            return false;
        }

        // --- 2. Exclude Creepers and Armor Stands ---
        if (target instanceof CreeperEntity || target instanceof ArmorStandEntity) {
            return false;
        }

        // --- 3. Explicitly Check for TameableEntity ---
        if (target instanceof TameableEntity tameablePet) {
            UUID petOwnerUuid = tameablePet.getOwnerUuid();
            if (petOwnerUuid != null && petOwnerUuid.equals(ownerUuid)) {
                // AdorableHamsterPets.LOGGER.debug("[canAttackWithOwner] Target is a TameableEntity owned by the same player. Preventing attack.");
                return false;
            }
        }

        // --- 4. Explicitly Check for AbstractHorseEntity ---
        else if (target instanceof net.minecraft.entity.passive.AbstractHorseEntity horsePet) {
            Entity horseOwnerEntity = horsePet.getOwner();
            if (horseOwnerEntity != null && horseOwnerEntity.getUuid().equals(ownerUuid)) {
                // AdorableHamsterPets.LOGGER.debug("[canAttackWithOwner] Target is an AbstractHorseEntity owned by the same player. Preventing attack.");
                return false;
            }
        }

        // --- 5. General Ownable Check (Fallback) ---
        else if (target instanceof Ownable ownableFallback) {
            Entity fallbackOwnerEntity = ownableFallback.getOwner();
            if (fallbackOwnerEntity != null && fallbackOwnerEntity.getUuid().equals(ownerUuid)) {
                // AdorableHamsterPets.LOGGER.debug("[canAttackWithOwner] Target is an Ownable (fallback) owned by the same player. Preventing attack.");
                return false;
            }
        }

        // --- 6. Default: Allow Attack ---
        return true;
        // --- End 6. Default: Allow Attack ---
    }
// --- End Target Exclusion Override ---

    // --- Interaction Logic ---
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        // --- 1. Initial Setup ---
        ItemStack stack = player.getStackInHand(hand);
        World world = this.getWorld();
        AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Interaction start. Player: {}, Hand: {}, Item: {}", this.getId(), world.getTime(), player.getName().getString(), hand, stack.getItem());


        // --- 2. Check Knocked Out, Diamond Celebration, or Sulking ---
        // --- Check for Knocked Out ---
        if (this.isKnockedOut()) {
            AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Hamster is knocked out. Waking up.", this.getId(), world.getTime());
            if (!world.isClient()) {
                SoundEvent wakeUpSound = getRandomSoundFrom(ModSounds.HAMSTER_WAKE_UP_SOUNDS, this.random);
                if (wakeUpSound != null) {
                    world.playSound(null, this.getBlockPos(), wakeUpSound, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                }
                this.setKnockedOut(false); // Turn off knocked out
                this.setSitting(false, true); // Make sure sitting doesn't get turned on
                this.triggerAnimOnServer("mainController", "wakeup");
            }
            return ActionResult.success(world.isClient());
        }

        // --- Check for Diamond Celebration ---
        if (this.isCelebratingDiamond()) {
            if (!world.isClient()) {
                this.setCelebratingDiamond(false); // Turn off celebration
                this.setSitting(false, true); // Make sure sitting doesn't get turned on
                SoundEvent affectionSound = ModSounds.getRandomSoundFrom(ModSounds.HAMSTER_AFFECTION_SOUNDS, this.random);
                if (affectionSound != null) {
                    world.playSound(null, this.getBlockPos(), affectionSound, SoundCategory.NEUTRAL, 1.0f, this.getSoundPitch());
                } else { // Fallback
                    world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.5f, 1.5f);
                }
            }
            return ActionResult.success(world.isClient()); // Consume the interaction
        }

        // --- Check for Sulking ---
        if (this.isSulking()) {
            if (!world.isClient()) {
                this.setSulking(false); // Turn off sulking
                this.setSitting(false, true); // Make sure sitting doesn't get turned on
                SoundEvent affectionSound = ModSounds.getRandomSoundFrom(ModSounds.HAMSTER_AFFECTION_SOUNDS, this.random);
                if (affectionSound != null) {
                    world.playSound(null, this.getBlockPos(), affectionSound, SoundCategory.NEUTRAL, 1.0f, this.getSoundPitch());
                } else { // Fallback
                    world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_CHICKEN_STEP, SoundCategory.NEUTRAL, 0.5f, 1.5f);
                }
            }
            return ActionResult.success(world.isClient()); // Consume interaction
        }
        // --- End 2. Check Knocked Out, Diamond Celebration, or Sulking ---


        // --- 3. Interaction Cooldown Check ---
        if (this.interactionCooldown > 0) {
            AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Interaction cooldown active ({} ticks left). Passing.", this.getId(), world.getTime(), this.interactionCooldown);
            return ActionResult.PASS;
        }
        // --- End 3. Interaction Cooldown Check ---


        // --- 3a. Toggle Jade Debug with Guide Book ---
        if (player.isSneaking() && stack.isOf(ModItems.HAMSTER_GUIDE_BOOK)) {
            if (!world.isClient) { // Server-side logic
                AhpConfig currentConfig = AdorableHamsterPets.CONFIG;
                boolean currentSetting = currentConfig.enableJadeHamsterDebugInfo;
                boolean newSetting = !currentSetting;

                currentConfig.enableJadeHamsterDebugInfo = newSetting;
                currentConfig.save(); // Save the config to file

                Text message = Text.translatable(
                        newSetting ? "message.adorablehamsterpets.debug_overlay_enabled" : "message.adorablehamsterpets.debug_overlay_disabled"
                ).formatted(newSetting ? Formatting.GREEN : Formatting.RED);
                player.sendMessage(message, true); // Send to action bar

                AdorableHamsterPets.LOGGER.info("Player {} toggled Jade Hamster Debug Info via Guide Book to: {} for hamster {}", player.getName().getString(), newSetting, this.getId());
            }
            return ActionResult.success(world.isClient()); // Consume the action
        }
        // --- End 3a. Toggle Jade Debug with Guide Book ---


        // --- 3b. Pink Petal Application/Cycling (Tamed Owner Only, Not Sneaking) ---
        if (this.isTamed() && this.isOwner(player) && stack.isOf(Items.PINK_PETALS) && !player.isSneaking()) {
            if (!world.isClient) {

                // --- Reset Sleep Sequence if Dozing ---
                if (this.getDozingPhase() != DozingPhase.NONE) {
                    resetSleepSequence("Player interacted with pink petals.");
                }

                int currentPetalType = this.dataTracker.get(PINK_PETAL_TYPE);
                int nextPetalType = (currentPetalType % 3) + 1; // Cycles 0->1, 1->2, 2->3, 3->1

                this.dataTracker.set(PINK_PETAL_TYPE, nextPetalType);

                world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_PINK_PETALS_PLACE, SoundCategory.PLAYERS, 0.7f, 1.0f + random.nextFloat() * 0.2f);
                if (world instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.FALLING_SPORE_BLOSSOM,
                            this.getX(), this.getY() + this.getHeight() * 0.75, this.getZ(),
                            7, (this.getWidth() / 2.0F), (this.getHeight() / 2.0F), (this.getWidth() / 2.0F), 0.0);
                }

                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                AdorableHamsterPets.LOGGER.debug("[InteractMob {}] Cycled/Applied pink petal to type {}.", this.getId(), nextPetalType);

                // Trigger advancement criterion
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    ModCriteria.APPLIED_PINK_PETAL.trigger(serverPlayer, this);
                }
            }
            return ActionResult.success(world.isClient()); // Consume interaction
        }
        // --- End 3b. Pink Petal Application/Cycling ---

        // --- 3c. Pink Petal Removal with Shears (Tamed Owner Only, Not Sneaking) ---
        if (this.isTamed() && this.isOwner(player) && stack.isOf(Items.SHEARS) && !player.isSneaking()) {
            if (this.dataTracker.get(PINK_PETAL_TYPE) > 0) { // Only if petals are currently applied
                if (!world.isClient) {

                    // --- Reset Sleep Sequence if Dozing ---
                    if (this.getDozingPhase() != DozingPhase.NONE) {
                        resetSleepSequence("Player used shears (removed petals).");
                    }

                    this.dataTracker.set(PINK_PETAL_TYPE, 0); // Remove petals

                    world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 0.9f, 1.0f + random.nextFloat() * 0.1f);
                    if (world instanceof ServerWorld serverWorld) {
                        serverWorld.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.PINK_PETALS)),
                                this.getX(), this.getY() + this.getHeight() * 0.5, this.getZ(),
                                5, (this.getWidth() / 2.0F), (this.getHeight() / 2.0F), (this.getWidth() / 2.0F), 0.05);
                    }

                    // Drop one pink petal
                    ItemScatterer.spawn(world, this.getX(), this.getY() + 0.5, this.getZ(), new ItemStack(Items.PINK_PETALS, 1));

                    if (!player.getAbilities().creativeMode) {
                        stack.damage(1, player, LivingEntity.getSlotForHand(hand)); // Damage shears
                    }
                    AdorableHamsterPets.LOGGER.debug("[InteractMob {}] Removed pink petals with shears.", this.getId());
                }
                return ActionResult.success(world.isClient()); // Consume interaction
            }
        }
        // --- End 3c. Pink Petal Removal with Shears ---


        // --- 4. Taming Logic ---
        if (!this.isTamed()) {
            AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Hamster not tamed. Checking for taming attempt.", this.getId(), world.getTime());
            if (player.isSneaking() && stack.isOf(ModItems.SLICED_CUCUMBER)) {
                AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Taming attempt detected.", this.getId(), world.getTime());
                if (!world.isClient) { tryTame(player, stack); }
                return ActionResult.success(world.isClient());
            }
            AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Not a taming attempt. Calling super.interactMob for untamed.", this.getId(), world.getTime());
            return super.interactMob(player, hand);
        }
        // --- End 4. Taming Logic ---


        // --- 5. Owner Interaction Logic ---
        if (this.isOwner(player)) {
            AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Player is owner. Processing owner interactions.", this.getId(), world.getTime());
            boolean isSneaking = player.isSneaking();

            // --- 5a. Custom Owner Interactions ---
            // --- Shoulder Mounting with Cheese ---
            if (!isSneaking && stack.isOf(ModItems.CHEESE)) {
                if (!world.isClient) {
                    if (player.getAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA) == null) {

                        // --- Reset Sleep Sequence if Dozing ---
                        if (this.getDozingPhase() != DozingPhase.NONE) {
                            resetSleepSequence("Player mounted hamster with cheese.");
                        }

                        HamsterShoulderData data = this.saveToShoulderData();
                        player.setAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA, data);

                        // Store hamster's current position before discarding for cheese sound
                        BlockPos hamsterPosForCheeseSound = this.getBlockPos();
                        this.discard(); // Remove hamster from world

                        if (player instanceof ServerPlayerEntity serverPlayer) {
                            ModCriteria.HAMSTER_ON_SHOULDER.trigger(serverPlayer);
                        }
                        player.sendMessage(Text.literal("Your hamster scurries onto your shoulder!"), true);

                        // Play cheese use sound at the hamster's last known position
                        world.playSound(null, hamsterPosForCheeseSound, ModSounds.CHEESE_USE_SOUND, SoundCategory.PLAYERS, 1.0f, 1.0f);

                        // Play hamster mount sound at the player's position (near their ear)
                        SoundEvent mountSound = ModSounds.getRandomSoundFrom(ModSounds.HAMSTER_SHOULDER_MOUNT_SOUNDS, this.random);
                        if (mountSound != null) {
                            world.playSound(null, player.getBlockPos(), mountSound, SoundCategory.PLAYERS, 1.0f, this.getSoundPitch());
                        }

                        // Spawn cheese particles at the hamster's last known position
                        ((ServerWorld)world).spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(ModItems.CHEESE)),
                                hamsterPosForCheeseSound.getX() + 0.5, hamsterPosForCheeseSound.getY() + 0.5, hamsterPosForCheeseSound.getZ() + 0.5,
                                8, 0.25D, 0.25D, 0.25D, 0.05);

                        if (!player.getAbilities().creativeMode) {
                            stack.decrement(1);
                        }
                    } else {
                        player.sendMessage(Text.literal("Your shoulder is already occupied!"), true);
                    }
                }
                return ActionResult.success(world.isClient());
            }
            // --- End Shoulder Mounting with Cheese ---


            // Inventory Access (Server-Side)
            if (!world.isClient() && isSneaking) {
                // Check if pouch is unlocked OR if config disables the lock
                if (this.dataTracker.get(CHEEK_POUCH_UNLOCKED) || !AdorableHamsterPets.CONFIG.requireFoodMixToUnlockCheeks) {

                    // --- Reset Sleep Sequence if Dozing ---
                    if (this.getDozingPhase() != DozingPhase.NONE) {
                        resetSleepSequence("Player accessed inventory.");
                    }

                    player.openHandledScreen(new HamsterEntityScreenHandlerFactory(this));
                } else {
                    player.sendMessage(Text.translatable("message.adorablehamsterpets.cheek_pouch_locked").formatted(Formatting.WHITE), true);
                }
                return ActionResult.CONSUME; // Consume sneak action regardless of opening
            }


            // Feeding Logic (Server-Side, only if not sneaking)
            boolean isPotentialFood = isIsFood(stack) || stack.isOf(ModItems.STEAMED_GREEN_BEANS);
            if (!world.isClient() && !isSneaking && isPotentialFood) {
                AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Owner not sneaking, holding potential food. Checking refusal.", this.getId(), world.getTime());

                // --- Reset Sleep Sequence if Dozing ---
                if (this.getDozingPhase() != DozingPhase.NONE) {
                    resetSleepSequence("Player attempted to feed hamster.");
                }

                if (checkRepeatFoodRefusal(stack, player)) {
                    AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Food refused. Consuming interaction.", this.getId(), world.getTime());
                    return ActionResult.CONSUME; // Consume refusal action
                }


                AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Attempting feeding via tryFeedingAsTamed.", this.getId(), world.getTime());
                boolean feedingOccurred = tryFeedingAsTamed(player, stack); // Calls the method with detailed logging


                if (feedingOccurred) {
                    AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] tryFeedingAsTamed returned true. Setting last food, decrementing stack.", this.getId(), world.getTime());
                    this.lastFoodItem = stack.copy(); // Track last food *only* if feeding was successful
                    if (!player.getAbilities().creativeMode) {
                        stack.decrement(1);
                    }
                    return ActionResult.CONSUME; // Consume successful feeding action
                } else {
                    // If tryFeedingAsTamed returned false (e.g., cooldown, full health+no breed),
                    // We might still want to allow vanilla interaction or sitting.
                    // Let's PASS for now to allow super.interactMob to run.
                    AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] tryFeedingAsTamed returned false. Passing to vanilla/sitting.", this.getId(), world.getTime());
                }
            }
            // --- End 5a. Custom Owner Interactions ---


            // --- 5b. Vanilla Interaction Handling (Fallback AFTER custom checks) ---
            if (!isSneaking && !isPotentialFood && !stack.isOf(ModItems.CHEESE) && !stack.isOf(Items.PINK_PETALS)) {
                AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Not sneaking or holding handled food/petals. Calling super.interactMob.", this.getId(), world.getTime());
                ActionResult vanillaResult = super.interactMob(player, hand);
                AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] super.interactMob returned: {}", this.getId(), world.getTime(), vanillaResult);
                if (vanillaResult.isAccepted()) {
                    return vanillaResult;
                }
            }
            // --- End 5b. Vanilla Interaction Handling ---


            // --- 5c. Sitting Logic (Fallback if nothing else handled it) ---
            // This now acts as the default right-click action if not sneaking,
            // not feeding successfully, and vanilla didn't handle it.
            if (!world.isClient() && !isSneaking) {
                AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Fallback: Toggling sitting state.", this.getId(), world.getTime());
                this.setSitting(!this.dataTracker.get(IS_SITTING)); // Toggle sitting state
                this.jumping = false;
                this.navigation.stop();
                this.setTarget(null);
                return ActionResult.CONSUME_PARTIAL; // Indicate partial consumption for state toggle
            }
            // --- End 5c. Sitting Logic ---


            // Client-side success or fallback pass for owner
            AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Reached end of owner logic. Returning client-side success/pass.", this.getId(), world.getTime());
            return ActionResult.success(world.isClient());


        } else {
            // Interaction by a non-owner on a tamed hamster. Let vanilla handle it.
            AdorableHamsterPets.LOGGER.debug("[InteractMob {} Tick {}] Player is not owner. Calling super.interactMob.", this.getId(), world.getTime());
            return super.interactMob(player, hand);
        }
        // --- End 5. Owner Interaction Logic ---
    }

    // --- Taming Override ---
    @Override
    public void setTamed(boolean tamed, boolean updateAttributes) {
        // --- 1. Set Tamed State and Attributes ---
        super.setTamed(tamed, updateAttributes);
        if (tamed) {
            this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(16.0D);
            this.setHealth(this.getMaxHealth()); // Set health to the updated maximum
            // --- Ensure Attack Damage is set correctly on tame ---
            // Set the base attack damage attribute to the defined melee damage when tamed.
            this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(Configs.AHP.meleeDamage.get());
            // --- End Attack Damage ---
        } else {
            this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(8.0D);
            // Reset attack damage if untamed (optional, but good practice)
            this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(Configs.AHP.meleeDamage.get());
        }
        // --- End 1. Set Tamed State and Attributes ---
    }

    // --- Breeding ---
    public boolean isInCustomLove() { return this.customLoveTimer > 0; }
    public void setCustomInLove(PlayerEntity player) {
        this.customLoveTimer = CUSTOM_LOVE_TICKS;
        if (!this.getWorld().isClient) { this.getWorld().sendEntityStatus(this, (byte) 18); }
    }

    @Override
    public void setBaby(boolean baby) {
        this.setBreedingAge(baby ? -24000 : 0); // Vanilla logic for setting age based on baby status
    }


    // --- Method to Synchronize Custom Sitting DataTracker with Vanilla Pose ---
    /** This method is called by vanilla logic (like SitGoal) when the sitting pose changes.
     * We override it to ensure our custom IS_SITTING DataTracker, which drives animations,
     * stays synchronized with the entity's actual sitting pose state.
     */
    @Override
    public void setInSittingPose(boolean inSittingPose) {
        // --- 1. Call Superclass Method ---
        super.setInSittingPose(inSittingPose);


        // --- 2. Synchronize Custom DataTracker ---
        if (this.dataTracker.get(IS_SITTING) != inSittingPose) {
            this.dataTracker.set(IS_SITTING, inSittingPose);
        }

        // --- 3. Additional State Reset if Standing Up ---
        if (!inSittingPose) {
            if (this.isSleeping()) {
                this.setSleeping(false);
            }
            if (this.isKnockedOut()) {
                this.setKnockedOut(false);
            }
        }
    }


    // --- Get Variant Enum ---
    /**
     * Gets the HamsterVariant enum constant corresponding to this entity's current variant ID.
     * @return The HamsterVariant enum.
     */
    public HamsterVariant getVariantEnum() {
        return HamsterVariant.byId(this.getVariant());
    }

    // --- Hamster Breeding and Baby Variant Logic ---
    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity mate) {
        HamsterEntity baby = ModEntities.HAMSTER.create(world);
        if (baby == null) return null;

        if (!(mate instanceof HamsterEntity mother)) { // mother is the 'other' parent
            // Fallback if mate is not HamsterEntity (shouldn't happen with vanilla breeding)
            int randomVariantId = this.random.nextInt(HamsterVariant.values().length);
            baby.setVariant(randomVariantId);
            baby.setBaby(true);
            AdorableHamsterPets.LOGGER.warn("Hamster breeding attempted with non-hamster mate. Assigning random variant to baby.");
            return baby;
        }

        // --- 1. Determine Baby's Base Color ---
        HamsterEntity father = this; // 'this' is one parent
        // Randomly select which parent provides the base color for the baby
        HamsterVariant parentProvidingBaseColor = this.random.nextBoolean() ? father.getVariantEnum() : mother.getVariantEnum();
        HamsterVariant babyBaseColorEnum = parentProvidingBaseColor.getBaseVariant();

        // --- 2. Identify Parent Overlays ---
        @Nullable String fatherOverlayName = father.getVariantEnum().getOverlayTextureName();
        @Nullable String motherOverlayName = mother.getVariantEnum().getOverlayTextureName();

        // --- 3. Get All Potential Variants (base + overlays) for Baby's Base Color ---
        List<HamsterVariant> allVariantsForBabyBase = HamsterVariant.getVariantsForBase(babyBaseColorEnum);

        // --- 4. Build List of Eligible Overlay Names (including null for "no overlay") ---
        // These are overlays NOT used by either parent.
        List<@Nullable String> eligibleOverlayNames = new ArrayList<>();
        for (HamsterVariant variant : allVariantsForBabyBase) {
            @Nullable String candidateOverlay = variant.getOverlayTextureName(); // This can be null for the base variant itself

            boolean matchesFather = fatherOverlayName != null && fatherOverlayName.equals(candidateOverlay);
            boolean matchesMother = motherOverlayName != null && motherOverlayName.equals(candidateOverlay);

            if (!matchesFather && !matchesMother) {
                eligibleOverlayNames.add(candidateOverlay);
            }
        }

        // --- 5. Apply Conditional Overlay Rule ---
        boolean fatherHasOverlay = fatherOverlayName != null;
        boolean motherHasOverlay = motherOverlayName != null;

        List<@Nullable String> finalSelectableOverlayNames = new ArrayList<>();

        if (fatherHasOverlay && motherHasOverlay) {
            // Baby MUST have an overlay.
            // Prioritize overlays from eligibleOverlayNames (those different from parents).
            for (@Nullable String overlayName : eligibleOverlayNames) {
                if (overlayName != null) { // Only add actual overlays, not 'null' (no overlay)
                    finalSelectableOverlayNames.add(overlayName);
                }
            }

            // If no *different* overlay is available (finalSelectableOverlayNames is empty),
            // it means eligibleOverlayNames only contained 'null' (no overlay).
            // To satisfy "baby must have an overlay", we must pick an overlay,
            // even if it means matching a parent's overlay type (relaxing rule 1 for this conflict).
            // So, repopulate with ALL actual overlays for that base color.
            if (finalSelectableOverlayNames.isEmpty() && babyBaseColorEnum != HamsterVariant.WHITE) { // WHITE has no overlays
                AdorableHamsterPets.LOGGER.debug("Baby of base {} (parents {} & {} had overlays) must have an overlay, but no *different* overlay was available. Picking any overlay for the base.",
                        babyBaseColorEnum, father.getVariantEnum(), mother.getVariantEnum());
                for (HamsterVariant variant : allVariantsForBabyBase) {
                    if (variant.getOverlayTextureName() != null) { // Ensure it's an overlay variant
                        finalSelectableOverlayNames.add(variant.getOverlayTextureName());
                    }
                }
            }
            // If still empty (only for WHITE, which has no overlays), it will be handled by the fallback in step 6.
        } else {
            // If one or neither parent has an overlay, baby CAN have no overlay.
            // eligibleOverlayNames already correctly reflects this (includes null for base variant if eligible, and different overlays).
            finalSelectableOverlayNames.addAll(eligibleOverlayNames);
        }

        // --- 6. Select Final Baby Overlay and Construct Variant ---
        HamsterVariant babyFinalVariant;
        if (!finalSelectableOverlayNames.isEmpty()) {
            @Nullable String chosenOverlayName = finalSelectableOverlayNames.get(this.random.nextInt(finalSelectableOverlayNames.size()));
            babyFinalVariant = HamsterVariant.getVariantByBaseAndOverlay(babyBaseColorEnum, chosenOverlayName);
        } else {
            // Extreme fallback: Should only happen if allVariantsForBabyBase was empty or for WHITE if logic above failed.
            AdorableHamsterPets.LOGGER.warn("Baby variant selection fallback: No selectable overlay names for base {}. Parents: {} (overlay: {}), {} (overlay: {}). Defaulting to base color.",
                    babyBaseColorEnum, father.getVariantEnum(), fatherOverlayName, mother.getVariantEnum(), motherOverlayName);
            babyFinalVariant = babyBaseColorEnum; // Default to just the base color
        }

        baby.setVariant(babyFinalVariant.getId());

        // --- 7. Standard Baby Setup ---
        UUID ownerUUID = father.getOwnerUuid(); // Inherit owner from the 'this' parent (father)
        if (ownerUUID != null) {
            baby.setOwnerUuid(ownerUUID);
            baby.setTamed(true, true);
        }
        baby.setBaby(true);

        return baby;
    }
// --- End {{HamsterEntity createChild Method}} ---

    @Override
    public boolean isBreedingItem(ItemStack stack) { return isIsFood(stack); } // Use helper

    // --- Tick Logic ---
    @Override
    public void tick() {
        // --- 1. Decrement Timers ---
        if (this.interactionCooldown > 0) this.interactionCooldown--;
        // --- Cleaning Timer Logic ---
        if (this.cleaningCooldownTimer > 0) this.cleaningCooldownTimer--;
        if (this.cleaningTimer > 0) {
            this.cleaningTimer--;
            if (this.cleaningTimer == 0) {
                if (!this.getWorld().isClient) {
                    this.dataTracker.set(IS_CLEANING, false);
                }
                this.cleaningCooldownTimer = 200;
            }
        }
        // --- End Cleaning Timer Logic ---
        if (this.wakingUpTicks > 0) this.wakingUpTicks--;
        if (this.autoEatCooldownTicks > 0) this.autoEatCooldownTicks--;
        if (this.autoEatProgressTicks > 0) this.autoEatProgressTicks--;
        if (this.ejectionCheckCooldown > 0) this.ejectionCheckCooldown--;
        if (this.preAutoEatDelayTicks > 0) this.preAutoEatDelayTicks--;
        if (this.celebrationParticleTicks > 0) this.celebrationParticleTicks--;
        if (this.celebrationParticleTicks > 0) this.celebrationParticleTicks--;
        if (this.diamondCelebrationSoundTicks > 0) this.diamondCelebrationSoundTicks--;
        if (this.sulkOrchestraHitDelayTicks > 0) this.sulkOrchestraHitDelayTicks--;
        if (this.sulkFailParticleTicks > 0) this.sulkFailParticleTicks--;
        if (this.sulkEntityEffectTicks > 0) this.sulkEntityEffectTicks--;
        if (this.sulkShockedSoundDelayTicks > 0) this.sulkShockedSoundDelayTicks--;
        if (this.diamondSparkleSoundDelayTicks > 0) this.diamondSparkleSoundDelayTicks--;
        // --- Logic for Handling Cleaning State ---
        DozingPhase currentPhase = this.getDozingPhase();
        if (!this.getWorld().isClient() && this.dataTracker.get(IS_SITTING) && !this.dataTracker.get(IS_CLEANING) && this.cleaningCooldownTimer <= 0) {
            // Allow cleaning if the hamster is just sitting, but not if it's actively sleeping.
            if (currentPhase == DozingPhase.NONE || currentPhase == DozingPhase.QUIESCENT_SITTING) {
                int chanceDenominator = Configs.AHP.cleaningChanceDenominator.get();
                if (chanceDenominator > 0 && this.random.nextInt(chanceDenominator) == 0) {
                    this.cleaningTimer = this.random.nextBetween(30, 60);
                    this.dataTracker.set(IS_CLEANING, true);
                }
            }
        }
        // --- End Logic for Handling Cleaning State ---
        // --- End 1. Decrement Timers ---

        // --- 2. Thrown State Logic ---
        if (this.isThrown()) {
            this.throwTicks++; // Increment throw timer

            Vec3d currentPos = this.getPos();
            Vec3d currentVel = this.getVelocity();
            Vec3d nextPos = currentPos.add(currentVel);
            World world = this.getWorld();

            HitResult blockHit = world.raycast(new RaycastContext(currentPos, nextPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));

            boolean stopped = false;

            if (blockHit.getType() == HitResult.Type.BLOCK) {
                // --- 2a. Block Collision Handling ---
                Vec3d hitPos = blockHit.getPos();
                Vec3d pushback = currentVel.normalize().multiply(-0.1).add(0, 0.1, 0);
                this.setPosition(currentPos.add(pushback));
                AdorableHamsterPets.LOGGER.debug("[HamsterTick] Hit block, applying pushback: {}", pushback);

                this.setVelocity(currentVel.multiply(0.6, 0.0, 0.6));
                this.setThrown(false);
                this.playSound(SoundEvents.ENTITY_GENERIC_SMALL_FALL, 0.4f, 1.5f);
                this.setKnockedOut(true);
                // --- Trigger Crash Animation ---
                if (!world.isClient()) {
                    this.triggerAnimOnServer("mainController", "crash");
                }
                // --- End Trigger ---
                stopped = true;
                // --- End 2a. Block Collision Handling ---

            } else {
                EntityHitResult entityHit = ProjectileUtil.getEntityCollision(world, this, currentPos, nextPos, this.getBoundingBox().stretch(currentVel).expand(1.0), this::canHitEntity);

                if (entityHit != null && entityHit.getEntity() != null) {
                    // --- 2b. Entity Collision Handling ---
                    Entity hitEntity = entityHit.getEntity();
                    boolean playEffects = false;

                    if (hitEntity instanceof ArmorStandEntity) {
                        AdorableHamsterPets.LOGGER.debug("Hamster hit Armor Stand.");
                        playEffects = true;
                    } else if (hitEntity instanceof LivingEntity livingHit) {
                        // --- Use Config Value for Throw Damage ---
                        boolean damaged = livingHit.damage(this.getDamageSources().thrown(this, this.getOwner()),
                                Configs.AHP.hamsterThrowDamage.get().floatValue());
                        if (damaged) {
                            int nauseaDuration = 20;
                            livingHit.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, nauseaDuration, 0, false, false, false));
                            playEffects = true;
                        }
                    } else {
                        playEffects = true;
                    }

                    if (playEffects) {
                        world.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.HAMSTER_IMPACT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                        if (!world.isClient()) {
                            ((ServerWorld)world).spawnParticles(
                                    ParticleTypes.POOF,
                                    this.getX(), this.getY() + this.getHeight() / 2.0, this.getZ(),
                                    50, 0.4, 0.4, 0.4, 0.1
                            );
                            AdorableHamsterPets.LOGGER.debug("Spawned POOF particles at impact.");
                        }
                    }

                    this.setVelocity(currentVel.multiply(0.1, 0.1, 0.1));
                    this.setThrown(false);
                    this.setKnockedOut(true);
                    // --- Trigger Crash Animation ---
                    if (!world.isClient()) {
                        this.triggerAnimOnServer("mainController", "crash");
                    }
                    // --- End Trigger ---
                    stopped = true;
                    // --- End 2b. Entity Collision Handling ---
                }
            }

            // Apply gravity, update position, and spawn trail particles if still thrown
            if (this.isThrown() && !stopped) {
                if (!this.hasNoGravity()) {
                    this.setVelocity(this.getVelocity().add(0.0, THROWN_GRAVITY, 0.0));
                }

                Vec3d currentVelocity = this.getVelocity();
                if (Double.isNaN(currentVelocity.x) || Double.isNaN(currentVelocity.y) || Double.isNaN(currentVelocity.z)) {
                    this.setVelocity(Vec3d.ZERO);
                    this.setThrown(false);
                    AdorableHamsterPets.LOGGER.warn("Hamster velocity became NaN, resetting and stopping throw.");
                } else {
                    this.setPosition(this.getX() + currentVelocity.x, this.getY() + currentVelocity.y, this.getZ() + currentVelocity.z);
                    this.velocityDirty = true;

                    if (!world.isClient() && this.throwTicks > 5) {
                        ((ServerWorld)world).spawnParticles(
                                ParticleTypes.GUST,
                                this.getX(), this.getY() + this.getHeight() / 2.0, this.getZ(),
                                1, 0.1, 0.1, 0.1, 0.0
                        );
                    }
                }
            } else {
                if (this.throwTicks != 0) {
                    this.throwTicks = 0;
                }
            }
        }
        // --- End 2. Thrown State Logic ---

        // --- 3. Tamed Hamster "Path to Slumber" State Machine ---
        // This logic only applies to tamed hamsters and runs on the server.
        if (!this.getWorld().isClient() && this.isTamed()) {
            boolean canInitiateDrowsiness = checkConditionsForInitiatingDrowsiness(); // Helper method call
            boolean canSustainSlumber = checkConditionsForSustainingSlumber();       // Helper method call

            switch (currentPhase) {
                case NONE:
                    // If commanded to sit and conditions are right, start Phase 1
                    if (this.dataTracker.get(IS_SITTING) && canInitiateDrowsiness) {
                        // Check if quiescentSitDurationTimer is 0, meaning we can start a new cycle
                        if (this.quiescentSitDurationTimer == 0) {
                            this.setDozingPhase(DozingPhase.QUIESCENT_SITTING);

                            // Calculate random duration based on config
                            int minSeconds = Configs.AHP.tamedQuiescentSitMinSeconds.get();
                            int maxSeconds = Configs.AHP.tamedQuiescentSitMaxSeconds.get();

                            // Safety rail: ensure min is not greater than max
                            if (minSeconds > maxSeconds) {
                                AdorableHamsterPets.LOGGER.info("Config issue: tamedQuiescentSitMinSeconds ({}) > tamedQuiescentSitMaxSeconds ({}). Swapping.", minSeconds, maxSeconds);
                                int temp = minSeconds;
                                minSeconds = maxSeconds;
                                maxSeconds = temp;
                            }
                            // Safety rail: ensure max is not less than min after potential swap
                            if (maxSeconds < minSeconds) maxSeconds = minSeconds;

                            int durationTicks = this.random.nextBetween(minSeconds * 20, maxSeconds * 20 + 1);
                            this.quiescentSitDurationTimer = durationTicks;
                            AdorableHamsterPets.LOGGER.debug("Hamster {} entering QUIESCENT_SITTING for {} ticks.", this.getId(), durationTicks);
                        }
                    }
                    break;

                case QUIESCENT_SITTING:
                    if (!this.dataTracker.get(IS_SITTING) || !canInitiateDrowsiness) {
                        // Interrupted (stood up, conditions changed, etc.)
                        resetSleepSequence("Quiescent sitting interrupted: no longer sitting or conditions unfavorable.");
                        break;
                    }
                    if (this.quiescentSitDurationTimer > 0) {
                        this.quiescentSitDurationTimer--;
                    } else {
                        // Timer expired, attempt to move to Drifting Off
                        this.setDozingPhase(DozingPhase.DRIFTING_OFF);
                        this.driftingOffTimer = 90 * 20; // 90 seconds for the animation
                        // Animation controller will pick up anim_hamster_drifting_off
                        AdorableHamsterPets.LOGGER.debug("Hamster {} entering DRIFTING_OFF for {} ticks.", this.getId(), this.driftingOffTimer);
                    }
                    break;

                case DRIFTING_OFF:
                    if (!canSustainSlumber) { // Check sustain conditions
                        resetSleepSequence("Drifting off interrupted: conditions for slumber no longer met.");
                        break;
                    }
                    if (this.driftingOffTimer > 0) {
                        this.driftingOffTimer--;
                    } else {
                        // Drifting off animation completed
                        this.setDozingPhase(DozingPhase.SETTLING_INTO_SLUMBER);
                        // Randomly select a settle animation and corresponding deep sleep pose
                        int choice = this.random.nextInt(3);
                        String settleAnimId;
                        String deepSleepAnimIdForTracker; // Temporary variable for clarity
                        switch (choice) {
                            case 0:
                                settleAnimId = "anim_hamster_settle_sleep1";
                                deepSleepAnimIdForTracker = "anim_hamster_sleep_pose1";
                                break;
                            case 1:
                                settleAnimId = "anim_hamster_settle_sleep2";
                                deepSleepAnimIdForTracker = "anim_hamster_sleep_pose2";
                                break;
                            default: // case 2
                                settleAnimId = "anim_hamster_settle_sleep3";
                                deepSleepAnimIdForTracker = "anim_hamster_sleep_pose3";
                                break;
                        }
                        this.dataTracker.set(CURRENT_DEEP_SLEEP_ANIM_ID, deepSleepAnimIdForTracker); // Set DataTracker
                        this.triggerAnimOnServer("mainController", settleAnimId);
                        this.settleSleepAnimationCooldown = 20;
                        AdorableHamsterPets.LOGGER.debug("Hamster {} entering SETTLING_INTO_SLUMBER, triggering {}, target deep sleep anim ID: {}.", this.getId(), settleAnimId, deepSleepAnimIdForTracker);
                    }
                    break;

                case SETTLING_INTO_SLUMBER:
                    if (!canSustainSlumber) {
                        resetSleepSequence("Settling into slumber interrupted: conditions for slumber no longer met.");
                        break;
                    }
                    if (this.settleSleepAnimationCooldown > 0) {
                        this.settleSleepAnimationCooldown--;
                    } else {
                        // Settle animation finished, transition to deep sleep
                        this.setDozingPhase(DozingPhase.DEEP_SLEEP);
                        // Animation controller will now loop currentDeepSleepAnimationId
                        AdorableHamsterPets.LOGGER.debug("Hamster {} entering DEEP_SLEEP, playing {}.", this.getId(), this.dataTracker.get(CURRENT_DEEP_SLEEP_ANIM_ID));
                    }
                    break;

                case DEEP_SLEEP:
                    if (!canSustainSlumber) {
                        resetSleepSequence("Deep sleep interrupted: conditions for slumber no longer met.");
                    }
                    // Hamster remains in deep sleep, looping animation, until interrupted
                    break;
            }
        }
        // --- End 3. Tamed Hamster "Path to Slumber" State Machine ---

        // --- Client-Side Render State Tick ---
        if (this.getWorld().isClient()) {
            HamsterRenderState state = this.getAttachedOrCreate(ModEntityAttachments.HAMSTER_RENDER_STATE, HamsterRenderState::new);
            state.tickClient();
            this.tickCleaningSound();
        }

        // Call super.tick() *after* processing thrown state and timers
        super.tick();


        // --- 4. Server-Side Logic ---
        World world = this.getWorld();
        if (!world.isClient()) {

            // --- 4a. Ejection Logic ---
            if (this.ejectionCheckCooldown <= 0) {
                this.ejectionCheckCooldown = 100; // Reset cooldown (check every 5 seconds)
                boolean ejectedItem = false; // Flag to only eject one item per cycle

                for (int i = 0; i < this.items.size(); ++i) {
                    ItemStack stack = this.items.get(i);
                    if (!stack.isEmpty() && this.isItemDisallowed(stack)) {
                        AdorableHamsterPets.LOGGER.warn("[HamsterTick {}] Ejecting disallowed item {} from slot {}.", this.getId(), stack.getItem(), i);
                        // Drop the item at the hamster's feet
                        ItemScatterer.spawn(world, this.getX(), this.getY(), this.getZ(), stack.copy());
                        // Remove it from the inventory
                        this.items.set(i, ItemStack.EMPTY);
                        // Mark dirty and update visuals
                        this.markDirty(); // This calls updateCheekTrackers
                        ejectedItem = true;
                        break; // Eject only one item per check cycle
                    }
                }
            }
            // --- End 4a. Ejection Logic ---

            // --- 4b. Auto Eating Logic ---
            // This section now handles the multi-stage auto-eating: considering, eating, healing.

            // --- Stage 1: Check Eligibility and Start "Considering" ---
            if (this.isTamed() && this.getHealth() < this.getMaxHealth() &&
                    !this.isAutoEating() && !this.dataTracker.get(IS_CONSIDERING_AUTO_EAT) && // Not already eating or considering
                    this.autoEatCooldownTicks == 0 &&
                    !this.isThrown() && !this.isKnockedOut())
            {
                // Check inventory for eligible food
                for (int i = 0; i < this.items.size(); ++i) {
                    ItemStack stack = this.items.get(i);
                    if (!stack.isEmpty() && AUTO_HEAL_FOODS.contains(stack.getItem())) {
                        // Found food, start "considering" phase
                        this.dataTracker.set(IS_CONSIDERING_AUTO_EAT, true);
                        this.preAutoEatDelayTicks = 40; // 2-second delay
                        AdorableHamsterPets.LOGGER.trace("[HamsterTick {}] Eligible to auto-eat. Starting 2s pre-eat delay.", this.getId());
                        break; // Stop searching for food once consideration starts
                    }
                }
            }
            // --- End Stage 1 ---

            // --- Stage 2: Process "Considering" Delay & Start Actual Eating ---
            if (this.dataTracker.get(IS_CONSIDERING_AUTO_EAT) && this.preAutoEatDelayTicks == 0) {
                this.dataTracker.set(IS_CONSIDERING_AUTO_EAT, false); // No longer just considering

                // Re-check for food in case it was removed during the delay
                boolean foodStillAvailable = false;
                ItemStack foodToEat = ItemStack.EMPTY;
                int foodSlot = -1;

                for (int i = 0; i < this.items.size(); ++i) {
                    ItemStack stack = this.items.get(i);
                    if (!stack.isEmpty() && AUTO_HEAL_FOODS.contains(stack.getItem())) {
                        foodStillAvailable = true;
                        foodToEat = stack;
                        foodSlot = i;
                        break;
                    }
                }

                if (foodStillAvailable) {
                    AdorableHamsterPets.LOGGER.trace("[HamsterTick {}] Pre-eat delay finished. Starting auto-eat on {} from slot {}", this.getId(), foodToEat.getItem(), foodSlot);
                    this.isAutoEating = true; // Use the boolean flag for the eating animation state
                    this.autoEatProgressTicks = 60; // 3 seconds eating time

                    this.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.7F, 1.3F);
                    if (world instanceof ServerWorld serverWorld) {
                        serverWorld.spawnParticles(
                                new ItemStackParticleEffect(ParticleTypes.ITEM, foodToEat.split(1)), // Consume one for particles
                                this.getX() + this.random.nextGaussian() * 0.1,
                                this.getY() + this.getHeight() / 2.0 + this.random.nextGaussian() * 0.1,
                                this.getZ() + this.random.nextGaussian() * 0.1,
                                5, 0.1, 0.1, 0.1, 0.02
                        );
                    }
                    if (foodToEat.isEmpty()) { // If split made it empty
                        this.items.set(foodSlot, ItemStack.EMPTY);
                    }
                    this.updateCheekTrackers();
                } else {
                    AdorableHamsterPets.LOGGER.trace("[HamsterTick {}] Pre-eat delay finished, but food no longer available.", this.getId());
                    // No food, so don't proceed to eating state. Cooldowns remain 0.
                }
            }
            // --- End Stage 2 ---

            // --- Stage 3: Apply Healing After Eating Progress Finishes ---
            if (this.isAutoEating() && this.autoEatProgressTicks == 0) {
                this.heal(Configs.AHP.hamsterFoodMixHealing.get());
                this.autoEatCooldownTicks = 60; // Set main cooldown (3 seconds)
                this.isAutoEating = false; // Reset eating animation flag
                AdorableHamsterPets.LOGGER.trace("[HamsterTick {}] Auto-eat finished. Healed. Cooldown set to 60.", this.getId());

                if (this.getOwner() instanceof ServerPlayerEntity serverPlayerOwner) {
                    ModCriteria.HAMSTER_AUTO_FED.trigger(serverPlayerOwner, this);
                }
            }
            // --- End Stage 3 ---
            // --- End 4b. Auto Eating Logic ---

            // --- 4c. Handle Continuous Diamond Celebration Effects ---
            if (!this.getWorld().isClient()) {
                if (this.isCelebratingDiamond()) {
                    // Delayed Diamond Sparkle Sound
                    if (this.diamondSparkleSoundDelayTicks == 1) { // Play when delay reaches 1
                        SoundEvent sparkleSound = ModSounds.getRandomSoundFrom(ModSounds.DIAMOND_SPARKLE_SOUNDS, this.random);
                        if (sparkleSound != null) {
                            // Play sound at the ORE'S location
                            if (this.currentOreTarget != null) {
                                this.getWorld().playSound(null, this.currentOreTarget, sparkleSound, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                            } else { // Fallback to hamster pos if ore target is somehow null
                                this.getWorld().playSound(null, this.getBlockPos(), sparkleSound, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                            }
                        }
                    }

                    // Particle Spawning
                    if (this.celebrationParticleTicks > 0) {
                            ((ServerWorld)this.getWorld()).spawnParticles(
                                    ParticleTypes.TRIAL_SPAWNER_DETECTION_OMINOUS, // 1. Particle Type
                                    this.getX(),                                   // 2. Center X-coordinate
                                    this.getY() + 1.5,                             // 3. Center Y-coordinate
                                    this.getZ(),                                   // 4. Center Z-coordinate
                                    4,                                             // 5. Count
                                    0.08,                                          // 6. Delta X (Spread X)
                                    0.15,                                          // 7. Delta Y (Spread Y)
                                    0.08,                                          // 8. Delta Z (Spread Z)
                                    0.01                                           // 9. Speed
                            );

                        if (this.currentOreTarget != null && this.random.nextInt(4) == 0) {
                            BlockPos particlePos = this.currentOreTarget.up(); // Spawn above the diamond ore
                            ((ServerWorld)this.getWorld()).spawnParticles(
                                    ParticleTypes.FIREWORK,   // 1. Particle Type
                                    particlePos.getX() + 0.5, // 2. Center X-coordinate
                                    particlePos.getY() + 0.5, // 3. Center Y-coordinate
                                    particlePos.getZ() + 0.5, // 4. Center Z-coordinate
                                    1,                        // 5. Count
                                    0.2,                      // 6. Delta X (Spread X)
                                    0.35,                     // 7. Delta Y (Spread Y)
                                    0.2,                      // 8. Delta Z (Spread Z)
                                    0.003                     // 9. Speed
                            );
                        }
                    }

                    //  Begging Sounds
                    if (this.diamondCelebrationSoundTicks <= 0) {
                        SoundEvent celebrationSound = ModSounds.getRandomSoundFrom(ModSounds.HAMSTER_BEG_SOUNDS, this.random);
                        if (celebrationSound != null) {
                            this.getWorld().playSound(null, this.getBlockPos(), celebrationSound, SoundCategory.NEUTRAL, 0.8F, this.getSoundPitch());
                        }
                        this.diamondCelebrationSoundTicks = 30;
                    }
                }
            }
            // --- End 4c. ---

            // --- 4d. Handle Continuous Sulking Effects ---
            if (this.isSulking()) {
                // Delayed Orchestra Hit
                if (this.sulkOrchestraHitDelayTicks == 1) { // Play when delay reaches 1 (was 10, now 1 after 9 ticks)
                    this.getWorld().playSound(null, this.getBlockPos(), ModSounds.ALARM_ORCHESTRA_HIT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                }

                // Delayed Single Shocked Sound
                if (this.sulkShockedSoundDelayTicks == 1) { // Play when this timer reaches 1
                    this.getWorld().playSound(null, this.getBlockPos(), ModSounds.HAMSTER_SHOCKED, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                }


                // Angry Smoke Particles above Gold Ore
                if (this.sulkFailParticleTicks > 0 && this.currentOreTarget != null) {
                    if (this.random.nextInt(3) == 0) {
                        BlockPos particlePos = this.currentOreTarget.up();
                        ((ServerWorld)this.getWorld()).spawnParticles(
                                ParticleTypes.SMOKE,          // 1. Particle Type
                                particlePos.getX() + 0.5,     // 2. Center X-coordinate
                                particlePos.getY() + 0.5,     // 3. Center Y-coordinate
                                particlePos.getZ() + 0.5,     // 4. Center Z-coordinate
                                2,                            // 5. Count
                                0.3,                          // 6. Delta X (Spread X)
                                0.3,                          // 7. Delta Y (Spread Y)
                                0.3,                          // 8. Delta Z (Spread Z)
                                0.005                         // 9. Speed
                        );
                    }
                }

                // Black Entity Effect Particles on Hamster
                if (this.sulkEntityEffectTicks > 0) {
                    if (this.random.nextInt(5) == 0) { // Spawn periodically
                        // 1. Create the ParticleEffect with a specific color
                        EntityEffectParticleEffect darkGrayEffect = EntityEffectParticleEffect.create(
                                ParticleTypes.ENTITY_EFFECT, // The base particle type
                                0.3f, // Red component (0.0 to 1.0)
                                0.3f, // Green component (0.0 to 1.0)
                                0.3f  // Blue component (0.0 to 1.0)
                        );

                        // 2. Spawn the pre-colored particle effect
                        ((ServerWorld)this.getWorld()).spawnParticles(
                                darkGrayEffect,             // The ParticleEffect instance (already has color)
                                this.getParticleX(0.3),   // Center X: Random X within 0.3 * hamster's width
                                this.getRandomBodyY(),      // Center Y: Random Y on hamster's body
                                this.getParticleZ(0.3),   // Center Z: Random Z within 0.3 * hamster's width
                                1,                          // Count: Number of particles to spawn
                                0.01,                       // Delta X: Half-width of spawn box (small spread)
                                0.05,                       // Delta Y: Half-height of spawn box (small vertical spread)
                                0.01,                       // Delta Z: Half-depth of spawn box (small spread)
                                0.005                       // Speed: Initial motion multiplier for the particles (very gentle)
                        );
                    }
                }
            }
            // --- End 4d. ---
        }
        // --- End 4. Server-Side Logic ---

        // --- 5. Client-Side Buff Particle Logic ---
        if (world.isClient) {
            // Check if the hamster has one of the buff effects (e.g., Strength)
            if (this.hasStatusEffect(StatusEffects.STRENGTH)) {
                // Only spawn particles occasionally to avoid clutter
                if (this.random.nextInt(5) == 0) { // Spawn roughly every 1/4 second
                    // Spawn standard entity effect particles randomly around the hamster
                    for (int i = 0; i < 2; ++i) { // Spawn a couple particles each time
                        world.addParticle((ParticleEffect)ParticleTypes.ENTITY_EFFECT, // Cast ParticleType to ParticleEffect
                                this.getParticleX(0.6), // Get random X within bounds
                                this.getRandomBodyY(),     // Get random Y on the body
                                this.getParticleZ(0.6), // Get random Z within bounds
                                this.random.nextGaussian() * 0.02, // dx (slight random motion)
                                this.random.nextGaussian() * 0.02, // dy
                                this.random.nextGaussian() * 0.02  // dz
                        );
                    }
                }
            }
        }
        // --- End 5. Client-Side Buff Particle Logic ---

        // --- 6. Other Non-Movement Tick Logic ---
        if (this.isRefusingFood() && refuseTimer > 0) { if (--refuseTimer <= 0) this.setRefusingFood(false); }
        if (tamingCooldown > 0) tamingCooldown--;
        if (customLoveTimer > 0) customLoveTimer--;
        if (customLoveTimer <= 0 && this.isInLove()) this.setInLove(false);
        // --- End 6. Other Non-Movement Tick Logic ---
    }

    @Override public boolean canMoveVoluntarily() { return super.canMoveVoluntarily() && !this.isThrown(); }
    @Override public boolean isPushable() { return super.isPushable() && !this.isThrown(); }

    // --- Override onDeath to Drop Inventory ---
    @Override
    public void onDeath(DamageSource source) {
        // --- 1. Drop Cheek Pouch Inventory ---
        World world = this.getWorld(); // Get the world instance
        if (!world.isClient()) {
            // Iterate through the items list and drop each non-empty stack
            for (ItemStack stack : this.items) {
                if (!stack.isEmpty()) {
                    // Use ItemScatterer to drop the stack at the hamster's position
                    ItemScatterer.spawn(world, this.getX(), this.getY(), this.getZ(), stack);
                }
            }
            // Clear the internal list after dropping
            this.items.clear();
            // Update cheek trackers one last time
            this.updateCheekTrackers();
        }
        // --- End 1. Drop Cheek Pouch Inventory ---

        // Call the superclass method AFTER dropping items
        super.onDeath(source);
    }
    // --- End Override ---

    // --- HamsterEntity.java ---

    // --- Animation ---
    /**
     * Registers the animation controllers for the HamsterEntity.
     * This method defines the main animation state machine, prioritizing states like
     * knocked out, thrown, and the detailed "Path to Slumber" sequence for tamed hamsters.
     * It also handles animations for wild hamster sleep, player-commanded sitting (including cleaning),
     * movement, begging, and defaults to an idle animation.
     *
     * <p>The "Path to Slumber" for tamed hamsters involves several phases:
     * <ul>
     *     <li>{@link DozingPhase#DRIFTING_OFF}: Plays {@code anim_hamster_drifting_off}. Its completion is
     *         managed by {@code driftingOffTimer} in the {@link #tick()} method.</li>
     *     <li>{@link DozingPhase#SETTLING_INTO_SLUMBER}: A short, 1-second {@code anim_hamster_settle_sleepX}
     *         animation is triggered from {@link #tick()}. During this brief transition, this controller
     *         defaults to {@code SITTING_ANIM}. The {@code settleSleepAnimationCooldown} in {@link #tick()}
     *         manages the progression to {@code DEEP_SLEEP}.</li>
     *     <li>{@link DozingPhase#DEEP_SLEEP}: Loops the chosen {@code anim_hamster_sleep_poseX} (e.g.,
     *         {@code SLEEP_POSE1_ANIM}), determined by {@code currentDeepSleepAnimationId}.</li>
     * </ul>
     * Wild hamsters use a simpler sleep mechanism: {@code anim_hamster_wild_settle_sleep} is triggered,
     * followed by looping {@code SLEEP_POSE1_ANIM} if {@link #isSleeping()} is true.
     * </p>
     *
     * <p>Several animations like attack, crash, wakeup, and the settle animations are registered
     * as triggerable and will interrupt the main looping state when fired via {@link #triggerAnimOnServer}.</p>
     *
     * @param controllers The registrar for adding animation controllers.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "mainController", 5, event -> {
            DozingPhase currentDozingPhase = this.getDozingPhase();
            int personality = this.dataTracker.get(ANIMATION_PERSONALITY_ID);

            // --- Knocked Out State ---
            if (this.isKnockedOut()) {
                return event.setAndContinue(KNOCKED_OUT_ANIM);
            }

            // --- Sulking State ---
            if (this.isSulking()) {
                return event.setAndContinue(SULKING_ANIM);
            }

            // --- Thrown State ---
            if (this.isThrown()) {
                return event.setAndContinue(FLYING_ANIM);
            }

            // --- Seeking/Wanting to Seek Diamond/Ore State ---
            boolean isSeekingGoalActive = false;
            String activeGoalName = this.getActiveCustomGoalDebugName();
            if (activeGoalName.startsWith(HamsterSeekDiamondGoal.class.getSimpleName())) {
                isSeekingGoalActive = true;
            }

            if (isSeekingGoalActive) {
                double horizontalSpeedSquared = this.getVelocity().horizontalLengthSquared();
                if (horizontalSpeedSquared > 1.0E-7) { // Use a very small threshold to detect any movement
                    return event.setAndContinue(SEEKING_DIAMOND_ANIM); // Hamster is moving
                } else {
                    return event.setAndContinue(WANTS_TO_SEEK_DIAMOND_ANIM); // Hamster is not moving
                }
            }

            // --- Diamond Celebration Animation ---
            if (this.isCelebratingDiamond()) {
                return event.setAndContinue(BEGGING_ANIM); // Reuse begging animation for celebration
            }
            // --- End Seeking/Wanting to Seek Diamond Ore State ---

                    // --- Tamed Hamster Sleep Sequence ---
                    if (this.isTamed()) {
                        switch (currentDozingPhase) {
                            case DRIFTING_OFF:
                                return event.setAndContinue(switch (personality) {
                                    case 2 -> DRIFTING_OFF_POSE2_ANIM;
                                    case 3 -> DRIFTING_OFF_POSE3_ANIM;
                                    default -> DRIFTING_OFF_POSE1_ANIM;
                                });

                            case SETTLING_INTO_SLUMBER:
                                String targetDeepSleepId = this.dataTracker.get(CURRENT_DEEP_SLEEP_ANIM_ID);
                                if (!targetDeepSleepId.isEmpty()) {
                                    RawAnimation targetDeepSleepAnim = switch (targetDeepSleepId) {
                                        case "anim_hamster_sleep_pose1" -> SLEEP_POSE1_ANIM;
                                        case "anim_hamster_sleep_pose2" -> SLEEP_POSE2_ANIM;
                                        case "anim_hamster_sleep_pose3" -> SLEEP_POSE3_ANIM;
                                        default -> SITTING_POSE1_ANIM; // Fallback
                                    };
                                    return event.setAndContinue(targetDeepSleepAnim);
                                } else if (this.dataTracker.get(IS_SITTING)) {
                                    // If interrupted, return to the correct personality-based sitting pose
                                    return event.setAndContinue(switch (personality) {
                                        case 2 -> SITTING_POSE2_ANIM;
                                        case 3 -> SITTING_POSE3_ANIM;
                                        default -> SITTING_POSE1_ANIM;
                                    });
                                }
                                break;

                            case DEEP_SLEEP:
                                String deepSleepId = this.dataTracker.get(CURRENT_DEEP_SLEEP_ANIM_ID);
                                RawAnimation deepSleepAnimToPlay = switch (deepSleepId) {
                                    case "anim_hamster_sleep_pose1" -> SLEEP_POSE1_ANIM;
                                    case "anim_hamster_sleep_pose2" -> SLEEP_POSE2_ANIM;
                                    case "anim_hamster_sleep_pose3" -> SLEEP_POSE3_ANIM;
                                    // If interrupted, return to the correct personality-based sitting pose
                                    default -> switch (personality) {
                                        case 2 -> SITTING_POSE2_ANIM;
                                        case 3 -> SITTING_POSE3_ANIM;
                                        default -> SITTING_POSE1_ANIM;
                                    };
                                };
                                return event.setAndContinue(deepSleepAnimToPlay);
                        }
                    }

            // --- Wild Hamster Sleeping State ---
            if (!this.isTamed() && this.isSleeping()) {
                // After anim_hamster_wild_settle_sleep (triggerable) finishes, loop sleep_pose1.
                return event.setAndContinue(SLEEP_POSE1_ANIM);
            }

            // --- Player-Commanded Sitting / Tamed Quiescent Sitting ---
            if (this.dataTracker.get(IS_SITTING)) {
                if (this.dataTracker.get(IS_CLEANING)) {
                    return event.setAndContinue(CLEANING_ANIM);
                } else {
                    // The logic to start cleaning lives in the tick() method.
                    // The animation controller only reacts to the state.
                    return event.setAndContinue(switch (personality) {
                        case 2 -> SITTING_POSE2_ANIM;
                        case 3 -> SITTING_POSE3_ANIM;
                        default -> SITTING_POSE1_ANIM;
                    });
                }
            }

            // --- Movement State ---
            double horizontalSpeedSquared = this.getVelocity().horizontalLengthSquared();
            if (horizontalSpeedSquared > 1.0E-6) {
                return event.setAndContinue(horizontalSpeedSquared > 0.002 ? RUNNING_ANIM : WALKING_ANIM);
            }

            // --- Begging State ---
            if (this.isBegging()) {
                return event.setAndContinue(BEGGING_ANIM);
            }

            // --- Default Idle State ---
            return event.setAndContinue(IDLE_ANIM);

        })
                .triggerableAnim("crash", CRASH_ANIM)
                .triggerableAnim("wakeup", WAKE_UP_ANIM)
                .triggerableAnim("no", NO_ANIM)
                .triggerableAnim("attack", ATTACK_ANIM)
                .triggerableAnim("anim_hamster_settle_sleep1", SETTLE_SLEEP1_ANIM)
                .triggerableAnim("anim_hamster_settle_sleep2", SETTLE_SLEEP2_ANIM)
                .triggerableAnim("anim_hamster_settle_sleep3", SETTLE_SLEEP3_ANIM)
                .triggerableAnim("anim_hamster_wild_settle_sleep", WILD_SETTLE_SLEEP_ANIM)
                .triggerableAnim("anim_hamster_sulk", SULK_ANIM)
                .setParticleKeyframeHandler(event -> {
                    // --- Logic for Handling Attack Particles ---
                    if ("attack_poof".equals(event.getKeyframeData().getEffect())) {
                        if (this.getWorld().isClient()) {
                            var renderer = net.minecraft.client.MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this);
                            if (renderer instanceof net.dawson.adorablehamsterpets.entity.client.HamsterRenderer hamsterRenderer) {
                                hamsterRenderer.triggerAttackParticleSpawn();
                            }
                        }
                    }
                    // --- Logic for Handling Sniffing Particles ---
                    if ("seeking_dust".equals(event.getKeyframeData().getEffect())) {
                        if (this.getWorld().isClient()) {
                            var renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this);
                            if (renderer instanceof net.dawson.adorablehamsterpets.entity.client.HamsterRenderer hamsterRenderer) {
                                hamsterRenderer.triggerSeekingDustSpawn();
                            }
                        }
                    }
                })
                // --- Logic for Handling Bounce and Step Sounds ---
                .setSoundKeyframeHandler(event -> {
                    if (!this.getWorld().isClient()) return; // Should always be client, but good practice
                    String soundEffect = event.getKeyframeData().getSound();
                    AdorableHamsterPets.LOGGER.debug("[SoundKeyframe] Hamster ID: {}, Effect: '{}'", this.getId(), soundEffect);
                    // --- Handle Step Sounds ---
                    if ("hamster_step_sound".equals(soundEffect)) {
                        HamsterRenderState state = this.getAttachedOrCreate(ModEntityAttachments.HAMSTER_RENDER_STATE, HamsterRenderState::new);
                        if (!state.isSoundDelayed()) {
                            BlockPos pos = this.getBlockPos();
                            BlockState blockState = this.getWorld().getBlockState(pos.down());

                            if (blockState.isAir()) { // If the block below the hamster is air, check one block further
                                blockState = this.getWorld().getBlockState(pos.down(2));
                            }
                            if (!blockState.isAir()) {
                                BlockSoundGroup group = blockState.getSoundGroup();
                                SoundEvent stepSound = group.getStepSound();

                                // Determine volume based on block type
                                float volume = blockState.isOf(Blocks.GRAVEL)
                                        ? (DEFAULT_FOOTSTEP_VOLUME * GRAVEL_VOLUME_MODIFIER)
                                        : DEFAULT_FOOTSTEP_VOLUME;
                                // MinecraftClient.getInstance().getSoundManager().play(
                                //         new PositionedSoundInstance(
                                //                 stepSound,              // The dynamic sound from the block
                                //                 SoundCategory.NEUTRAL,
                                //                 volume,                   // Use the calculated volume
                                //                 group.getPitch() * 1.5F,  // Pitch from the block's sound group
                                //                 this.random,
                                //                 this.getX(), this.getY(), this.getZ()
                                //         )
                                // );
                                this.clientPlaySound(stepSound, volume, group.getPitch() * 1.5F);
                            }
                        }
                    }
                    // --- Handle Begging Bounce Sounds ---
                    String effect = event.getKeyframeData().getSound();
                    AdorableHamsterPets.LOGGER.debug("[SoundKeyframe] Hamster ID: {}, Effect: '{}'", this.getId(), effect);

                    if ("hamster_beg_bounce".equals(effect)) {
                        if (this.getWorld().isClient()) {
                            SoundEvent bounceSound = ModSounds.getRandomSoundFrom(ModSounds.HAMSTER_BOUNCE_SOUNDS, this.random);
                            if (bounceSound != null) {
                                float basePitch = this.getSoundPitch();
                                float randomPitchAddition = this.random.nextFloat() * 0.2f;
                                float finalPitch = (basePitch * 1.2f) + randomPitchAddition; // Base pitch adjustment + random positive addition

                                // MinecraftClient.getInstance().getSoundManager().play(
                                //         new PositionedSoundInstance(
                                //                 bounceSound,            // SoundEvent
                                //                 SoundCategory.NEUTRAL,  // Category
                                //                 0.6f,                   // Volume
                                //                 finalPitch,             // Pitch
                                //                 this.random,            // Random
                                //                 this.getX(),            // X position
                                //                 this.getY(),            // Y position
                                //                 this.getZ()             // Z position
                                //         )
                                // );
                                this.clientPlaySound(bounceSound, 0.6f, finalPitch);

                                AdorableHamsterPets.LOGGER.debug("[SoundKeyframe CLIENT] Played bounce sound: {} at pitch {}", bounceSound.getId().getPath(), finalPitch);
                            } else {
                                AdorableHamsterPets.LOGGER.debug("[SoundKeyframe CLIENT] Could not get random bounce sound for 'hamster_beg_bounce'.");
                            }
                        }
                    }
                })
        );
    }


    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- Helper method to trigger animations server-side ---
    // Needs to be called from server-side logic (tick, interactMob, goals)
    public void triggerAnimOnServer(String controllerName, String animName) {
        if (!this.getWorld().isClient()) { // Ensure we're on the server
            ServerWorld serverWorld = (ServerWorld) this.getWorld();
            // Use the GeoAnimatable's built-in method for triggering server-side
            this.triggerAnim(controllerName, animName);
            // The library handles the synchronization to clients automatically.
            AdorableHamsterPets.LOGGER.trace("[HamsterEntity {}] Triggered server-side animation: Controller='{}', Anim='{}'", this.getId(), controllerName, animName);
        }
    }



    /* ──────────────────────────────────────────────────────────────────────────────
     *                           5. Protected Methods
     * ────────────────────────────────────────────────────────────────────────────*/

    // --- Data Tracker Initialization ---
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VARIANT, 0);
        builder.add(IS_SLEEPING, false);
        builder.add(IS_SITTING, false);
        builder.add(IS_BEGGING, false);
        builder.add(IS_IN_LOVE, false);
        builder.add(IS_REFUSING_FOOD, false);
        builder.add(IS_THROWN, false);
        builder.add(LEFT_CHEEK_FULL, false);
        builder.add(RIGHT_CHEEK_FULL, false);
        builder.add(IS_KNOCKED_OUT, false);
        builder.add(PINK_PETAL_TYPE, 0);
        builder.add(CHEEK_POUCH_UNLOCKED, false);
        builder.add(IS_CONSIDERING_AUTO_EAT, false);
        builder.add(DOZING_PHASE, DozingPhase.NONE.ordinal());
        builder.add(CURRENT_DEEP_SLEEP_ANIM_ID, "");
        builder.add(ACTIVE_CUSTOM_GOAL_NAME_DEBUG, "None");
        builder.add(IS_SULKING, false);
        builder.add(IS_CELEBRATING_DIAMOND, false);
        builder.add(IS_CLEANING, false);
        builder.add(ANIMATION_PERSONALITY_ID, 1);
    }

    // --- AI Goals ---
    @Override
    protected void initGoals() {
        AdorableHamsterPets.LOGGER.debug("[AI Init {} Tick {}] Initializing goals. Current State: isSleeping={}, isSittingPose={}",
                this.getId(), this.getWorld().isClient ? "ClientTick?" : this.getWorld().getTime(), this.isSleeping(), this.isInSittingPose());
        // --- 1. Initialize Goals ---
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new HamsterSeekDiamondGoal(this));
        this.goalSelector.add(2, new HamsterMeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.add(3, new HamsterMateGoal(this, 1.0D));
        this.goalSelector.add(4, new HamsterFollowOwnerGoal(this, 1.2D, 4.0F, 16.0F));
        this.goalSelector.add(5, new HamsterFleeGoal<>(this, LivingEntity.class, 8.0F, 1.0D, 1.5D));
        this.goalSelector.add(6, new HamsterTemptGoal(this, 1.2D,
                itemStack -> itemStack.isOf(ModItems.SLICED_CUCUMBER) ||
                        itemStack.isOf(ModItems.CHEESE) ||
                        itemStack.isOf(ModItems.STEAMED_GREEN_BEANS),
                false));
        this.goalSelector.add(7, new HamsterSitGoal(this));
        this.goalSelector.add(8, new HamsterSleepGoal(this));
        this.goalSelector.add(9, new HamsterWanderAroundFarGoal(this, 0.75D));
        this.goalSelector.add(10, new HamsterLookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.add(11, new HamsterLookAroundGoal(this));

        // --- Target Selector Goals ---
        this.targetSelector.add(1, new TrackOwnerAttackerGoal(this));
        this.targetSelector.add(2, new AttackWithOwnerGoal(this));
        this.targetSelector.add(3, new RevengeGoal(this).setGroupRevenge());
        // --- End Target Selector Goals ---
        // --- End 1. Initialize Goals ---
        AdorableHamsterPets.LOGGER.debug("[AI Init {} Tick {}] Finished initializing goals.",
                this.getId(), this.getWorld().isClient ? "ClientTick?" : this.getWorld().getTime());
    }

    // --- Retaliation Against Other Pets Prevention ---
    /**
    * This method is overridden to prevent the hamster from targeting (e.g., retaliating against)
    * other pets owned by its own owner.
    */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target == null) {
            super.setTarget(null);
            return;
        }

        // --- 1. Check if Tamed and Has Owner ---
        if (this.isTamed() && this.getOwner() != null) {
            LivingEntity owner = this.getOwner();
            UUID ownerUuid = owner.getUuid();

            boolean preventTargeting = false;

            // Check TameableEntity
            if (target instanceof TameableEntity tameablePet) {
                UUID petOwnerUuid = tameablePet.getOwnerUuid();
                if (petOwnerUuid != null && petOwnerUuid.equals(ownerUuid) && tameablePet != this) {
                    // AdorableHamsterPets.LOGGER.debug("[setTarget] Proposed target is a TameableEntity owned by the same player. Preventing targeting.");
                    preventTargeting = true;
                }
            }
            // Check AbstractHorseEntity
            else if (target instanceof net.minecraft.entity.passive.AbstractHorseEntity horsePet) {
                Entity horseOwnerEntity = horsePet.getOwner();
                if (horseOwnerEntity != null && horseOwnerEntity.getUuid().equals(ownerUuid)) {
                    // AdorableHamsterPets.LOGGER.debug("[setTarget] Proposed target is an AbstractHorseEntity owned by the same player. Preventing targeting.");
                    preventTargeting = true;
                }
            }
            // General Ownable Check (fallback)
            else if (target instanceof Ownable ownableFallback) {
                Entity fallbackOwnerEntity = ownableFallback.getOwner();
                if (fallbackOwnerEntity != null && fallbackOwnerEntity.getUuid().equals(ownerUuid) && ownableFallback != this) {
                    // AdorableHamsterPets.LOGGER.debug("[setTarget] Proposed target is an Ownable (fallback) owned by the same player. Preventing targeting.");
                    preventTargeting = true;
                }
            }

            if (preventTargeting) {
                super.setTarget(null);
                return;
            }
        }
        // --- End 1. Check if Tamed ---

        // --- 3. Default Behavior ---
        super.setTarget(target);
    }

    // --- Sounds ---
    @Override
    protected SoundEvent getAmbientSound() {
        // --- 0. Knocked Out Check (Silence) ---
        if (this.isKnockedOut()) {
            return null; // Knocked out hamsters make no ambient sounds
        }
        // --- 1. Begging Sounds ---
        if (this.isBegging()) {
            return getRandomSoundFrom(ModSounds.HAMSTER_BEG_SOUNDS, this.random);
        }
        // --- 2. Sleep Sounds ---
        boolean playSleepSounds = false;
        if (this.isTamed()) {
            DozingPhase phase = this.getDozingPhase();
            // Play sleep sounds if drifting, settling, or in deep sleep
            if (phase == DozingPhase.DRIFTING_OFF || phase == DozingPhase.SETTLING_INTO_SLUMBER || phase == DozingPhase.DEEP_SLEEP) {
                playSleepSounds = true;
            }
        } else { // Wild hamster
            if (this.isSleeping()) { // Checks the IS_SLEEPING DataTracker for wild hamsters
                playSleepSounds = true;
            }
        }
        if (playSleepSounds) {
            return getRandomSoundFrom(ModSounds.HAMSTER_SLEEP_SOUNDS, this.random);
        }
        // --- 3. Idle Sounds (Default) ---
        return getRandomSoundFrom(ModSounds.HAMSTER_IDLE_SOUNDS, this.random);
    }

    @Override
    public void playAmbientSound() {
        SoundEvent soundEvent = this.getAmbientSound();
        // Check if the selected sound is a begging sound
        if (soundEvent != null && Arrays.asList(ModSounds.HAMSTER_BEG_SOUNDS).contains(soundEvent)) {
            // If it's a begging sound, play it with lower volume
            this.playSound(soundEvent, 0.8F, this.getSoundPitch());
        } else {
            // For all other sounds, use the default behavior
            super.playAmbientSound();
        }
    }

    @Override protected SoundEvent getHurtSound(DamageSource source) { return getRandomSoundFrom(ModSounds.HAMSTER_HURT_SOUNDS, this.random); }

    @Override protected SoundEvent getDeathSound() { return getRandomSoundFrom(ModSounds.HAMSTER_DEATH_SOUNDS, this.random); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (this.getWorld().isClient()) {
            return; // Server-side only
        }
        // Get the render state attachment
        HamsterRenderState renderState = this.getAttached(ModEntityAttachments.HAMSTER_RENDER_STATE);
        // Only play the fallback sound if the hamster is NOT being rendered by any player
        if (renderState == null || !renderState.isBeingRenderedByAnyPlayer()) {
            try {
                BlockSoundGroup group = state.getSoundGroup();

                // Determine volume based on block type
                float volume = state.isOf(Blocks.GRAVEL)
                        ? (DEFAULT_FOOTSTEP_VOLUME * GRAVEL_VOLUME_MODIFIER)
                        : DEFAULT_FOOTSTEP_VOLUME;

                this.playSound(group.getStepSound(), volume, group.getPitch() * 1.5F);
            } catch (Exception ex) {
                AdorableHamsterPets.LOGGER.warn("Error playing fallback step sound", ex);
            }
        }
    }

    protected boolean canHitEntity(Entity entity) {
        // --- 1. Check if Entity Can Be Hit ---
        // Allow hitting armor stands specifically
        if (entity instanceof net.minecraft.entity.decoration.ArmorStandEntity) {
            return !entity.isSpectator(); // Can hit non-spectator armor stands
        }

        // Original logic for other entities
        if (!entity.isSpectator() && entity.isAlive() && entity.canHit()) {
            Entity owner = this.getOwner();
            // Prevent hitting self or owner or entities owner is riding
            return entity != this && (owner == null || !owner.isConnectedThroughVehicle(entity));
        }
        return false;
        // --- End 1. Check if Entity Can Be Hit ---
    }

    @Override protected void applyGravity() { if (this.isThrown() && this.hasNoGravity()) return; super.applyGravity(); }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
        // Assign Animation Personality
        this.dataTracker.set(ANIMATION_PERSONALITY_ID, this.random.nextBetween(1, 3));
        // Apply biome variants for natural spawns, spawn eggs, AND chunk generation
        if (spawnReason == SpawnReason.NATURAL || spawnReason == SpawnReason.SPAWN_EGG || spawnReason == SpawnReason.CHUNK_GENERATION) {
            RegistryEntry<Biome> biomeEntry = world.getBiome(this.getBlockPos());
            String biomeKeyStr = biomeEntry.getKey().map(key -> key.getValue().toString()).orElse("UNKNOWN");
            AdorableHamsterPets.LOGGER.trace("[HamsterInit] SpawnReason: {}, BiomeKey: {}", spawnReason, biomeKeyStr); // Keep this log

            HamsterVariant chosenVariant = determineVariantForBiome(biomeEntry, this.random);
            this.setVariant(chosenVariant.getId());
            AdorableHamsterPets.LOGGER.trace("[HamsterInit] Assigned variant: {}", chosenVariant.name());

        } else {
            // Fallback for other spawns (command, breeding, structure, etc.)
            int randomVariantId = this.random.nextInt(HamsterVariant.values().length);
            this.setVariant(randomVariantId);
            AdorableHamsterPets.LOGGER.trace("[HamsterInit] SpawnReason: {}, Assigned random variant: {}",
                    spawnReason, HamsterVariant.byId(randomVariantId).name());
        }

        // Always update cheek trackers on initialization
        this.updateCheekTrackers();

        // Call and return the super method's result
        return super.initialize(world, difficulty, spawnReason, entityData);
    }



    /* ──────────────────────────────────────────────────────────────────────────────
     *                       6. Private Helper Methods
     * ────────────────────────────────────────────────────────────────────────────*/

    /**
     * Checks if the given item stack is disallowed in the hamster's inventory based on a disallow list.
     * @param stack The ItemStack to check.
     * @return True if the item is explicitly disallowed, false otherwise.
     */
    public boolean isItemDisallowed(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();

        // --- 1. Explicit item + tag bans ---
        if (DISALLOWED_ITEMS.contains(item)) return true;
        for (TagKey<Item> tag : DISALLOWED_ITEM_TAGS)
            if (stack.isIn(tag)) return true;

        // --- 2. Global block‑size rule ---
        if (item instanceof BlockItem) {
            // Any block not on the tiny‑block whitelist is too big
            return !stack.isIn(ModItemTags.ALLOWED_POUCH_BLOCKS);
        }

        // --- 3. Spawn eggs ---
        return item instanceof SpawnEggItem;
    }

    private RegistryWrapper.WrapperLookup getRegistryLookup() {
        return this.getWorld().getRegistryManager();
    }

    private boolean tryTame(PlayerEntity player, ItemStack itemStack) {
        // --- 1. Taming Attempt ---
        if (!player.getAbilities().creativeMode) {
            itemStack.decrement(1);
        }

        // --- Use Config Value for Taming Chance ---
        final AhpConfig config = AdorableHamsterPets.CONFIG;
        int denominator = Math.max(1, config.tamingChanceDenominator.get()); // Ensure denominator is at least 1
        if (this.random.nextInt(denominator) == 0) {
            // --- End Use Config Value ---
            this.setOwnerUuid(player.getUuid());
            this.setTamed(true, true);
            this.navigation.stop();
            this.setSitting(false);
            this.setSleeping(false);
            this.setTarget(null);
            this.getWorld().sendEntityStatus(this, (byte) 7);

            // Play celebrate sound only on success
            SoundEvent celebrateSound = getRandomSoundFrom(HAMSTER_CELEBRATE_SOUNDS, this.random);
            this.getWorld().playSound(null, this.getBlockPos(), celebrateSound, SoundCategory.NEUTRAL, 0.7F, 1.0F);

            if (player instanceof ServerPlayerEntity serverPlayer) {
                Criteria.TAME_ANIMAL.trigger(serverPlayer, this);
            }

            return true;
        } else {
            this.getWorld().sendEntityStatus(this, (byte) 6);
            return false;
        }
        // --- End 1. Taming Attempt ---
    }

    // --- Check for Repeatable Foods ---
    private boolean checkRepeatFoodRefusal(ItemStack currentStack, PlayerEntity player) {
        // --- 1. Check Repeat Food Refusal ---
        if (REPEATABLE_FOODS.contains(currentStack.getItem())) return false;
        if (!this.lastFoodItem.isEmpty() && ItemStack.areItemsEqual(this.lastFoodItem, currentStack)) {
            this.setRefusingFood(true);
            this.refuseTimer = REFUSE_FOOD_TIMER_TICKS;
            player.sendMessage(Text.literal("Hamster wants to try something different."), true);
            // --- Trigger Refusal Animation ---
            if (!this.getWorld().isClient()) {
                this.triggerAnimOnServer("mainController", "no");
            }
            // --- End Trigger ---
            return true;
        }
        return false;
        // --- End 1. Check Repeat Food Refusal ---
    }
    // --- End Check for Repeatable Foods ---

    /**
     * Attempts to feed the hamster when interacted with by its owner.
     * Handles healing, breeding initiation, and buff application.
     *
     * @param player The player feeding the hamster.
     * @param stack  The ItemStack being used for feeding.
     * @return True if the feeding action (healing, breeding, buff) was successfully processed, false otherwise.
     */
    private boolean tryFeedingAsTamed(PlayerEntity player, ItemStack stack) {
        // --- 1. Initial Setup & Logging ---
        boolean isFood = isIsFood(stack);
        boolean isBuffItem = stack.isOf(ModItems.STEAMED_GREEN_BEANS);
        boolean canHeal = this.getHealth() < this.getMaxHealth();
        boolean readyToBreed = this.getBreedingAge() == 0 && !this.isInCustomLove(); // Check custom love timer
        World world = this.getWorld();
        final AhpConfig config = AdorableHamsterPets.CONFIG;
        boolean actionTaken = false; // Initialize return value


        AdorableHamsterPets.LOGGER.debug("[FeedAttempt {} Tick {}] Entering tryFeedingAsTamed. Item: {}, isFood={}, isBuff={}, canHeal={}, breedingAge={}, isInCustomLove={}, readyToBreed={}",
                this.getId(), world.getTime(), stack.getItem(), isFood, isBuffItem, canHeal, this.getBreedingAge(), this.isInCustomLove(), readyToBreed);


        if (!isFood && !isBuffItem) {
            AdorableHamsterPets.LOGGER.debug("[FeedAttempt {} Tick {}] Item is not valid food or buff item. Returning false.", this.getId(), world.getTime());
            return false; // Not a valid item for feeding
        }
        // --- End 1. Initial Setup & Logging ---


        // --- 2. Steamed Green Beans Logic ---
        if (isBuffItem) {
            long currentTime = world.getTime();
            if (this.steamedBeansCooldownEndTick > currentTime) {
                // Still on cooldown
                long remainingTicks = this.steamedBeansCooldownEndTick - currentTime;
                long totalSecondsRemaining = remainingTicks / 20;
                long minutes = totalSecondsRemaining / 60;
                long seconds = totalSecondsRemaining % 60;
                player.sendMessage(Text.translatable("message.adorablehamsterpets.beans_cooldown", minutes, seconds).formatted(Formatting.RED), true);
                AdorableHamsterPets.LOGGER.debug("[FeedAttempt {} Tick {}] Buff item used, but on cooldown ({} ticks remaining). Returning false.", this.getId(), world.getTime(), remainingTicks);
                return false; // Action failed due to cooldown
            } else {
                // Apply Buffs
                int duration = config.greenBeanBuffDuration.get();
                int speedAmplifier = config.greenBeanBuffAmplifierSpeed.get();
                int strengthAmplifier = config.greenBeanBuffAmplifierStrength.get();
                int absorptionAmplifier = config.greenBeanBuffAmplifierAbsorption.get();
                int regenAmplifier = config.greenBeanBuffAmplifierRegen.get();
                this.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, speedAmplifier));
                this.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, duration, strengthAmplifier));
                this.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, duration, absorptionAmplifier));
                this.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, duration, regenAmplifier));


                // Play sound
                SoundEvent buffSound = getRandomSoundFrom(ModSounds.HAMSTER_CELEBRATE_SOUNDS, this.random);
                world.playSound(null, this.getBlockPos(), buffSound, SoundCategory.NEUTRAL, 1.0F, 1.0F);


                // Set cooldown
                this.steamedBeansCooldownEndTick = currentTime + config.steamedGreenBeansBuffCooldown.get();
                actionTaken = true; // Action was successful
                AdorableHamsterPets.LOGGER.debug("[FeedAttempt {} Tick {}] Applied buffs from Steamed Green Beans. Cooldown set to {}. Returning true.", this.getId(), world.getTime(), this.steamedBeansCooldownEndTick);

                // Trigger Fed Steamed Beans Criterion
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    ModCriteria.FED_HAMSTER_STEAMED_BEANS.trigger(serverPlayer, this);
                }
            }
        }
        // --- End 2. Steamed Green Beans Logic ---


        // --- 3. Handle Standard Food (Healing/Breeding and Pouch Unlock) ---
        else if (isFood) { // This implies stack.getItem() is in HAMSTER_FOODS
            boolean wasHealedOrBredThisTime = false; // Local flag for this feeding instance

            if (canHeal) {
                this.heal(config.standardFoodHealing.get());
                actionTaken = true;
                wasHealedOrBredThisTime = true;
                AdorableHamsterPets.LOGGER.debug("[FeedAttempt {}] Healed with standard food.", this.getId());
            } else if (readyToBreed) {
                this.setSitting(false, true);
                this.setCustomInLove(player);
                this.setInLove(true);
                actionTaken = true;
                wasHealedOrBredThisTime = true;
                AdorableHamsterPets.LOGGER.debug("[FeedAttempt {}] Entered love mode with standard food.", this.getId());
            }

            // --- Unlock Cheek Pouch if Hamster Food Mix was fed AND a healing/breeding action occurred ---
            if (wasHealedOrBredThisTime && stack.isOf(ModItems.HAMSTER_FOOD_MIX)) {
                if (!this.dataTracker.get(CHEEK_POUCH_UNLOCKED)) {
                    this.dataTracker.set(CHEEK_POUCH_UNLOCKED, true);
                    AdorableHamsterPets.LOGGER.debug("Hamster {} cheek pouch unlocked by food mix.", this.getId());
                    if (player instanceof ServerPlayerEntity serverPlayer) {
                        ModCriteria.CHEEK_POUCH_UNLOCKED.trigger(serverPlayer, this);
                    }
                    world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.5f, 1.5f);
                    if (!world.isClient) {
                        ((ServerWorld) world).spawnParticles(
                                new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(ModItems.HAMSTER_FOOD_MIX)),
                                this.getX(), this.getBodyY(0.2D), this.getZ(),
                                10, 0.25D, 0.15D, 0.25D, 0.20D
                        );
                    }
                }
            }
            // --- End Unlock Cheek Pouch ---

            if (!actionTaken) { // If not healed and not bred (e.g., full health, not ready to breed)
                AdorableHamsterPets.LOGGER.debug("[FeedAttempt {}] Standard food used, but no action (heal/breed) taken.", this.getId());
            }
        }
        // --- End 3. Handle Standard Food ---
        return actionTaken;
    }

    // --- Tamed Sleep Sequence Helper Methods ---
    /**
     * Checks if the conditions are met for a tamed, sitting hamster to potentially start becoming drowsy.
     * Conditions: Daytime (if configured), no nearby hostile entities, on solid ground, not in love mode.
     * @return True if conditions are met, false otherwise.
     */
    @Unique
    private boolean checkConditionsForInitiatingDrowsiness() {
        if (!this.dataTracker.get(IS_SITTING)) return false; // Must be player-commanded to sit

        World world = this.getWorld();
        if (Configs.AHP.requireDaytimeForTamedSleep && !world.isDay()) {
            return false; // Must be daytime if config requires it
        }
        if (this.isInLove()) return false; // Cannot sleep if in love mode
        if (!this.isOnGround()) return false; // Must be on safe, solid ground

        // Check for nearby hostile entities
        double threatRadius = Configs.AHP.tamedSleepThreatDetectionRadiusBlocks.get();
        List<LivingEntity> nearbyHostiles = world.getEntitiesByClass(
                LivingEntity.class,
                this.getBoundingBox().expand(threatRadius),
                entity -> entity instanceof HostileEntity && entity.isAlive() && !entity.isSpectator()
        );
        return nearbyHostiles.isEmpty(); // No hostiles nearby
    }


    /**
     * Checks if the conditions are met to sustain any phase of the slumber sequence (Drifting, Settling, Deep Sleep).
     * These are generally the same as initiating, but crucially, the hamster must *remain* sitting.
     * @return True if conditions are met, false otherwise.
     */
    @Unique
    private boolean checkConditionsForSustainingSlumber() {
        // Includes all checks from initiating, plus ensures it's still in a sitting pose.
        // The IS_SITTING datatracker is the primary driver for player-commanded sitting.
        return this.dataTracker.get(IS_SITTING) && checkConditionsForInitiatingDrowsiness();
    }

    /**
     * Resets the hamster's sleep sequence state to NONE and clears associated timers.
     * Called when the sleep sequence is interrupted.
     * @param reason A debug message explaining why the sequence was reset.
     */
    @Unique
    private void resetSleepSequence(String reason) {
        AdorableHamsterPets.LOGGER.debug("Hamster {} resetting sleep sequence: {}. Current phase was: {}", this.getId(), reason, this.getDozingPhase());
        this.setDozingPhase(DozingPhase.NONE);
        this.quiescentSitDurationTimer = 0;
        this.driftingOffTimer = 0;
        this.settleSleepAnimationCooldown = 0;
    }

    /**
     * Manages the lifecycle of the cleaning sound on the client.
     * If the hamster is cleaning and no sound is playing, it starts a new
     * {@link HamsterCleaningSoundInstance}. This ensures the sound loops
     * correctly for the duration of the cleaning animation.
     */
    @Environment(EnvType.CLIENT)
    private void tickCleaningSound() {
        if (!this.getWorld().isClient()) return;

        // Check the synchronized DataTracker to decide if a sound should be playing
        boolean shouldBeCleaning = this.dataTracker.get(IS_CLEANING);
        boolean isSoundPlaying = this.cleaningSoundInstance != null && !this.cleaningSoundInstance.isDone();

        // If it should be cleaning but no sound is playing, start one.
        if (shouldBeCleaning && !isSoundPlaying) {
            this.cleaningSoundInstance = new HamsterCleaningSoundInstance(this);
            MinecraftClient.getInstance().getSoundManager().play(this.cleaningSoundInstance);
        }
    }
    
    @Environment(EnvType.CLIENT)
    private void clientPlaySound(SoundEvent sound, float volume, float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(
            new PositionedSoundInstance(
                sound,                  // SoundEvent
                SoundCategory.NEUTRAL,  // Category
                volume,                 // Volume
                pitch,                  // Pitch
                this.random,            // Random
                this.getX(),            // X position
                this.getY(),            // Y position
                this.getZ()             // Z position
            )
        );
    }
}