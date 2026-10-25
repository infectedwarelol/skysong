package dev.infectedware.skysong.gui.click;

import net.minecraft.util.Mth;
import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.Profiles;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.SpotifyManager;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.VScreen;
import dev.infectedware.skysong.hud.InputEvents;
import dev.infectedware.skysong.lyrics.LyricsManager;
import dev.infectedware.skysong.module.Binds;
import dev.infectedware.skysong.module.Category;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.module.ModuleRegistry;
import dev.infectedware.skysong.render.GlassRenderer;
import dev.infectedware.skysong.render.Icons;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.theme.SavedThemes;
import dev.infectedware.skysong.ui.Clip;
import dev.infectedware.skysong.ui.Theme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;

public class LiveEditorScreen extends VScreen {
    public static final long PREVIEW_LINE_MS = 3500L;
    private static final String THEMES_ROW = "Themes";
    private static final String[] PREVIEW_TEXT = new String[]{"Sing it to the sky", "Every word becomes a star", "Skysong lights the night"};
    private static final LyricsManager.LyricLine[] PREVIEW_LINES = previewLines();
    public static final long PREVIEW_LOOP_MS = PREVIEW_LINE_MS * PREVIEW_TEXT.length;
    private static final float MIN_VIRTUAL_W = 600.0F;
    private static final float MIN_VIRTUAL_H = 320.0F;
    private static final float MARGIN = 10.0F;
    private static final float GAP = 6.0F;
    private static final float TOP_H = 24.0F;
    private static final float FOOT_H = 14.0F;
    private static final float LIST_W = 132.0F;
    private static final float INSPECTOR_W = 196.0F;
    private static final float ROW_H = 18.0F;
    private static final float LIST_TOP_PAD = 10.0F;
    private static final String MENU_MODULE = "Click GUI";
    private static final float HEADER_ROW_H = 15.0F;
    private static final float ICON_BOX = 14.0F;
    private static final float ICON_SIZE = 10.0F;
    private static final float SEARCH_W = 150.0F;
    private static final float BUTTON = 14.0F;
    private static final float TOGGLE_W = 16.0F;
    private static final float TOGGLE_H = 8.0F;
    static final Map<String, Item> ICONS = Map.ofEntries(
        Map.entry("Lyrics Text", Items.MUSIC_DISC_CAT),
        Map.entry("HUD Lyrics", Items.SPYGLASS),
        Map.entry("Name Tags", Items.NAME_TAG),
        Map.entry("ViewModel", Items.ARMOR_STAND),
        Map.entry("Shader Hands", Items.BLAZE_POWDER),
        Map.entry("Motion Blur", Items.FEATHER),
        Map.entry("Aspect Ratio", Items.ITEM_FRAME),
        Map.entry("Crosshair", Items.TARGET),
        Map.entry("Kill Effects", Items.NETHERITE_SWORD),
        Map.entry("Death Effect", Items.TOTEM_OF_UNDYING),
        Map.entry("Swing Trail", Items.TRIDENT),
        Map.entry("Afterimages", Items.PHANTOM_MEMBRANE),
        Map.entry("Shader ESP", Items.SPECTRAL_ARROW),
        Map.entry("World Shader", Items.NETHER_STAR),
        Map.entry("Interface", Items.PAINTING),
        Map.entry("Click GUI", Items.KNOWLEDGE_BOOK),
        Map.entry("ArrayList", Items.BOOK),
        Map.entry("Watermark", Items.MAP),
        Map.entry("TargetHud", Items.CROSSBOW),
        Map.entry("Potions", Items.POTION),
        Map.entry("KeyStrokes", Items.LEVER),
        Map.entry("Armor", Items.DIAMOND_CHESTPLATE),
        Map.entry("Inventory", Items.CHEST),
        Map.entry("Cooldowns", Items.CLOCK),
        Map.entry("Hotkeys", Items.TRIPWIRE_HOOK),
        Map.entry("Info", Items.COMPASS),
        Map.entry("Notifications", Items.BELL),
        Map.entry("Custom Hotbar", Items.BUNDLE),
        Map.entry("HPFocus", Items.GOLDEN_APPLE),
        Map.entry("Now Playing", Items.MUSIC_DISC_PIGSTEP),
        Map.entry("Dynamic Colors", Items.AMETHYST_SHARD),
        Map.entry("Spotify", Items.GOAT_HORN),
        Map.entry("Config", Items.COMPARATOR),
        Map.entry("Party", Items.JUKEBOX),
        Map.entry("Small Totem", Items.TOTEM_OF_UNDYING),
        Map.entry("Low Fire", Items.FLINT_AND_STEEL),
        Map.entry("No Hurt Cam", Items.IRON_CHESTPLATE),
        Map.entry(THEMES_ROW, Items.GLOW_INK_SAC)
    );
    private static final Map<String, ItemStack> STACKS = new java.util.HashMap<>();
    private static String lastSelected = "Lyrics Text";
    private final DockedSettings inspector = new DockedSettings();
    private final ThemesGrid themes = new ThemesGrid();
    private final SearchField search = new SearchField();
    private final BindPopup bind = new BindPopup();
    private final ScrollBar listBar = new ScrollBar();
    private final Anim openAnim = new Anim(220);
    private String selected = lastSelected;
    private float listScroll;
    private float listScrollTarget;
    private boolean closing;
    private boolean menuKeyReleased;
    private long lastNs = System.nanoTime();
    private float dt;
    private float mx;
    private float my;
    private float inspectorBodyTop;
    private float inspectorBodyH;

