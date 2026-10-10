package com.example.esp;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPipelines;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class EspRenderer {
    private static final double RANGE = 400.0;
    private static final double TARGET_DOT = 0.98;
    private static final int SEGMENTS = 48;
    private static final float HAT_RADIUS = 0.55f;
    private static final float HAT_HEIGHT = 0.3f;

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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        init();

        float tickProgress = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d look = mc.player.getRotationVec(tickProgress).normalize();

        // pick target: highest dot product above threshold
        AbstractClientPlayerEntity target = null;
        double best = TARGET_DOT;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            Vec3d pos = p.getLerpedPos(tickProgress);
            Vec3d center = pos.add(0, p.getHeight() / 2.0, 0);
            Vec3d to = center.subtract(cam);
            double dist = to.length();
            if (dist > RANGE || dist < 1.0E-4) continue;
            double dot = look.dotProduct(to.multiply(1.0 / dist));
            if (dot > best) {
                best = dot;
                target = p;
            }
        }

        VertexConsumerProvider consumers = ctx.consumers();
        Matrix4f m = ctx.matrices().peek().getPositionMatrix();

        VertexConsumer boxes = consumers.getBuffer(BOX_LAYER);
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            Vec3d pos = p.getLerpedPos(tickProgress);
            if (pos.distanceTo(cam) > RANGE) continue;
            boolean isTarget = p == target;
            float r = isTarget ? 1.0f : 0.0f;
            float g = isTarget ? 0.1f : 0.8f;
            float b = isTarget ? 0.1f : 1.0f;
            float hw = p.getWidth() / 2.0f;
            drawBox(boxes, m,
                    (float) (pos.x - cam.x) - hw, (float) (pos.y - cam.y), (float) (pos.z - cam.z) - hw,
                    (float) (pos.x - cam.x) + hw, (float) (pos.y - cam.y) + p.getHeight(), (float) (pos.z - cam.z) + hw,
                    r, g, b, 0.25f);
        }
        if (consumers instanceof VertexConsumerProvider.Immediate imm) imm.draw(BOX_LAYER);

        if (target != null) {
            VertexConsumer hat = consumers.getBuffer(HAT_LAYER);
            Vec3d pos = target.getLerpedPos(tickProgress);
            float cx = (float) (pos.x - cam.x);
            float baseY = (float) (pos.y - cam.y) + target.getHeight() + 0.05f;
            float cz = (float) (pos.z - cam.z);
            float time = (System.currentTimeMillis() % 4000L) / 4000.0f;
            drawHat(hat, m, cx, baseY, cz, time);
            if (consumers instanceof VertexConsumerProvider.Immediate imm) imm.draw(HAT_LAYER);
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
            vc.vertex(m, cx, apexY, cz).color(rgb(cm, 16), rgb(cm, 8), rgb(cm, 0), 0.9f);
            vc.vertex(m, x1, baseY, z1).color(rgb(c1, 16), rgb(c1, 8), rgb(c1, 0), 0.75f);
            vc.vertex(m, x0, baseY, z0).color(rgb(c0, 16), rgb(c0, 8), rgb(c0, 0), 0.75f);
        }
    }

    private static float rgb(int c, int shift) {
        return ((c >> shift) & 0xFF) / 255.0f;
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
        q(vc, m, r, g, b, a, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2); // bottom
        q(vc, m, r, g, b, a, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1); // top
        q(vc, m, r, g, b, a, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1); // north
        q(vc, m, r, g, b, a, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2); // south
        q(vc, m, r, g, b, a, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1); // west
        q(vc, m, r, g, b, a, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2); // east
    }
}
