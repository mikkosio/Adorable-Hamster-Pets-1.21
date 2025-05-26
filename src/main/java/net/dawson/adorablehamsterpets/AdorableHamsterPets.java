package net.dawson.adorablehamsterpets;

import net.dawson.adorablehamsterpets.advancement.criterion.ModCriteria;
import net.dawson.adorablehamsterpets.attachment.ModEntityAttachments;
import net.dawson.adorablehamsterpets.block.ModBlocks;
import net.dawson.adorablehamsterpets.command.ModCommands;
import net.dawson.adorablehamsterpets.component.ModDataComponentTypes;
import net.dawson.adorablehamsterpets.config.ModConfig;
import net.dawson.adorablehamsterpets.networking.payload.SpawnAttackParticlesPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.dawson.adorablehamsterpets.entity.ModEntities;
import net.dawson.adorablehamsterpets.entity.custom.HamsterEntity;
import net.dawson.adorablehamsterpets.item.ModItemGroups;
import net.dawson.adorablehamsterpets.item.ModItems;
import net.dawson.adorablehamsterpets.networking.payload.StartHamsterFlightSoundPayload;
import net.dawson.adorablehamsterpets.networking.payload.StartHamsterThrowSoundPayload;
import net.dawson.adorablehamsterpets.screen.ModScreenHandlers;
import net.dawson.adorablehamsterpets.sound.ModSounds;
import net.dawson.adorablehamsterpets.world.ModWorldGeneration;
import net.dawson.adorablehamsterpets.world.gen.ModEntitySpawns;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.registry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.dawson.adorablehamsterpets.networking.payload.ThrowHamsterPayload;



public class AdorableHamsterPets implements ModInitializer {
	public static final String MOD_ID = "adorablehamsterpets";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Stores the loaded configuration instance for easy access throughout the mod.
	 * Initialized once in onInitialize.
	 */
	public static ModConfig CONFIG;



	// --- 1. Define Packet ID ---
	public static final Identifier THROW_HAMSTER_PACKET_ID = Identifier.of(MOD_ID, "throw_hamster");
	// --- End Packet ID ---


