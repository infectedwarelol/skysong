package com.spotifyclient.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;

public record WorldCtx(Camera camera, Frustum frustum, Matrix4f view, Matrix4f projection, float partialTick, PoseStack poseStack) {
}
