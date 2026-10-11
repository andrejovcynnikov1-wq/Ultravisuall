package com.example.esp;

import com.example.esp.UltraVisualClient.Settings;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class UltraScreen extends Screen {
    private static final int W = 460, SIDE = 110;
    private static final int ACCENT = UltraVisualClient.ACCENT;
    private static final String[] TABS = {"Visuals", "Target", "HUD"};
    private static final float[] RING_SIZES = {0.6f, 0.8f, 1.0f, 1.2f};
    private static final float[] AURA_RADII = {3.0f, 4.0f, 5.0f, 6.0f};
    private static final float[] KILL_RANGES = {3.0f, 4.0f, 5.0f, 6.0f};
    private static final float[] CPS_VALUES = {4f, 8f, 12f, 16f, 20f};
    private static final int[] RANGES = {100, 200, 300, 400, 500};
    private static int tab = 0;

    private static class Item {
        String label;
        Supplier<String> value;
        BooleanSupplier on;
        Runnable click;
        int x, y, w, h;
    }

    private final List<Item> items = new ArrayList<>();
    private int px, py, ph;
    public static String bindingLabel = null;

    public UltraScreen() { super(Text.literal("ULTRA VISUAL")); }

    @Override public boolean shouldPause() { return false; }

    private void toggle(String label, BooleanSupplier get, Runnable click) {
        Item it = new Item(); it.label = label; it.on = get; it.click = click; items.add(it);
    }
    private void cycle(String label, Supplier<String> val, Runnable click) {
        Item it = new Item(); it.label = label; it.value = val; it.click = click; items.add(it);
    }

    private void buildItems() {
        items.clear();
        if (tab == 0) {
            toggle("ESP", () -> Settings.esp, () -> Settings.esp = !Settings.esp);
            cycle("Box style", () -> Settings.STYLE_NAMES[Settings.boxStyle],
                    () -> Settings.boxStyle = (Settings.boxStyle + 1) % Settings.STYLE_NAMES.length);
            cycle("ESP color", () -> Settings.ESP_NAMES[Settings.espColor],
                    () -> Settings.espColor = (Settings.espColor + 1) % Settings.ESP_NAMES.length);
            cycle("Range", () -> Settings.range + " blocks", () -> {
                int idx = 0;
                for (int i = 0; i < RANGES.length; i++) if (RANGES[i] == Settings.range) idx = i;
                Settings.range = RANGES[(idx + 1) % RANGES.length];
            });
            toggle("Show invisible", () -> Settings.showInvisible, () -> Settings.showInvisible = !Settings.showInvisible);
            toggle("Tracers", () -> Settings.tracers, () -> Settings.tracers = !Settings.tracers);
            toggle("Nametags", () -> Settings.nametags, () -> Settings.nametags = !Settings.nametags);
            toggle("Health bars", () -> Settings.healthBars, () -> Settings.healthBars = !Settings.healthBars);
            toggle("Trail", () -> Settings.trail, () -> Settings.trail = !Settings.trail);
            toggle("Jump circle", () -> Settings.jumpCircle, () -> Settings.jumpCircle = !Settings.jumpCircle);
            toggle("China hat (me)", () -> Settings.hat, () -> Settings.hat = !Settings.hat);
            toggle("Aura circle", () -> Settings.auraCircle, () -> Settings.auraCircle = !Settings.auraCircle);
            cycle("Aura radius", () -> Settings.auraRadius + " bl", () -> {
                int idx = 0;
                for (int i = 0; i < AURA_RADII.length; i++) if (AURA_RADII[i] == Settings.auraRadius) idx = i;
                Settings.auraRadius = AURA_RADII[(idx + 1) % AURA_RADII.length];
            });
        } else if (tab == 1) {
            toggle("Target ESP", () -> Settings.targetEsp, () -> Settings.targetEsp = !Settings.targetEsp);
            cycle("Ring color", () -> Settings.TARGET_NAMES[Settings.targetColor],
                    () -> Settings.targetColor = (Settings.targetColor + 1) % Settings.TARGET_NAMES.length);
            cycle("Ring size", () -> String.valueOf(Settings.ringSize), () -> {
                int idx = 0;
                for (int i = 0; i < RING_SIZES.length; i++) if (RING_SIZES[i] == Settings.ringSize) idx = i;
                Settings.ringSize = RING_SIZES[(idx + 1) % RING_SIZES.length];
            });
            toggle("Target HUD (on hit)", () -> Settings.targetHud, () -> Settings.targetHud = !Settings.targetHud);
            toggle("Hit Flash", () -> Settings.hitFlash, () -> Settings.hitFlash = !Settings.hitFlash);
            toggle("Kill Aura", () -> Settings.killAura, () -> Settings.killAura = !Settings.killAura);
            cycle("Kill range", () -> Settings.killRange + " bl", () -> {
                int idx = 0;
                for (int i = 0; i < KILL_RANGES.length; i++) if (KILL_RANGES[i] == Settings.killRange) idx = i;
                Settings.killRange = KILL_RANGES[(idx + 1) % KILL_RANGES.length];
            });
            cycle("Kill CPS", () -> (int) Settings.killCps + " CPS", () -> {
                int idx = 0;
                for (int i = 0; i < CPS_VALUES.length; i++) if (CPS_VALUES[i] == Settings.killCps) idx = i;
                Settings.killCps = CPS_VALUES[(idx + 1) % CPS_VALUES.length];
            });
        } else {
            toggle("Watermark", () -> Settings.watermark, () -> Settings.watermark = !Settings.watermark);
            toggle("Coordinates", () -> Settings.coords, () -> Settings.coords = !Settings.coords);
            toggle("Module list", () -> Settings.moduleList, () -> Settings.moduleList = !Settings.moduleList);
            toggle("Fullbright", () -> Settings.fullbright, () -> Settings.fullbright = !Settings.fullbright);
            toggle("Inventory HUD", () -> Settings.inventoryHud, () -> Settings.inventoryHud = !Settings.inventoryHud);
            toggle("Radar", () -> Settings.radar, () -> Settings.radar = !Settings.radar);
        }
    }

    @Override
    protected void init() {
        buildItems();
        ph = 50 + Math.min(items.size(), 13) * 28 + 10;
        if (ph > this.height - 20) ph = this.height - 20;
        px = (this.width - W) / 2;
        py = (this.height - ph) / 2;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                tab = idx; bindingLabel = null; this.clearAndInit();
            }).dimensions(px + 8, py + 44 + i * 28, SIDE - 16, 24).build());
        }

        int cx = px + SIDE + 8, cw = W - SIDE - 16, y = py + 44;
        for (Item it : items) {
            it.x = cx; it.y = y; it.w = cw; it.h = 24;
            final Item f = it;
            // Main action button
            this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> f.click.run())
                    .dimensions(cx, y, cw - 20, 24).build());
            // Bind square button
            final String lbl = it.label;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> bindingLabel = lbl)
                    .dimensions(cx + cw - 18, y + 3, 16, 18).build());
            y += 28;
        }
    }

    // Poll for key when binding is active
    @Override
    public void tick() {
        if (bindingLabel == null) return;
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            Settings.binds.remove(bindingLabel);
            bindingLabel = null;
            return;
        }
        for (int key = 32; key < 349; key++) {
            if (GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS) {
                Settings.binds.put(bindingLabel, key);
                bindingLabel = null;
                return;
            }
        }
    }

    // no @Override — signature changed in 1.21.11
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);
        ctx.fill(px, py, px + W, py + ph, 0xFF1B1B1B);
        ctx.fill(px, py, px + W, py + 2, ACCENT);
        ctx.fill(px + 4, py + 36, px + SIDE - 4, py + ph - 8, 0xFF151515);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = this.textRenderer;

        Text title = Text.literal("ULTRA VISUAL").formatted(Formatting.BOLD);
        ctx.drawText(tr, title, px + 14, py + 14, 0xFFFFFFFF, false);
        int fps = MinecraftClient.getInstance().getCurrentFps();
        int fcol = fps >= 100 ? 0xFF55FF55 : (fps >= 50 ? 0xFFFFFF55 : 0xFFFF5555);
        ctx.drawText(tr, Text.literal(fps + " FPS"),
                px + 14 + tr.getWidth(title) + 12, py + 14, fcol, false);

        for (int i = 0; i < TABS.length; i++) {
            int bx = px + 8, by = py + 44 + i * 28, bw = SIDE - 16;
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 24;
            ctx.fill(bx, by, bx + bw, by + 24, 0xFF151515);
            if (i == tab) {
                UltraVisualClient.rr(ctx, bx, by, bw, 24, 0xFF262626);
                ctx.fill(bx, by + 4, bx + 3, by + 20, ACCENT);
            } else if (hover) {
                UltraVisualClient.rr(ctx, bx, by, bw, 24, 0xFF1E1E1E);
            }
            ctx.drawText(tr, Text.literal(TABS[i]), bx + 12, by + 8,
                    i == tab ? 0xFFFFFFFF : 0xFF999999, false);
        }

        for (Item it : items) {
            boolean rowHover = mouseX >= it.x && mouseX < it.x + it.w
                    && mouseY >= it.y && mouseY < it.y + it.h;
            boolean isBinding = it.label.equals(bindingLabel);

            ctx.fill(it.x, it.y, it.x + it.w - 20, it.y + it.h, 0xFF1B1B1B);
            UltraVisualClient.rr(ctx, it.x, it.y, it.w - 20, it.h,
                    isBinding ? ACCENT : (rowHover ? 0xFF303030 : 0xFF262626));

            ctx.drawText(tr, Text.literal(it.label), it.x + 10, it.y + 8, 0xFFFFFFFF, false);

            Integer bind = Settings.binds.get(it.label);
            if (bind != null && bind != GLFW.GLFW_KEY_UNKNOWN) {
                String bindStr = " [" + UltraVisualClient.getKeyName(bind) + "]";
                ctx.drawText(tr, Text.literal(bindStr),
                        it.x + 10 + tr.getWidth(it.label), it.y + 8, ACCENT, false);
            }

            // Bind square
            int sqX = it.x + it.w - 18, sqY = it.y + 3;
            boolean sqHover = mouseX >= sqX && mouseX < sqX + 16 && mouseY >= sqY && mouseY < sqY + 18;
            ctx.fill(sqX, sqY, sqX + 16, sqY + 18,
                    isBinding ? 0xFFFFAA00 : (sqHover ? 0xFF555555 : 0xFF333333));
            ctx.drawText(tr, Text.literal(isBinding ? "?" : "B"),
                    sqX + 4, sqY + 5, isBinding ? 0xFF000000 : 0xFF888888, false);

            // Toggle or cycle
            if (it.on != null) {
                boolean on = it.on.getAsBoolean();
                int sx = it.x + it.w - 56, sy = it.y + 6;
                UltraVisualClient.rr(ctx, sx, sy, 28, 12, on ? ACCENT : 0xFF444444);
                ctx.fill(sx + (on ? 18 : 2), sy + 2, sx + (on ? 26 : 10), sy + 10, 0xFFFFFFFF);
            } else {
                String v = it.value.get();
                int vw = tr.getWidth(v) + 14;
                int vx = it.x + it.w - vw - 28;
                UltraVisualClient.rr(ctx, vx, it.y + 4, vw, 16, 0xFF333333);
                ctx.drawText(tr, Text.literal(v), vx + 7, it.y + 8, ACCENT, false);
            }
        }

        // Bind popup
        if (bindingLabel != null) {
            ctx.fill(0, 0, this.width, this.height, 0x88000000);
            int popW = 220, popH = 64;
            int popX = (this.width - popW) / 2, popY = (this.height - popH) / 2;
            UltraVisualClient.rr(ctx, popX, popY, popW, popH, 0xFF2B2B2B);
            ctx.fill(popX + 1, popY, popX + popW - 1, popY + 2, ACCENT);
            ctx.drawText(tr, Text.literal("Press a key to bind:"), popX + 10, popY + 8, 0xFFAAAAAA, false);
            ctx.drawText(tr, Text.literal(bindingLabel).formatted(Formatting.BOLD),
                    popX + 10, popY + 22, 0xFFFFFFFF, false);
            Integer cur = Settings.binds.get(bindingLabel);
            ctx.drawText(tr, Text.literal("Current: " + (cur != null ? UltraVisualClient.getKeyName(cur) : "None")),
                    popX + 10, popY + 36, ACCENT, false);
            ctx.drawText(tr, Text.literal("ESC = remove bind"), popX + 10, popY + 50, 0xFF666666, false);
        }
    }
}