    public LiveEditorScreen() {
        super(Component.literal("Skysong"));
    }

    public static LyricsManager.LyricLine previewLine(long loopMs) {
        int index = (int)Math.min(PREVIEW_LINES.length - 1, Math.floorMod(loopMs, PREVIEW_LOOP_MS) / PREVIEW_LINE_MS);
        return PREVIEW_LINES[index];
    }

    private static LyricsManager.LyricLine[] previewLines() {
        LyricsManager.LyricLine[] lines = new LyricsManager.LyricLine[PREVIEW_TEXT.length];

        for (int i = 0; i < lines.length; i++) {
            lines[i] = new LyricsManager.LyricLine(i * PREVIEW_LINE_MS, (i + 1) * PREVIEW_LINE_MS, PREVIEW_TEXT[i]);
        }

        return lines;
    }

    @Override
    protected void init() {
        super.init();
        if (this.openAnim.linear() <= 0.0F) {
            this.openAnim.restart();
            Sounds.play("open");
        }

        this.closing = false;
        this.openAnim.forwards(true);
        this.lastNs = System.nanoTime();
        this.menuKeyReleased = InputEvents.isUp(Binds.key("Click GUI"));
        this.select(this.selected);
    }

    @Override
    public void onClose() {
        if (!this.closing) {
            this.closing = true;
            this.openAnim.forwards(false);
            this.bind.close();
            this.inspector.closeOverlays();
            this.search.blur();
            Sounds.play("close");
            ModConfig.save();
        }
    }

