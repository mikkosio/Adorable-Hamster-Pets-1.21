package net.dawson.adorablehamsterpets;

import net.dawson.adorablehamsterpets.attachment.HamsterRenderState;
import net.dawson.adorablehamsterpets.block.ModBlocks;
import net.dawson.adorablehamsterpets.client.option.ModKeyBindings;
import net.dawson.adorablehamsterpets.client.sound.HamsterThrowSoundInstance;
import net.dawson.adorablehamsterpets.config.AhpConfig;
import net.dawson.adorablehamsterpets.config.Configs;
import net.dawson.adorablehamsterpets.entity.ModEntities;
import net.dawson.adorablehamsterpets.entity.client.HamsterRenderer;
import net.dawson.adorablehamsterpets.entity.client.ModModelLayers;
import net.dawson.adorablehamsterpets.entity.client.model.HamsterShoulderModel;
import net.dawson.adorablehamsterpets.item.ModItems;
import net.dawson.adorablehamsterpets.networking.payload.StartHamsterThrowSoundPayload;
import net.dawson.adorablehamsterpets.networking.payload.UpdateHamsterRenderStatePayload;
import net.dawson.adorablehamsterpets.screen.HamsterInventoryScreen;
import net.dawson.adorablehamsterpets.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.dawson.adorablehamsterpets.client.sound.HamsterFlightSoundInstance;
import net.dawson.adorablehamsterpets.entity.custom.HamsterEntity;
import net.dawson.adorablehamsterpets.networking.payload.StartHamsterFlightSoundPayload;
import net.dawson.adorablehamsterpets.sound.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.dawson.adorablehamsterpets.attachment.ModEntityAttachments;
import net.dawson.adorablehamsterpets.networking.payload.ThrowHamsterPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;

import java.util.HashSet;
import java.util.Set;

public class AdorableHamsterPetsClient implements ClientModInitializer {

    // --- Fields for tracking render state ---
    private static final Set<Integer> renderedHamsterIdsThisTick = new HashSet<>();
    private static final Set<Integer> renderedHamsterIdsLastTick = new HashSet<>();

