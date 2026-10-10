package com.example.esp;

import com.example.esp.UltraVisualClient.Settings;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class EspRenderer {
    private static final double TARGET_DOT = 0.98;
    private static final int SEGMENTS = 48;
    private static final int RING_SEG = 64;
    private static final float HAT_RADIUS = 0.55f;
    private static final float HAT_HEIGHT = 0.3f;

    public static AbstractClientPlayerEntity currentTarget = null;

    private static RenderPipeline pipeline(String name, VertexFormat.DrawMode mode) {
        return RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                        .withLocation(Identifier.of("ultravisual", "pipeline/" + name))
                        .withVertexFormat(VertexFormats.POSITION_COLOR, mode)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .build());
    }

    private static RenderLayer BOX_LAYER;
    private static RenderLayer HAT_LAYER;

    private static void init() {
        if (BOX_LAYER != null) return;
        BOX_LAYER = RenderLayer.of("ultravisual_box",
                RenderSetup.builder(pipeline("box", VertexFormat.DrawMode.QUADS)).build());
        HAT_LAYER = RenderLayer.of("ultravisual_hat",
                RenderSetup.builder(pipeline("hat", VertexFormat.DrawMode.TRIANGLES)).build());
    }

    public static void render(WorldRenderContext ctx) {
        currentTarget = null;
        if (!Settings.esp && !Settings.targetEsp && !Settings.hat && !Settings.targetHud) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        init();

        float tickProgress = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d look = mc.player.getRotationVec(tickProgress).normalize();
        double range = Settings.range;

        AbstractClientPlayerEntity target = null;
        double best = TARGET_DOT;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            Vec3d pos = p.getLerpedPos(tickProgress);
            Vec3d center = pos.add(0, p.getHeight() / 2.0, 0);
            Vec3d to = center.subtract(cam);
            double dist = to.length();
            if (dist > range || dist < 1.0E-4) continue;
            double dot = look.dotProduct(to.multiply(1.0 / dist));
            if (dot > best) {
                best = dot;
                target = p;
            }
        }
        currentTarget = target;

        VertexConsumerProvider consumers = ctx.consumers();
        Matrix4f m = ctx.matrices().peek().getPositionMatrix();
        float time = (System.currentTimeMillis() % 4000L) / 4000.0f;

        VertexConsumer quads = consumers.getBuffer(BOX_LAYER);
        if (Settings.esp) {
            float[] c = Settings.ESP_RGB[Settings.espColor];
            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                Vec3d pos = p.getLerpedPos(tickProgress);
                if (pos.distanceTo(cam) > range) continue;
                boolean isTarget = p == target;
                float r = isTarget ? 1.0f : c[0];
                float g = isTarget ? 0.1f : c[1];
                float b = isTarget ? 0.1f : c[2];
                float hw = p.getWidth() / 2.0f;
                float x = (float) (pos.x - cam.x);
                float y = (float) (pos.y - cam.y);
                float z = (float) (pos.z - cam.z);
                float x1 = x - hw, y1 = y, z1 = z - hw;
                float x2 = x + hw, y2 = y + p.getHeight(), z2 = z + hw;
                if (Settings.boxStyle == 0 || Settings.boxStyle == 2) {
                    drawBox(quads, m, x1, y1, z1, x2, y2, z2, r, g, b, 0.25f);
                }
                if (Settings.boxStyle == 1 || Settings.boxStyle == 2) {
                    drawEdges(quads, m, x1, y1, z1, x2, y2, z2, r, g, b, 0.95f, 0.02f);
                }
            }
        }
        if (Settings.targetEsp && target != null) {
            Vec3d pos = target.getLerpedPos(tickProgress);
            float x = (float) (pos.x - cam.x);
            float y = (float) (pos.y - cam.y);
            float z = (float) (pos.z - cam.z);
            float frac = MathHelper.clamp(target.getHealth() / Math.max(1.0f, target.getMaxHealth()), 0.0f, 1.0f);
            float t = (System.currentTimeMillis() % 2000L) / 2000.0f;
            float h = target.getHeight();
            for (int k = 0; k < 6; k++) {
                float tt = t - k * 0.035f;
                float yy = (1.0f - MathHelper.cos(tt * (float) (2 * Math.PI))) / 2.0f * h;
                float alpha = 0.75f * (1.0f - k / 6.0f);
                drawRing(quads, m, x, y + yy, z, Settings.ringSize, 0.07f, time, frac,
                        Settings.targetColor, alpha);
            }
        }
        if (consumers instanceof VertexConsumerProvider.Immediate imm) imm.draw(BOX_LAYER);

        if (Settings.hat) {
            VertexConsumer hat = consumers.getBuffer(HAT_LAYER);
            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                if (!Settings.hatAll && p != target) continue;
                Vec3d pos = p.getLerpedPos(tickProgress);
                if (pos.distanceTo(cam) > range) continue;
                float cx = (float) (pos.x - cam.x);
                float baseY = (float) (pos.y - cam.y) + p.getHeight() + 0.05f;
                float cz = (float) (pos.z - cam.z);
                drawHat(hat, m, cx, baseY, cz, time);
            }
            if (consumers instanceof VertexConsumerProvider.Immediate imm) imm.draw(HAT_LAYER);
        }
    }

    private static int ringColor(int mode, float pos, float time, float frac) {
        float hue;
        switch (mode) {
            case 0: hue = 0.33f * frac; break;
            case 1: hue = (pos + time) % 1.0f; break;
            case 2: hue = 0.5f; break;
            default: hue = 0.9f; break;
        }
        return MathHelper.hsvToRgb(hue, 1.0f, 1.0f);
    }

    private static float rgb(int c, int shift) {
        return ((c >> shift) & 0xFF) / 255.0f;
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, float x, float y, float z, int c, float a) {
        vc.vertex(m, x, y, z).color(rgb(c, 16), rgb(c, 8), rgb(c, 0), a);
    }

    private static void drawRing(VertexConsumer vc, Matrix4f m, float cx, float y, float cz,
                                 float radius, float width, float time, float frac, int mode, float alpha) {
        float ri = radius - width;
        for (int i = 0; i < RING_SEG; i++) {
            float a0 = (float) (2 * Math.PI * i / RING_SEG);
            float a1 = (float) (2 * Math.PI * (i + 1) / RING_SEG);
            int c0 = ringColor(mode, i / (float) RING_SEG, time, frac);
            int c1 = ringColor(mode, (i + 1) / (float) RING_SEG, time, frac);
            vtx(vc, m, cx + MathHelper.cos(a0) * ri, y, cz + MathHelper.sin(a0) * ri, c0, alpha);
            vtx(vc, m, cx + MathHelper.cos(a0) * radius, y, cz + MathHelper.sin(a0) * radius, c0, alpha);
            vtx(vc, m, cx + MathHelper.cos(a1) * radius, y, cz + MathHelper.sin(a1) * radius, c1, alpha);
            vtx(vc, m, cx + MathHelper.cos(a1) * ri, y, cz + MathHelper.sin(a1) * ri, c1, alpha);
        }
    }

    private static void drawHat(VertexConsumer vc, Matrix4f m, float cx, float baseY, float cz, float time) {
        float apexY = baseY + HAT_HEIGHT;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (2 * Math.PI * i / SEGMENTS);
            float a1 = (float) (2 * Math.PI * (i + 1) / SEGMENTS);
            float x0 = cx + MathHelper.cos(a0) * HAT_RADIUS, z0 = cz + MathHelper.sin(a0) * HAT_RADIUS;
            float x1 = cx + MathHelper.cos(a1) * HAT_RADIUS, z1 = cz + MathHelper.sin(a1) * HAT_RADIUS;
            int c0 = MathHelper.hsvToRgb((i / (float) SEGMENTS + time) % 1.0f, 0.9f, 1.0f);
            int c1 = MathHelper.hsvToRgb(((i + 1) / (float) SEGMENTS + time) % 1.0f, 0.9f, 1.0f);
            int cm = MathHelper.hsvToRgb(((i + 0.5f) / SEGMENTS + time) % 1.0f, 0.5f, 1.0f);
            vtx(vc, m, cx, apexY, cz, cm, 0.9f);
            vtx(vc, m, x1, baseY, z1, c1, 0.75f);
            vtx(vc, m, x0, baseY, z0, c0, 0.75f);
        }
    }

    private static void q(VertexConsumer vc, Matrix4f m, float r, float g, float b, float a,
                          float x1, float y1, float z1, float x2, float y2, float z2,
                          float x3, float y3, float z3, float x4, float y4, float z4) {
        vc.vertex(m, x1, y1, z1).color(r, g, b, a);
        vc.vertex(m, x2, y2, z2).color(r, g, b, a);
        vc.vertex(m, x3, y3, z3).color(r, g, b, a);
        vc.vertex(m, x4, y4, z4).color(r, g, b, a);
    }

    private static void drawBox(VertexConsumer vc, Matrix4f m,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float r, float g, float b, float a) {
        q(vc, m, r, g, b, a, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        q(vc, m, r, g, b, a, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        q(vc, m, r, g, b, a, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        q(vc, m, r, g, b, a, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        q(vc, m, r, g, b, a, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        q(vc, m, r, g, b, a, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

    private static void drawEdges(VertexConsumer vc, Matrix4f m,
                                  float x1, float y1, float z1, float x2, float y2, float z2,
                                  float r, float g, float b, float a, float t) {
        float[] ys = {y1, y2};
        float[] zs = {z1, z2};
        float[] xs = {x1, x2};
        for (float yy : ys) {
            for (float zz : zs) {
                drawBox(vc, m, x1, yy - t, zz - t, x2, yy + t, zz + t, r, g, b, a);
            }
        }
        for (float xx : xs) {
            for (float zz : zs) {
                drawBox(vc, m, xx - t, y1, zz - t, xx + t, y2, zz + t, r, g, b, a);
            }
        }
        for (float xx : xs) {
            for (float yy : ys) {
                drawBox(vc, m, xx - t, yy - t, z1, xx + t, yy + t, z2, r, g, b, a);
            }
        }
    }
}
