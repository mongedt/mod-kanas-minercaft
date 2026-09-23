package dev.boxarab.boxguard.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import dev.boxarab.boxguard.BoxGuardPayloads;

public final class BoxGuardClient implements ClientModInitializer {
    private static KeyMapping openKey;
    private static boolean espPlayers;
    private static List<BoxGuardPayloads.SuspectEntry> suspects = List.of();

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.boxguard.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.boxguard"
        ));

        ClientPlayNetworking.registerGlobalReceiver(BoxGuardPayloads.SuspectSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> suspects = List.copyOf(payload.entries()))
        );

        BoxGuardEspRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new BoxGuardScreen());
            }
        });
    }

    public static void sendCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.connection.sendCommand(command);
    }

    public static void requestSuspects() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && ClientPlayNetworking.canSend(BoxGuardPayloads.RequestSuspectsPayload.TYPE)) {
            ClientPlayNetworking.send(new BoxGuardPayloads.RequestSuspectsPayload());
        }
    }

    public static List<BoxGuardPayloads.SuspectEntry> suspects() {
        return suspects;
    }

    public static boolean isEspPlayers() {
        return espPlayers;
    }

    public static void toggleEspPlayers() {
        espPlayers = !espPlayers;
    }
}
