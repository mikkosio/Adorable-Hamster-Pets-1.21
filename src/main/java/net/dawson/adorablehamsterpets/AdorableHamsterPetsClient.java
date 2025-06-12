package net.dawson.adorablehamsterpets;

import net.dawson.adorablehamsterpets.attachment.ModEntityAttachments;
import net.dawson.adorablehamsterpets.block.ModBlocks;
import net.dawson.adorablehamsterpets.client.option.ModKeyBindings;
import net.dawson.adorablehamsterpets.client.sound.HamsterCleaningSoundInstance;
import net.dawson.adorablehamsterpets.client.sound.HamsterFlightSoundInstance;
import net.dawson.adorablehamsterpets.client.sound.HamsterThrowSoundInstance;
import net.dawson.adorablehamsterpets.config.AhpConfig;
import net.dawson.adorablehamsterpets.entity.ModEntities;
import net.dawson.adorablehamsterpets.entity.client.HamsterRenderer;
import net.dawson.adorablehamsterpets.entity.client.ModModelLayers;
import net.dawson.adorablehamsterpets.entity.client.model.HamsterShoulderModel;
import net.dawson.adorablehamsterpets.entity.custom.HamsterEntity;
import net.dawson.adorablehamsterpets.item.ModItems;
import net.dawson.adorablehamsterpets.networking.payload.StartHamsterFlightSoundPayload;
import net.dawson.adorablehamsterpets.networking.payload.StartHamsterThrowSoundPayload;
import net.dawson.adorablehamsterpets.networking.payload.ThrowHamsterPayload;
import net.dawson.adorablehamsterpets.networking.payload.UpdateHamsterRenderStatePayload;
import net.dawson.adorablehamsterpets.screen.HamsterInventoryScreen;
import net.dawson.adorablehamsterpets.screen.ModScreenHandlers;
import net.dawson.adorablehamsterpets.sound.ModSounds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;

import java.util.HashSet;
import java.util.Set;

public class AdorableHamsterPetsClient implements ClientModInitializer {

    // --- Render Tracking Fields ---
    private static final Set<Integer> renderedHamsterIdsThisTick = new HashSet<>();
    private static final Set<Integer> renderedHamsterIdsLastTick = new HashSet<>();

    @Override
    public void onInitializeClient() {
        // --- Block Render Layers ---
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GREEN_BEANS_CROP, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CUCUMBER_CROP, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SUNFLOWER_BLOCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WILD_CUCUMBER_BUSH, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WILD_GREEN_BEAN_BUSH, RenderLayer.getCutout());

        // --- Entity Rendering and Models ---
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.HAMSTER_SHOULDER_LAYER, HamsterShoulderModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.HAMSTER, HamsterRenderer::new);

        // --- Screens and Handlers ---
        HandledScreens.register(ModScreenHandlers.HAMSTER_INVENTORY_SCREEN_HANDLER, HamsterInventoryScreen::new);

        // --- Keybinds and Packet Handlers ---
        ModKeyBindings.registerKeyInputs();
        registerPacketHandlers();

        // --- Consolidated Client Tick Event Handler ---
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);

        // --- Color Providers ---
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> -1, ModItems.HAMSTER_SPAWN_EGG);
    }

    /**
     * Called by the HamsterRenderer every frame a hamster is rendered.
     * @param entityId The ID of the rendered hamster.
     */
    public static void onHamsterRendered(int entityId) {
        renderedHamsterIdsThisTick.add(entityId);
    }

    /**
     * Handles all logic that needs to run at the end of every client tick.
     * This includes processing keybinds and managing render state updates.
     * @param client The Minecraft client instance.
     */
    private void onEndClientTick(MinecraftClient client) {
        // --- Guard Clause: Prevent crash when not in a world ---
        if (client.player == null || client.world == null) {
            // Clear the sets to prevent sending stale data on next world load
            renderedHamsterIdsThisTick.clear();
            renderedHamsterIdsLastTick.clear();
            return;
        }

        // --- Handle Throw Hamster Key ('G' by default) ---
        if (ModKeyBindings.THROW_HAMSTER_KEY.wasPressed()) {
            final AhpConfig currentConfig = AdorableHamsterPets.CONFIG;
            if (!currentConfig.enableHamsterThrowing) {
                client.player.sendMessage(Text.literal("Hamster throwing is disabled in config."), true);
            } else {
                boolean lookingAtReachableBlock = client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK;
                boolean hasShoulderHamsterClient = client.player.getAttached(ModEntityAttachments.HAMSTER_SHOULDER_DATA) != null;

                if (!lookingAtReachableBlock && hasShoulderHamsterClient) {
                    ClientPlayNetworking.send(new ThrowHamsterPayload());
                }
            }
        }

        // --- Handle Render State Updates ---
        // Find hamsters that were rendered last tick but not this tick
        Set<Integer> stoppedRendering = new HashSet<>(renderedHamsterIdsLastTick);
        stoppedRendering.removeAll(renderedHamsterIdsThisTick);

        for (Integer entityId : stoppedRendering) {
            // Send packet indicating we stopped rendering this hamster
            ClientPlayNetworking.send(new UpdateHamsterRenderStatePayload(entityId, false));
        }

        // Update the sets for the next tick
        renderedHamsterIdsLastTick.clear();
        renderedHamsterIdsLastTick.addAll(renderedHamsterIdsThisTick);
        renderedHamsterIdsThisTick.clear();
    }

    /**
     * Registers all client-side packet receivers.
     */
    private void registerPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(StartHamsterFlightSoundPayload.ID, (payload, context) -> {
            context.client().execute(() -> handleStartFlightSound(payload, context.client()));
        });
        ClientPlayNetworking.registerGlobalReceiver(StartHamsterThrowSoundPayload.ID, (payload, context) -> {
            context.client().execute(() -> handleStartThrowSound(payload, context.client()));
        });
        AdorableHamsterPets.LOGGER.debug("Registered S2C Packet Handlers");
    }

    /**
     * Handles playing the continuous flight sound for a thrown hamster.
     */
    private void handleStartFlightSound(StartHamsterFlightSoundPayload payload, MinecraftClient client) {
        if (client.world == null) return;
        Entity entity = client.world.getEntityById(payload.hamsterEntityId());
        if (entity instanceof HamsterEntity hamster) {
            SoundEvent flightSound = ModSounds.getRandomSoundFrom(ModSounds.HAMSTER_FLYING_SOUNDS, hamster.getRandom());
            if (flightSound != null) {
                client.getSoundManager().play(new HamsterFlightSoundInstance(flightSound, SoundCategory.NEUTRAL, hamster));
            }
        }
    }

    /**
     * Handles playing the initial throw sound for a thrown hamster.
     */
    private void handleStartThrowSound(StartHamsterThrowSoundPayload payload, MinecraftClient client) {
        if (client.world == null) return;
        Entity entity = client.world.getEntityById(payload.hamsterEntityId());
        if (entity instanceof HamsterEntity hamster) {
            client.getSoundManager().play(new HamsterThrowSoundInstance(ModSounds.HAMSTER_THROW, SoundCategory.PLAYERS, hamster));
        }
    }
}