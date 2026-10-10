package com.example.esp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class UltraScreen extends Screen {
    private static final int PANEL_W = 240;
    private static final int PANEL_H = 90;

    public UltraScreen() {
        super(Text.literal("ULTRA VISUAL"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // darkened background (Esc closing is handled by Screen)
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);

        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;
        ctx.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFF2B2B2B);
        ctx.fill(x, y, x + PANEL_W, y + 2, 0xFF00CED1);

        Text title = Text.literal("ULTRA VISUAL").formatted(Formatting.BOLD);
        int ty = y + PANEL_H / 2 - 4;
        int tx = x + 16;
        ctx.drawText(this.textRenderer, title, tx, ty, 0xFFFFFFFF, false);

        int fps = MinecraftClient.getInstance().getCurrentFps();
        int color = fps >= 100 ? 0xFF55FF55 : (fps >= 50 ? 0xFFFFFF55 : 0xFFFF5555);
        int fx = tx + this.textRenderer.getWidth(title) + 12;
        ctx.drawText(this.textRenderer, Text.literal(fps + " FPS"), fx, ty, color, false);
    }
}
