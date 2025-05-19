package net.dawson.adorablehamsterpets.advancement.criterion;

// ... other imports ...
import net.dawson.adorablehamsterpets.AdorableHamsterPets;
import net.minecraft.advancement.criterion.Criterion; // Ensure this is imported
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModCriteria {
    public static final HamsterOnShoulderCriterion HAMSTER_ON_SHOULDER = register("hamster_on_shoulder", new HamsterOnShoulderCriterion());
    public static final HamsterThrownCriterion HAMSTER_THROWN = register("hamster_thrown", new HamsterThrownCriterion());
    public static final FirstJoinCriterion FIRST_JOIN_GUIDEBOOK_CHECK = register("first_join_guidebook_check", new FirstJoinCriterion());
    public static final FedHamsterSteamedBeansCriterion FED_HAMSTER_STEAMED_BEANS = register("fed_hamster_steamed_beans", new FedHamsterSteamedBeansCriterion());
    public static final CheekPouchUnlockedCriterion CHEEK_POUCH_UNLOCKED = register("cheek_pouch_unlocked", new CheekPouchUnlockedCriterion());


    private static <T extends Criterion<?>> T register(String name, T criterion) {
        return Registry.register(Registries.CRITERION, Identifier.of(AdorableHamsterPets.MOD_ID, name), criterion);
    }

    public static void registerCriteria() {
        AdorableHamsterPets.LOGGER.info("Registering Mod Criteria for " + AdorableHamsterPets.MOD_ID);
    }
}