    @Override
    public void onInitializeClient() {

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GREEN_BEANS_CROP, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CUCUMBER_CROP, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SUNFLOWER_BLOCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WILD_CUCUMBER_BUSH, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WILD_GREEN_BEAN_BUSH, RenderLayer.getCutout());
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.HAMSTER_SHOULDER_LAYER, HamsterShoulderModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.HAMSTER, HamsterRenderer::new);
        HandledScreens.register(ModScreenHandlers.HAMSTER_INVENTORY_SCREEN_HANDLER, HamsterInventoryScreen::new);
        ModKeyBindings.registerKeyInputs();
        registerPacketHandlers();
        // --- Register Client Tick Event Handler ---
        ClientTickEvents.START_CLIENT_TICK.register(this::handleClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(this::handleClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> onEndClientTick());
        // --- End Registration ---

        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            return -1; // No tint
        }, ModItems.HAMSTER_SPAWN_EGG);
    }

    public static void onHamsterRendered(int entityId) {
        renderedHamsterIdsThisTick.add(entityId);
    }


    // --- Client Tick Handler Method ---
    private void handleClientTick(MinecraftClient client) {
        // --- Ensure Player Exists ---
        if (client.player == null) {
            return;
        }

        // --- Handle Throw Hamster Key ('G' by default) ---
        if (ModKeyBindings.THROW_HAMSTER_KEY.wasPressed()) {
            final AhpConfig currentConfig = AdorableHamsterPets.CONFIG; // Use a local final variable for config
            if (!currentConfig.enableHamsterThrowing) {
                client.player.sendMessage(Text.literal("Hamster throwing is disabled in config."), true);
                // No return here, allow other keybinds to be processed if needed in the future
            } else {
                boolean lookingAtReachableBlock = client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK;
                boolean hasShoulderHamsterClient = client.player.getAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA) != null;

                if (!lookingAtReachableBlock && hasShoulderHamsterClient) {
                    AdorableHamsterPets.LOGGER.debug("[ClientTick START] Throw key pressed! Sending ThrowHamsterPayload.");
                    ClientPlayNetworking.send(new ThrowHamsterPayload());
                }
            }
        }
    }



    // --- Register S2C Packet Handler ---
    private void registerPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(StartHamsterFlightSoundPayload.ID, (payload, context) -> {
            context.client().execute(() -> handleStartFlightSound(payload, context.client()));
        });
        ClientPlayNetworking.registerGlobalReceiver(StartHamsterThrowSoundPayload.ID, (payload, context) -> {
            context.client().execute(() -> handleStartThrowSound(payload, context.client()));
        });
        AdorableHamsterPets.LOGGER.debug("Registered S2C Packet Handlers");
    }
    // --- End Packet Handler Registration ---



    // --- Handle the S2C Packet ---
    private void handleStartFlightSound(StartHamsterFlightSoundPayload payload, MinecraftClient client) {
        // --- Add Logging ---
        AdorableHamsterPets.LOGGER.debug("[Client] Executing handleStartFlightSound on client thread for entity ID: {}", payload.hamsterEntityId());
        // --- End Logging ---

        if (client.world == null) {
            // --- Log specific method ---
            AdorableHamsterPets.LOGGER.warn("handleStartFlightSound: Client world is null!");
            // --- End Log ---
            return;
        }

        Entity entity = client.world.getEntityById(payload.hamsterEntityId());

        // --- Add Logging: Check if entity was found ---
        if (entity == null) {
            AdorableHamsterPets.LOGGER.warn("handleStartFlightSound: Could not find entity with ID: {}", payload.hamsterEntityId());
            return;
        }
        // --- End Logging ---

        if (entity instanceof HamsterEntity hamster) {
            // --- Add Logging: Confirm it's a HamsterEntity ---
            AdorableHamsterPets.LOGGER.debug("handleStartFlightSound: Found HamsterEntity instance for ID: {}", payload.hamsterEntityId());
            // --- End Logging ---


            SoundEvent flightSound = ModSounds.getRandomSoundFrom(ModSounds.HAMSTER_FLYING_SOUNDS, hamster.getRandom());

            // --- Add Logging: Check if sound event is valid ---
            if (flightSound == null) {
                AdorableHamsterPets.LOGGER.error("handleStartFlightSound: getRandomSoundFrom(HAMSTER_FLYING_SOUNDS) returned null!");
                return;
            }
            AdorableHamsterPets.LOGGER.debug("handleStartFlightSound: Selected flight sound: {}", flightSound.getId());
            // --- End Logging ---

            try {
                HamsterFlightSoundInstance soundInstance = new HamsterFlightSoundInstance(flightSound, SoundCategory.NEUTRAL, hamster);
                // --- Add Logging: Confirm sound instance creation ---
                AdorableHamsterPets.LOGGER.debug("handleStartFlightSound: Created HamsterFlightSoundInstance.");
                // --- End Logging ---

                client.getSoundManager().play(soundInstance);
                // --- Add Logging: Confirm play() was called ---
                AdorableHamsterPets.LOGGER.debug("handleStartFlightSound: Called soundManager.play() for hamster {}", payload.hamsterEntityId());
                // --- End Logging ---

            } catch (Exception e) {
                // --- Add Logging: Catch any unexpected errors during sound creation/play ---
                AdorableHamsterPets.LOGGER.error("handleStartFlightSound: Error creating or playing sound instance!", e);
                // --- End Logging ---
            }

        } else {
            // --- Add Logging: Entity found but wasn't a HamsterEntity ---
            AdorableHamsterPets.LOGGER.warn("handleStartFlightSound: Found entity for ID {}, but it was not a HamsterEntity (Type: {})", payload.hamsterEntityId(), entity.getType().getUntranslatedName());
            // --- End Logging ---
        }
    }
    // --- End Packet Handler ---

    // --- HANDLER for Throw Sound ---
    private void handleStartThrowSound(StartHamsterThrowSoundPayload payload, MinecraftClient client) {
        if (client.world == null) {
            AdorableHamsterPets.LOGGER.warn("Received StartHamsterThrowSoundPayload but client world is null!");
            return;
        }

        Entity entity = client.world.getEntityById(payload.hamsterEntityId());
        if (entity instanceof HamsterEntity hamster) {
            AdorableHamsterPets.LOGGER.debug("Found hamster {} for throw sound.", payload.hamsterEntityId());
            // Use the specific throw sound
            SoundEvent throwSound = ModSounds.HAMSTER_THROW;
            if (throwSound != null) {
                HamsterThrowSoundInstance soundInstance = new HamsterThrowSoundInstance(throwSound, SoundCategory.PLAYERS, hamster); // Use PLAYERS category like original
                client.getSoundManager().play(soundInstance);
                AdorableHamsterPets.LOGGER.debug("Playing HamsterThrowSoundInstance for hamster {}", payload.hamsterEntityId());
            } else {
                AdorableHamsterPets.LOGGER.error("HAMSTER_THROW sound event is null! Cannot play throw sound."); // Should not happen
            }
        } else {
            AdorableHamsterPets.LOGGER.warn("Received StartHamsterThrowSoundPayload for non-hamster or unknown entity ID: {}", payload.hamsterEntityId());
        }
    }

    // --- Helper method for tick end logic ---
    private void onEndClientTick() {
        // Find hamsters that were rendered last tick but not this tick
        Set<Integer> stoppedRendering = new HashSet<>(renderedHamsterIdsLastTick);
        stoppedRendering.removeAll(renderedHamsterIdsThisTick);

        for (Integer entityId : stoppedRendering) {
            // Send packet indicating we stopped rendering this hamster
            ClientPlayNetworking.send(new UpdateHamsterRenderStatePayload(entityId, false));
            // Also update the local state immediately
            if (MinecraftClient.getInstance().world != null) {
                Entity entity = MinecraftClient.getInstance().world.getEntityById(entityId);
                if (entity instanceof HamsterEntity hamster) {
                    HamsterRenderState state = hamster.getAttachedOrCreate(ModEntityAttachments.HAMSTER_RENDER_STATE, HamsterRenderState::new);
                    state.setClientRendering(false);
                }
            }
        }
        // Update the sets for the next tick
        renderedHamsterIdsLastTick.clear();
        renderedHamsterIdsLastTick.addAll(renderedHamsterIdsThisTick);
        renderedHamsterIdsThisTick.clear();
    }
}