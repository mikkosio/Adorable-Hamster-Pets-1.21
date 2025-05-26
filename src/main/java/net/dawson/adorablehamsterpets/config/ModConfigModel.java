package net.dawson.adorablehamsterpets.config;

import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Modmenu;
import io.wispforest.owo.config.annotation.Nest;
import io.wispforest.owo.config.annotation.RangeConstraint;
import io.wispforest.owo.config.annotation.SectionHeader;
import blue.endless.jankson.Comment;
import net.dawson.adorablehamsterpets.AdorableHamsterPets;

@Modmenu(modId = AdorableHamsterPets.MOD_ID)
@Config(name = AdorableHamsterPets.MOD_ID, wrapperName = "ModConfig")
public class ModConfigModel {

    // --- Top-Level Sections ---
    @SectionHeader("Hamster Spawning")
    @Nest
    public HamsterSpawningSettings hamsterSpawning = new HamsterSpawningSettings();

    @SectionHeader("Hamster Behavior & Care") // Broadened title slightly
    @Nest
    public HamsterBehaviorSettings hamsterBehavior = new HamsterBehaviorSettings();

    @SectionHeader("Feature Toggles & Mechanics")
    @Nest
    public FeatureToggleSettings featureToggles = new FeatureToggleSettings();

    @SectionHeader("World Generation Tweaks") // Renamed for clarity
    @Nest
    public WorldGenAdjustmentSettings worldGenAdjustments = new WorldGenAdjustmentSettings();

    @SectionHeader("UI & Quality of Life")
    @Nest
    public UiPreferenceSettings uiPreferences = new UiPreferenceSettings();

    // --- Nested Classes Defining the Options ---

    public static class HamsterSpawningSettings {
        @Comment("Adjusts hamster spawn frequency. Higher = more chaos. 0 = blissful silence.")
        @RangeConstraint(min = 0, max = 100)
        public int spawnWeight = 30;

        @Comment("Maximum hamsters per spawn group. Because sometimes one just isn't cute enough.")
        @RangeConstraint(min = 1, max = 10)
        public int maxGroupSize = 1;
    }

    public static class HamsterBehaviorSettings {
        @SectionHeader("Combat & Damage") // Sub-header
        @Comment("Tamed hamster melee damage. Mostly for show, let's be honest.")
        @RangeConstraint(min = 0.0, max = 40.0)
        public double meleeDamage = 2.0;

        @Comment("Damage dealt by thrown hamster. Surprisingly, the default value is exactly enough to kill a creeper. What a coincidence.")
        @RangeConstraint(min = 0.0f, max = 40.0f) // Changed from float to double for consistency with meleeDamage
        public double hamsterThrowDamage = 20.0;

        @SectionHeader("Interaction & General Care") // Sub-header
        @Comment("Taming difficulty (1 in X chance). Higher means more cucumbers sacrificed to the RNG gods.")
        @RangeConstraint(min = 1, max = 20)
        public int tamingChanceDenominator = 3;

        @Comment("Breeding cooldown (ticks) for hamsters. They need their space. (20 ticks = 1s)")
        @RangeConstraint(min = 600, max = 24000) // 30s to 20m
        public int breedingCooldownTicks = 6000;

        @SectionHeader("Food Effects & Healing") // Sub-header
        @Comment("Healing amount from Hamster Food Mix. The good stuff.")
        @RangeConstraint(min = 0.0f, max = 10.0f)
        public float hamsterFoodMixHealing = 4.0f;

        @Comment("Healing from basic seeds/crops. Better than nothing... probably.")
        @RangeConstraint(min = 0.0f, max = 5.0f)
        public float standardFoodHealing = 2.0f;

        @Comment("Duration (ticks) of the Steamed Green Bean buff. Fleeting power. (20 ticks = 1s)")
        @RangeConstraint(min = 20, max = 20 * 60 * 10) // 1s to 10m
        public int greenBeanBuffDuration = 3600;

        @Comment("Speed boost level (0=I, 1=II...) from beans. Gotta go fast?")
        @RangeConstraint(min = 0, max = 4)
        public int greenBeanBuffAmplifierSpeed = 1;

        @Comment("Strength boost level (0=I, 1=II...) from beans. For slightly mightier nibbles.")
        @RangeConstraint(min = 0, max = 4)
        public int greenBeanBuffAmplifierStrength = 1;

        @Comment("Absorption boost level (0=I, 1=II...) from beans. Extra fluff padding.")
        @RangeConstraint(min = 0, max = 4)
        public int greenBeanBuffAmplifierAbsorption = 1;

        @Comment("Regeneration boost level (0=I, 1=II...) from beans. Heals minor paper-cuts.")
        @RangeConstraint(min = 0, max = 4)
        public int greenBeanBuffAmplifierRegen = 0;

