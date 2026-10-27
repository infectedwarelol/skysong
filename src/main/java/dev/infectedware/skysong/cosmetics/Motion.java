package dev.infectedware.skysong.cosmetics;

import net.minecraft.util.Mth;

final class Motion {
    private static final float MIN_LENGTH = 1.0E-6F;

    private Motion() {
    }

    static void spring(float[] state, int index, float target, float stiffness, float damping) {
        float velocity = (state[index + 1] + (target - state[index]) * stiffness) * (1.0F - damping);
        state[index + 1] = velocity;
        state[index] += velocity;
    }

    static void critical(float[] state, int index, float target, float smoothTicks) {
        float omega = 2.0F / Math.max(0.05F, smoothTicks);
        float x = omega;
        float decay = 1.0F / (1.0F + x + 0.48F * x * x + 0.235F * x * x * x);
        float change = state[index] - target;
        float temp = state[index + 1] + omega * change;
        state[index + 1] = (state[index + 1] - omega * temp) * decay;
        state[index] = target + (change + temp) * decay;
    }

    static void integrate(float[] current, float[] previous, int fromPoint, int toPoint, float gravityX, float gravityY, float gravityZ, float drag) {
        float keep = 1.0F - drag;
        for (int i = fromPoint * 3; i < toPoint * 3; i += 3) {
            float vx = (current[i] - previous[i]) * keep;
            float vy = (current[i + 1] - previous[i + 1]) * keep;
            float vz = (current[i + 2] - previous[i + 2]) * keep;
            previous[i] = current[i];
            previous[i + 1] = current[i + 1];
            previous[i + 2] = current[i + 2];
            current[i] += vx + gravityX;
            current[i + 1] += vy + gravityY;
            current[i + 2] += vz + gravityZ;
        }
    }

    static void link(float[] points, int pointA, int pointB, float rest, float stiffness, boolean pinnedA) {
        int a = pointA * 3;
        int b = pointB * 3;
        float dx = points[b] - points[a];
        float dy = points[b + 1] - points[a + 1];
        float dz = points[b + 2] - points[a + 2];
        float lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < MIN_LENGTH) {
            return;
        }

        float length = (float)Math.sqrt(lengthSquared);
        float correction = (length - rest) / length * stiffness;
        if (pinnedA) {
            points[b] -= dx * correction;
            points[b + 1] -= dy * correction;
            points[b + 2] -= dz * correction;
            return;
        }

        float half = correction * 0.5F;
        points[a] += dx * half;
        points[a + 1] += dy * half;
        points[a + 2] += dz * half;
        points[b] -= dx * half;
        points[b + 1] -= dy * half;
        points[b + 2] -= dz * half;
    }

    static void capsule(float[] points, int point, float ax, float ay, float az, float bx, float by, float bz, float radius) {
        int i = point * 3;
        float px = points[i];
        float py = points[i + 1];
        float pz = points[i + 2];
        float sx = bx - ax;
        float sy = by - ay;
        float sz = bz - az;
        float segmentSquared = sx * sx + sy * sy + sz * sz;
        float t = segmentSquared < MIN_LENGTH ? 0.0F : Math.clamp(((px - ax) * sx + (py - ay) * sy + (pz - az) * sz) / segmentSquared, 0.0F, 1.0F);
        float dx = px - (ax + sx * t);
        float dy = py - (ay + sy * t);
        float dz = pz - (az + sz * t);
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared >= radius * radius) {
            return;
        }

        if (distanceSquared < MIN_LENGTH) {
            points[i + 2] = az + sz * t + radius;
            return;
        }

        float scale = radius * Mth.invSqrt(distanceSquared);
        points[i] = ax + sx * t + dx * scale;
        points[i + 1] = ay + sy * t + dy * scale;
        points[i + 2] = az + sz * t + dz * scale;
    }

    static void chain(float[] current, float[] previous, int firstPoint, int count, float anchorX, float anchorY, float anchorZ, float rest, float gravity, float drag, int iterations) {
        int root = firstPoint * 3;
        current[root] = anchorX;
        current[root + 1] = anchorY;
        current[root + 2] = anchorZ;
        previous[root] = anchorX;
        previous[root + 1] = anchorY;
        previous[root + 2] = anchorZ;
        integrate(current, previous, firstPoint + 1, firstPoint + count, 0.0F, gravity, 0.0F, drag);
        for (int pass = 0; pass < iterations; pass++) {
            for (int k = 1; k < count; k++) {
                link(current, firstPoint + k - 1, firstPoint + k, rest, 1.0F, k == 1);
            }
        }
    }

    static void shift(float[] current, float[] previous, int fromPoint, int toPoint, float pivotX, float pivotZ, float cos, float sin, float dx, float dy, float dz) {
        for (int i = fromPoint * 3; i < toPoint * 3; i += 3) {
            turn(current, i, pivotX, pivotZ, cos, sin, dx, dy, dz);
            turn(previous, i, pivotX, pivotZ, cos, sin, dx, dy, dz);
        }
    }

    private static void turn(float[] points, int i, float pivotX, float pivotZ, float cos, float sin, float dx, float dy, float dz) {
        float x = points[i] - pivotX;
        float z = points[i + 2] - pivotZ;
        points[i] = pivotX + x * cos - z * sin - dx;
        points[i + 1] -= dy;
        points[i + 2] = pivotZ + x * sin + z * cos - dz;
    }
}