    @Override
    public void removed() {
        ModConfig.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @NullMarked
    @Override
    public void onFilesDrop(List<Path> paths) {
        Profiles.importFiles(paths);
    }

    private float scale() {
        return Math.min(Theme.unitScale(), Math.min(this.width / MIN_VIRTUAL_W, this.height / MIN_VIRTUAL_H));
    }

    private float vw() {
        return this.width / this.scale();
    }

    private float vh() {
        return this.height / this.scale();
    }

    private float bodyY() {
        return MARGIN + TOP_H + GAP;
    }

    private float bodyH() {
        return this.vh() - this.bodyY() - GAP - FOOT_H - MARGIN;
    }

    private float inspectorX() {
        return this.vw() - MARGIN - INSPECTOR_W;
    }

    private float previewX() {
        return MARGIN + LIST_W + GAP;
    }

    private float previewW() {
        return this.inspectorX() - GAP - this.previewX();
    }

    private void select(String name) {
        this.selected = name;
        lastSelected = name;
        if (THEMES_ROW.equals(name)) {
            this.inspector.show(null);
            this.themes.open();
        } else {
            this.inspector.show(ModuleRegistry.byName(name));
        }
    }

    @Override
    protected void draw(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        this.dt = Math.min(0.1F, (now - this.lastNs) / 1.0E9F);
        this.lastNs = now;
        ClientAccent.beginFrame();
        if (this.closing && this.openAnim.done()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }

        float s = this.scale();
        this.mx = mouseX / s;
        this.my = mouseY / s;
        float a = this.openAnim.get();
        boolean previous = R2D.squareCorners;
        R2D.squareCorners = true;
        ctx.pose().pushPose();
        ctx.pose().translate(0.0F, 0.0F, 200.0F);
        ctx.pose().scale(s, s, 1.0F);

        try {
            this.drawTopBar(ctx, a);
            this.drawList(ctx, a);
            this.drawPreview(ctx, a);
            this.drawInspector(ctx, a);
            this.drawFooter(ctx, a);
            if (this.bind.isVisible()) {
                this.bind.render(ctx, a, this.mx, this.my);
            }
        } finally {
            ctx.pose().popPose();
            R2D.squareCorners = previous;
        }
    }

    private static void panel(GuiGraphics c, float x, float y, float w, float h, float a) {
        R2D.rect(c, x, y, w, h, 0.0F, Colors.rgba(14, 16, 26, 200.0F * a));
        R2D.outline(c, x, y, w, h, 0.0F, 0.6F, Colors.rgba(255, 255, 255, 26.0F * a));
    }

    private void drawTopBar(GuiGraphics c, float a) {
        float x = MARGIN;
        float y = MARGIN;
        float w = this.vw() - MARGIN * 2.0F;
        float cy = y + TOP_H / 2.0F;
        panel(c, x, y, w, TOP_H, a);
        Icons.logo(c, x + 7.0F, cy - 6.0F, 12.0F, a);
        float[] bb = R2D.bounds(MsdfFont.Font.PIXEL, "SKYSONG", 9.0F);
        R2D.text(c, MsdfFont.Font.PIXEL, "SKYSONG", x + 24.0F, cy - (bb[1] + bb[3]) / 2.0F, 9.0F, Ui.white(255.0F * a));
        float crumbX = x + 24.0F + R2D.width(MsdfFont.Font.PIXEL, "SKYSONG", 9.0F) + 12.0F;
        ClientModule m = ModuleRegistry.byName(this.selected);
        String category = m != null ? m.category().title : "Style";
        String prefix = category + " / ";
        Ui.text(c, prefix, crumbX, cy - 4.0F, 6.5F, Ui.white(140.0F * a));
        R2D.text(c, MsdfFont.Font.SEMIBOLD, this.selected, crumbX + Ui.width(prefix, 6.5F), cy - 4.0F, 6.5F, Ui.white(245.0F * a));
        float playerW = this.drawNowPlaying(c, x + w, cy, a);
        float searchX = x + w - playerW - 10.0F - SEARCH_W;
        this.search.render(c, searchX, cy - 7.0F, SEARCH_W, 14.0F, a, this.mx, this.my, this.dt);
    }

    private float drawNowPlaying(GuiGraphics c, float right, float cy, float a) {
        SpotifyManager spotify = SkysongClient.getSpotifyManager();
        float buttons = BUTTON * 4.0F + 3.0F * 3.0F;
        float bx = right - 6.0F - buttons;
        String[] kinds = new String[]{"prev", spotify != null && spotify.isPlaying() ? "pause" : "play", "next", "like"};

        for (int i = 0; i < kinds.length; i++) {
            float x = bx + i * (BUTTON + 3.0F);
            float y = cy - BUTTON / 2.0F;
            boolean hover = Ui.inside(this.mx, this.my, x, y, BUTTON, BUTTON);
            R2D.rect(c, x, y, BUTTON, BUTTON, 0.0F, Ui.white((hover ? 34.0F : 14.0F) * a));
            R2D.outline(c, x, y, BUTTON, BUTTON, 0.0F, 0.5F, Ui.white(30.0F * a));
            drawControl(c, kinds[i], x + BUTTON / 2.0F, cy, Ui.white(235.0F * a));
        }

        String title = spotify == null || spotify.getCurrentTrackName().isEmpty() ? "Nothing playing" : spotify.getCurrentTrackName();
        String artist = spotify == null ? "" : spotify.getCurrentArtist();
        float widest = Math.max(Ui.width(title, 6.5F), Ui.width(artist, 5.5F));
        float textW = Math.min(120.0F, widest);
        float textX = bx - 8.0F - textW;
        R2D.text(c, MsdfFont.Font.SEMIBOLD, trim(title, textW, 6.5F), textX, cy - 7.0F, 6.5F, Ui.white(240.0F * a));
        Ui.text(c, trim(artist, textW, 5.5F), textX, cy + 1.0F, 5.5F, Ui.white(150.0F * a));
        float artX = textX - 22.0F;
        Identifier art = spotify == null ? null : spotify.getAlbumArtId();
        if (art != null && !spotify.getCurrentTrackName().isEmpty()) {
            R2D.image(c, art, artX, cy - 8.0F, 16.0F, 16.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, Math.round(a * 255.0F) << 24 | 16777215, true);
        } else {
            R2D.rect(c, artX, cy - 8.0F, 16.0F, 16.0F, 0.0F, ClientAccent.gradientA(200.0F * a), ClientAccent.gradientB(200.0F * a), ClientAccent.gradientB(200.0F * a), ClientAccent.gradientA(200.0F * a));
        }

        R2D.rect(c, artX - 7.0F, cy - 8.0F, 0.6F, 16.0F, 0.0F, Ui.white(30.0F * a));
        return right - artX + 7.0F;
    }

    private static void drawControl(GuiGraphics c, String kind, float cx, float cy, int color) {
        switch (kind) {
            case "prev" -> {
                R2D.rect(c, cx - 3.0F, cy - 3.0F, 1.2F, 6.0F, 0.0F, color);
                GlassRenderer.fillPolygon(c, new float[][]{{cx + 3.0F, cy - 3.0F}, {cx + 3.0F, cy + 3.0F}, {cx - 1.5F, cy}}, color);
            }
            case "next" -> {
                GlassRenderer.fillPolygon(c, new float[][]{{cx - 3.0F, cy - 3.0F}, {cx + 1.5F, cy}, {cx - 3.0F, cy + 3.0F}}, color);
                R2D.rect(c, cx + 1.8F, cy - 3.0F, 1.2F, 6.0F, 0.0F, color);
            }
            case "pause" -> {
                R2D.rect(c, cx - 2.4F, cy - 3.0F, 1.8F, 6.0F, 0.0F, color);
                R2D.rect(c, cx + 0.6F, cy - 3.0F, 1.8F, 6.0F, 0.0F, color);
            }
            case "play" -> GlassRenderer.fillPolygon(c, new float[][]{{cx - 2.0F, cy - 3.0F}, {cx + 3.0F, cy}, {cx - 2.0F, cy + 3.0F}}, color);
            default -> GlassRenderer.fillPolygon(
                c, new float[][]{{cx, cy + 3.2F}, {cx - 3.4F, cy - 0.2F}, {cx - 2.0F, cy - 2.8F}, {cx, cy - 1.4F}, {cx + 2.0F, cy - 2.8F}, {cx + 3.4F, cy - 0.2F}}, color
            );
        }
    }

    private List<String[]> rows() {
        List<String[]> rows = new ArrayList<>();
        String query = this.search.getText().trim().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            rows.add(new String[]{"header", "RESULTS"});

            for (ClientModule m : ModuleRegistry.ALL) {
                if (MENU_MODULE.equals(m.name())) {
                    continue;
                }

                if (m.name().toLowerCase(Locale.ROOT).contains(query) || m.description().toLowerCase(Locale.ROOT).contains(query)) {
                    rows.add(new String[]{"module", m.name()});
                }
            }

            if (THEMES_ROW.toLowerCase(Locale.ROOT).contains(query)) {
                rows.add(new String[]{"module", THEMES_ROW});
            }

            return rows;
        }

        for (Category category : Category.values()) {
            rows.add(new String[]{"header", category.title.toUpperCase(Locale.ROOT)});

            for (ClientModule m : ModuleRegistry.in(category)) {
                if (!MENU_MODULE.equals(m.name())) {
                    rows.add(new String[]{"module", m.name()});
                }
            }
        }

        rows.add(new String[]{"header", "STYLE"});
        rows.add(new String[]{"module", THEMES_ROW});
        return rows;
    }

