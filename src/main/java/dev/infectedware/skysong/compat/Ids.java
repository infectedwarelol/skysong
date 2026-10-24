package dev.infectedware.skysong.compat;

import net.minecraft.resources.Identifier;

public final class Ids {
    private Ids() {
    }

    public static Identifier of(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static Identifier mc(String path) {
        return of("minecraft", path);
    }
}
