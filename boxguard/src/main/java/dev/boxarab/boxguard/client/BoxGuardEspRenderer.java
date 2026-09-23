package dev.boxarab.boxguard.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class BoxGuardEspRenderer {
    private BoxGuardEspRenderer() {}

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(BoxGuardEspRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        if (!BoxGuardClient.isEspPlayers()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || context.consumers() == null) return;

        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        PoseStack matrices = context.matrices();
        MultiBufferSource consumers = context.consumers();
        VertexConsumer lines = consumers.getBuffer(RenderTypes.lines());

        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0F);

        for (Player target : client.level.players()) {
            if (target == client.player || target.isSpectator() || !target.isAlive()) continue;
            if (target.distanceTo(client.player) > 128.0F) continue;

            AABB box = target.getBoundingBox().move(-camera.x, -camera.y, -camera.z);
            matrices.pushPose();
            ShapeRenderer.renderShape(matrices, lines, Shapes.create(box), 0.0D, 0.0D, 0.0D, 0xE600FFFF, 2.0F);
            matrices.popPose();
        }

        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableDepthTest();
    }
}