    private static float rowHeight(String[] row) {
        return row[0].equals("header") ? HEADER_ROW_H : ROW_H;
    }

    private float listContentHeight(List<String[]> rows) {
        float h = 0.0F;

        for (String[] row : rows) {
            h += rowHeight(row);
        }

        return h + LIST_TOP_PAD * 2.0F;
    }

    private void drawList(GuiGraphics c, float a) {
        float x = MARGIN;
        float y = this.bodyY();
        float h = this.bodyH();
        panel(c, x, y, LIST_W, h, a);
        List<String[]> rows = this.rows();
        float content = this.listContentHeight(rows);
        float max = Math.max(0.0F, content - h);
        this.listScrollTarget = Mth.clamp(this.listScrollTarget, 0.0F, max);
        this.listScroll = Anim.follow(this.listScroll, this.listScrollTarget, this.dt, 16.0F);
        Clip.push(c, x, y, x + LIST_W, y + h);
        float ry = y + LIST_TOP_PAD - this.listScroll;

        for (String[] row : rows) {
            float rh = rowHeight(row);
            if (ry + rh >= y && ry <= y + h) {
                if (row[0].equals("header")) {
                    R2D.text(c, MsdfFont.Font.SEMIBOLD, row[1], x + 8.0F, ry + 5.0F, 5.5F, Ui.white(130.0F * a));
                } else {
                    this.drawModuleRow(c, row[1], x + 3.0F, ry, a);
                }
            }

            ry += rh;
        }

        Clip.pop(c);
        if (content > h + 0.5F) {
            float s = this.listBar.render(c, x + LIST_W - 3.5F, y, h, h, content, this.listScroll, a, this.mx, this.my);
            if (this.listBar.isDragging()) {
                this.listScroll = this.listScrollTarget = s;
            }
        }
    }

    private void drawModuleRow(GuiGraphics c, String name, float x, float y, float a) {
        boolean active = name.equals(this.selected);
        boolean hover = Ui.inside(this.mx, this.my, x, y, (LIST_W - 6.0F), ROW_H);
        if (active) {
            R2D.rect(c, x, y + 1.0F, (LIST_W - 6.0F), ROW_H - 2.0F, 0.0F, ClientAccent.accent(52.0F * a));
            R2D.rect(c, x, y + 1.0F, 1.5F, ROW_H - 2.0F, 0.0F, ClientAccent.accentBright(255.0F * a));
        } else if (hover) {
            R2D.rect(c, x, y + 1.0F, (LIST_W - 6.0F), ROW_H - 2.0F, 0.0F, Ui.white(14.0F * a));
        }

        float boxX = x + 5.0F;
        float boxY = y + (ROW_H - ICON_BOX) / 2.0F;
        if (active) {
            R2D.rect(c, boxX, boxY, ICON_BOX, ICON_BOX, 0.0F, ClientAccent.gradientA(255.0F * a), ClientAccent.gradientB(255.0F * a), ClientAccent.gradientB(255.0F * a), ClientAccent.gradientA(255.0F * a));
        } else {
            R2D.rect(c, boxX, boxY, ICON_BOX, ICON_BOX, 0.0F, Ui.white(16.0F * a));
            R2D.outline(c, boxX, boxY, ICON_BOX, ICON_BOX, 0.0F, 0.5F, Ui.white(30.0F * a));
        }

        drawItem(c, name, boxX + (ICON_BOX - ICON_SIZE) / 2.0F, boxY + (ICON_BOX - ICON_SIZE) / 2.0F);
        Ui.text(c, trim(name, (LIST_W - 6.0F) - 48.0F, 6.5F), boxX + ICON_BOX + 6.0F, y + ROW_H / 2.0F - 4.0F, 6.5F, Ui.white((active ? 255.0F : 215.0F) * a));
        ClientModule m = ModuleRegistry.byName(name);
        float tx = x + (LIST_W - 6.0F) - TOGGLE_W - 5.0F;
        float ty = y + (ROW_H - TOGGLE_H) / 2.0F;
        if (m != null && m.hasToggle()) {
            drawToggle(c, tx, ty, m.isOn(), a);
        } else {
            Ui.text(c, "›", x + (LIST_W - 6.0F) - 9.0F, y + ROW_H / 2.0F - 4.5F, 7.0F, Ui.white(120.0F * a));
        }
    }

