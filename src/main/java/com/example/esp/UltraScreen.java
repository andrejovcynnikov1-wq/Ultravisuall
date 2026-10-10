package com.example.esp;

import com.example.esp.UltraVisualClient.Settings;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class UltraScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final float[] RING_SIZES = {0.6f, 0.8f, 1.0f, 1.2f};
    private static final int[] RANGES = {100, 200, 300, 400, 500};

    private int px, py, ph;

    public UltraScreen() {
        super(Text.literal("ULTRA VISUAL"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private ButtonWidget btn(int x, int y, int w, Supplier<String> label, Runnable action) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label.get()), bt -> {
            action.run();
            bt.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, w, 20).build();
        this.addDrawableChild(b);
        return b;
    }

    private static String onOff(boolean v) {
        return v ? "ON" : "OFF";
    }

    @Override
    protected void init() {
        ph = 50 + 5 * 26 + 10;
        px = (this.width - PANEL_W) / 2;
        py = (this.height - ph) / 2;
        int x1 = px + 10;
        int x2 = px + 165;
        int w = 145;
        int y = py + 50;

        btn(x1, y, w, () -> "ESP: " + onOff(Settings.esp), () -> Settings.esp = !Settings.esp);
        btn(x2, y, w, () -> "Range: " + Settings.range, () -> {
            int idx = 0;
            for (int i = 0; i < RANGES.length; i++) if (RANGES[i] == Settings.range) idx = i;
            Settings.range = RANGES[(idx + 1) % RANGES.length];
        });
        y += 26;

        btn(x1, y, w, () -> "ESP color: " + Settings.ESP_NAMES[Settings.espColor],
                () -> Settings.espColor = (Settings.espColor + 1) % Settings.ESP_NAMES.length);
        btn(x2, y, w, () -> "Target ESP: " + onOff(Settings.targetEsp),
                () -> Settings.targetEsp = !Settings.targetEsp);
        y += 26;

        btn(x1, y, w, () -> "Target color: " + Settings.TARGET_NAMES[Settings.targetColor],
                () -> Settings.targetColor = (Settings.targetColor + 1) % Settings.TARGET_NAMES.length);
        btn(x2, y, w, () -> "Ring size: " + Settings.ringSize, () -> {
            int idx = 0;
            for (int i = 0; i < RING_SIZES.length; i++) if (RING_SIZES[i] == Settings.ringSize) idx = i;
            Settings.ringSize = RING_SIZES[(idx + 1) % RING_SIZES.length];
        });
        y += 26;

        btn(x1, y, w, () -> "China hat: " + onOff(Settings.hat), () -> Settings.hat = !Settings.hat);
        btn(x2, y, w, () -> "Hat on: " + (Settings.hatAll ? "All" : "Target"),
                () -> Settings.hatAll = !Settings.hatAll);
        y += 26;

        ButtonWidget sky = btn(x1, y, w, () -> "Sky color (soon)", () -> {});
        sky.active = false;
        ButtonWidget fog = btn(x2, y, w, () -> "Fog (soon)", () -> {});
        fog.active = false;
    }

    // no @Override on purpose: draws our own dark background and panel instead of the blur
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);
        ctx.fill(px, py, px + PANEL_W, py + ph, 0xFF2B2B2B);
        ctx.fill(px, py, px + PANEL_W, py + 2, 0xFF00CED1);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        Text title = Text.literal("ULTRA VISUAL").formatted(Formatting.BOLD);
        int tx = px + 12;
        int ty = py + 18;
        ctx.drawText(this.textRenderer, title, tx, ty, 0xFFFFFFFF, false);

        int fps = MinecraftClient.getInstance().getCurrentFps();
        int color = fps >= 100 ? 0xFF55FF55 : (fps >= 50 ? 0xFFFFFF55 : 0xFFFF5555);
        int fx = tx + this.textRenderer.getWidth(title) + 12;
        ctx.drawText(this.textRenderer, Text.literal(fps + " FPS"), fx, ty, color, false);
    }
}
