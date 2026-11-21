package ru.vaultauto;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class VaultAutoClient implements ClientModInitializer {
    static final Item[] ITEMS = {
        Items.MACE, Items.HEAVY_CORE, Items.FLOW_BANNER_PATTERN, Items.GUSTER_BANNER_PATTERN,
        Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.OMINOUS_BOTTLE, Items.ENCHANTED_GOLDEN_APPLE
    };
    static Item target = null;
    static int cooldown = 0;
    static boolean menuOpen = false;

    @Override
    public void onInitializeClient() {
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vaultauto.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) mc.gui.setScreen(new PickScreen());
            if (cooldown > 0) { cooldown--; return; }
            if (target == null || menuOpen || mc.player == null || mc.level == null) return;
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof VaultBlockEntity vault)) return;

            if (vault.getSharedData().getDisplayItem().is(target)) {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                cooldown = 40;
            }
        });
    }

    static class PickScreen extends Screen {
        PickScreen() { super(Component.literal("Vault Auto")); }

        @Override protected void init() {
            menuOpen = true;
            int y = 30;
            addRenderableWidget(Button.builder(Component.literal("Выкл"), b -> { target = null; onClose(); })
                .bounds(width / 2 - 100, y, 200, 20).build());
            for (Item it : ITEMS) {
                y += 24;
                addRenderableWidget(Button.builder(it.getDefaultInstance().getHoverName(), b -> { target = it; onClose(); })
                    .bounds(width / 2 - 100, y, 200, 20).build());
            }
        }

        @Override public void removed() { menuOpen = false; }
    }
}
