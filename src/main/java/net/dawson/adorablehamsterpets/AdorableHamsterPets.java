package net.dawson.adorablehamsterpets;

import net.dawson.adorablehamsterpets.advancement.criterion.ModCriteria;
import net.dawson.adorablehamsterpets.attachment.HamsterRenderState;
import net.dawson.adorablehamsterpets.attachment.ModEntityAttachments;
import net.dawson.adorablehamsterpets.block.ModBlocks;
import net.dawson.adorablehamsterpets.command.ModCommands;
import net.dawson.adorablehamsterpets.component.ModDataComponentTypes;
import net.dawson.adorablehamsterpets.config.AhpConfig;
import net.dawson.adorablehamsterpets.config.Configs;
import net.dawson.adorablehamsterpets.entity.ModEntities;
import net.dawson.adorablehamsterpets.entity.custom.HamsterEntity;
import net.dawson.adorablehamsterpets.item.ModItemGroups;
import net.dawson.adorablehamsterpets.item.ModItems;
import net.dawson.adorablehamsterpets.networking.payload.*;
import net.dawson.adorablehamsterpets.screen.ModScreenHandlers;
import net.dawson.adorablehamsterpets.sound.ModSounds;
import net.dawson.adorablehamsterpets.world.ModWorldGeneration;
import net.dawson.adorablehamsterpets.world.gen.ModEntitySpawns;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AdorableHamsterPets implements ModInitializer {
	public static final String MOD_ID = "adorablehamsterpets";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


	// --- 1. Define Packet ID ---
	public static final Identifier THROW_HAMSTER_PACKET_ID = Identifier.of(MOD_ID, "throw_hamster");
	// --- End Packet ID ---

	// Top-Level Field
	public static AhpConfig CONFIG;

	@Override
	public void onInitialize() {

		// Config
		CONFIG = Configs.AHP;

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
		// ---  Register payloads with coordinates ---
		PayloadTypeRegistry.playC2S().register(SpawnAttackParticlesPayload.ID, SpawnAttackParticlesPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SpawnSeekingDustPayload.ID, SpawnSeekingDustPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UpdateHamsterRenderStatePayload.ID, UpdateHamsterRenderStatePayload.CODEC);

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

			if (Configs.AHP.enableAutoGuidebookDelivery) {
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

		// --- Spawn Seeking Dust Packet Receiver ---
		ServerPlayNetworking.registerGlobalReceiver(SpawnSeekingDustPayload.ID,
				(SpawnSeekingDustPayload payload, ServerPlayNetworking.Context context) -> {
					MinecraftServer server = context.server();
					server.execute(() -> {
						handleSpawnSeekingDustPacket(payload, context);
					});
				});
		LOGGER.info("Registered C2S Packet Receiver for Payload: {}", SpawnSeekingDustPayload.ID.id());
		// --- End Spawn Seeking Dust ---

		// --- Update Render State ---
		ServerPlayNetworking.registerGlobalReceiver(UpdateHamsterRenderStatePayload.ID,
				(payload, context) -> {
					context.server().execute(() -> {
						handleUpdateRenderStatePacket(payload, context);
					});
				});
		LOGGER.info("Registered C2S Packet Receiver for Payload: {}", UpdateHamsterRenderStatePayload.ID.id());
		// --- End Update Render State ---
	}

	// --- End Receiver Registration ---

	// --- 3. Handle the Received Packet ---
	private static void handleThrowHamsterPacket(ServerPlayerEntity player) {
		HamsterEntity.tryThrowFromShoulder(player);
	}
	// --- End Packet Handler ---


	// --- Handler for the attack particle packet ---
	private static void handleSpawnAttackParticlesPacket(SpawnAttackParticlesPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayerEntity player = context.player();
		ServerWorld world = player.getServerWorld();

		LOGGER.debug("[ServerPacketHandler] handleSpawnAttackParticlesPacket invoked. Spawning at ({}, {}, {})", payload.x(), payload.y(), payload.z());

		if (world != null) {
			// Spawn WHITE_SMOKE particles at the *coordinates received from the client*
			world.spawnParticles(
					ParticleTypes.WHITE_SMOKE, // The particle type
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
	// --- Handler for the seeking dust particle packet ---
	private static void handleSpawnSeekingDustPacket(SpawnSeekingDustPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayerEntity player = context.player(); // Player who owns the hamster (or is nearby)
		ServerWorld world = player.getServerWorld();

		if (world == null) {
			LOGGER.warn("[ServerPacketHandler] Could not get server world for player {} when handling seeking dust.", player.getName().getString());
			return;
		}

		Entity entity = world.getEntityById(payload.hamsterEntityId()); // Get hamster by ID

		if (!(entity instanceof HamsterEntity hamster)) {
			LOGGER.warn("[ServerPacketHandler] Seeking dust packet received for non-hamster or unknown entity ID: {}", payload.hamsterEntityId());
			return;
		}

		// Get the block the hamster is standing on
		BlockPos hamsterFeetPos = hamster.getBlockPos(); // Position of the hamster's hitbox origin
		BlockPos blockBelowHamster = hamsterFeetPos.down(); // Block directly beneath the hamster
		BlockState stateForParticles = world.getBlockState(blockBelowHamster);

		// Fallback if the block below is air or unsuitable (e.g., tall grass, flowers)
		if (stateForParticles.isAir() || stateForParticles.isReplaceable()) {
			// Try one more block down if the immediate one was replaceable
			BlockPos furtherBelow = blockBelowHamster.down();
			BlockState stateFurtherBelow = world.getBlockState(furtherBelow);
			if (!stateFurtherBelow.isAir() && !stateFurtherBelow.isReplaceable()) {
				stateForParticles = stateFurtherBelow;
			} else {
				stateForParticles = Blocks.ANDESITE.getDefaultState(); // Default to Andesite if still unsuitable
			}
		}
		// Ensure we don't use a BlockState that FALLING_DUST can't handle (e.g. some technical blocks)
		// A simple check is if it has a collision shape.
		if (stateForParticles.getCollisionShape(world, blockBelowHamster).isEmpty() && stateForParticles != Blocks.WATER.getDefaultState() && stateForParticles != Blocks.LAVA.getDefaultState()) {
			// If it has no collision (like flowers, grass) and isn't a fluid, default to Andesite.
			// Fluids have special rendering for FALLING_DUST, so they are okay.
			stateForParticles = Blocks.DIRT.getDefaultState();
		}


		LOGGER.debug("[ServerPacketHandler] handleSpawnSeekingDustPacket for Hamster ID {}. Particle spawn at ({}, {}, {}). Sampled block state: {}",
				payload.hamsterEntityId(), payload.particleX(), payload.particleY(), payload.particleZ(), stateForParticles.toString());

		world.spawnParticles(
				new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, stateForParticles),
				payload.particleX(),    // Spawn at nose X
				payload.particleY(),    // Spawn at nose Y
				payload.particleZ(),    // Spawn at nose Z
				12,                     // Number of particles
				0.2,                    // Spread in X
				0.03,                   // Spread in Y
				0.2,                    // Spread in Z
				0.0                     // Speed
		);
	}

	// --- Handler For Render State ---
	private static void handleUpdateRenderStatePacket(UpdateHamsterRenderStatePayload payload, ServerPlayNetworking.Context context) {
		ServerPlayerEntity player = context.player();
		ServerWorld world = player.getServerWorld();
		Entity entity = world.getEntityById(payload.hamsterEntityId());


		if (entity instanceof HamsterEntity hamster) {
			HamsterRenderState state = hamster.getAttachedOrCreate(ModEntityAttachments.HAMSTER_RENDER_STATE, HamsterRenderState::new);
			if (payload.isRendering()) {
				state.addPlayer(player);
			} else {
				state.removePlayer(player);
			}
		}
	}

}