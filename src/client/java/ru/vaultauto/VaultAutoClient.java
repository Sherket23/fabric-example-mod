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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
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

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) mc.gui.setScreen(new PickScreen());
            work(mc);
        });
    }

    static InteractionHand keyHand(Player p, boolean ominous) {
        Item need = ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;
        if (p.getMainHandItem().is(need)) return InteractionHand.MAIN_HAND;
        if (p.getOffhandItem().is(need)) return InteractionHand.OFF_HAND;
        return null;
    }

    static void work(Minecraft mc) {
        if (cooldown > 0) cooldown--;
        if (!enabled || targets.isEmpty() || menuOpen) return;
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null || mc.gameMode == null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof VaultBlock)) return;
        // главное исправление: кликаем только по хранилищу, которое ждёт ключ
        if (state.getValue(VaultBlock.STATE) != VaultState.ACTIVE) return;
        boolean ominous = state.getValue(VaultBlock.OMINOUS);

        if (!(level.getBlockEntity(pos) instanceof VaultBlockEntity vault)) return;
        var shared = vault.getSharedData();
        if (!shared.hasDisplayItem()) return;
        if (!targets.contains(shared.getDisplayItem().getItem())) return;

        if (cooldown > 0) return;
        InteractionHand hand = keyHand(player, ominous);
        if (hand == null) return;

        mc.gameMode.useItemOn(player, hand, hit);
        cooldown = 10;
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