    private static void drawToggle(GuiGraphics c, float x, float y, boolean on, float a) {
        if (on) {
            R2D.rect(c, x, y, TOGGLE_W, TOGGLE_H, 0.0F, ClientAccent.gradientA(255.0F * a), ClientAccent.gradientB(255.0F * a), ClientAccent.gradientB(255.0F * a), ClientAccent.gradientA(255.0F * a));
            R2D.rect(c, x + TOGGLE_W - TOGGLE_H + 1.5F, y + 1.5F, TOGGLE_H - 3.0F, TOGGLE_H - 3.0F, 0.0F, Ui.white(255.0F * a));
        } else {
            R2D.rect(c, x, y, TOGGLE_W, TOGGLE_H, 0.0F, Ui.white(26.0F * a));
            R2D.outline(c, x, y, TOGGLE_W, TOGGLE_H, 0.0F, 0.5F, Ui.white(34.0F * a));
            R2D.rect(c, x + 1.5F, y + 1.5F, TOGGLE_H - 3.0F, TOGGLE_H - 3.0F, 0.0F, Ui.white(190.0F * a));
        }
    }

    private static void drawItem(GuiGraphics c, String name, float x, float y) {
        Item item = ICONS.getOrDefault(name, Items.PAPER);
        ItemStack stack = STACKS.computeIfAbsent(name, key -> new ItemStack(item));
        c.pose().pushPose();
        c.pose().translate(x, y, 0.0F);
        c.pose().scale(ICON_SIZE / 16.0F, ICON_SIZE / 16.0F, 1.0F);
        c.renderItem(stack, 0, 0);
        c.pose().popPose();
    }

    private void drawPreview(GuiGraphics c, float a) {
        float x = this.previewX();
        float y = this.bodyY();
        float w = this.previewW();
        float h = this.bodyH();
        R2D.outline(c, x, y, w, h, 0.0F, 0.6F, Ui.white(40.0F * a));
        float tagX = x + 6.0F;
        tagX = this.tag(c, "LIVE PREVIEW", tagX, y + 6.0F, a, true) + 4.0F;
        this.tag(c, this.previewDescription(), tagX, y + 6.0F, a, false);
        SpotifyManager spotify = SkysongClient.getSpotifyManager();
        boolean playing = spotify != null && spotify.isPlaying();
        String hint = !ModSettings.enable3DLyrics
            ? "Turn on Lyrics Text to see lyrics here"
            : (playing ? "" : "Nothing playing: showing a preview line");
        if (!hint.isEmpty()) {
            float hw = Ui.width(hint, 6.0F);
            R2D.rect(c, x + (w - hw) / 2.0F - 6.0F, y + h - 44.0F, hw + 12.0F, 13.0F, 0.0F, Colors.rgba(0, 0, 0, 120.0F * a));
            Ui.text(c, hint, x + (w - hw) / 2.0F, y + h - 41.0F, 6.0F, Ui.white(210.0F * a));
        }

        if (spotify != null && spotify.getDurationMs() > 0L && !spotify.getCurrentTrackName().isEmpty()) {
            long progress = spotify.getSmoothedProgressMs();
            long duration = spotify.getDurationMs();
            float barX = x + 30.0F;
            float barW = w - 60.0F;
            float barY = y + h - 14.0F;
            float pct = Math.min(1.0F, (float)progress / duration);
            R2D.rect(c, x + 4.0F, barY - 7.0F, w - 8.0F, 16.0F, 0.0F, Colors.rgba(0, 0, 0, 90.0F * a));
            Ui.text(c, time(progress), x + 8.0F, barY - 2.5F, 5.5F, Ui.white(220.0F * a));
            Ui.text(c, time(duration), x + w - 8.0F - Ui.width(time(duration), 5.5F), barY - 2.5F, 5.5F, Ui.white(220.0F * a));
            R2D.rect(c, barX, barY, barW, 2.5F, 0.0F, Ui.white(40.0F * a));
            R2D.rect(c, barX, barY, barW * pct, 2.5F, 0.0F, ClientAccent.gradientA(255.0F * a), ClientAccent.gradientB(255.0F * a), ClientAccent.gradientB(255.0F * a), ClientAccent.gradientA(255.0F * a));
            R2D.rect(c, barX + barW * pct - 1.5F, barY - 2.0F, 3.0F, 6.5F, 0.0F, Ui.white(255.0F * a));
        }
    }

