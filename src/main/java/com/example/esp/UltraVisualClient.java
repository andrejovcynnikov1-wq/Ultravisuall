package com.example.esp;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class UltraVisualClient implements ClientModInitializer {
    public static final int ACCENT = 0xFF00CED1;

    public static class Settings {
        public static boolean esp = true;
        public static int boxStyle = 0;
        public static int range = 500;
        public static int espColor = 0;
        public static boolean showInvisible = true;

        public static boolean targetEsp = true;
        public static int targetColor = 0;
        public static float ringSize = 0.8f;
        public static boolean targetHud = true;

        public static boolean hat = true;

        public static boolean auraCircle = true;
        public static float auraRadius = 3.0f;

        public static boolean killAura = false;
        public static float killRange = 3.0f;

        public static boolean watermark = true;
        public static boolean coords = true;
        public static boolean moduleList = true;

        public static final String[] STYLE_NAMES = {"Fill", "Outline", "Both"};
        public static final String[] ESP_NAMES = {"Cyan", "Pink", "Purple", "Green", "Orange"};
        public static final float[][] ESP_RGB = {
                {0.0f, 0.8f, 1.0f},
                {1.0f, 0.4f, 0.8f},
                {0.7f, 0.3f, 1.0f},
                {0.2f, 1.0f, 0.3f},
                {1.0f, 0.6f, 0.1f}
        };
        public static final String[] TARGET_NAMES = {"Health", "Rainbow", "Cyan", "Pink"};
    }

    public static KeyBinding openKey, espKey, targetKey, hatKey;
    public static LivingEntity lastHit = null;
    public static long lastHitTime = 0L;

    private static KeyBinding reg(String name, int key, KeyBinding.Category cat) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(name, InputUtil.Type.KEYSYM, key, cat));
    }

    private static void msg(MinecraftClient c, String name, boolean on) {
        if (c.player != null) {
            c.player.sendMessage(Text.literal("ULTRA VISUAL: " + name + (on ? " ON" : " OFF")), true);
        }
    }

    @Override
    public void onInitializeClient() {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("ultravisual", "main"));
        openKey = reg("key.ultravisual.open", GLFW.GLFW_KEY_RIGHT_SHIFT, cat);
        espKey = reg("key.ultravisual.esp", GLFW.GLFW_KEY_UNKNOWN, cat);
        targetKey = reg("key.ultravisual.target", GLFW.GLFW_KEY_UNKNOWN, cat);
        hatKey = reg("key.ultravisual.hat", GLFW.GLFW_KEY_UNKNOWN, cat);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new UltraScreen());
                }
            }
            while (espKey.wasPressed()) {
                Settings.esp = !Settings.esp;
                msg(client, "ESP", Settings.esp);
            }
            while (targetKey.wasPressed()) {
                Settings.targetEsp = !Settings.targetEsp;
                msg(client, "Target ESP", Settings.targetEsp);
            }
            while (hatKey.wasPressed()) {
                Settings.hat = !Settings.hat;
                msg(client, "China Hat", Settings.hat);
            }
        });

        // kill aura tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!Settings.killAura || client.player == null || client.world == null) return;
            if (client.currentScreen != null) return;
            if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
            LivingEntity nearest = null;
            double bestDist = Settings.killRange + 0.5;
            for (Entity e : client.world.getEntities()) {
                if (e == client.player) continue;
                if (!(e instanceof LivingEntity le)) continue;
                if (!le.isAlive()) continue;
                double dist = client.player.distanceTo(le);
                if (dist < bestDist) { bestDist = dist; nearest = le; }
            }
            if (nearest != null) {
                client.interactionManager.attackEntity(client.player, nearest);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player == MinecraftClient.getInstance().player && entity instanceof LivingEntity le) {
                lastHit = le;
                lastHitTime = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });

        WorldRenderEvents.END_MAIN.register(EspRenderer::render);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> hud(ctx));
    }

    private static void rr(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 1, y, x + w - 1, y + h, col);
        c.fill(x, y + 1, x + w, y + h - 1, col);
    }

    private static void hud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        TextRenderer tr = mc.textRenderer;
        int y = 6;

        if (Settings.watermark) {
            String s = "ULTRA VISUAL  |  " + mc.getCurrentFps() + " FPS";
            int w = tr.getWidth(s) + 14;
            rr(ctx, 6, y, w, 16, 0xCC141414);
            ctx.fill(7, y, 5 + w, y + 1, ACCENT);
            ctx.drawText(tr, Text.literal(s), 13, y + 4, 0xFFFFFFFF, false);
            y += 20;
        }

        if (Settings.coords) {
            String s = String.format("XYZ  %.0f  %.0f  %.0f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ());
            int w = tr.getWidth(s) + 14;
            rr(ctx, 6, y, w, 16, 0xCC141414);
            ctx.drawText(tr, Text.literal(s), 13, y + 4, 0xFFDDDDDD, false);
        }

        if (Settings.moduleList) {
            List<String> names = new ArrayList<>();
            if (Settings.esp) names.add("ESP");
            if (Settings.targetEsp) names.add("Target ESP");
            if (Settings.targetHud) names.add("Target HUD");
            if (Settings.hat) names.add("China Hat");
            if (Settings.auraCircle) names.add("Aura Circle");
            if (Settings.killAura) names.add("Kill Aura");
            int ry = 6;
            for (String n : names) {
                int w = tr.getWidth(n) + 12;
                int x = ctx.getScaledWindowWidth() - w - 6;
                rr(ctx, x, ry, w, 14, 0xCC141414);
                ctx.fill(x + w - 2, ry + 1, x + w, ry + 13, ACCENT);
                ctx.drawText(tr, Text.literal(n), x + 5, ry + 3, 0xFFFFFFFF, false);
                ry += 16;
            }
        }

        LivingEntity t = lastHit;
        if (Settings.targetHud && t != null && t.isAlive()
                && System.currentTimeMillis() - lastHitTime < 6000L) {
            int bx = ctx.getScaledWindowWidth() / 2 + 24;
            int by = ctx.getScaledWindowHeight() / 2 + 24;
            float hp = t.getHealth();
            float frac = MathHelper.clamp(hp / Math.max(1.0f, t.getMaxHealth()), 0.0f, 1.0f);
            rr(ctx, bx, by, 130, 40, 0xCC141414);
            ctx.fill(bx + 1, by, bx + 129, by + 1, ACCENT);
            ctx.drawText(tr, t.getName(), bx + 8, by + 5, 0xFFFFFFFF, false);
            ctx.fill(bx + 8, by + 18, bx + 122, by + 23, 0xFF333333);
            int col = 0xFF000000 | MathHelper.hsvToRgb(0.33f * frac, 1.0f, 1.0f);
            ctx.fill(bx + 8, by + 18, bx + 8 + (int) (114 * frac), by + 23, col);
            ctx.drawText(tr, Text.literal(String.format("%.1f HP", hp)), bx + 8, by + 28, 0xFFDDDDDD, false);
        }
    }
}
