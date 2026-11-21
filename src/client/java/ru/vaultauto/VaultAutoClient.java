package ru.vaultauto;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
        Items.OMINOUS_BOTTLE, Items.ENCHANTED_GOLDEN_APPLE, Items.ENCHANTED_BOOK
    };
    static final String[] NAMES = {
        "Ядро булавы", "Узор Flow", "Узор Guster", "Шаблон Flow", "Шаблон Bolt",
        "Зловещий флакон", "Зач. яблоко", "Книга (любая)"
    };
    static final Set<Item> targets = ConcurrentHashMap.newKeySet();
    static volatile boolean enabled = false;
    static volatile boolean menuOpen = false;
    static volatile long lastClick = 0;
    static volatile boolean pending = false;

    @Override
    public void onInitializeClient() {
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vaultauto.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) mc.gui.setScreen(new PickScreen());
        });

        // ОДИНОЧНЫЙ МИР: проверка и клик на стороне сервера, в том же тике
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!enabled || targets.isEmpty() || menuOpen) return;
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) serverClick(sp);
        });

        // ОБЫЧНЫЙ СЕРВЕР: быстрый опрос из отдельного потока
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    boolean active = enabled && !targets.isEmpty() && !menuOpen;
                    Thread.sleep(active ? 1 : 50);
                    if (!active || pending) continue;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.getSingleplayerServer() != null) continue; // в одиночке работает серверная часть
                    if (tryClick(mc, false)) {
                        pending = true;
                        mc.execute(() -> {
                            try { tryClick(mc, true); } finally { pending = false; }
                        });
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable ignored) {
                    pending = false;
                }
            }
        }, "vaultauto-poll");
        t.setDaemon(true);
        t.start();
    }

    static InteractionHand keyHand(Player p, boolean ominous) {
        Item need = ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;
        if (p.getMainHandItem().is(need)) return InteractionHand.MAIN_HAND;
        if (p.getOffhandItem().is(need)) return InteractionHand.OFF_HAND;
        return null;
    }

    /** Серверная версия: тот же тик, никакой сетевой задержки (одиночный мир). */
    static void serverClick(ServerPlayer sp) {
        try {
            if (System.currentTimeMillis() - lastClick < 400) return;
            Level level = sp.level();
            if (!(sp.pick(5.0, 1.0f, false) instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;

            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof VaultBlock)) return;
            if (state.getValue(VaultBlock.STATE) != VaultState.ACTIVE) return;
            boolean ominous = state.getValue(VaultBlock.OMINOUS);

            if (!(level.getBlockEntity(pos) instanceof VaultBlockEntity vault)) return;
            var shared = vault.getSharedData();
            if (!shared.hasDisplayItem()) return;
            if (!targets.contains(shared.getDisplayItem().getItem())) return;

            InteractionHand hand = keyHand(sp, ominous);
            if (hand == null) return;

            sp.gameMode.useItemOn(sp, level, sp.getItemInHand(hand), hand, hit);
            lastClick = System.currentTimeMillis();
        } catch (Throwable ignored) {
        }
    }

    /** Клиентская версия для обычных серверов. */
    static boolean tryClick(Minecraft mc, boolean doClick) {
        try {
            if (!enabled || targets.isEmpty() || menuOpen) return false;
            if (System.currentTimeMillis() - lastClick < 400) return false;
            var player = mc.player;
            var level = mc.level;
            if (player == null || level == null || mc.gameMode == null) return false;
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;

            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof VaultBlock)) return false;
            if (state.getValue(VaultBlock.STATE) != VaultState.ACTIVE) return false;
            boolean ominous = state.getValue(VaultBlock.OMINOUS);

            if (!(level.getBlockEntity(pos) instanceof VaultBlockEntity vault)) return false;
            var shared = vault.getSharedData();
            if (!shared.hasDisplayItem()) return false;
            if (!targets.contains(shared.getDisplayItem().getItem())) return false;

            InteractionHand hand = keyHand(player, ominous);
            if (hand == null) return false;

            if (doClick) {
                mc.gameMode.useItemOn(player, hand, hit);
                lastClick = System.currentTimeMillis();
            }
            return true;
        } catch (Throwable t) {
            return false;
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