    private String previewDescription() {
        String layout = ModSettings.LAYOUT_NAMES[Math.floorMod(ModSettings.lyricLayout, ModSettings.LAYOUT_NAMES.length)];
        String font = ModSettings.FONT_NAMES[Math.floorMod(ModSettings.lyricFont, ModSettings.FONT_NAMES.length)];
        String color = ModSettings.LYRIC_COLOR_NAMES[Math.floorMod(ModSettings.lyricColorMode, ModSettings.LYRIC_COLOR_NAMES.length)];
        return layout + " · " + font + " · " + color;
    }

    private float tag(GuiGraphics c, String text, float x, float y, float a, boolean live) {
        float tw = Ui.width(text, 5.5F);
        float pad = live ? 13.0F : 5.0F;
        float w = tw + pad + 5.0F;
        R2D.rect(c, x, y, w, 12.0F, 0.0F, Colors.rgba(0, 0, 0, 120.0F * a));
        R2D.outline(c, x, y, w, 12.0F, 0.0F, 0.5F, Ui.white(30.0F * a));
        if (live) {
            float pulse = 0.6F + 0.4F * (float)Math.sin(System.currentTimeMillis() / 300.0);
            R2D.rect(c, x + 4.5F, y + 4.0F, 4.0F, 4.0F, 0.0F, Colors.rgba(255, 79, 109, 255.0F * a * pulse));
        }

        Ui.text(c, text, x + pad, y + 2.5F, 5.5F, Ui.white(225.0F * a));
        return x + w;
    }

    private static String time(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return seconds / 60L + ":" + String.format(Locale.ROOT, "%02d", seconds % 60L);
    }

    private void drawInspector(GuiGraphics c, float a) {
        float x = this.inspectorX();
        float y = this.bodyY();
        float h = this.bodyH();
        panel(c, x, y, INSPECTOR_W, h, a);
        float pad = 8.0F;
        R2D.text(c, MsdfFont.Font.SEMIBOLD, this.selected, x + pad, y + pad, 9.0F, Ui.white(250.0F * a));
        ClientModule m = ModuleRegistry.byName(this.selected);
        if (m != null && m.hasToggle()) {
            drawToggle(c, x + INSPECTOR_W - pad - TOGGLE_W, y + pad + 1.0F, m.isOn(), a);
        }

        String description = THEMES_ROW.equals(this.selected) ? "Built-in client color themes." : (m == null ? "" : m.description());
        float descY = y + pad + 13.0F;
        List<String> lines = wrap(description, INSPECTOR_W - pad * 2.0F);

        for (String line : lines) {
            Ui.text(c, line, x + pad, descY, 5.5F, Ui.white(140.0F * a));
            descY += 7.0F;
        }

        float lineY = descY + 3.0F;
        R2D.rect(c, x + pad, lineY, INSPECTOR_W - pad * 2.0F, 0.6F, 0.0F, Ui.white(30.0F * a));
        float footH = 16.0F;
        float bodyTop = lineY + 6.0F;
        float bodyH = y + h - pad - footH - 6.0F - bodyTop;
        this.inspectorBodyTop = bodyTop;
        this.inspectorBodyH = bodyH;
        if (THEMES_ROW.equals(this.selected)) {
            this.themes.render(c, x + 2.0F, bodyTop, INSPECTOR_W - 4.0F, bodyH, a, 1.0F, this.dt, this.mx, this.my, !this.bind.isOpen(), x + INSPECTOR_W, 0.0F);
        } else if (this.inspector.isEmpty()) {
            Ui.text(c, "No settings for this module.", x + pad, bodyTop + 4.0F, 6.0F, Ui.white(150.0F * a));
        } else {
            this.inspector.render(c, x + pad - 2.0F, bodyTop, INSPECTOR_W - pad * 2.0F + 4.0F, bodyH, a, this.mx, this.my);
        }

        float by = y + h - pad - footH;
        float bw = INSPECTOR_W - pad * 2.0F;
        boolean hover = Ui.inside(this.mx, this.my, x + pad, by, bw, footH);
        R2D.rect(c, x + pad, by, bw, footH, 0.0F, ClientAccent.gradientA((hover ? 255.0F : 215.0F) * a), ClientAccent.gradientB((hover ? 255.0F : 215.0F) * a), ClientAccent.gradientB((hover ? 255.0F : 215.0F) * a), ClientAccent.gradientA((hover ? 255.0F : 215.0F) * a));
        String label = "Save look as theme";
        R2D.text(c, MsdfFont.Font.SEMIBOLD, label, x + pad + (bw - R2D.width(MsdfFont.Font.SEMIBOLD, label, 6.5F)) / 2.0F, by + 4.5F, 6.5F, Ui.white(255.0F * a));
    }

