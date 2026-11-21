package ru.vaultauto;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
        Items.HEAVY_CORE, Items.FLOW_BANNER_PATTERN, Items.GUSTER_BANNER_PATTERN,
        Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.OMINOUS_BOTTLE, Items.ENCHANTED_GOLDEN_APPLE
    };
    static final String[] NAMES = {
        "Ядро булавы", "Узор Flow", "Узор Guster", "Шаблон Flow", "Шаблон Bolt",
        "Зловещий флакон", "Зач. яблоко"
    };
    static final Set<Item> targets = new HashSet<>();
    static boolean enabled = false;
    static int cooldown = 0;
    static boolean menuOpen = false;

    @Override
    public void onInitializeClient() {
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vaultauto.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, KeyMapping.Category.MISC));

        ClientTickEvents.START_CLIENT_TICK.register(mc -> work(mc));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) mc.gui.setScreen(new PickScreen());
            if (cooldown > 0) { cooldown--; return; }
            work(mc);
        });
    }

    static void work(Minecraft mc) {
        if (cooldown > 0) return;
        if (!enabled || targets.isEmpty() || menuOpen || mc.player == null || mc.level == null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof VaultBlockEntity vault)) return;

        if (targets.contains(vault.getSharedData().getDisplayItem().getItem())) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            cooldown = 40;
        }
    }

    static Component toggleLabel() {
        return enabled
            ? Component.literal("Мод: ВКЛ").withStyle(ChatFormatting.GREEN)
            : Component.literal("Мод: ВЫКЛ").withStyle(ChatFormatting.RED);
    }

    static Component itemLabel(int i) {
        return targets.contains(ITEMS[i])
            ? Component.literal("✔ " + NAMES[i]).withStyle(ChatFormatting.GREEN)
            : Component.literal(NAMES[i]);
    }

    static class PickScreen extends Screen {
        PickScreen() { super(Component.literal("Vault Auto")); }

        @Override protected void init() {
            menuOpen = true;
            int cx = width / 2;
            addRenderableWidget(Button.builder(toggleLabel(), b -> {
                enabled = !enabled;
                b.setMessage(toggleLabel());
            }).bounds(cx - 150, 20, 300, 20).build());

            for (int i = 0; i < ITEMS.length; i++) {
                final int idx = i;
                int x = cx - 150 + (i % 2) * 152;
                int y = 48 + (i / 2) * 24;
                addRenderableWidget(Button.builder(itemLabel(i), b -> {
                    if (!targets.remove(ITEMS[idx])) targets.add(ITEMS[idx]);
                    b.setMessage(itemLabel(idx));
                }).bounds(x, y, 148, 20).build());
            }

            int closeY = 48 + ((ITEMS.length + 1) / 2) * 24 + 4;
            addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(cx - 150, closeY, 300, 20).build());
        }

        @Override public void removed() { menuOpen = false; }
    }
}
