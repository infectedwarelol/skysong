package dev.infectedware.skysong.module;

public enum Category {
    VISUALS("Visuals", "p", "r"),
    DISPLAY("Display", "j", "i"),
    UTILS("Utils", "r", "j"),
    PVP("PvP", "r", "r"),
    COSMETICS("Cosmetics", "f", "f");

    public final String title;
    public final String icon;
    public final String hudIcon;

    Category(String title, String icon, String hudIcon) {
        this.title = title;
        this.icon = icon;
        this.hudIcon = hudIcon;
    }
}
