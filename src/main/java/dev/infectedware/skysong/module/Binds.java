package dev.infectedware.skysong.module;

import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Binds {
    private static final Map<String, Binds.Bind> BINDS = new LinkedHashMap<>();
    private static String parsedFrom;
    public static Runnable openClickGui = () -> {};

    private Binds() {
    }

    public static Binds.Bind get(String module) {
        sync();
        return BINDS.get(module);
    }

    public static int key(String module) {
        Binds.Bind b = get(module);
        return b == null ? -1 : b.key();
    }

    public static boolean isHold(String module) {
        Binds.Bind b = get(module);
        return b != null && b.hold();
    }

    public static void set(String module, int key) {
        sync();
        if (key != -1 && key != 256) {
            BINDS.put(module, new Binds.Bind(key, isHold(module)));
        } else {
            BINDS.remove(module);
        }

        save();
    }

    public static void setHold(String module, boolean hold) {
        sync();
        BINDS.compute(module, (name, b) -> new Binds.Bind(b == null ? -1 : b.key(), hold));
        save();
    }

    public static boolean isBound(String module) {
        return key(module) != -1;
    }

    public static void handle(int key, boolean pressed) {
        if (key != -1) {
            sync();

            for (Entry<String, Binds.Bind> e : BINDS.entrySet()) {
                if (e.getValue().key() == key) {
                    if (e.getKey().equals("Click GUI")) {
                        if (pressed) {
                            openClickGui.run();
                        }
                    } else {
                        ClientModule m = ModuleRegistry.byName(e.getKey());
                        if (m != null && m.hasToggle()) {
                            if (e.getValue().hold()) {
                                m.setOn(pressed);
                            } else if (pressed) {
                                m.toggle();
                            }
                        }
                    }
                }
            }
        }
    }

    public static String shortName(int key) {
        if (key == -1) {
            return "None";
        }

        if (key <= -100) {
            int button = -100 - key;

            return switch (button) {
                case 0 -> "LMB";
                case 1 -> "RMB";
                case 2 -> "MMB";
                default -> "M" + (button + 1);
            };
        } else {
            return switch (key) {
                case 32 -> "SPACE";
                case 257 -> "ENTER";
                case 258 -> "TAB";
                case 259 -> "BACK";
                case 260 -> "INS";
                case 261 -> "DEL";
                case 262 -> "RIGHT";
                case 263 -> "LEFT";
                case 264 -> "DOWN";
                case 265 -> "UP";
                case 266 -> "PGUP";
                case 267 -> "PGDN";
                case 268 -> "HOME";
                case 269 -> "END";
                case 280 -> "CAPS";
                case 340 -> "LSHIFT";
                case 341 -> "LCTRL";
                case 342 -> "LALT";
                case 344 -> "RSHIFT";
                case 345 -> "RCTRL";
                case 346 -> "RALT";
                default -> {
                    if (key >= 290 && key <= 314) {
                        yield "F" + (key - 290 + 1);
                    } else if (key >= 320 && key <= 329) {
                        yield "NUM" + (key - 320);
                    } else {
                        String name = GLFW.glfwGetKeyName(key, 0);
                        yield name != null && !name.isBlank()
                            ? name.toUpperCase(Locale.ROOT)
                            : Type.KEYSYM.getOrCreate(key).getDisplayName().getString().toUpperCase(Locale.ROOT);
                    }
                }
            };
        }
    }

    private static void sync() {
        String raw = ModSettings.moduleBinds;
        if (!raw.equals(parsedFrom)) {
            BINDS.clear();

            for (String entry : raw.split(";")) {
                int eq = entry.lastIndexOf(61);
                if (eq > 0) {
                    String[] parts = entry.substring(eq + 1).split(":");

                    try {
                        int key = Integer.parseInt(parts[0].trim());
                        boolean hold = parts.length > 1 && parts[1].trim().equals("1");
                        String name = entry.substring(0, eq);
                        if (name.equals("Click GUI") || ModuleRegistry.byName(name) != null) {
                            BINDS.put(name, new Binds.Bind(key, hold));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            parsedFrom = raw;
        }
    }

    private static void save() {
        StringBuilder sb = new StringBuilder();

        for (Entry<String, Binds.Bind> e : BINDS.entrySet()) {
            if (e.getValue().key() != -1 || e.getValue().hold()) {
                if (!sb.isEmpty()) {
                    sb.append(';');
                }

                sb.append(e.getKey()).append('=').append(e.getValue().key()).append(':').append(e.getValue().hold() ? 1 : 0);
            }
        }

        ModSettings.moduleBinds = sb.toString();
        parsedFrom = ModSettings.moduleBinds;
        ModConfig.save();
    }

    public record Bind(int key, boolean hold) {
    }
}
