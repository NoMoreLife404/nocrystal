package com.example.nocrystal;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NoCrystalAfterKillClient implements ClientModInitializer {

    // مدة القفل بالـ ticks (20 = ثانية). 100 = 5 ثواني
    private static final int LOCK_DURATION_TICKS = 100;
    // أقصى مسافة للقتل (بالبلوكات)
    private static final double KILL_RANGE = 16.0;
    // آخر كام tick نعتبر فيهم إنك استخدمت كريستال/أنكر
    private static final int RECENT_ACTION_TICKS = 100;

    private static boolean enabled = true;
    private static int lockTicks = 0;
    private static int tick = 0;
    private static int lastActionTick = -100000;

    private static final Set<UUID> countedDeaths = new HashSet<>();
    private static Map<UUID, String> nearPrev = new HashMap<>();
    private static final Map<String, Integer> recentNear = new HashMap<>();
    private static KeyMapping toggleKey;

    private static boolean isLocked() {
        return enabled && lockTicks > 0;
    }

    private static boolean isLocalPlayer(Player player) {
        return player == Minecraft.getInstance().player;
    }

    private static void actionBar(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), true);
        }
    }

    private static void trigger(String reason) {
        if (!enabled) return;
        lockTicks = LOCK_DURATION_TICKS;
        actionBar("§eKill! Crystals / Anchors locked (" + reason + ")");
    }

    private static String argToString(Object o) {
        if (o instanceof Component c) return c.getString();
        return String.valueOf(o);
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("nocrystalafterkill", "main"));

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.nocrystalafterkill.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                lockTicks = 0;
                countedDeaths.clear();
                nearPrev = new HashMap<>();
                recentNear.clear();
                return;
            }

            tick++;

            while (toggleKey.consumeClick()) {
                enabled = !enabled;
                lockTicks = 0;
                actionBar(enabled ? "§aNo Crystal After Kill: ON" : "§cNo Crystal After Kill: OFF");
            }

            if (lockTicks > 0) {
                lockTicks--;
                if (lockTicks == 0 && enabled) {
                    actionBar("§aCrystals / Anchors unlocked");
                }
            }

            if (!enabled) return;

            double range2 = KILL_RANGE * KILL_RANGE;
            Map<UUID, String> nowNear = new HashMap<>();

            for (Player p : client.level.players()) {
                if (p == client.player) continue;
                boolean inRange = client.player.distanceToSqr(p) <= range2;
                boolean dead = p.isDeadOrDying() || p.getHealth() <= 0.0f;
                if (dead) {
                    if (countedDeaths.add(p.getUUID()) && inRange) {
                        trigger("death");
                    }
                } else {
                    countedDeaths.remove(p.getUUID());
                    if (inRange) {
                        String name = p.getScoreboardName();
                        nowNear.put(p.getUUID(), name);
                        recentNear.put(name, tick);
                    }
                }
            }

            // لاعب كان جنبك واختفى تماماً بعد ما استخدمت كريستال/أنكر
            for (UUID id : nearPrev.keySet()) {
                if (!nowNear.containsKey(id)
                        && client.level.getPlayerByUUID(id) == null
                        && tick - lastActionTick <= RECENT_ACTION_TICKS) {
                    trigger("gone");
                }
            }
            nearPrev = nowNear;
        });

        // كشف من رسالة الموت في الشات
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || !enabled) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (!(message.getContents() instanceof TranslatableContents tc)) return;
            if (!tc.getKey().startsWith("death.")) return;

            Object[] args = tc.getArgs();
            if (args.length == 0) return;

            String me = mc.player.getScoreboardName();
