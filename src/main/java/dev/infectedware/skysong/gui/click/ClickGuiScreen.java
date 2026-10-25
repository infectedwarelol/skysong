package dev.infectedware.skysong.gui.click;

import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.Profiles;
import dev.infectedware.skysong.SpotifyManager;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Ids;
import dev.infectedware.skysong.compat.VScreen;
import dev.infectedware.skysong.hud.InputEvents;
import dev.infectedware.skysong.module.Binds;
import dev.infectedware.skysong.module.Category;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.module.ModuleRegistry;
import dev.infectedware.skysong.render.Icons;
import dev.infectedware.skysong.render.r2d.Backdrop;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.ui.Clip;
import dev.infectedware.skysong.ui.Theme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ClickGuiScreen extends VScreen {
    public static final float PANEL_W = 430.0F;
    public static final float PANEL_H = 290.0F;
    public static final float SIDEBAR_W = 110.0F;
    private static final float CONTENT_X = 117.0F;
    private static final float CONTENT_INSET = 122.0F;
    private static final float CONTENT_H = 280.0F;
    private static final float HEADER_H = 30.0F;
    private static final Category[] MAIN = new Category[]{Category.VISUALS, Category.DISPLAY, Category.UTILS};
    private static final String RU = "йцукенгшщзхъфывапролджэячсмитьбюё";
    private static final String EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";
    private ClickGuiScreen.View target = ClickGuiScreen.View.NONE;
    private ClickGuiScreen.View content = ClickGuiScreen.View.NONE;
    private float categoryT = 1.0F;
    private float themesRowT = 1.0F;
    private final Map<ClickGuiScreen.View, Anim> categoryAnims = new EnumMap<>(ClickGuiScreen.View.class);
    private final Anim modulesHeader = new Anim(200);
    private final Anim placeholder = new Anim(200, true);
    private final ModuleCards cards = new ModuleCards();
    private final ThemesGrid themes = new ThemesGrid();
    private final SearchField search = new SearchField();
    private final SettingsPopup settings = new SettingsPopup();
    private final BindPopup bind = new BindPopup();
    private final GifPlayer cat = new GifPlayer(Ids.of("skysong", "textures/gui/placeholder.gif"));
    private final Anim openAnim = new Anim(300);
    private boolean closing;
    private boolean menuKeyReleased;
    private long lastNs = System.nanoTime();
    private float dt;
    private float mx;
    private float my;
    private static ClickGuiScreen shared;
    private String reopenSettings;
    private float reopenX;
    private float reopenY;

    public ClickGuiScreen() {
        super(Component.literal("Skysong"));
        ClickGuiScreen.View[] views = ClickGuiScreen.View.values();
        int last = ModSettings.guiLastView;
        if (last > 0 && last < views.length) {
            this.target = this.content = views[last];
        }
    }

    public static ClickGuiScreen shared() {
        if (shared == null) {
            shared = new ClickGuiScreen();
        }

        return shared;
    }

    @Override
    protected void init() {
        super.init();
        if (this.openAnim.linear() <= 0.0F) {
            this.openAnim.restart();
            Sounds.play("open");
        }

        this.closing = false;
        if (this.reopenSettings != null) {
            ClientModule m = ModuleRegistry.byName(this.reopenSettings);
            this.reopenSettings = null;
            if (m != null && m.hasSettings()) {
                this.settings.reopen(m, this.reopenX, this.reopenY, 250.0F, this.vw(), this.vh());
            }
        }

        this.openAnim.forwards(true);
        this.lastNs = System.nanoTime();
        int menuKey = Binds.key("Click GUI");
        this.menuKeyReleased = InputEvents.isUp(menuKey);
    }

    @Override
    public void onClose() {
        if (!this.closing) {
            this.closing = true;
            this.openAnim.forwards(false);
            if (this.settings.isOpen() && this.settings.module() != null) {
                this.reopenSettings = this.settings.module().name();
                this.reopenX = this.settings.x();
                this.reopenY = this.settings.y();
            }

            this.settings.close();
            this.bind.close();
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

    @Override
    public void onFilesDrop(List<Path> paths) {
        Profiles.importFiles(paths);
    }

    public void openSettingsFor(String name) {
        ClientModule m = ModuleRegistry.byName(name);
        if (m != null && m.hasSettings()) {
            ClickGuiScreen.View v = viewOf(m.category());
            this.target = v;
            this.content = v;
            this.categoryT = 1.0F;
            float s = this.scale();
            float px = this.panelX();
            float py = this.panelY();
            this.settings.toggle(m, px + 117.0F + 150.0F, py + 40.0F, 250.0F, this.vw(), this.vh());
        }
    }

    public void resetForTest() {
        this.bind.close();
        this.settings.close();
        this.search.setText("");
        this.search.blur();
    }

    public SettingsPopup settingsPopup() {
        return this.settings;
    }

    public SearchField searchField() {
        return this.search;
    }

    public void openBindFor(String name) {
        ClientModule m = ModuleRegistry.byName(name);
        if (m != null) {
            this.bind.open(m, this.panelX() + 117.0F + 120.0F, this.panelY() + 80.0F);
        }
    }

    public ThemesGrid themesForTest() {
        return this.themes;
    }

    public void selectForTest(String view) {
        this.select(ClickGuiScreen.View.valueOf(view.toUpperCase(Locale.ROOT)));
        this.content = this.target;
        this.categoryT = 1.0F;
    }

    private float scale() {
        float fit = Math.min((this.width - 16.0F) / 430.0F, (this.height - 16.0F) / 290.0F);
        return Math.min(Theme.unitScale(), fit);
    }

    private float vw() {
        return this.width / this.scale();
    }

    private float vh() {
        return this.height / this.scale();
    }

    private float panelX() {
        return (this.vw() - 430.0F) / 2.0F;
    }

    private float panelY() {
        return (this.vh() - 290.0F) / 2.0F;
    }

    private static ClickGuiScreen.View viewOf(Category c) {
        return switch (c) {
            case VISUALS -> ClickGuiScreen.View.VISUALS;
            case DISPLAY -> ClickGuiScreen.View.DISPLAY;
            case UTILS -> ClickGuiScreen.View.UTILS;
        };
    }

    private static Category categoryOf(ClickGuiScreen.View v) {
        return switch (v) {
            case VISUALS -> Category.VISUALS;
            case DISPLAY -> Category.DISPLAY;
            case UTILS -> Category.UTILS;
            default -> null;
        };
    }

    private boolean isModuleView() {
        return categoryOf(this.content) != null;
    }

    private Anim anim(ClickGuiScreen.View v) {
        return this.categoryAnims.computeIfAbsent(v, k -> new Anim(200));
    }

    @Override
    protected void draw(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        this.dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
        this.lastNs = now;
        ClientAccent.beginFrame();
        float open = this.openAnim.get();
        if (this.closing && this.openAnim.done()) {
            Backdrop.release();
            Minecraft.getInstance().setScreen(null);
        } else {
            Backdrop.capture(ctx, delta);
            float s = this.scale();
            this.mx = mouseX / s;
            this.my = mouseY / s;
            float alpha = open;
            ctx.pose().pushPose();
            ctx.pose().translate(0.0F, 0.0F, 200.0F);
            ctx.pose().scale(s, s, 1.0F);
            R2D.rect(ctx, -5.0F, -5.0F, this.vw() + 10.0F, this.vh() + 10.0F, 0.0F, Colors.rgba(0, 0, 0, 60.0F * alpha));
            float px = this.panelX();
            float py = this.panelY();
            float motion = this.closing ? 0.96F + 0.04F * open : 0.9F + 0.1F * Anim.easeOutBack(Math.min(1.0F, this.openAnim.linear() * 1.05F));
            ctx.pose().pushPose();
            ctx.pose().translate(px + 215.0F, py + 145.0F, 0.0F);
            ctx.pose().scale(motion, motion, 1.0F);
            ctx.pose().translate(-(px + 215.0F), -(py + 145.0F), 0.0F);
            this.renderPanel(ctx, px, py, alpha);
            ctx.pose().popPose();
            ctx.pose().popPose();
            Backdrop.release();
        }
    }

    private void renderPanel(GuiGraphics c, float px, float py, float a) {
        float r = ModSettings.rectCornerRadius + 6.0F;
        R2D.glass(c, px, py, 430.0F, 290.0F, r, r, r, r, a, true);
        Ui.panelBg(c, px + 5.0F, py + 5.0F, 110.0F, 280.0F, 12.0F, 0.0F, 0.0F, 12.0F, a);
        this.themesRowT = Anim.follow(this.themesRowT, ClientAccent.isThemes() ? 1.0F : 0.0F, this.dt, 10.0F);
        if (this.themesRowT < 0.5F && this.target == ClickGuiScreen.View.THEMES) {
            this.select(ClickGuiScreen.View.NONE);
        }

        this.renderSidebar(c, px + 5.0F, py + 2.0F, a);
        Ui.panelBg(c, px + 117.0F, py + 5.0F, 308.0F, 280.0F, 0.0F, 12.0F, 12.0F, 0.0F, a);
        this.updateCrossfade();
        boolean interactive = !this.closing && !this.settings.isVisible() && !this.bind.isVisible();
        float contentA = a * this.categoryT;
        float appear = this.closing ? 1.0F : this.categoryT;
        float areaX = px + 117.0F;
        float areaW = 308.0F;
        if (contentA > 0.01F && this.content != ClickGuiScreen.View.NONE) {
            if (this.content == ClickGuiScreen.View.THEMES) {
                this.themes
                    .render(
                        c,
                        areaX,
                        py + 5.0F,
                        areaW,
                        280.0F,
                        contentA,
                        appear,
                        this.dt,
                        this.mx,
                        this.my,
                        interactive,
                        px + 430.0F,
                        ModSettings.rectCornerRadius + 6.0F
                    );
            } else {
                List<ClientModule> list = this.filtered();
                this.cards
                    .render(
                        c,
                        areaX,
                        py + 5.0F + 30.0F,
                        areaW,
                        250.0F,
                        contentA,
                        appear,
                        this.dt,
                        this.search.hasText() ? "search:" + this.search.getText() : this.content,
                        list,
                        this.mx,
                        this.my,
                        interactive,
                        this.settings.isOpen() ? this.settings.module() : null,
                        px + 430.0F,
                        ModSettings.rectCornerRadius + 6.0F
                    );
                if (list.isEmpty() && this.search.hasText()) {
                    String none = "Nothing found";
                    float w = Ui.width(none, 6.5F);
                    Ui.text(c, none, areaX + (areaW - w) / 2.0F, py + 5.0F + 30.0F + 125.0F - 3.0F, 6.5F, Ui.white(110.0F * contentA));
                }
            }
        }

        float header = this.modulesHeader.forwards(this.isModuleView() || categoryOf(this.target) != null).get();
        if (this.isModuleView() && header > 0.004F) {
            float ha = a * header;
            Ui.panelBg(c, areaX, py + 5.0F, areaW, 26.0F, 0.0F, 12.0F, 0.0F, 0.0F, ha);
            Clip.push(c, areaX, py + 5.0F, areaX + areaW, py + 31.0F);
            this.renderHeader(c, areaX, py + 5.0F, areaW, ha);
            Clip.pop(c);
        }

        float ph = this.placeholder.forwards(this.content == ClickGuiScreen.View.NONE && this.target == ClickGuiScreen.View.NONE).get();
        if (this.content == ClickGuiScreen.View.NONE && this.target == ClickGuiScreen.View.NONE && ph > 0.01F) {
            float size = 60.0F;
            float gx = areaX + (areaW - size) / 2.0F;
            float gy = py + 5.0F + (280.0F - size) / 2.0F - 10.0F;
            this.cat.draw(c, gx, gy, size, size, 8.0F, a * ph);
            String hint = "Open a category to start";
            float w = Ui.width(hint, 6.0F);
            Ui.text(c, hint, areaX + (areaW - w) / 2.0F, gy + size + 8.0F, 6.0F, Ui.white(100.0F * a * ph));
        }

        if (this.settings.isVisible()) {
            this.settings.render(c, a, this.mx, this.my);
        }

        if (this.bind.isVisible()) {
            this.bind.render(c, a, this.mx, this.my);
        }
    }

    private void renderSidebar(GuiGraphics c, float x, float y, float a) {
        float top = y + 3.0F;
        float headH = 26.0F;
        Ui.panelBg(c, x, top, 110.0F, headH, 12.0F, 0.0F, 0.0F, 0.0F, a);
        float cy = top + headH / 2.0F;
        float emblem = 16.0F;
        float gap = 5.0F;
        float wordSize = 11.0F;
        String brand = "SKYSONG";
        float wordW = R2D.width(MsdfFont.Font.PIXEL, brand, wordSize);
        float bx = x + (110.0F - (emblem + gap + wordW)) / 2.0F;
        Icons.logo(c, bx, cy - emblem / 2.0F, emblem, a);
        float[] bb = R2D.bounds(MsdfFont.Font.PIXEL, brand, wordSize);
        R2D.text(c, MsdfFont.Font.PIXEL, brand, bx + emblem + gap, cy - (bb[1] + bb[3]) / 2.0F, wordSize, Ui.white(255.0F * a));
        float groupTop = y + 34.0F;
        float groupCy = groupTop + 10.0F;
        float h = this.modulesHeader.get();
        boolean mainSelected = categoryOf(this.target) != null;
        if (h > 0.004F && mainSelected) {
            R2D.rect(c, x + 4.0F, groupTop + 4.5F, 102.0F, 13.333F, 5.0F, Ui.white(16.0F * h * a));
        }

        float iconX = x + 9.0F;
        float listIconW = R2D.width(MsdfFont.Font.ICONS, "h", 8.0F);
        R2D.accentIcon(c, "h", iconX, groupCy - 4.0F + 1.5F - 0.64F, 8.0F, (200.0F + 55.0F * h) * a, 0.15F);
        Ui.text(c, "Modules", iconX + listIconW + 6.0F, groupCy - 4.0F + 0.5F - 1.2F, 8.0F, Ui.white((215.0F + 40.0F * h) * a));
        float rowsTop = groupTop + 24.0F;
        float lineX = iconX + 3.0F;
        float rowX = lineX + 9.0F;
        R2D.rect(c, lineX, rowsTop + 9.0F, 1.0F, (MAIN.length - 1) * 18.0F, 0.0F, Ui.white(36.0F * a));

        for (int i = 0; i < MAIN.length; i++) {
            Category cat = MAIN[i];
            float rowCy = rowsTop + i * 18.0F + 9.0F;
            float t = this.anim(viewOf(cat)).forwards(this.target == viewOf(cat)).get();
            int alpha = Math.min(255, 140 + Math.round(t * 115.0F));
            float iw = R2D.width(MsdfFont.Font.ICONS, cat.icon, 7.0F);
            if (t > 0.01F) {
                float uw = (iw + 5.0F + Ui.width(cat.title, 7.0F)) * t;
                R2D.accentFillHorizontal(c, rowX, rowCy + 8.5F - 2.0F, uw, 0.75F, 0.375F, 88.0F * t * a);
            }

            R2D.accentIcon(c, cat.icon, rowX, rowCy - 3.5F + 1.5F - 2.0F - 0.5F, 7.0F, alpha * a, MAIN.length > 1 ? i / 3.0F : 0.5F);
            Ui.text(c, cat.title, rowX + iw + 5.0F, rowCy - 3.5F + 0.5F - 2.0F - 1.0F, 7.0F, Ui.white(alpha * a));
            String count = String.valueOf(ModuleRegistry.countIn(cat));
            float cw = Ui.width(count, 5.5F);
            Ui.text(c, count, x + 110.0F - 10.0F - cw, rowCy - 2.75F + 0.5F - 2.0F - 0.8F, 5.5F, Ui.white((80.0F + 80.0F * t) * a));
        }

        float themesTop = rowsTop + MAIN.length * 18.0F + 12.0F;
        if (this.themesRowT > 0.01F) {
            float rowCy = themesTop + 9.5F;
            float t = this.anim(ClickGuiScreen.View.THEMES).forwards(this.target == ClickGuiScreen.View.THEMES).get();
            float rowA = Math.round(Math.min(255, 140 + Math.round(t * 115.0F)) * this.themesRowT);
            float iw = R2D.width(MsdfFont.Font.ICONS, "B", 7.5F);
            R2D.accentIcon(c, "B", iconX, rowCy - 3.75F + 1.5F - 2.0F - 0.5F, 7.5F, rowA * a, 0.85F);
            Ui.text(c, "Themes", iconX + iw + 6.0F, rowCy - 3.5F + 0.5F - 2.0F - 1.0F, 7.0F, Ui.white(rowA * a));
        }
    }

    private void renderHeader(GuiGraphics c, float x, float y, float w, float a) {
        float pad = 6.0F;
        float searchH = 14.0F;
        float rowH = 26.0F;
        float avatar = 18.0F;
        float ax = x + w - pad - avatar;
        float ay = y + (rowH - avatar) / 2.0F;
        this.drawAvatar(c, ax, ay, avatar, a);
        Minecraft mc = Minecraft.getInstance();
        String name = mc.getUser().getName();
        SpotifyManager spotify = SkysongClient.getSpotifyManager();
        String role = spotify != null && spotify.isPlaying() && !spotify.getCurrentTrackName().isEmpty() ? spotify.getCurrentTrackName() : "Skysong";
        role = trim(role, 90.0F, 5.0F);
        float nameW = Ui.width(name, 6.5F);
        float roleW = Ui.width(role, 5.0F);
        float right = ax - 5.0F;
        float ty = ay + (avatar - 12.5F) / 2.0F - 0.8F;
        Ui.text(c, name, right - nameW, ty, 6.5F, Ui.white(230.0F * a));
        Ui.text(c, role, right - roleW, ty + 6.5F + 1.0F, 5.0F, ClientAccent.accentSoft(195.0F * a));
        float left = x + pad;
        float searchW = Math.max(60.0F, right - Math.max(nameW, roleW) - pad - left);
        this.search.render(c, left, y + (rowH - searchH) / 2.0F, searchW, searchH, a, this.mx, this.my, this.dt);
    }

    private static String trim(String s, float max, float size) {
        if (Ui.width(s, size) <= max) {
            return s;
        }

        while (s.length() > 1 && Ui.width(s + "…", size) > max) {
            s = s.substring(0, s.length() - 1);
        }

        return s + "…";
    }

    private void drawAvatar(GuiGraphics c, float x, float y, float size, float a) {
        Minecraft mc = Minecraft.getInstance();
        Identifier skin = Compat.ownSkin();
        int white = Math.round(255.0F * a) << 24 | 16777215;
        if (skin == null) {
            R2D.rect(c, x, y, size, size, size / 2.0F, Colors.rgba(0, 0, 0, 90.0F * a));
            R2D.outline(c, x, y, size, size, size / 2.0F, 0.8F, Ui.white(30.0F * a));
        } else {
            R2D.image(c, skin, x, y, size, size, size / 2.0F, 0.125F, 0.125F, 0.25F, 0.25F, white, false);
            R2D.image(c, skin, x, y, size, size, size / 2.0F, 0.625F, 0.125F, 0.75F, 0.25F, white, false);
        }
    }

    private void updateCrossfade() {
        if (this.target == this.content) {
            if (this.content != ClickGuiScreen.View.NONE && this.categoryT < 1.0F) {
                this.categoryT = Math.min(1.0F, this.categoryT + this.dt / 0.15F);
            }
        } else {
            if (this.content == ClickGuiScreen.View.NONE) {
                this.swapContent();
                this.categoryT = 0.0F;
            } else {
                this.categoryT = Math.max(0.0F, this.categoryT - this.dt / 0.15F);
                if (this.categoryT <= 0.0F) {
                    this.swapContent();
                }
            }
        }
    }

    private void swapContent() {
        this.content = this.target;
        this.cards.resetScroll();
        if (this.content == ClickGuiScreen.View.THEMES) {
            this.themes.open();
        }
    }

    private void select(ClickGuiScreen.View v) {
        ClickGuiScreen.View next = v == this.target ? ClickGuiScreen.View.NONE : v;
        if (next != this.target) {
            this.search.setText("");
            this.search.blur();
            this.settings.close();
            this.target = next;
            ModSettings.guiLastView = next.ordinal();
        }
    }

    private List<ClientModule> filtered() {
        String q = this.search.getText().trim().toLowerCase(Locale.ROOT);
        Category cat = categoryOf(this.content);
        if (q.isEmpty()) {
            return cat == null ? List.of() : ModuleRegistry.in(cat);
        }

        String qEn = layout(q);
        String qs = q.replace(" ", "");
        String qEns = qEn.replace(" ", "");
        List<ClientModule> out = new ArrayList<>();

        for (ClientModule m : ModuleRegistry.ALL) {
            String n = m.name().toLowerCase(Locale.ROOT);
            String ns = n.replace(" ", "");
            if (n.contains(q) || n.contains(qEn) || ns.contains(qs) || ns.contains(qEns)) {
                out.add(m);
            }
        }

        return out;
    }

    private static String layout(String s) {
        StringBuilder sb = new StringBuilder(s.length());

        for (char ch : s.toCharArray()) {
            int i = "йцукенгшщзхъфывапролджэячсмитьбюё".indexOf(ch);
            sb.append(i >= 0 ? "qwertyuiop[]asdfghjkl;'zxcvbnm,.`".charAt(i) : ch);
        }

        return sb.toString();
    }

    private ClientModule cardAt(float x, float y) {
        if (!this.isModuleView()) {
            return null;
        }

        float px = this.panelX();
        float py = this.panelY();
        float areaX = px + 117.0F;
        float areaY = py + 5.0F + 30.0F;
        float areaW = 308.0F;
        if (!Ui.inside(x, y, areaX, areaY, areaW, 250.0F)) {
            return null;
        }

        List<ClientModule> list = this.filtered();

        for (int i = 0; i < list.size(); i++) {
            float[] r = this.cards.cardRect(list, i, areaX, areaY, areaW);
            if (r != null && Ui.inside(x, y, r[0], r[1], r[2], r[3])) {
                return list.get(i);
            }
        }

        return null;
    }

    @Override
    public boolean onClick(double mouseX, double mouseY, int button) {
        if (!this.closing && !(this.openAnim.linear() < 0.3F)) {
            float s = this.scale();
            float x = (float)(mouseX / s);
            float y = (float)(mouseY / s);
            if (this.bind.isOpen() && this.bind.mouseBind(button)) {
                return true;
            }

            if (this.settings.isOpen() && this.settings.mouseBind(button)) {
                return true;
            }

            if (this.bind.isOpen() && button == 0) {
                this.bind.click(x, y);
                return true;
            }

            if (button == 2) {
                if (this.settings.isOpen() && this.settings.middleClick(x, y)) {
                    return true;
                }

                ClientModule m = this.cardAt(x, y);
                if (m != null) {
                    this.bind.open(m, x, y);
                }

                return true;
            } else {
                if (this.settings.isOpen() && button == 0) {
                    this.settings.click(x, y);
                    return true;
                }

                if (this.settings.isOpen() && button == 1 && this.settings.contains(x, y)) {
                    return true;
                }

                if (this.isModuleView() && this.search.mouseClicked(x, y, button)) {
                    return true;
                }

                float px = this.panelX();
                float py = this.panelY();
                if (this.content == ClickGuiScreen.View.THEMES && button == 0 && this.themes.scrollbarGrab(x, y)) {
                    return true;
                }

                if (this.content == ClickGuiScreen.View.THEMES && button == 0 && this.themes.click(px + 117.0F, py + 5.0F, 308.0F, 280.0F, x, y)) {
                    return true;
                }

                if (this.isModuleView() && (button == 0 || button == 1)) {
                    if (this.cards.scrollbarGrab(x, y)) {
                        return true;
                    }

                    float areaX = px + 117.0F;
                    float areaY = py + 5.0F + 30.0F;
                    float areaW = 308.0F;
                    List<ClientModule> list = this.filtered();

                    for (int i = 0; i < list.size(); i++) {
                        float[] r = this.cards.cardRect(list, i, areaX, areaY, areaW);
                        if (r != null && Ui.inside(x, y, r[0], r[1], r[2], r[3]) && Ui.inside(x, y, areaX, areaY, areaW, 250.0F)) {
                            ClientModule m = list.get(i);
                            if (button == 1 || this.cards.hitGear(m, x, y, r[0], r[1], r[2]) || !m.hasToggle() && m.hasSettings()) {
                                if (m.hasSettings()) {
                                    boolean opened = this.settings.toggle(m, x, y, 250.0F, this.vw(), this.vh());
                                    Sounds.play(opened ? "module_settings_open" : "module_settings_close");
                                } else if (button == 1) {
                                    Sounds.play("no_settings");
                                }

                                return true;
                            }

                            if (this.search.hasText() && Compat.controlDown()) {
                                this.cards.focus(m.name());
                                ClickGuiScreen.View v = viewOf(m.category());
                                if (this.target != v) {
                                    Sounds.play("select_category");
                                    this.select(v);
                                } else {
                                    this.search.setText("");
                                    this.search.blur();
                                }

                                return true;
                            }

                            if (m.hasToggle()) {
                                m.toggle();
                                Sounds.play(m.isOn() ? "module_on" : "module_off", 0.7F);
                            }

                            return true;
                        }
                    }
                }

                if (button != 0) {
                    return true;
                }

                ClickGuiScreen.View hit = this.sidebarAt(x, y);
                if (hit != null) {
                    if (hit != this.target) {
                        Sounds.play("select_category");
                    }

                    this.select(hit);
                    return true;
                } else {
                    return super.onClick(mouseX, mouseY, button);
                }
            }
        } else {
            return true;
        }
    }

    private ClickGuiScreen.View sidebarAt(float x, float y) {
        float sx = this.panelX() + 5.0F;
        float left = sx + 4.0F;
        float right = sx + 110.0F - 4.0F;
        if (!(x < left) && !(x > right)) {
            float rowsTop = this.panelY() + 2.0F + 34.0F + 24.0F;

            for (int i = 0; i < MAIN.length; i++) {
                float top = rowsTop + i * 18.0F;
                if (y >= top && y <= top + 18.0F) {
                    return viewOf(MAIN[i]);
                }
            }

            float themesTop = rowsTop + MAIN.length * 18.0F + 12.0F;
            return this.themesRowT >= 0.5F && y >= themesTop && y <= themesTop + 19.0F ? ClickGuiScreen.View.THEMES : null;
        } else {
            return null;
        }
    }

    @Override
    public boolean onRelease(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.settings.releaseDrags();
            this.bind.releaseDrag();
        }

        this.cards.scrollbarRelease();
        this.themes.scrollbarRelease();
        this.search.mouseReleased();
        return super.onRelease(mouseX, mouseY, button);
    }

    static double unscaledScroll(double amount) {
        double sensitivity = Minecraft.getInstance().options.mouseWheelSensitivity().get();
        return sensitivity > 0.001 ? amount / sensitivity : amount;
    }

    @Override
    protected boolean scrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (this.closing) {
            return true;
        }

        vertical = unscaledScroll(vertical);
        float s = this.scale();
        float x = (float)(mouseX / s);
        float y = (float)(mouseY / s);
        if (this.bind.isOpen()) {
            this.bind.close();
            return true;
        }

        if (this.settings.isOpen() && this.settings.scroll(x, y, vertical)) {
            return true;
        }

        float px = this.panelX();
        float py = this.panelY();
        if (Ui.inside(x, y, px + 117.0F, py + 5.0F, 308.0F, 280.0F)) {
            if (this.content == ClickGuiScreen.View.THEMES) {
                this.themes.scroll(vertical, 280.0F);
            } else {
                this.settings.close();
                this.cards.scroll(vertical, 250.0F);
            }

            return true;
        } else {
            return this.scrolledVanilla(mouseX, mouseY, horizontal, vertical);
        }
    }

    @Override
    public boolean onKey(int key, int scanCode, int mods) {
        if (this.closing) {
            if (key == Binds.key("Click GUI") && this.menuKeyReleased) {
                this.menuKeyReleased = false;
                this.closing = false;
                this.openAnim.forwards(true);
                Sounds.play("open");
            }

            return true;
        } else if (this.bind.isOpen()) {
            if (this.bind.keyPressed(key)) {
                return true;
            }

            if (key == 256) {
                this.bind.close();
            }

            return true;
        } else {
            if (this.settings.keyPressed(key, mods)) {
                return true;
            }

            if (key == 256 && this.settings.hasOpenOverlay()) {
                this.settings.closeOverlays();
                return true;
            }

            if (key == 256 && this.settings.isOpen()) {
                this.settings.close();
                Sounds.play("module_settings_close");
                return true;
            }

            if (this.search.isTyping() && this.search.keyPressed(key, mods)) {
                return true;
            }

            if (key == Binds.key("Click GUI")) {
                if (this.menuKeyReleased) {
                    this.menuKeyReleased = false;
                    this.onClose();
                }

                return true;
            } else if (key == 256) {
                this.onClose();
                return true;
            } else {
                return super.onKey(key, scanCode, mods);
            }
        }
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

        if (this.settings.charTyped(chr)) {
            return true;
        }

        if (this.isModuleView() && !this.search.isTyping() && Character.isLetterOrDigit(chr)) {
            this.search.focus();
        }

        return this.search.isTyping() && this.search.charTyped(chr) ? true : super.onChar(chr, modifiers);
    }

    private enum View {
        NONE,
        VISUALS,
        DISPLAY,
        UTILS,
        THEMES
    }
}
