package dev.boxarab.boxguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

import dev.boxarab.boxguard.BoxGuardPayloads;

public final class SuspectsScreen extends Screen {
    public SuspectsScreen() {
        super(Component.literal("BoxGuard - Suspected Players"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("REFRESH"), button -> BoxGuardClient.requestSuspects())
                .bounds(center - 130, this.height - 52, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("BACK"), button -> this.minecraft.setScreen(new BoxGuardScreen()))
                .bounds(center - 40, this.height - 52, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("CLEAR"), button -> {
            BoxGuardClient.sendCommand("boxguard reset suspects");
            BoxGuardClient.requestSuspects();
        }).bounds(center + 50, this.height - 52, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        int center = this.width / 2;

        graphics.drawCenteredString(this.font, this.title, center, 24, 0x55FFFF);
        graphics.drawCenteredString(this.font,
                Component.literal("Indicators are heuristics, not proof of cheating."), center, 39, 0xAAAAAA);

        List<BoxGuardPayloads.SuspectEntry> entries = BoxGuardClient.suspects();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal("No suspicious players detected."), center, 75, 0x55FF55);
        } else {
            int y = 60;
            for (int i = 0; i < Math.min(entries.size(), 15); i++) {
                BoxGuardPayloads.SuspectEntry entry = entries.get(i);
                int score = entry.score();
                int rankColor = score >= 20 ? 0xFF5555 : (score >= 10 ? 0xFFFF55 : 0x55FFFF);

                graphics.drawString(this.font,
                        Component.literal(entry.name()), center - 180, y, 0xFFFFFF);
                graphics.drawString(this.font,
                        Component.literal("Score: " + score), center - 70, y, rankColor);
                graphics.drawString(this.font,
                        Component.literal(entry.flags()), center - 180, y + 12, 0xAAAAAA);
                y += 30;
            }
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
