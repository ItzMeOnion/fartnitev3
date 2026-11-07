package com.fortnitelocker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FortniteLockerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("fortnitelocker");
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.fortnitelocker.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.fortnitelocker"));

        LockerState.load();
        ClientLifecycleEvents.CLIENT_STARTED.register(c -> LockerState.reload());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    LockerState.reload();
                    client.setScreen(new LockerScreen());
                }
            }
        });
    }
}
