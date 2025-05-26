package net.dawson.adorablehamsterpets.block;

import net.dawson.adorablehamsterpets.AdorableHamsterPets;
import net.dawson.adorablehamsterpets.block.custom.*;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public class ModBlocks {

    // --- 1. Constants and Static Block Definitions ---
    public static final Block GREEN_BEANS_CROP = Registry.register(Registries.BLOCK,
            Identifier.of(AdorableHamsterPets.MOD_ID, "green_beans_crop"),
            new GreenBeansCropBlock(AbstractBlock.Settings.copy(Blocks.WHEAT).sounds(BlockSoundGroup.CROP).nonOpaque().noCollision()));

    public static final Block CUCUMBER_CROP = Registry.register(Registries.BLOCK,
            Identifier.of(AdorableHamsterPets.MOD_ID, "cucumber_crop"),
            new CucumberCropBlock(AbstractBlock.Settings.copy(Blocks.WHEAT).sounds(BlockSoundGroup.CROP).nonOpaque().noCollision()));

    public static final Block WILD_GREEN_BEAN_BUSH = registerBlock("wild_green_bean_bush",
            new WildGreenBeanBushBlock(AbstractBlock.Settings.copy(Blocks.SWEET_BERRY_BUSH)
                    .nonOpaque()
                    .noCollision()
                    .ticksRandomly()
                    .sounds(BlockSoundGroup.SWEET_BERRY_BUSH)));

    public static final Block WILD_CUCUMBER_BUSH = registerBlock("wild_cucumber_bush",
            new WildCucumberBushBlock(AbstractBlock.Settings.copy(Blocks.SWEET_BERRY_BUSH)
                    .nonOpaque()
                    .noCollision()
                    .ticksRandomly()
                    .sounds(BlockSoundGroup.SWEET_BERRY_BUSH)));

    public static final Block SUNFLOWER_BLOCK = registerBlock("sunflower_block",
            new SunflowerBlock(AbstractBlock.Settings.copy(Blocks.SUNFLOWER).nonOpaque()));
    // --- End 1. Constants and Static Block Definitions ---

    // --- 2. Public Methods (Registration) ---
    public static void registerModBlocks() {
        AdorableHamsterPets.LOGGER.info("Registering ModBlocks for " + AdorableHamsterPets.MOD_ID);
        // Static initializers handle the actual registration when the class is loaded.
    }
    // --- End 2. Public Methods ---

    // --- 3. Private Helper Methods ---
    private static Block registerBlock(String name, Block block) {
        registerBlockItemWithTooltip(name, block); // Changed to use the more specific helper
        return Registry.register(Registries.BLOCK, Identifier.of(AdorableHamsterPets.MOD_ID, name), block);
    }

    private static Item registerBlockItemWithTooltip(String name, Block block) {
        Item item = new BlockItem(block, new Item.Settings()) {
            @Override
            public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
                // --- Config Check for Tooltips ---
                if (AdorableHamsterPets.CONFIG.uiPreferences.enableItemTooltips()) {
                    String translationKey = this.getTranslationKey(stack); // e.g., "block.adorablehamsterpets.wild_green_bean_bush"
                    // Check which block it is to apply specific hints
                    if (block instanceof WildGreenBeanBushBlock || block instanceof WildCucumberBushBlock) {
                        tooltip.add(Text.translatable(translationKey + ".hint1").formatted(Formatting.YELLOW));
                        tooltip.add(Text.translatable(translationKey + ".hint2").formatted(Formatting.GRAY));
                    } else if (block instanceof SunflowerBlock) {
                        tooltip.add(Text.translatable(translationKey + ".hint1").formatted(Formatting.YELLOW));
                        tooltip.add(Text.translatable(translationKey + ".hint2").formatted(Formatting.GRAY));
                    }
                } else {
                    tooltip.add(Text.literal("Adorable Hamster Pets").formatted(Formatting.BLUE, Formatting.ITALIC));
                }
                // --- End Config Check ---
                super.appendTooltip(stack, context, tooltip, type); // Always call super
            }
        };
        return Registry.register(Registries.ITEM, Identifier.of(AdorableHamsterPets.MOD_ID, name), item);
    }
    // --- End 3. Private Helper Methods ---
}