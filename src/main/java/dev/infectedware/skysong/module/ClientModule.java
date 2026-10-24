package dev.infectedware.skysong.module;

import dev.infectedware.skysong.ModConfig;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record ClientModule(
    String name,
    String description,
    Category category,
    BooleanSupplier enabled,
    Consumer<Boolean> setEnabled,
    Supplier<String> suffix,
    Supplier<List<Setting>> settings
) {
    public static Consumer<ClientModule> toggleListener = m -> {};

    public boolean hasToggle() {
        return this.enabled != null;
    }

    public boolean isOn() {
        return this.enabled != null && this.enabled.getAsBoolean();
    }

    public boolean hasSettings() {
        return this.settings != null && !this.settings.get().isEmpty();
    }

    public void toggle() {
        this.setOn(!this.isOn());
    }

    public void setOn(boolean on) {
        if (this.hasToggle() && this.isOn() != on) {
            this.setEnabled.accept(on);
            ModConfig.save();
            toggleListener.accept(this);
        }
    }
}