    private void drawFooter(GuiGraphics c, float a) {
        float y = this.vh() - MARGIN - FOOT_H;
        float w = this.vw() - MARGIN * 2.0F;
        panel(c, MARGIN, y, w, FOOT_H, a);
        String[][] hints = new String[][]{{"Esc", "close"}, {"Ctrl F", "search"}, {"Middle click", "bind a key"}, {"Click toggle", "turn on/off"}};
        float hx = MARGIN + 8.0F;

        for (String[] hint : hints) {
            R2D.text(c, MsdfFont.Font.SEMIBOLD, hint[0], hx, y + 4.0F, 5.5F, Ui.white(230.0F * a));
            hx += R2D.width(MsdfFont.Font.SEMIBOLD, hint[0], 5.5F) + 3.0F;
            Ui.text(c, hint[1], hx, y + 4.0F, 5.5F, Ui.white(130.0F * a));
            hx += Ui.width(hint[1], 5.5F) + 12.0F;
        }

        String right = "Changes apply live";
        Ui.text(c, right, MARGIN + w - 8.0F - Ui.width(right, 5.5F), y + 4.0F, 5.5F, Ui.white(130.0F * a));
    }

    private static List<String> wrap(String text, float maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (Ui.width(candidate, 5.5F) <= maxW || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                if (lines.size() == 2) {
                    return lines;
                }

                line.setLength(0);
                line.append(word);
            }
        }

        if (!line.isEmpty() && lines.size() < 2) {
            lines.add(trim(line.toString(), maxW, 5.5F));
        }

