package dev.boxarab.boxguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BoxGuardScreen extends Screen {
    private EditBox playerName;
    private Button espButton;

    public BoxGuardScreen() {
        super(Component.literal("BoxGuard Admin"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;

        playerName = new EditBox(this.font, center - 150, 55, 300, 20, Component.literal("Player"));
        playerName.setHint(Component.literal("Player name for InvSee"));
        this.addRenderableWidget(playerName);

        this.addRenderableWidget(Button.builder(Component.literal("INVSEE"), button -> openInvSee())
                .bounds(center - 150, 82, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("SUSPECTED PLAYERS"), button -> {
            BoxGuardClient.requestSuspects();
            this.minecraft.setScreen(new SuspectsScreen());
        }).bounds(center - 50, 82, 145, 20).build());

        espButton = Button.builder(espTitle(), button -> {
            BoxGuardClient.toggleEspPlayers();
            button.setMessage(espTitle());
        }).bounds(center - 150, 108, 145, 20).build();
        this.addRenderableWidget(espButton);

        this.addRenderableWidget(Button.builder(Component.literal("TEST KA ALERT"), button ->
                BoxGuardClient.sendCommand("boxguard test killaura"))
                .bounds(center + 5, 108, 145, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("TEST TOTEM ALERT"), button ->
                BoxGuardClient.sendCommand("boxguard test autototem"))
                .bounds(center - 150, 134, 145, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("DETECTORS STATUS"), button ->
                BoxGuardClient.sendCommand("boxguard status"))
                .bounds(center + 5, 134, 145, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("RESET SUSPECTS"), button ->
                BoxGuardClient.sendCommand("boxguard reset suspects"))
                .bounds(center - 150, 160, 300, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("CLOSE"), button -> onClose())
                .bounds(center - 75, 188, 150, 20).build());
    }

    private Component espTitle() {
        return Component.literal("ESP PLAYERS: " + (BoxGuardClient.isEspPlayers() ? "ON" : "OFF"));
    }

    private void openInvSee() {
        String name = playerName.getValue().trim();
        if (!name.isEmpty()) BoxGuardClient.sendCommand("boxguard invsee " + name);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        int center = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, center, 28, 0x55FFFF);
        graphics.drawCenteredString(this.font,
                Component.literal("Admin surveillance / anti-cheat testing"), center, 42, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
