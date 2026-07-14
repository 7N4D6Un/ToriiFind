package com.fletime.riatoriifind.service;

import com.fletime.riatoriifind.RiaToriiFind;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class NavigatorHud {

    private static final Identifier ARROW = RiaToriiFind.id("textures/arrow.png");
    private static final int SIZE = 16, MARGIN = 12;

    private NavigatorHud() {
        throw new UnsupportedOperationException("Static accessor");
    }

    public static void register() {
        HudElementRegistry.addLast(
                RiaToriiFind.id("nav_hud"),
                NavigatorHud::extractRenderState);
    }

    private static void extractRenderState(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (!Navigator.hasTarget()) return;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        var target = Navigator.getTarget();
        double dx = target.getX() + 0.5 - player.getX();
        double dz = target.getZ() + 0.5 - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        var text = String.format("%.0f m", dist);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int tw = mc.font.width(text);
        int ew = Math.max(SIZE, tw);

        int cx = sw - MARGIN - ew / 2;
        int ty = sh - MARGIN - mc.font.lineHeight;
        int cy = ty - 6 - SIZE / 2;

        // 半透明背景（箭头 + 文字）
        g.fill(cx - SIZE / 2 - 4, cy - SIZE / 2 - 4, cx + SIZE / 2 + 4, cy + SIZE / 2 + 4, 0x80000000);
        //g.fill(cx - ew / 2 - 4, ty - 2, cx + ew / 2 + 4, ty + mc.font.lineHeight + 2, 0x80000000);

        // 旋转箭头纹理
        double angle = Math.toDegrees(Math.atan2(dz, -dx)) - 90.0;
        float rot = (float) -Math.toRadians(angle + player.getYRot());

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(rot);
        g.pose().translate(-SIZE / 2.0f, -SIZE / 2.0f);
        g.blit(RenderPipelines.GUI_TEXTURED, ARROW, 0, 0, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
        g.pose().popMatrix();

        // 距离文字
        g.text(mc.font, text, cx - tw / 2, ty, 0xFFFFFFFF, true);
    }
}
