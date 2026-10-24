package com.spotifyclient.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class Hooks {
    private static final List<Consumer<WorldCtx>> AFTER_ENTITIES = new ArrayList<>();
    private static final List<Consumer<WorldCtx>> LAST = new ArrayList<>();
    private static final List<Runnable> START = new ArrayList<>();
    private static Camera camera;
    private static Frustum frustum;
    private static final Matrix4f view = new Matrix4f();
    private static final Matrix4f projection = new Matrix4f();
    private static float partialTick;
    private static PoseStack entityPose;
    private static final List<HudLayer> HUD = new ArrayList<>();

    private Hooks() {
    }

    public static void afterEntities(Consumer<WorldCtx> pass) {
        AFTER_ENTITIES.add(withMatrices(pass));
    }

    public static void last(Consumer<WorldCtx> pass) {
        LAST.add(withMatrices(pass));
    }

    public static void start(Runnable r) {
        START.add(r);
    }

    public static void trackWorldCamera() {
    }

    public static void beginWorld(Camera cam, Matrix4f viewMatrix, Matrix4f projectionMatrix, Matrix4f cullProjection, float tick) {
        camera = cam;
        view.set(viewMatrix);
        projection.set(projectionMatrix);
        partialTick = tick;
        Vec3 pos = Compat.camPos(cam);
        frustum = new Frustum(view, cullProjection);
        frustum.prepare(pos.x, pos.y, pos.z);
        Gpu.setWorldCamera(projection, view);

        for (Runnable r : START) {
            r.run();
        }
    }

    public static void entitiesQueued(PoseStack pose) {
        entityPose = pose;
    }

    public static void entitiesDrawn() {
        if (entityPose != null) {
            PoseStack pose = entityPose;
            entityPose = null;
            runAll(AFTER_ENTITIES, pose);
        }
    }

    public static void lastNow() {
        entityPose = null;
        runAll(LAST, new PoseStack());
    }

    private static void runAll(List<Consumer<WorldCtx>> passes, PoseStack pose) {
        if (camera != null && !passes.isEmpty()) {
            for (Consumer<WorldCtx> pass : passes) {
                pass.accept(new WorldCtx(camera, frustum, new Matrix4f(view), new Matrix4f(projection), partialTick, pose));
            }
        }
    }

    public static void hud(HudLayer layer) {
        HUD.add(layer);
    }

    public static void drawHud(net.minecraft.client.gui.GuiGraphics g, float partialTick) {
        GuiGraphics ours = Compat.gui(g);

        for (HudLayer layer : HUD) {
            layer.render(ours, partialTick);
        }
    }

    private static Consumer<WorldCtx> withMatrices(Consumer<WorldCtx> pass) {
        return w -> {
            Gpu.setMatrices(w.projection(), w.view());
            pass.accept(w);
        };
    }
}
