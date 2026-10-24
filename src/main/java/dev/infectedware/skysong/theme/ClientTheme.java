package dev.infectedware.skysong.theme;

import java.awt.Color;

public enum ClientTheme {
    SKYSONG("Skysong", 9081843, 11838975),
    BLUE_PINK("Blue Pink", 4776676, 14045679),
    BLUE_GREEN("Blue Green", 911192, 157920),
    DARK_BLUE("Dark Blue", 4023774, 2166617),
    BLACK_WHITE("Black & White", 13158600, 8092539),
    WATER("Water", 6134508, 737480),
    VIOLET("Violet", 9443736, 4395888),
    LAVENDER("Lavender", 12618202, 6892934),
    SUNRISE("Sunrise", 16087094, 14039456),
    SUNSET("Sunset", 16742912, 9317858),
    SPRING("Spring", 11067491, 5679919),
    SUMMER("Summer", 16769625, 16754513),
    WINTER("Winter", 14740220, 13623027),
    MIDNIGHT("Midnight", 991271, 2904932),
    HALLOWEEN("Halloween", 16741656, 1710618),
    NEW_YEAR("New Year", 1981554, 12597547),
    VALENTINE("Valentine", 16739229, 12862825),
    FIRE("Fire", 16765440, 16711680),
    EARTH("Earth", 9132587, 4073251),
    ICE("Ice", 10616782, 46299),
    FOREST("Forest", 1265171, 3046706),
    GALAXY("Galaxy", 986153, 9055202),
    DESERT("Desert", 15254430, 12092939),
    GOLD("Gold", 16774839, 12092939),
    EMERALD("Emerald", 5294200, 222768),
    CORAL("Coral", 16744272, 15287402),
    MINT("Mint", 11993051, 2074234),
    PASTEL("Pastel", 16765404, 12710128),
    TURQUOISE("Turquoise", 1022862, 23639),
    BLOOD("Blood", 12138082, 6757403),
    NEON("Neon", 16720128, 59903),
    AUTUMN("Autumn", 59903, 12601856),
    CHRISTMAS("Christmas", 14617355, 14470091),
    NEON_REVOLUTION("Neon Revolution", 5034078, 7881892),
    WATERMELON("Watermelon", 13453107, 10796658);

    private final String displayName;
    private final int[] palette;
    private final int accent;
    private final int accentBright;
    private final int accentSoft;
    private final int accentFill;
    private final int toggleOn;
    private final int gradientA;
    private final int gradientB;

    ClientTheme(String displayName, int... palette) {
        this.displayName = displayName;
        this.palette = palette.clone();
        int base = palette[0] & 16777215;
        this.accent = base;
        this.accentBright = lighten(base, 0.65F, 1.15F, 0.1F);
        this.accentSoft = lighten(base, 0.45F, 1.25F, 0.15F);
        this.accentFill = darken(base, 1.05F, 0.78F);
        this.toggleOn = darken(base, 1.1F, 0.55F);
        this.gradientA = base;
        this.gradientB = palette[palette.length - 1] & 16777215;
    }

    public String displayName() {
        return this.displayName;
    }

    public int[] palette() {
        return this.palette;
    }

    public int[] shades() {
        return new int[]{this.accent, this.accentBright, this.accentSoft, this.accentFill, this.toggleOn, this.gradientA, this.gradientB};
    }

    public int gradientA() {
        return this.gradientA;
    }

    public int gradientB() {
        return this.gradientB;
    }

    public int accentBright() {
        return this.accentBright;
    }

    public static ClientTheme byName(String name) {
        for (ClientTheme t : values()) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }

        return SKYSONG;
    }

    static int lighten(int rgb, float sat, float bright, float add) {
        float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
        return Color.HSBtoRGB(hsb[0], clamp01(hsb[1] * sat), clamp01(hsb[2] * bright + add)) & 16777215;
    }

    static int darken(int rgb, float sat, float bright) {
        float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
        return Color.HSBtoRGB(hsb[0], clamp01(hsb[1] * sat), hsb[2] * bright) & 16777215;
    }

    static float clamp01(float f) {
        return f < 0.0F ? 0.0F : Math.min(f, 1.0F);
    }
}
