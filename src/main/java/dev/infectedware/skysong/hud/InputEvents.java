package dev.infectedware.skysong.hud;

import dev.infectedware.skysong.module.Binds;
import java.util.ArrayDeque;
import java.util.BitSet;
import net.minecraft.client.Minecraft;

public final class InputEvents {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static final BitSet DOWN = new BitSet();

    private InputEvents() {
    }

    public static void mouseButton(int button, int action) {
        if (Minecraft.getInstance().screen == null) {
            if (action == 1) {
                long now = System.currentTimeMillis();
                if (button == 0) {
                    LEFT.addLast(now);
                } else if (button == 1) {
                    RIGHT.addLast(now);
                }
            }

            if (action == 1 || action == 0) {
                Binds.handle(-100 - button, action == 1);
            }
        }
    }

    public static void key(int key, int action) {
        if (key >= 0 && action == 1) {
            DOWN.set(key);
        } else if (key >= 0 && action == 0) {
            DOWN.clear(key);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null) {
            if (action == 1 || action == 0) {
                Binds.handle(key, action == 1);
            }
        }
    }

    public static boolean isUp(int key) {
        return key < 0 || !DOWN.get(key);
    }

    public static int leftCps() {
        return prune(LEFT);
    }

    public static int rightCps() {
        return prune(RIGHT);
    }

    private static int prune(ArrayDeque<Long> clicks) {
        long cutoff = System.currentTimeMillis() - 1000L;

        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.pollFirst();
        }

        return clicks.size();
    }
}
