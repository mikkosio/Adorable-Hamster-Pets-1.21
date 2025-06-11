package net.dawson.adorablehamsterpets.screen;

import net.dawson.adorablehamsterpets.entity.custom.HamsterEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.network.PacketByteBuf; // Keep if needed elsewhere
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class HamsterEntityScreenHandlerFactory implements ExtendedScreenHandlerFactory<Integer> {
    private final HamsterEntity entity;

    public HamsterEntityScreenHandlerFactory(HamsterEntity entity) {
        this.entity = entity;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("entity.adorablehamsterpets.hamster.inventory_title");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new HamsterInventoryScreenHandler(syncId, playerInventory, this.entity);
    }

    @Override
    public Integer getScreenOpeningData(ServerPlayerEntity player) {
        return entity.getId();
    }
}