        return lines;
    }

    private static String trim(String s, float max, float size) {
        if (Ui.width(s, size) <= max) {
            return s;
        }

        String out = s;
        while (out.length() > 1 && Ui.width(out + "…", size) > max) {
            out = out.substring(0, out.length() - 1);
        }

        return out + "…";
    }

    private String rowAt(float x, float y) {
        float ly = this.bodyY();
        if (!Ui.inside(x, y, MARGIN, ly, LIST_W, this.bodyH())) {
            return null;
        }

        float ry = ly + LIST_TOP_PAD - this.listScroll;

        for (String[] row : this.rows()) {
            float rh = rowHeight(row);
            if (y >= ry && y < ry + rh) {
                return row[0].equals("module") ? row[1] : null;
            }

            ry += rh;
        }

        return null;
    }

    private boolean clickControls(float x, float y) {
        float right = MARGIN + this.vw() - MARGIN * 2.0F;
        float cy = MARGIN + TOP_H / 2.0F;
        float bx = right - 6.0F - (BUTTON * 4.0F + 9.0F);
        SpotifyManager spotify = SkysongClient.getSpotifyManager();
        if (spotify == null) {
            return false;
        }

        for (int i = 0; i < 4; i++) {
            float x0 = bx + i * (BUTTON + 3.0F);
            if (Ui.inside(x, y, x0, cy - BUTTON / 2.0F, BUTTON, BUTTON)) {
                switch (i) {
                    case 0 -> spotify.previousTrack();
                    case 1 -> spotify.togglePlayback();
                    case 2 -> spotify.nextTrack();
                    default -> spotify.toggleLike();
                }

                Sounds.play("button");
                return true;
            }
        }

        return false;
    }

    private boolean clickSeek(float x, float y) {
        SpotifyManager spotify = SkysongClient.getSpotifyManager();
        if (spotify == null || spotify.getDurationMs() <= 0L || spotify.getCurrentTrackName().isEmpty()) {
            return false;
        }

        float px = this.previewX();
        float w = this.previewW();
        float barX = px + 30.0F;
        float barW = w - 60.0F;
        float barY = this.bodyY() + this.bodyH() - 14.0F;
        if (Ui.inside(x, y, barX, barY - 5.0F, barW, 12.0F)) {
            float pct = Math.clamp((x - barX) / barW, 0.0F, 1.0F);
            spotify.seekTo(Math.round(pct * spotify.getDurationMs()));
            return true;
        }

        return false;
    }

    @Override
    public boolean onClick(double mouseX, double mouseY, int button) {
        if (this.closing) {
            return true;
        }

        float s = this.scale();
        float x = (float)(mouseX / s);
        float y = (float)(mouseY / s);
        if (this.bind.isOpen() && this.bind.mouseBind(button)) {
            return true;
        }

        if (this.inspector.mouseBind(button)) {
            return true;
        }

        if (this.bind.isOpen()) {
            if (button == 0) {
                this.bind.click(x, y);
            } else {
                this.bind.close();
            }

            return true;
        }

        if (button == 2) {
            if (this.inspector.middleClick(x, y)) {
                return true;
            }

            String row = this.rowAt(x, y);
            ClientModule m = row == null ? null : ModuleRegistry.byName(row);
            if (m != null) {
                this.bind.open(m, x, y);
            }

            return true;
        }

        if (button == 0 && this.inspector.click(x, y)) {
            return true;
        }

        if (this.search.mouseClicked(x, y, button)) {
            return true;
        }

        if (button == 0 && this.clickControls(x, y)) {
            return true;
        }

        if (button == 0 && this.clickSeek(x, y)) {
            return true;
        }

        if (button == 0 && this.listBar.tryGrab(x, y)) {
            return true;
        }

        float ix = this.inspectorX();
        float iy = this.bodyY();
        if (button == 0 && THEMES_ROW.equals(this.selected) && Ui.inside(x, y, ix, iy, INSPECTOR_W, this.bodyH())) {
            if (this.themes.scrollbarGrab(x, y)) {
                return true;
            }

            if (this.themes.click(ix + 2.0F, this.inspectorBodyTop, INSPECTOR_W - 4.0F, this.inspectorBodyH, x, y)) {
                return true;
            }
        }

        ClientModule selectedModule = ModuleRegistry.byName(this.selected);
        if (button == 0 && selectedModule != null && selectedModule.hasToggle()) {
            float tx = ix + INSPECTOR_W - 8.0F - TOGGLE_W;
            if (Ui.inside(x, y, tx - 2.0F, iy + 7.0F, TOGGLE_W + 4.0F, TOGGLE_H + 4.0F)) {
                selectedModule.toggle();
                Sounds.play(selectedModule.isOn() ? "module_on" : "module_off", 0.7F);
                return true;
            }
        }

        if (button == 0 && Ui.inside(x, y, ix + 8.0F, iy + this.bodyH() - 24.0F, INSPECTOR_W - 16.0F, 16.0F)) {
            SavedThemes.save("");
            SettingsPopup.rebuildSoon();
            Sounds.play("button");
            return true;
        }

        String row = this.rowAt(x, y);
        if (row != null && (button == 0 || button == 1)) {
            ClientModule m = ModuleRegistry.byName(row);
            float tx = MARGIN + 3.0F + LIST_W - 6.0F - TOGGLE_W - 5.0F;
            boolean onToggle = x >= tx - 3.0F;
            if (m != null && m.hasToggle() && (onToggle || button == 1)) {
                m.toggle();
                Sounds.play(m.isOn() ? "module_on" : "module_off", 0.7F);
            } else if (!row.equals(this.selected)) {
                this.select(row);
                Sounds.play("select_category");
            }

            return true;
        }

        return super.onClick(mouseX, mouseY, button);
    }

    @Override
    public boolean onRelease(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.inspector.releaseDrags();
            this.bind.releaseDrag();
        }

        this.listBar.release();
        this.themes.scrollbarRelease();
        this.search.mouseReleased();
        return super.onRelease(mouseX, mouseY, button);
    }

    @Override
    protected boolean scrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (this.closing) {
            return true;
        }

        double amount = ClickGuiScreen.unscaledScroll(vertical);
        float s = this.scale();
        float x = (float)(mouseX / s);
        float y = (float)(mouseY / s);
        if (this.bind.isOpen()) {
            this.bind.close();
            return true;
        }

        if (Ui.inside(x, y, MARGIN, this.bodyY(), LIST_W, this.bodyH())) {
            this.listScrollTarget -= (float)amount * 14.0F;
            return true;
        }

        if (Ui.inside(x, y, this.inspectorX(), this.bodyY(), INSPECTOR_W, this.bodyH())) {
            if (THEMES_ROW.equals(this.selected)) {
                this.themes.scroll(amount, this.inspectorBodyH);
                return true;
            }

            return this.inspector.scroll(x, y, amount);
        }

        return this.inspector.scroll(x, y, amount) || this.scrolledVanilla(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean onKey(int key, int scanCode, int mods) {
        int menuKey = Binds.key("Click GUI");
        if (this.closing) {
            if (key == menuKey && this.menuKeyReleased) {
                this.menuKeyReleased = false;
                this.closing = false;
                this.openAnim.forwards(true);
                Sounds.play("open");
            }

            return true;
        }

        if (this.bind.isOpen()) {
            if (!this.bind.keyPressed(key) && key == 256) {
                this.bind.close();
            }

            return true;
        }

        if (this.inspector.keyPressed(key)) {
            return true;
        }

        if (key == 256 && this.inspector.hasOpenOverlay()) {
            this.inspector.closeOverlays();
            return true;
        }

        if (this.search.isTyping() && this.search.keyPressed(key, mods)) {
            return true;
        }

        if (key == 70 && Compat.controlDown()) {
            this.search.focus();
            return true;
        }

        if (key == menuKey) {
            if (this.menuKeyReleased) {
                this.menuKeyReleased = false;
                this.onClose();
            }

            return true;
        }

        if (key == 256) {
            if (this.search.hasText()) {
                this.search.setText("");
                this.search.blur();
            } else {
                this.onClose();
            }

            return true;
        }

        return super.onKey(key, scanCode, mods);
    }

    @Override
    public boolean onKeyUp(int key, int scanCode, int mods) {
        if (key == Binds.key("Click GUI")) {
            this.menuKeyReleased = true;
        }

        return super.onKeyUp(key, scanCode, mods);
    }

    @Override
    public boolean onChar(char chr, int modifiers) {
        if (this.closing || this.bind.isOpen()) {
            return true;
        }

        if (this.inspector.charTyped(chr)) {
            return true;
        }

        if (!this.search.isTyping() && !this.inspector.typing() && Character.isLetterOrDigit(chr)) {
            this.search.focus();
        }

        if (this.search.isTyping() && this.search.charTyped(chr)) {
            this.listScroll = this.listScrollTarget = 0.0F;
            return true;
        }

        return super.onChar(chr, modifiers);
    }
}
