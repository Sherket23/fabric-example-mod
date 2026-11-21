package ru.vaultauto;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class VaultAutoClient implements ClientModInitializer {

    interface Filter { boolean matches(ItemStack stack); }

    static Filter simple(Item it) { return stack -> stack.is(it); }

    static Filter windBurst(int level) {
        return stack -> {
            if (!stack.is(Items.ENCHANTED_BOOK)) return false;
            ItemEnchantments ench = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (ench == null) return false;
            for (var entry : ench.entrySet()) {
                if (entry.getKey().is(Enchantments.WIND_BURST) && entry.getIntValue() == level) return true;
            }
            return false;
        };
    }

    static final String[] NAMES = {
        "Ядро булавы", "Узор Flow", "Узор Guster", "Шаблон Flow", "Шаблон Bolt",
        "Зловещий флакон", "Зач. яблоко", "Трезубец", "Изумруд",
        "Порыв ветра I", "Порыв ветра II", "Порыв ветра III"
    };
    static final Filter[] FILTERS = {
        simple(Items.HEAVY_CORE), simple(Items.FLOW_BANNER_PATTERN), simple(Items.GUSTER_BANNER_PATTERN),
        simple(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE), simple(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE),
        simple(Items.OMINOUS_BOTTLE), simple(Items.ENCHANTED_GOLDEN_APPLE), simple(Items.TRIDENT), simple(Items.EMERALD),
        windBurst(1), windBurst(2), windBurst(3)
    };

    static final Set<Integer> selected = ConcurrentHashMap.newKeySet();
    static volatile boolean enabled = false;
    static volatile boolean menuOpen = false;
    static volatile long lastClick = 0;
    static volatile boolean pending = false;
    static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("vaultauto", "main"));

        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vaultauto.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) mc.gui.setScreen(new PickScreen());
        });

        Thread t = new Thread(() -> {
            while (true) {
                try {
                    boolean active = enabled && !selected.isEmpty() && !menuOpen;
                    Thread.sleep(active ? 1 : 50);
                    if (!active || pending) continue;
                    Minecraft mc = Minecraft.getInstance();
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

    static boolean tryClick(Minecraft mc, boolean doClick) {
        try {
            if (!enabled || selected.isEmpty() || menuOpen) return false;
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
            ItemStack displayStack = shared.getDisplayItem();

            boolean match = false;
            for (int idx : selected) {
                if (FILTERS[idx].matches(displayStack)) { match = true; break; }
            }
            if (!match) return false;

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
        return selected.contains(i)
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
            }).bounds(cx - 150, 20, 266, 24).build());

            addRenderableWidget(Button.builder(
                Component.literal("⚙").withStyle(ChatFormatting.BOLD),
                b -> this.minecraft.gui.setScreen(new KeySetScreen(this))
            ).bounds(cx + 120, 20, 30, 24).build());

            for (int i = 0; i < NAMES.length; i++) {
                final int idx = i;
                int x = cx - 150 + (i % 2) * 152;
                int y = 52 + (i / 2) * 24;
                addRenderableWidget(Button.builder(itemLabel(i), b -> {
                    if (!selected.remove(idx)) selected.add(idx);
                    b.setMessage(itemLabel(idx));
                }).bounds(x, y, 148, 20).build());
            }

            int closeY = 52 + ((NAMES.length + 1) / 2) * 24 + 4;
            addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(cx - 150, closeY, 300, 20).build());
        }

        @Override public void removed() { menuOpen = false; }
    }

    /** Экран "Нажмите клавишу" для назначения клавиши открытия меню. */
    static class KeySetScreen extends Screen {
        final Screen parent;
        KeySetScreen(Screen parent) {
            super(Component.literal("Нажмите клавишу..."));
            this.parent = parent;
        }

        @Override protected void init() {
            int cx = width / 2;
            int cy = height / 2;

            addRenderableWidget(new StringWidget(
                cx - 150, cy - 40, 300, 20,
                Component.literal("Нажмите клавишу для открытия меню"), this.font));

            addRenderableWidget(new StringWidget(
                cx - 150, cy - 18, 300, 12,
                Component.literal("Esc — отмена").withStyle(ChatFormatting.GRAY), this.font));

            addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> this.minecraft.gui.setScreen(parent))
                .bounds(cx - 75, cy + 30, 150, 20).build());
        }

        @Override public boolean keyPressed(KeyEvent event) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                this.minecraft.gui.setScreen(parent);
                return true;
            }
            openKey.setKey(InputConstants.Type.KEYBOARD.getOrCreate(event.key()));
            KeyMapping.resetMapping();
            if (this.minecraft.options != null) this.minecraft.options.save();
            this.minecraft.gui.setScreen(parent);
            return true;
        }
    }
}
