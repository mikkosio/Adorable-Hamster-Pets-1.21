package net.dawson.adorablehamsterpets.world.gen;

import net.dawson.adorablehamsterpets.AdorableHamsterPets;
import net.dawson.adorablehamsterpets.config.AhpConfig;
import net.dawson.adorablehamsterpets.config.Configs;
import net.dawson.adorablehamsterpets.entity.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class ModEntitySpawns {

    // --- Constants and Static Utilities ---
    private static final Set<Block> VALID_SPAWN_BLOCKS = new HashSet<>();

    static {
        // --- Valid Spawn Blocks Initialization ---
        VALID_SPAWN_BLOCKS.add(Blocks.SAND);
        VALID_SPAWN_BLOCKS.add(Blocks.RED_SAND);
        VALID_SPAWN_BLOCKS.add(Blocks.TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.WHITE_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.ORANGE_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.MAGENTA_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.LIGHT_BLUE_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.YELLOW_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.LIME_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.PINK_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.GRAY_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.LIGHT_GRAY_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.CYAN_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.PURPLE_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.BLUE_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.BROWN_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.GREEN_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.RED_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.BLACK_TERRACOTTA);
        VALID_SPAWN_BLOCKS.add(Blocks.STONE);
        VALID_SPAWN_BLOCKS.add(Blocks.ANDESITE);
        VALID_SPAWN_BLOCKS.add(Blocks.DIORITE);
        VALID_SPAWN_BLOCKS.add(Blocks.GRANITE);
        VALID_SPAWN_BLOCKS.add(Blocks.GRAVEL);
        VALID_SPAWN_BLOCKS.add(Blocks.DIRT);
        VALID_SPAWN_BLOCKS.add(Blocks.COARSE_DIRT);
        VALID_SPAWN_BLOCKS.add(Blocks.PODZOL);
        VALID_SPAWN_BLOCKS.add(Blocks.SNOW_BLOCK);
        // --- End Valid Spawn Blocks Initialization ---
    }

    /**
     * Creates a predicate that checks if a biome context matches any of the given biome keys.
     * @param keys The biome keys to match against.
     * @return A predicate for biome selection.
     */
    private static Predicate<BiomeSelectionContext> biomeKeyPredicate(RegistryKey<Biome>... keys) {
        return context -> {
            for (RegistryKey<Biome> key : keys) {
                if (context.getBiomeKey().equals(key)) {
                    return true;
                }
            }
            return false;
        };
    }

    // --- Public Methods ---

    /**
     * Adds hamster spawn modifications to biomes and registers spawn restrictions.
     * Hamster spawn weight and max group size are determined by mod configuration.
     * Spawning is restricted to specific biomes suitable for different hamster variants.
     */
    public static void addSpawns() {
        // --- Define Biome Selectors ---
        // These predicates define categories of biomes where hamsters can spawn.
        Predicate<BiomeSelectionContext> snowySelector = biomeKeyPredicate(
                BiomeKeys.SNOWY_PLAINS, BiomeKeys.SNOWY_TAIGA, BiomeKeys.SNOWY_SLOPES,
                BiomeKeys.FROZEN_PEAKS, BiomeKeys.JAGGED_PEAKS,
                BiomeKeys.GROVE, BiomeKeys.FROZEN_RIVER, BiomeKeys.SNOWY_BEACH,
                BiomeKeys.FROZEN_OCEAN, BiomeKeys.DEEP_FROZEN_OCEAN
        );
        Predicate<BiomeSelectionContext> iceSpikesSelector = biomeKeyPredicate(BiomeKeys.ICE_SPIKES);
        Predicate<BiomeSelectionContext> cherryGroveSelector = biomeKeyPredicate(BiomeKeys.CHERRY_GROVE);

        Predicate<BiomeSelectionContext> caveSelector = biomeKeyPredicate(
                BiomeKeys.LUSH_CAVES, BiomeKeys.DRIPSTONE_CAVES
        );
        Predicate<BiomeSelectionContext> swampSelector = biomeKeyPredicate(
                BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP
        );
        Predicate<BiomeSelectionContext> desertSelector = biomeKeyPredicate(BiomeKeys.DESERT);
        Predicate<BiomeSelectionContext> badlandsSelector = BiomeSelectors.tag(BiomeTags.IS_BADLANDS);
        Predicate<BiomeSelectionContext> beachSelector = BiomeSelectors.tag(BiomeTags.IS_BEACH)
                .and(snowySelector.negate()); // Exclude snowy beaches from this general beach category
        Predicate<BiomeSelectionContext> forestSelector = BiomeSelectors.tag(BiomeTags.IS_FOREST)
                .and(cherryGroveSelector.negate()); // Exclude Cherry Grove if it's also IS_FOREST
        Predicate<BiomeSelectionContext> taigaSelector = BiomeSelectors.tag(BiomeTags.IS_TAIGA)
                .and(snowySelector.negate()); // Exclude snowy taigas
        Predicate<BiomeSelectionContext> savannaSelector = BiomeSelectors.tag(BiomeTags.IS_SAVANNA);
        Predicate<BiomeSelectionContext> plainsSelector = biomeKeyPredicate(
                BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.MEADOW
        );
        Predicate<BiomeSelectionContext> mountainSelector = BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN)
                .and(snowySelector.negate())
                .and(iceSpikesSelector.negate()); // Exclude snowy mountains and ice spikes
        // --- End Define Biome Selectors ---

        // --- Combine Selectors ---
        // An OR combination of all defined selectors to get the final set of biomes for hamster spawning.
        Predicate<BiomeSelectionContext> combinedSelector = snowySelector
                .or(iceSpikesSelector)
                .or(cherryGroveSelector)
                .or(caveSelector)
                .or(swampSelector)
                .or(desertSelector)
                .or(badlandsSelector)
                .or(beachSelector)
                .or(forestSelector)
                .or(taigaSelector)
                .or(savannaSelector)
                .or(plainsSelector)
                .or(mountainSelector);
        // --- End Combine Selectors ---

        // --- Add Spawn Modification ---
        // Access the mod's configuration.
        final AhpConfig config = AdorableHamsterPets.CONFIG;

        BiomeModifications.addSpawn(
                combinedSelector,
                SpawnGroup.CREATURE,
                ModEntities.HAMSTER,
                config.spawnWeight.get(),
                1, // Min group size (Hardcoded, so not accessing config here)
                config.maxGroupSize.get()
        );
        // --- End Add Spawn Modification ---

        // --- Spawn Restriction ---
        // Defines where hamsters can physically spawn within the selected biomes.
        SpawnRestriction.register(
                ModEntities.HAMSTER,
                SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                (entityType, world, spawnReason, pos, random) -> {
                    // Allow spawning if vanilla conditions are met OR if the block below is in our custom valid set.
                    // Light level check is not purposefully not present to allow cave spawns.
                    if (AnimalEntity.isValidNaturalSpawn(entityType, world, spawnReason, pos, random)) {
                        return true;
                    }
                    Block blockBelow = world.getBlockState(pos.down()).getBlock();
                    return VALID_SPAWN_BLOCKS.contains(blockBelow);
                }
        );
        // --- End Spawn Restriction ---
    }
}