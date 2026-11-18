package com.roadforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.roadforge.RoadForge;
import com.roadforge.item.RoadWandItem;
import com.roadforge.network.Net;
import com.roadforge.network.PickPacket;
import com.roadforge.road.OutlinePlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RoadForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    /** Точка под прицелом и контур считаются раз в тик. */
    private static BlockPos aim;
    private static OutlinePlan outline;

    private static boolean holdingWand(Minecraft mc) {
        return mc.player != null && mc.player.getMainHandItem().getItem() instanceof RoadWandItem;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (ClientModEvents.OPEN_MENU.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new RoadScreen());
            }
        }
        if (mc.level != null && mc.screen == null && holdingWand(mc)) {
            aim = Aim.pick(mc, Aim.planeY(mc));
            outline = OutlinePlan.compute(ClientState.settings(), ClientState.pointA(), ClientState.pointB(),
                    ClientState.path(), aim);
        } else {
            aim = null;
            outline = null;
        }
    }

    /** ПКМ жезлом работает по лучу на любом расстоянии (можно целиться из воздуха), ЛКМ убирает точку. */
    @SubscribeEvent
    public static void onInteract(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !holdingWand(mc)) {
            return;
        }
        if (event.isUseItem()) {
            if (mc.player.isShiftKeyDown()) {
                Net.CHANNEL.sendToServer(new PickPacket(PickPacket.Action.BUILD, BlockPos.ZERO));
            } else if (aim != null) {
                Net.CHANNEL.sendToServer(new PickPacket(PickPacket.Action.CLICK, aim));
            }
            event.setCanceled(true);
            event.setSwingHit(true);
        } else if (event.isAttack()) {
            Net.CHANNEL.sendToServer(new PickPacket(PickPacket.Action.UNDO_POINT, BlockPos.ZERO));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || outline == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        PoseStack.Pose pose = ps.last();
        double y = outline.y + 1.02 - cam.y;
        for (double[] l : outline.lines) {
            line(vc, pose, l[0] - cam.x, y, l[1] - cam.z, l[2] - cam.x, y, l[3] - cam.z, 0.25F, 0.85F, 1.0F);
        }
        for (double[] p : outline.posts) {
            double x = p[0] - cam.x;
            double z = p[1] - cam.z;
            line(vc, pose, x, y, z, x, y + 3, z, 1.0F, 0.85F, 0.2F);
            line(vc, pose, x - 0.4, y, z - 0.4, x + 0.4, y, z - 0.4, 1.0F, 0.85F, 0.2F);
            line(vc, pose, x + 0.4, y, z - 0.4, x + 0.4, y, z + 0.4, 1.0F, 0.85F, 0.2F);
            line(vc, pose, x + 0.4, y, z + 0.4, x - 0.4, y, z + 0.4, 1.0F, 0.85F, 0.2F);
            line(vc, pose, x - 0.4, y, z + 0.4, x - 0.4, y, z - 0.4, 1.0F, 0.85F, 0.2F);
        }
        if (aim != null) {
            double x = aim.getX() + 0.5 - cam.x;
            double z = aim.getZ() + 0.5 - cam.z;
            line(vc, pose, x, y, z, x, y + 2, z, 1.0F, 1.0F, 1.0F);
        }
        buf.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, double x1, double y1, double z1,
                             double x2, double y2, double z2, float r, float g, float b) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6F) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        vc.vertex(pose.pose(), (float) x1, (float) y1, (float) z1).color(r, g, b, 1.0F)
                .normal(pose.normal(), dx, dy, dz).endVertex();
        vc.vertex(pose.pose(), (float) x2, (float) y2, (float) z2).color(r, g, b, 1.0F)
                .normal(pose.normal(), dx, dy, dz).endVertex();
    }

    /** Подпись у прицела: «Дорога: 59 м · полос 4». */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (outline == null || outline.label.isEmpty() || mc.screen != null || mc.options.hideGui) {
            return;
        }
        int x = event.getWindow().getGuiScaledWidth() / 2;
        int y = event.getWindow().getGuiScaledHeight() / 2 + 14;
        event.getGuiGraphics().drawCenteredString(mc.font, outline.label, x, y, 0x55FFFF);
    }

    private ClientForgeEvents() {}
}