	@Override
	public void onInitialize() {

		// Config
		CONFIG = ModConfig.createAndLoad();

		// Registrations (Sounds, Blocks, Items, etc.)
		ModDataComponentTypes.registerDataComponentTypes();
		ModSounds.registerSounds();
		ModBlocks.registerModBlocks();
		ModItems.registerModItems();
		ModItemGroups.registerItemGroups();
		ModEntities.registerModEntities();
		ModEntityAttachments.registerAttachments();
		ModScreenHandlers.registerScreenHandlers();

		// Register custom criteria
		ModCriteria.registerCriteria();

		// World Gen and Spawns
		ModWorldGeneration.generateModWorldGen();
		ModEntitySpawns.addSpawns();

		// Payload Registrations
		PayloadTypeRegistry.playC2S().register(ThrowHamsterPayload.ID, ThrowHamsterPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StartHamsterFlightSoundPayload.ID, StartHamsterFlightSoundPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StartHamsterThrowSoundPayload.ID, StartHamsterThrowSoundPayload.CODEC);
		// ---  Register payload with coordinates ---
		PayloadTypeRegistry.playC2S().register(SpawnAttackParticlesPayload.ID, SpawnAttackParticlesPayload.CODEC);

		// Register Packet Handlers
		registerC2SPackets();

		// Other Registries
		CompostingChanceRegistry.INSTANCE.add(ModItems.GREEN_BEANS, 0.5f);
		CompostingChanceRegistry.INSTANCE.add(ModItems.CUCUMBER, 0.5f);
		CompostingChanceRegistry.INSTANCE.add(ModItems.GREEN_BEAN_SEEDS, 0.25f);
		CompostingChanceRegistry.INSTANCE.add(ModItems.CUCUMBER_SEEDS, 0.25f);
		CompostingChanceRegistry.INSTANCE.add(ModItems.SUNFLOWER_SEEDS, 0.25f);

		FabricDefaultAttributeRegistry.register(ModEntities.HAMSTER, HamsterEntity.createHamsterAttributes());

		// --- Register Player Join Event for Guidebook ---
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;

			if (AdorableHamsterPets.CONFIG.uiPreferences.enableAutoGuidebookDelivery()) {
				PlayerAdvancementTracker advancementTracker = player.getAdvancementTracker();

				Identifier flagAdvId = Identifier.of(MOD_ID, "technical/has_received_initial_guidebook");
				net.minecraft.advancement.AdvancementEntry flagAdvancementEntry = server.getAdvancementLoader().get(flagAdvId);

				if (flagAdvancementEntry != null) {
					AdvancementProgress flagProgress = advancementTracker.getProgress(flagAdvancementEntry);

					if (!flagProgress.isDone()) {
						// Player has NOT received the initial guidebook yet (based on the flag)
						AdorableHamsterPets.LOGGER.debug("Player {} has not received initial guidebook. Granting now.", player.getName().getString());

						// 1. Trigger the criterion that grants "technical/receive_guide_book"
						// This advancement's reward function will give the book and revoke "technical/receive_guide_book"
						ModCriteria.FIRST_JOIN_GUIDEBOOK_CHECK.trigger(player);

						// 2. Grant the persistent "flag" advancement
						// We grant all its criteria to mark it as complete.
						for (String criterion : flagAdvancementEntry.value().criteria().keySet()) {
							advancementTracker.grantCriterion(flagAdvancementEntry, criterion);
						}
						// No need to revoke this flag advancement.
					} else {
						// Player has the flag, so they've received the initial book before.
						// LOGGER.debug("Player {} already has the 'has_received_initial_guidebook' flag. No new book given.", player.getName().getString());
					}
				} else {
					LOGGER.warn("Could not find flag advancement: {}", flagAdvId);
				}
			}
		});
		// --- End Register Player Join Event ---

		CommandRegistrationCallback.EVENT.register(ModCommands::register);
	}


	// --- 2. Register Server-Side Packet Receiver ---
	public static void registerC2SPackets() {

		// --- Throw Hamster Packet Receiver ---
		ServerPlayNetworking.registerGlobalReceiver(ThrowHamsterPayload.ID, // Use the Payload ID here
				(ThrowHamsterPayload payload, ServerPlayNetworking.Context context) -> { // Correct lambda signature
					// Access server and player via context
					MinecraftServer server = context.server();
					ServerPlayerEntity player = context.player();

					// Execute on the server thread to ensure thread safety
					server.execute(() -> {
						handleThrowHamsterPacket(player); // Pass the player from the context
					});
				});
		LOGGER.info("Registered C2S Packet Receiver for Payload: {}", ThrowHamsterPayload.ID.id()); // Log the actual ID
		// --- End Throw Hamster ---

		// --- Spawn Attack Particles Packet Receiver ---
		ServerPlayNetworking.registerGlobalReceiver(SpawnAttackParticlesPayload.ID,
				(SpawnAttackParticlesPayload payload, ServerPlayNetworking.Context context) -> {
					MinecraftServer server = context.server();
					// Execute on the server thread
					server.execute(() -> {
						// Call the handler method
						handleSpawnAttackParticlesPacket(payload, context);
					});
				});
		LOGGER.info("Registered C2S Packet Receiver for Payload: {}", SpawnAttackParticlesPayload.ID.id());
		// --- END Spawn Attack Particles ---
	}
	// --- End Receiver Registration ---

	// --- 3. Handle the Received Packet ---
	private static void handleThrowHamsterPacket(ServerPlayerEntity player) {
		HamsterEntity.tryThrowFromShoulder(player);
	}
	// --- End Packet Handler ---


	// --- Handler for the new particle packet ---
	private static void handleSpawnAttackParticlesPacket(SpawnAttackParticlesPayload payload, ServerPlayNetworking.Context context) {
		// --- Description: Handle the client's request to spawn attack particles at specific coordinates ---
		ServerPlayerEntity player = context.player(); // Player who sent the packet
		ServerWorld world = player.getServerWorld(); // Get the server world

		// Log that the handler was invoked, including coordinates
		LOGGER.debug("[ServerPacketHandler] handleSpawnAttackParticlesPacket invoked. Spawning at ({}, {}, {})", payload.x(), payload.y(), payload.z());

		if (world != null) {
			// Spawn CRIT particles at the *coordinates received from the client*
			world.spawnParticles(
					ParticleTypes.CRIT, // The particle type
					payload.x(),        // Use X from payload
					payload.y(),        // Use Y from payload
					payload.z(),        // Use Z from payload
					10,                  // Number of particles
					0.1,                // Spread in X
					0.2,                // Spread in Y
					0.1,                // Spread in Z
					0.05                // Speed/Velocity of particles
			);
		} else {
			// Corrected log message for clarity
			LOGGER.debug("[ServerPacketHandler] Could not get server world for player {}", player.getName().getString());
		}
	}
	// --- END Handler ---

}