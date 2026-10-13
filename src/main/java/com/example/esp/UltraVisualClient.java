package com.example.esp;

import java.util.*;
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
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class UltraVisualClient implements ClientModInitializer {
    public static final int ACCENT = 0xFF00CED1;

    public static class Settings {
        public static boolean esp = true;
        public static int boxStyle = 0;
        public static int range = 500;
        public static int espColor = 0;
        public static boolean showInvisible = true;
        public static boolean tracers = true;
        public static boolean nametags = true;
        public static boolean healthBars = true;
        public static boolean trail = false;
        public static boolean jumpCircle = true;
        public static boolean hat = true;
        public static boolean auraCircle = true;
        public static float auraRadius = 3.0f;

        public static boolean targetEsp = true;
        public static int targetColor = 0;
        public static float ringSize = 0.8f;
        public static boolean targetHud = true;
        public static boolean hitFlash = true;
        public static boolean killAura = false;
        public static float killRange = 3.0f;
        public static float killCps = 8.0f;

        // New from PulseVisuals
        public static boolean damageNumbers = true;
        public static boolean trajectoryPredict = true;
        public static boolean crosshairTarget = true;

        public static boolean watermark = true;
        public static boolean coords = true;
        public static boolean moduleList = true;
        public static boolean fullbright = false;
        public static boolean inventoryHud = true;
        public static int inventoryHudX = 100;
        public static int inventoryHudY = 100;
        public static boolean radar = true;
        public static int radarX = 10;
        public static int radarY = 200;

        public static final String[] STYLE_NAMES = {"Fill", "Outline", "Both"};
        public static final String[] ESP_NAMES = {"Cyan", "Pink", "Purple", "Green", "Orange"};
        public static final float[][] ESP_RGB = {
                {0.0f, 0.8f, 1.0f}, {1.0f, 0.4f, 0.8f},
                {0.7f, 0.3f, 1.0f}, {0.2f, 1.0f, 0.3f}, {1.0f, 0.6f, 0.1f}
        };
        public static final String[] TARGET_NAMES = {"Health", "Rainbow", "Cyan", "Pink"};
        public static final Map<String, Integer> binds = new HashMap<>();
    }

    // Damage numbers system
    public static class DamageNum {
        public Vec3d pos;
        public float damage;
        public boolean isCrit;
        public int age;
        public final int maxAge = 30;
        DamageNum(Vec3d pos, float damage, boolean isCrit) {
            this.pos = pos; this.damage = damage; this.isCrit = isCrit;
        }
        float alpha() { return 1.0f - (float) age / maxAge; }
        float offsetY() { return age * 0.04f; }
    }
    public static final List<DamageNum> damageNums = new ArrayList<>();
    private static final Map<Integer, Float> lastHealth = new HashMap<>();

    // Trajectory points for projectile prediction
    public static final List<Vec3d> trajectoryPoints = new ArrayList<>();

    // Crosshair target (from PulseVisuals logic)
    public static LivingEntity crosshairTarget = null;

    public static KeyBinding openKey, espKey, targetKey, hatKey;
    public static LivingEntity lastHit = null;
    public static long lastHitTime = 0L;
    public static long lastAttackMs = 0L;
    public static final LinkedList<Vec3d> trailPoints = new LinkedList<>();
    public static final LinkedList<Long> trailTimes = new LinkedList<>();
    private static Vec3d lastTrailPos = null;
    public static boolean wasOnGround = true;
    public static Vec3d jumpPos = null;
    public static long jumpTime = 0L;
    private static double savedGamma = 1.0;
    private static boolean gammaSet = false;
    private static final Map<String, Boolean> bindPrevState = new HashMap<>();

    private static KeyBinding reg(String name, int key, KeyBinding.Category cat) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(name, InputUtil.Type.KEYSYM, key, cat));
    }

    private static void msg(MinecraftClient c, String name, boolean on) {
        if (c.player != null)
            c.player.sendMessage(Text.literal("UV: " + name + (on ? " ON" : " OFF")), true);
    }

    private static void triggerBind(String label, MinecraftClient mc) {
        switch (label) {
            case "ESP" -> { Settings.esp = !Settings.esp; msg(mc, "ESP", Settings.esp); }
            case "Target ESP" -> { Settings.targetEsp = !Settings.targetEsp; msg(mc, "Target ESP", Settings.targetEsp); }
            case "Target HUD (on hit)" -> { Settings.targetHud = !Settings.targetHud; msg(mc, "Target HUD", Settings.targetHud); }
            case "Hit Flash" -> { Settings.hitFlash = !Settings.hitFlash; msg(mc, "Hit Flash", Settings.hitFlash); }
            case "Aura circle" -> { Settings.auraCircle = !Settings.auraCircle; msg(mc, "Aura Circle", Settings.auraCircle); }
            case "Kill Aura" -> { Settings.killAura = !Settings.killAura; msg(mc, "Kill Aura", Settings.killAura); }
            case "Tracers" -> { Settings.tracers = !Settings.tracers; msg(mc, "Tracers", Settings.tracers); }
            case "Nametags" -> { Settings.nametags = !Settings.nametags; msg(mc, "Nametags", Settings.nametags); }
            case "Health bars" -> { Settings.healthBars = !Settings.healthBars; msg(mc, "Health Bars", Settings.healthBars); }
            case "Trail" -> { Settings.trail = !Settings.trail; msg(mc, "Trail", Settings.trail); }
            case "Jump circle" -> { Settings.jumpCircle = !Settings.jumpCircle; msg(mc, "Jump Circle", Settings.jumpCircle); }
            case "China hat (me)" -> { Settings.hat = !Settings.hat; msg(mc, "China Hat", Settings.hat); }
            case "Show invisible" -> { Settings.showInvisible = !Settings.showInvisible; msg(mc, "Show Invis", Settings.showInvisible); }
            case "Fullbright" -> { Settings.fullbright = !Settings.fullbright; msg(mc, "Fullbright", Settings.fullbright); }
            case "Radar" -> { Settings.radar = !Settings.radar; msg(mc, "Radar", Settings.radar); }
            case "Damage numbers" -> { Settings.damageNumbers = !Settings.damageNumbers; msg(mc, "Damage Nums", Settings.damageNumbers); }
            case "Trajectory" -> { Settings.trajectoryPredict = !Settings.trajectoryPredict; msg(mc, "Trajectory", Settings.trajectoryPredict); }
            case "Crosshair target" -> { Settings.crosshairTarget = !Settings.crosshairTarget; msg(mc, "XHair Target", Settings.crosshairTarget); }
            case "Watermark" -> Settings.watermark = !Settings.watermark;
            case "Coordinates" -> Settings.coords = !Settings.coords;
            case "Module list" -> Settings.moduleList = !Settings.moduleList;
            case "Inventory HUD" -> Settings.inventoryHud = !Settings.inventoryHud;
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
            while (openKey.wasPressed())
                if (client.currentScreen == null) client.setScreen(new UltraScreen());
            while (espKey.wasPressed()) { Settings.esp = !Settings.esp; msg(client, "ESP", Settings.esp); }
            while (targetKey.wasPressed()) { Settings.targetEsp = !Settings.targetEsp; msg(client, "Target ESP", Settings.targetEsp); }
            while (hatKey.wasPressed()) { Settings.hat = !Settings.hat; msg(client, "China Hat", Settings.hat); }

            if (client.player == null || client.world == null) return;

            // Custom binds
            long handle = client.getWindow().getHandle();
            for (Map.Entry<String, Integer> entry : Settings.binds.entrySet()) {
                String label = entry.getKey();
                int key = entry.getValue();
                if (key == GLFW.GLFW_KEY_UNKNOWN) continue;
                boolean pressed = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
                boolean prev = bindPrevState.getOrDefault(label, false);
                if (pressed && !prev) triggerBind(label, client);
                bindPrevState.put(label, pressed);
            }

            // Fullbright
            if (Settings.fullbright) {
                if (!gammaSet) { savedGamma = client.options.getGamma().getValue(); gammaSet = true; }
                client.options.getGamma().setValue(100.0);
            } else if (gammaSet) {
                client.options.getGamma().setValue(savedGamma); gammaSet = false;
            }

            // Trail
            if (Settings.trail) {
                Vec3d pos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
                if (lastTrailPos == null || pos.distanceTo(lastTrailPos) > 0.3) {
                    trailPoints.addFirst(pos); trailTimes.addFirst(System.currentTimeMillis());
                    lastTrailPos = pos;
                    while (trailPoints.size() > 60) { trailPoints.removeLast(); trailTimes.removeLast(); }
                }
                while (!trailTimes.isEmpty() && System.currentTimeMillis() - trailTimes.getLast() > 3000) {
                    trailPoints.removeLast(); trailTimes.removeLast();
                }
            } else { trailPoints.clear(); trailTimes.clear(); lastTrailPos = null; }

            // Jump circle
            if (Settings.jumpCircle) {
                boolean onGround = client.player.isOnGround();
                if (wasOnGround && !onGround) {
                    jumpPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
                    jumpTime = System.currentTimeMillis();
                }
                wasOnGround = onGround;
            }

            // Crosshair target (PulseVisuals logic)
            if (Settings.crosshairTarget && client.crosshairTarget != null) {
                if (client.crosshairTarget.getType() == HitResult.Type.ENTITY
                        && client.crosshairTarget instanceof EntityHitResult ehr
                        && ehr.getEntity() instanceof LivingEntity le
                        && le != client.player) {
                    crosshairTarget = le;
                } else {
                    crosshairTarget = null;
                }
            } else {
                crosshairTarget = null;
            }

            // Damage numbers: track health changes of nearby entities
            if (Settings.damageNumbers) {
                damageNums.removeIf(d -> d.age++ >= d.maxAge);
                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity le) || e == client.player) continue;
                    if (client.player.distanceTo(le) > 32) continue;
                    int id = le.getId();
                    float prev = lastHealth.getOrDefault(id, le.getHealth());
                    float curr = le.getHealth();
                    if (curr < prev) {
                        float dmg = prev - curr;
                        boolean crit = dmg >= 4.0f;
                        Vec3d pos = new Vec3d(le.getX(), le.getY() + le.getHeight() + 0.3, le.getZ());
                        damageNums.add(new DamageNum(pos, dmg, crit));
                    }
                    lastHealth.put(id, curr);
                }
            }

            // Trajectory: PulseVisuals logic adapted
            if (Settings.trajectoryPredict) {
                trajectoryPoints.clear();
                List<ProjectileEntity> projectiles = client.world.getEntitiesByClass(
                        ProjectileEntity.class,
                        client.player.getBoundingBox().expand(32),
                        e -> true);
                if (!projectiles.isEmpty()) {
                    ProjectileEntity closest = null;
                    double minDist = Double.MAX_VALUE;
                    for (ProjectileEntity p : projectiles) {
                        double d = client.player.distanceTo(p);
                        if (d < minDist) { minDist = d; closest = p; }
                    }
                    if (closest != null) {
                        Vec3d pos = closest.getPos();
                        Vec3d vel = closest.getVelocity();
                        for (int i = 0; i < 30; i++) {
                            trajectoryPoints.add(pos);
                            vel = vel.add(0, -0.03, 0).multiply(0.99);
                            pos = pos.add(vel);
                        }
                    }
                }
            } else {
                trajectoryPoints.clear();
            }
        });

        // Kill aura
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!Settings.killAura || client.player == null || client.world == null || client.currentScreen != null) return;
            long now = System.currentTimeMillis();
            if (now - lastAttackMs < (long)(1000f / Settings.killCps)) return;
            if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
            LivingEntity nearest = null;
            double bestDist = Settings.killRange + 0.5;
            for (Entity e : client.world.getEntities()) {
                if (e == client.player || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
                double dist = client.player.distanceTo(le);
                if (dist < bestDist) { bestDist = dist; nearest = le; }
            }
            if (nearest != null) {
                client.interactionManager.attackEntity(client.player, nearest);
                client.player.swingHand(Hand.MAIN_HAND);
                lastAttackMs = now;
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player == MinecraftClient.getInstance().player && entity instanceof LivingEntity le) {
                lastHit = le; lastHitTime = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });

        WorldRenderEvents.END_MAIN.register(EspRenderer::render);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> hud(ctx));
    }

    public static int[] worldToScreen(Vec3d world, Vec3d cam, float pitch, float yaw, int fov, int sw, int sh) {
        double dx = world.x - cam.x, dy = world.y - cam.y, dz = world.z - cam.z;
        float yRad = yaw * (float)Math.PI / 180f, pRad = pitch * (float)Math.PI / 180f;
        float sinY = MathHelper.sin(yRad), cosY = MathHelper.cos(yRad);
        float sinP = MathHelper.sin(pRad), cosP = MathHelper.cos(pRad);
        double cx2 = dx * cosY + dz * sinY;
        double czPre = dx * sinY - dz * cosY;
        double finalY = dy * cosP - czPre * sinP;
        double finalZ = dy * sinP + czPre * cosP;
        double finalX = cx2;
        if (finalZ >= -0.01) return null;
        float f = 1f / (float)Math.tan(fov * Math.PI / 360.0);
        float aspect = (float)sw / sh;
        int sx = (int)(sw / 2f + finalX / (-finalZ) * f / aspect * sw / 2f);
        int sy = (int)(sh / 2f - finalY / (-finalZ) * f * sh / 2f);
        return new int[]{sx, sy};
    }

    public static String getKeyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "None";
        String n = GLFW.glfwGetKeyName(key, 0);
        if (n != null) return n.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_F1 -> "F1"; case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3"; case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5"; case GLFW.GLFW_KEY_F6 -> "F6";
            default -> "K" + key;
        };
    }

    static void rr(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 1, y, x + w - 1, y + h, col);
        c.fill(x, y + 1, x + w, y + h - 1, col);
    }

    static void drawLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) return;
        float sx = (float)(x2 - x1) / steps, sy = (float)(y2 - y1) / steps;
        for (int i = 0; i <= steps; i += 3) {
            int px = (int)(x1 + sx * i), py = (int)(y1 + sy * i);
            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void hud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
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
            if (Settings.tracers) names.add("Tracers");
            if (Settings.nametags) names.add("Nametags");
            if (Settings.healthBars) names.add("Health Bars");
            if (Settings.trail) names.add("Trail");
            if (Settings.fullbright) names.add("Fullbright");
            if (Settings.radar) names.add("Radar");
            if (Settings.damageNumbers) names.add("Dmg Numbers");
            if (Settings.trajectoryPredict) names.add("Trajectory");
            if (Settings.crosshairTarget) names.add("XHair Target");
            int ry = 6;
            for (String n : names) {
                int w = tr.getWidth(n) + 12;
                int x = sw - w - 6;
                rr(ctx, x, ry, w, 14, 0xCC141414);
                ctx.fill(x + w - 2, ry + 1, x + w, ry + 13, ACCENT);
                ctx.drawText(tr, Text.literal(n), x + 5, ry + 3, 0xFFFFFFFF, false);
                ry += 16;
            }
        }

        // Tracers + Nametags + Health bars
        if ((Settings.tracers || Settings.nametags || Settings.healthBars) && mc.gameRenderer != null) {
            Camera cam = mc.gameRenderer.getCamera();
            Vec3d camPos = cam.getCameraPos();
            float pitch = cam.getPitch(), yawC = cam.getYaw();
            int fov = mc.options.getFov().getValue();
            int cx = sw / 2, cy = sh / 2;
            for (var p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                Vec3d headPos = new Vec3d(p.getX(), p.getY() + p.getHeight() * 0.9, p.getZ());
                int[] screen = worldToScreen(headPos, camPos, pitch, yawC, fov, sw, sh);
                if (screen == null) continue;
                int sx = screen[0], scy = screen[1];
                if (Settings.tracers) drawLine(ctx, cx, cy, sx, scy, 0x9900CED1);
                if (Settings.nametags) {
                    float hp = p.getHealth(), maxHp = p.getMaxHealth();
                    int hpCol = 0xFF000000 | MathHelper.hsvToRgb(0.33f * (hp / Math.max(1f, maxHp)), 1f, 1f);
                    String name = p.getName().getString();
                    String hpStr = String.format("%.0f HP", hp);
                    String dist = String.format("%.0fm", p.distanceTo(mc.player));
                    int nw = Math.max(tr.getWidth(name), tr.getWidth(hpStr)) + 8;
                    rr(ctx, sx - nw / 2, scy - 26, nw, 30, 0xAA141414);
                    ctx.drawText(tr, Text.literal(name), sx - tr.getWidth(name) / 2, scy - 24, 0xFFFFFFFF, false);
                    ctx.drawText(tr, Text.literal(hpStr), sx - tr.getWidth(hpStr) / 2, scy - 14, hpCol, false);
                    ctx.drawText(tr, Text.literal(dist), sx - tr.getWidth(dist) / 2, scy - 4, 0xFF888888, false);
                }
                if (Settings.healthBars) {
                    float hp = p.getHealth();
                    float frac = MathHelper.clamp(hp / Math.max(1f, p.getMaxHealth()), 0f, 1f);
                    int bw = 30, bh = 4;
                    int bx = sx - bw / 2, by = scy - 30;
                    ctx.fill(bx, by, bx + bw, by + bh, 0xFF333333);
                    int col = 0xFF000000 | MathHelper.hsvToRgb(0.33f * frac, 1f, 1f);
                    ctx.fill(bx, by, bx + (int)(bw * frac), by + bh, col);
                }
            }
        }

        // Damage numbers (PulseVisuals adapted)
        if (Settings.damageNumbers && mc.gameRenderer != null) {
            Camera cam = mc.gameRenderer.getCamera();
            Vec3d camPos = cam.getCameraPos();
            float pitch = cam.getPitch(), yawC = cam.getYaw();
            int fov = mc.options.getFov().getValue();
            for (DamageNum dn : damageNums) {
                Vec3d pos = dn.pos.add(0, dn.offsetY(), 0);
                int[] screen = worldToScreen(pos, camPos, pitch, yawC, fov, sw, sh);
                if (screen == null) continue;
                int alpha = (int)(dn.alpha() * 255);
                if (alpha <= 0) continue;
                String text = dn.isCrit ? String.format("✦ %.1f", dn.damage) : String.format("%.1f", dn.damage);
                int col = dn.isCrit ? (alpha << 24 | 0xFF4400) : (alpha << 24 | 0xFFAA00);
                ctx.drawText(tr, Text.literal(text), screen[0] - tr.getWidth(text) / 2, screen[1], col, true);
            }
        }

        // Trajectory dots (PulseVisuals adapted)
        if (Settings.trajectoryPredict && !trajectoryPoints.isEmpty() && mc.gameRenderer != null) {
            Camera cam = mc.gameRenderer.getCamera();
            Vec3d camPos = cam.getCameraPos();
            float pitch = cam.getPitch(), yawC = cam.getYaw();
            int fov = mc.options.getFov().getValue();
            for (int i = 0; i < trajectoryPoints.size(); i++) {
                int[] screen = worldToScreen(trajectoryPoints.get(i), camPos, pitch, yawC, fov, sw, sh);
                if (screen == null) continue;
                float fade = 1.0f - (float) i / trajectoryPoints.size();
                int alpha = (int)(fade * 180);
                int col = (alpha << 24) | 0xFFFF44;
                ctx.fill(screen[0] - 1, screen[1] - 1, screen[0] + 1, screen[1] + 1, col);
            }
        }

        // Crosshair target HUD (PulseVisuals TargetHUD logic)
        if (Settings.crosshairTarget && crosshairTarget != null && crosshairTarget.isAlive()) {
            int bx = sw / 2 - 65, by = sh / 2 + 30;
            float hp = crosshairTarget.getHealth();
            float frac = MathHelper.clamp(hp / Math.max(1f, crosshairTarget.getMaxHealth()), 0f, 1f);
            int armor = crosshairTarget.getArmor();
            double dist = mc.player.distanceTo(crosshairTarget);
            rr(ctx, bx, by, 130, 48, 0xCC141414);
            ctx.fill(bx + 1, by, bx + 129, by + 2, ACCENT);
            ctx.drawText(tr, crosshairTarget.getName(), bx + 8, by + 5, 0xFFFFFFFF, false);
            String distStr = String.format("%.1fm", dist);
            ctx.drawText(tr, Text.literal(distStr), bx + 130 - tr.getWidth(distStr) - 6, by + 5, 0xFF888888, false);
            ctx.fill(bx + 8, by + 18, bx + 122, by + 23, 0xFF333333);
            int col = 0xFF000000 | MathHelper.hsvToRgb(0.33f * frac, 1f, 1f);
            ctx.fill(bx + 8, by + 18, bx + 8 + (int)(114 * frac), by + 23, col);
            ctx.drawText(tr, Text.literal(String.format("%.1f / %.1f", hp, crosshairTarget.getMaxHealth())),
                    bx + 8, by + 28, 0xFFDDDDDD, false);
            ctx.drawText(tr, Text.literal("🛡 " + armor), bx + 8, by + 38, 0xFFAAAAAA, false);
        }

        // Old Target HUD (on hit)
        LivingEntity t = lastHit;
        if (Settings.targetHud && t != null && t.isAlive() && System.currentTimeMillis() - lastHitTime < 6000L) {
            int bx = sw / 2 + 24, by = sh / 2 + 24;
            float hp = t.getHealth();
            float frac = MathHelper.clamp(hp / Math.max(1f, t.getMaxHealth()), 0f, 1f);
            rr(ctx, bx, by, 130, 40, 0xCC141414);
            ctx.fill(bx + 1, by, bx + 129, by + 1, ACCENT);
            ctx.drawText(tr, t.getName(), bx + 8, by + 5, 0xFFFFFFFF, false);
            ctx.fill(bx + 8, by + 18, bx + 122, by + 23, 0xFF333333);
            int col = 0xFF000000 | MathHelper.hsvToRgb(0.33f * frac, 1f, 1f);
            ctx.fill(bx + 8, by + 18, bx + 8 + (int)(114 * frac), by + 23, col);
            ctx.drawText(tr, Text.literal(String.format("%.1f HP", hp)), bx + 8, by + 28, 0xFFDDDDDD, false);
        }

        // Hit flash
        if (Settings.hitFlash && lastHitTime > 0 && System.currentTimeMillis() - lastHitTime < 150) {
            int alpha = (int)(70 * (1.0 - (System.currentTimeMillis() - lastHitTime) / 150.0));
            ctx.fill(0, 0, sw, sh, (alpha << 24) | 0xFF2200);
        }

        // Inventory HUD
        if (Settings.inventoryHud) {
            int hudX = Settings.inventoryHudX, hudY2 = Settings.inventoryHudY;
            int slotSize = 18, pad = 4;
            int hudW = 9 * slotSize + 2 * pad, hudH = slotSize + 2 * pad;
            rr(ctx, hudX, hudY2, hudW, hudH, 0xCC141414);
            ctx.fill(hudX + 1, hudY2, hudX + hudW - 1, hudY2 + 1, ACCENT);
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                int ix = hudX + pad + i * slotSize, iy = hudY2 + pad;
                ctx.fill(ix, iy, ix + slotSize - 1, iy + slotSize - 1, 0xFF2A2A2A);
                if (i == mc.player.getInventory().getSelectedSlot())
                    ctx.fill(ix, iy, ix + slotSize - 1, iy + 1, 0xFFFFFFFF);
                ctx.drawItem(stack, ix + 1, iy + 1);
            }
        }

        // Radar
        if (Settings.radar) {
            int rx = Settings.radarX, ry2 = Settings.radarY;
            int rs = 80;
            rr(ctx, rx, ry2, rs, rs, 0xCC141414);
            ctx.fill(rx + 1, ry2, rx + rs - 1, ry2 + 1, ACCENT);
            int centerX = rx + rs / 2, centerY = ry2 + rs / 2;
            ctx.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, 0xFFFFFFFF);
            float yaw2 = mc.player.getYaw();
            for (var p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                double dx = p.getX() - mc.player.getX();
                double dz = p.getZ() - mc.player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > Settings.range) continue;
                double scale = (rs / 2.0 - 4) / Math.max(50.0, dist);
                double rad = Math.toRadians(yaw2);
                double rotX = dx * Math.cos(rad) - dz * Math.sin(rad);
                double rotZ = dx * Math.sin(rad) + dz * Math.cos(rad);
                int dotX = MathHelper.clamp((int)(centerX + rotX * scale), rx + 2, rx + rs - 2);
                int dotY = MathHelper.clamp((int)(centerY + rotZ * scale), ry2 + 2, ry2 + rs - 2);
                ctx.fill(dotX - 1, dotY - 1, dotX + 1, dotY + 1, 0xFFFF4444);
            }
        }
    }
}
