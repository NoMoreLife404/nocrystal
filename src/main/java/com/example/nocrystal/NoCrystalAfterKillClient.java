package com.example.nocrystal;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NoCrystalAfterKillClient implements ClientModInitializer {

    // ====== الإعدادات (غيّرها براحتك) ======
    /** مدة القفل بعد القتل بالـ ticks (20 tick = ثانية). 60 = 3 ثواني */
    private static final int LOCK_DURATION_TICKS = 60;
    /** أقصى مسافة لموت لاعب عشان يتحسب قتل (بالبلوكات) */
    private static final double KILL_RANGE = 16.0;
    // =======================================

    private static boolean enabled = true;
    private static int lockTicks = 0;
    private static final Set<UUID> countedDeaths = new HashSet<>();
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

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("nocrystalafterkill", "main"));

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.nocrystalafterkill.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                lockTicks = 0;
                countedDeaths.clear();
                return;
            }

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

            // كشف موت اللاعبين القريبين
            for (Player p : client.level.players()) {
                if (p == client.player) continue;
                boolean dead = p.isDeadOrDying() || p.getHealth() <= 0.0f;
                if (dead) {
                    if (countedDeaths.add(p.getUUID())
                            && client.player.distanceToSqr(p) <= KILL_RANGE * KILL_RANGE) {
                        lockTicks = LOCK_DURATION_TICKS;
                        actionBar("§eKill! Crystals / Anchors locked");
                    }
                } else {
                    countedDeaths.remove(p.getUUID());
                }
            }
        });

        // منع ضرب الكريستال
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (isLocalPlayer(player) && isLocked() && entity instanceof EndCrystal) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // منع تركيب الكريستال / الأنكر وتفجير أو شحن الأنكر
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!isLocalPlayer(player) || !isLocked()) return InteractionResult.PASS;

            ItemStack held = player.getItemInHand(hand);
            if (held.is(Items.END_CRYSTAL) || held.is(Items.RESPAWN_ANCHOR)) {
                return InteractionResult.FAIL;
            }
            if (level.getBlockState(hitResult.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }
}
