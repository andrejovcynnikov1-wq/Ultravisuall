package com.example.esp;

import com.example.esp.UltraVisualClient.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class UltraScreen extends Screen {
    private static final int W = 440, H = 250, SIDE = 110;
    private static final int ACCENT = UltraVisualClient.ACCENT;
    private static final String[] TABS = {"Visuals", "Target", "HUD"};
    private static final float[] RING_SIZES = {0.6f, 0.8f, 1.0f, 1.2f};
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
    private int px, py;

    public UltraScreen() {
        super(Text.literal("ULTRA VISUAL"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void toggle(String label, BooleanSupplier get, Runnable click) {
        Item it = new Item();
        it.label = label;
        it.on = get;
        it.click = click;
        items.add(it);
    }

    private void cycle(String label, Supplier<String> val, Runnable click) {
        Item it = new Item();
        it.label = label;
        it.value = val;
        it.click = click;
        items.add(it);
    }

    private void buildItems() {
        items.clear();
        if (tab == 0) {
            toggle("ESP", () -> Settings.esp, () -> Settings.esp = !Settings.esp);
            cycle("Box style", () -> Settings.STYLE_NAMES[Settings.boxStyle],
                    () -> Settings.boxStyle = (Settings.boxStyle + 1) % Settings.STYLE_NAMES.length);
            cycle("ESP color", () -> Settings.ESP_NAMES[Settings.espColor],
                    () -> Settings.espColor = (Settings.espColor + 1) % Settings.ESP_NAMES.length);
            cycle("Range", () -> String.valueOf(Settings.range), () -> {
                int idx = 0;
                for (int i = 0; i < RANGES.length; i++) if (RANGES[i] == Settings.range) idx = i;
                Settings.range = RANGES[(idx + 1) % RANGES.length];
            });
            toggle("China hat", () -> Settings.hat, () -> Settings.hat = !Settings.hat);
            cycle("Hat on", () -> Settings.hatAll ? "All players" : "Target",
                    () -> Settings.hatAll = !Settings.hatAll);
        } else if (tab == 1) {
            toggle("Target ESP", () -> Settings.targetEsp, () -> Settings.targetEsp = !Settings.targetEsp);
            cycle("Ring color", () -> Settings.TARGET_NAMES[Settings.targetColor],
                    () -> Settings.targetColor = (Settings.targetColor + 1) % Settings.TARGET_NAMES.length);
            cycle("Ring size", () -> String.valueOf(Settings.ringSize), () -> {
                int idx = 0;
                for (int i = 0; i < RING_SIZES.length; i++) if (RING_SIZES[i] == Settings.ringSize) idx = i;
                Settings.ringSize = RING_SIZES[(idx + 1) % RING_SIZES.length];
            });
            toggle("Target HUD", () -> Settings.targetHud, () -> Settings.targetHud = !Settings.targetHud);
        } else {
            toggle("Watermark", () -> Settings.watermark, () -> Settings.watermark = !Settings.watermark);
            toggle("Coordinates", () -> Settings.coords, () -> Settings.coords = !Settings.coords);
            toggle("Module list", () -> Settings.moduleList, () -> Settings.moduleList = !Settings.moduleList);
        }
    }

    @Override
    protected void init() {
        px = (this.width - W) / 2;
        py = (this.height - H) / 2;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                tab = idx;
                this.clearAndInit();
            }).dimensions(px + 8, py + 44 + i * 28, SIDE - 16, 24).build());
        }

        buildItems();
        int cx = px + SIDE + 8;
        int cw = W - SIDE - 16;
        int y = py + 44;
        for (Item it : items) {
            it.x = cx;
            it.y = y;
            it.w = cw;
            it.h = 24;
            final Item f = it;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> f.click.run())
                    .dimensions(cx, y, cw, 24).build());
            y += 28;
        }
    }

    private static void rr(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 1, y, x + w - 1, y + h, col);
        c.fill(x, y + 1, x + w, y + h - 1, col);
    }

    // no @Override on purpose: our own dark background and panel instead of the blur
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);
        ctx.fill(px, py, px + W, py + H, 0xFF1B1B1B);
        ctx.fill(px, py, px + W, py + 2, ACCENT);
        ctx.fill(px + 4, py + 36, px + SIDE - 4, py + H - 8, 0xFF151515);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = this.textRenderer;

        // header
        Text title = Text.literal("ULTRA VISUAL").formatted(Formatting.BOLD);
        ctx.drawText(tr, title, px + 14, py + 14, 0xFFFFFFFF, false);
        int fps = MinecraftClient.getInstance().getCurrentFps();
        int fcol = fps >= 100 ? 0xFF55FF55 : (fps >= 50 ? 0xFFFFFF55 : 0xFFFF5555);
        ctx.drawText(tr, Text.literal(fps + " FPS"), px + 14 + tr.getWidth(title) + 12, py + 14, fcol, false);

        // tabs (drawn over the vanilla buttons)
        for (int i = 0; i < TABS.length; i++) {
            int bx = px + 8, by = py + 44 + i * 28, bw = SIDE - 16;
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 24;
            ctx.fill(bx, by, bx + bw, by + 24, 0xFF151515);
            if (i == tab) {
                rr(ctx, bx, by, bw, 24, 0xFF262626);
                ctx.fill(bx, by + 4, bx + 3, by + 20, ACCENT);
            } else if (hover) {
                rr(ctx, bx, by, bw, 24, 0xFF1E1E1E);
            }
            ctx.drawText(tr, Text.literal(TABS[i]), bx + 12, by + 8,
                    i == tab ? 0xFFFFFFFF : 0xFF999999, false);
        }

        // rows
        for (Item it : items) {
            boolean hover = mouseX >= it.x && mouseX < it.x + it.w && mouseY >= it.y && mouseY < it.y + it.h;
            ctx.fill(it.x, it.y, it.x + it.w, it.y + it.h, 0xFF1B1B1B);
            rr(ctx, it.x, it.y, it.w, it.h, hover ? 0xFF303030 : 0xFF262626);
            ctx.drawText(tr, Text.literal(it.label), it.x + 10, it.y + 8, 0xFFFFFFFF, false);

            if (it.on != null) {
                boolean on = it.on.getAsBoolean();
                int sx = it.x + it.w - 38;
                int sy = it.y + 6;
                rr(ctx, sx, sy, 28, 12, on ? ACCENT : 0xFF444444);
                int kx = sx + (on ? 18 : 2);
                ctx.fill(kx, sy + 2, kx + 8, sy + 10, 0xFFFFFFFF);
            } else {
                String v = it.value.get();
                int vw = tr.getWidth(v) + 14;
                int vx = it.x + it.w - vw - 8;
                rr(ctx, vx, it.y + 4, vw, 16, 0xFF333333);
                ctx.drawText(tr, Text.literal(v), vx + 7, it.y + 8, ACCENT, false);
            }
        }
    }
}
