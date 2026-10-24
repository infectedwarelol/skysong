package com.spotifyclient.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;

public abstract class VertexSink implements VertexConsumer {
    protected abstract void onVertex(float var1, float var2, float var3);

    protected void onColor(int r, int g, int b, int a) {
    }

    protected void onUv(float u, float v) {
    }

    protected void onNormal(float x, float y, float z) {
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.onVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        this.onColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.onUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        this.onNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        this.onColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }
}
