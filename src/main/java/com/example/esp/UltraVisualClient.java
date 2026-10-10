package com.example.esp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class UltraVisualClient implements ClientModInitializer {

    public static class Settings {
        public static boolean esp = true;
        public static boolean targetEsp = true;
        public static boolean hat = true;
        public static boolean hatAll = false;
        public static int range = 500;
        public static int espColor = 0;
        public static int targetColor = 0;
        public static float ringSize = 0.8f;

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

        WorldRenderEvents.END_MAIN.register(EspRenderer::render);
    }
}
