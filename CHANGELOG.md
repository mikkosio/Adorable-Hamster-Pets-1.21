# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- 

### Changed
- 

### Fixed
- 

---

## [1.1.0] - 2025-05-19
<!-- Replace YYYY-MM-DD with 1.1.0's release date -->
### Added
- **Advancement Tree:** With distinct branches leading the player to explore different features.
- Custom Advancement Tab ("The Hamster Life"):
  - Guides players through mod features with custom titles, descriptions, and icons.
  - Features a branching structure after initial taming.
  - Includes advancements for obtaining seeds, taming, crafting key items (Sliced Cucumber, Food Mix, Steamed Beans), shouldering, throwing, feeding buffs, and unlocking cheek pouches.
  - Uses custom criteria for specific mod interactions (shouldering, throwing, feeding buffs, pouch unlock, initial guidebook).
  - "Goal" and "Challenge" advancements play distinct sounds on unlock.
- Crafting Recipe for Hamster Guide Book:
  - Players can craft the Hamster Guide Book (1 Vanilla Book + 1 Sliced Cucumber).
  - Crafted book comes pre-filled with all NBT content (pages).
  - A hidden advancement triggers on crafting, playing particle/sound effects.
- Config option `uiTweaks.enableItemTooltips` to toggle custom mod item/block tooltips.
  - When off, tooltips will show item name and "Adorable Hamster Pets" for mod identification.
- Config option `uiTweaks.enableAutoGuidebookDelivery` to toggle automatic guidebook delivery on first join.
  - Thanks to `@MylesGit` on GitHub for suggesting those config ideas, custom advancements, and Guide Book crafting recipe.
- New hamster base color variants: Blue and Lavender.
  - Blue hamsters (with 8 overlay options) spawn rarely in Ice Spikes biomes (70% chance for Blue, 30% for White).
  - Lavender hamsters (with 8 overlay options) spawn rarely in Cherry Grove biomes.
- Four new overlay patterns (overlay5, overlay6, overlay7, overlay8) for all applicable base colors, increasing visual diversity.
- Numerous sound effect variations for hamster actions (idle, hurt, attack, sleep, beg, death, creeper detect, diamond sniff, celebrate).
- Pink Petal cosmetic overlay system:
  - Players can right-click a tamed, owned hamster with `minecraft:pink_petals` to apply one of three random pink petal textures.
  - Right-clicking again with pink petals removes the cosmetic overlay.
  - Applying consumes a petal item; removing does not.
  - Petal state is saved with the hamster and visible on the shoulder.
  - Includes sound and particle effects for application/removal.
- Player-edible Cheese:
  - Cheese is now a food item for players (Nutrition: 8, Saturation: 0.8F).
  - Features a custom eating sound and a faster eating time (20 ticks vs. vanilla 32).
- Cheek Pouch Locking Mechanic:
  - By default, hamster cheek pouches are locked upon taming.
  - Feeding a hamster `HAMSTER_FOOD_MIX` for the first time (resulting in healing/love mode) permanently unlocks its pouches.
  - Plays a sound and spawns particles upon pouch unlock.
  - New config option `features.requireFoodMixToUnlockCheeks` (default: true) allows disabling this lock.
- Display a random, non-repeating message on the action bar when a shoulder hamster dismounts due to sneaking.
  - Configuration option (`features.enableShoulderDismountMessages`) to toggle shoulder dismount messages.

### Changed
- **Hamster Textures:** All hamster textures (including overlays) have been considerably reworked for improved aesthetics and to ensure they are not overly similar to textures from the "Hamsters" mod by Starfish Studios.
- **Hamster Variant System:** Breeding logic updated: If both parents have an overlay, baby must have an overlay, preferably different from parents. If one/neither parent has an overlay, baby can have an overlay (different from parents if applicable) or no overlay.
- **Shoulder Summoning:** Hamsters are now summoned to the shoulder by right-clicking a tamed, owned hamster while holding `ModItems.CHEESE` (instead of right-clicking air with cheese).
- **Hamster Tempting:** Hamsters are now also tempted by (and will beg for) `ModItems.CHEESE` and `ModItems.STEAMED_GREEN_BEANS`, in addition to `ModItems.SLICED_CUCUMBER`.
  - However, `ModItems.SLICED_CUCUMBER` is still the only thing that can tame a hamster.
- **Spawn Egg Item Model:** Now uses data generation (`Models.GENERATED`) for its model, pointing to a custom sprite texture `adorablehamsterpets:item/hamster_spawn_egg` to mesh better with the new spawn egg textures seen in Snapshot 25w08a. (Future proofing.)
- **Item/Creative Tab Icons:** Both the "Adorable Hamster Pets" creative tab and the root "The Hamster Life" advancement tab now use the (custom textured) `HAMSTER_SPAWN_EGG` as their icon.
- **Hamster Spawning:** Removed light level check from spawn restrictions to allow hamsters to spawn in dark areas like caves, provided the block below is valid.
- **Guidebook Content:** Significantly updated and expanded to reflect new features, revised mechanics, and ensure brand voice consistency. Page order adjusted.
- **Guidebook Delivery:** Initial delivery on first join now uses a persistent "flag" advancement (`technical/has_received_initial_guidebook`) to ensure it's truly one-time, respecting the config toggle. The advancement tab (`husbandry/root`) now unlocks immediately for all players via a `minecraft:location` trigger.

### Fixed
- **Melee Attack Particles:** Implemented Geckolib creator, Tslat's suggested fix; particles now spawn correctly at the attacking hamster's foot for all attacks, including the first, and in multi-entity scenarios.
- **Recipe Book Visibility:** Corrected an issue where the "Hamster Food Mix" recipe might not unlock as expected; ensured its advancement criterion correctly requires only `ModItems.SUNFLOWER_SEEDS`.
- Corrected various minor code errors and improved config access patterns.

### Removed
- Numerous hamster sound effect variations used for testing which were not intended to be present in the released version of the mod.
- Shoulder-mounted hamsters no longer dismount when the player takes damage.
---

## [1.0.1] - 2025-05-10
<!-- Replace YYYY-MM-DD with 1.0.1's release date -->

### Changed
- (Internal) Reorganized code structure and updated comments for `HamsterFleeGoal.java`, `HamsterSleepGoal.java`, `HamsterTemptGoal.java`, `HamsterShoulderFeatureRenderer.java`, and `PlayerEntityMixin.java` for improved readability.
- (Internal) Tweaked the "begging" animation to slightly shift the hamster model forward for better visual positioning (`anim_hamster.json`).

### Fixed
- Resolved issue where sitting hamsters could still be tempted by items, causing them to move while sat, which was hilarious. (`HamsterTemptGoal`).
- Corrected wild hamster sleeping behavior: they now consistently wake up when a player approaches, regardless of the player's sneaking status or held item (`HamsterSleepGoal`).
- Adjusted baby hamster rendering (`HamsterModel.java`, `HamsterRenderer.java`):
  - Corrected model scaling logic to properly apply base scales for baby/adult states while allowing JSON animations (e.g., breathing) to function proportionally.
  - Implemented differential scaling for baby hamsters, resulting in a relatively larger head compared to their body.

### Documentation
- Updated `README.md` and item tooltip for the guide book to remove references to an in-book command for re-obtaining it (the book was spawning in without any content and the command to generate its content was way too long for any sane person to type.)

---

## [1.0.0] - 2025-05-04
<!-- Replace YYYY-MM-DD with 1.0.0's release date -->

### Added
- First public version of Adorable Hamster Pets. Hello world!