        @SectionHeader("Cooldowns & Timers") // Sub-header for remaining cooldowns and sleep timers
        @Comment("Minimum time (seconds) a tamed, sitting hamster will wait before *considering* a nap. If you're impatient, crank it down.")
        @RangeConstraint(min = 1, max = 300) // Min 1s, Max 5m
        public int tamedQuiescentSitMinSeconds = 120;

        @Comment("Maximum time (seconds) a tamed, sitting hamster will wait. Because even digital rodents need their beauty sleep, eventually.")
        @RangeConstraint(min = 2, max = 600) // Min 2s (must be >= minQuiescentSitMinSeconds practical lower bound), Max 10m
        public int tamedQuiescentSitMaxSeconds = 180;

        @Comment("Cooldown (ticks) after using a hamster as a projectile. Give it a moment. (20 ticks = 1s)")
        @RangeConstraint(min = 20, max = 20 * 60 * 10) // 1s to 10m
        public int hamsterThrowCooldown = 2400;

        @Comment("Cooldown (ticks) before Steamed Green Bean buffs can be reapplied. Patience. (20 ticks = 1s)")
        @RangeConstraint(min = 20, max = 20 * 60 * 10) // 1s to 10m
        public int steamedGreenBeansBuffCooldown = 6000;
    }

    public static class FeatureToggleSettings { // Renamed from FeatureTogglesAndMechanics
        @SectionHeader("Core Gameplay Features") // Sub-header
        @Comment("Enable hamster throwing? ('G' key default). Use responsibly. Or don't.")
        public boolean enableHamsterThrowing = true;

        @Comment("Unlock cheek pouches with 'Hamster Food Mix'? If false, pouches are always usable. Note: Toggling this ON after it was OFF will re-lock pouches for hamsters not yet fed Food Mix.")
        public boolean requireFoodMixToUnlockCheeks = true;

        @SectionHeader("Shoulder Pet Alerts") // Sub-header
        @Comment("Allow shoulder hamsters to detect Creepers? Might save your inventory.")
        public boolean enableShoulderCreeperDetection = true;
        @Comment("Shoulder Creeper detection radius (blocks). Adjust paranoia levels.")
        @RangeConstraint(min = 1.0, max = 64.0)
        public double shoulderCreeperDetectionRadius = 16.0;

        @Comment("Allow shoulder hamsters to detect Diamonds? Or do you enjoy the surprise?")
        public boolean enableShoulderDiamondDetection = true;
        @Comment("Shoulder Diamond detection radius (blocks). How close do you need to be?")
        @RangeConstraint(min = 1.0, max = 32.0)
        public double shoulderDiamondDetectionRadius = 10.0;

        @SectionHeader("Sleep Mechanics Details") // Sub-header for sleep toggles/details
        @Comment("How close (blocks) a hostile mob can be before your napping hamster rudely awakens. Or decides napping wasn't a good idea.")
        @RangeConstraint(min = 1, max = 32)
        public int tamedSleepThreatDetectionRadiusBlocks = 8;

        @Comment("Must it be daytime for your tamed, sitting hamster to drift off? If false, they'll happily snooze through the night. Lazy bums.")
        public boolean requireDaytimeForTamedSleep = true;
    }

    public static class WorldGenAdjustmentSettings { // Renamed from WorldGenSettings
        @Comment("Sunflower seed regrowth speed. Higher values make it slower; lower values make it faster. Makes perfect sense.")
        @RangeConstraint(min = 0.1, max = 5.0)
        public double sunflowerRegrowthModifier = 1.0;

        @Comment("Wild bush seed regrowth speed. Higher values make it slower; lower values make it faster. Makes perfect sense.")
        @RangeConstraint(min = 0.1, max = 5.0)
        public double wildBushRegrowthModifier = 1.0;

        @Comment("Wild green bean bush rarity (1 in X chunks). WARNING: Low values (1-5) might cause... bush spam.")
        @RangeConstraint(min = 1, max = 100)
        public int wildGreenBeanBushRarity = 24;

        @Comment("Wild cucumber bush rarity (1 in X chunks). WARNING: Ditto on the bush incidents.")
        @RangeConstraint(min = 1, max = 100)
        public int wildCucumberBushRarity = 24;
    }

    public static class UiPreferenceSettings { // Renamed from UiSettings
        @Comment("Show helpful tooltips on hamster-related items and blocks? Your call. I'm not your conscience.")
        public boolean enableItemTooltips = true;

        @Comment("Automatically give players the 'Hamster Tips' guidebook when they first join a world.")
        public boolean enableAutoGuidebookDelivery = true;

        @Comment("Show a message when your hamster dismounts from your shoulder? Some find it helpful, others... less so.")
        public boolean enableShoulderDismountMessages = true;
    }
}