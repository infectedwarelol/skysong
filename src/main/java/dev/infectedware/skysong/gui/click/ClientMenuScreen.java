package dev.infectedware.skysong.gui.click;

import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.Profiles;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
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
import dev.infectedware.skysong.theme.SavedThemes;
import dev.infectedware.skysong.ui.Theme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;

public class ClientMenuScreen extends VScreen {
    private static final float MIN_VIRTUAL_W = 600.0F;
    private static final float MIN_VIRTUAL_H = 320.0F;
    private static final float MAX_W = 640.0F;
    private static final float MAX_H = 380.0F;
    private static final float EDGE = 12.0F;
    private static final float TITLE_H = 18.0F;
    private static final float PAD = 5.0F;
    private static final float RAIL_W = 78.0F;
    private static final float RAIL_ROW_H = 18.0F;
    private static final float TAB_H = 14.0F;
    private static final float TAB_GAP = 3.0F;
    private static final float CHECK = 7.0F;
    private static final float SEARCH_W = 130.0F;
    private static final float THEME_BUTTON_W = 96.0F;
    private static final String MENU_MODULE = "Click GUI";
    private static final String PROFILES_MODULE = "Config";
    private static final String VERSION = versionLabel();
    private static final Map<Item, ItemStack> STACKS = new HashMap<>();
    private static final Map<View, String> LAST_MODULE = new EnumMap<>(View.class);
    private static View lastView = View.VISUALS;
    private final PanelGrid grid = new PanelGrid();
    private final ThemesGrid themes = new ThemesGrid();
    private final SearchField search = new SearchField();
    private final BindPopup bind = new BindPopup();
    private final Anim openAnim = new Anim(220);
    private View view = lastView;
    private String selected;
    private boolean closing;
    private boolean menuKeyReleased;
    private long lastNs = System.nanoTime();
    private float dt;
    private float mx;
    private float my;

    private enum View {
        VISUALS("Visuals", Items.ENDER_EYE, Category.VISUALS),
        DISPLAY("Display", Items.PAINTING, Category.DISPLAY),
        UTILS("Utils", Items.COMPARATOR, Category.UTILS),
        THEMES("Themes", Items.GLOW_INK_SAC, null),
        PROFILES("Profiles", Items.WRITABLE_BOOK, null);

        final String title;
        final Item icon;
        final Category category;

        View(String title, Item icon, Category category) {
            this.title = title;
            this.icon = icon;
            this.category = category;
        }
    }

    private record Tab(ClientModule module, float x, float y, float w) {
    }

    public ClientMenuScreen() {
        super(Component.literal("Skysong"));
    }

    public static Screen create() {
        if (ModSettings.classicMenu) {
            return ClickGuiScreen.shared();
        }

        return ModSettings.liveEditorMenu ? new LiveEditorScreen() : new ClientMenuScreen();
    }

    private static String versionLabel() {
        String raw = FabricLoader.getInstance().getModContainer("skysong").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        int split = raw.indexOf("+mc");
        return split < 0 ? "v" + raw : "v" + raw.substring(0, split) + " · mc" + raw.substring(split + 3);
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
        this.menuKeyReleased = InputEvents.isUp(Binds.key(MENU_MODULE));
        this.openView(this.view);
    }

    @Override
    public void onClose() {
        if (!this.closing) {
            this.closing = true;
            this.openAnim.forwards(false);
            this.bind.close();
            this.grid.closeOverlays();
            this.search.blur();
            Sounds.play("close");
            ModConfig.save();
        }
    }

    @Override
    public void removed() {
        Backdrop.release();
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

    private float winW() {
        return Math.min(MAX_W, this.vw() - EDGE * 2.0F);
    }

    private float winH() {
        return Math.min(MAX_H, this.vh() - EDGE * 2.0F);
    }

    private float winX() {
        return (this.vw() - this.winW()) / 2.0F;
    }

    private float winY() {
        return (this.vh() - this.winH()) / 2.0F;
    }

    private float innerX() {
        return this.winX() + PAD;
    }

    private float innerY() {
        return this.winY() + TITLE_H + 2.0F;
    }

    private float innerW() {
        return this.winW() - PAD * 2.0F;
    }

    private float innerH() {
        return this.winH() - TITLE_H - 2.0F - PAD;
    }

    private float mainX() {
        return this.innerX() + PAD + RAIL_W + PAD;
    }

    private float mainW() {
        return this.innerX() + this.innerW() - PAD - this.mainX();
    }

    private float mainTop() {
        return this.innerY() + PAD;
    }

    private float mainBottom() {
        return this.innerY() + this.innerH() - PAD;
    }

    private String query() {
        return this.search.getText().trim().toLowerCase(Locale.ROOT);
    }

    private List<ClientModule> modules() {
        List<ClientModule> out = new ArrayList<>();
        String query = this.query();
        if (!query.isEmpty()) {
            for (ClientModule m : ModuleRegistry.ALL) {
                if (!MENU_MODULE.equals(m.name())
                    && (m.name().toLowerCase(Locale.ROOT).contains(query) || m.description().toLowerCase(Locale.ROOT).contains(query))) {
                    out.add(m);
                }
            }

            return out;
        }

        if (this.view == View.PROFILES) {
            ClientModule config = ModuleRegistry.byName(PROFILES_MODULE);
            if (config != null) {
                out.add(config);
            }
        } else if (this.view.category != null) {
            for (ClientModule m : ModuleRegistry.in(this.view.category)) {
                if (!MENU_MODULE.equals(m.name())) {
                    out.add(m);
                }
            }
        }

        return out;
    }

    private boolean themesShown() {
        return this.view == View.THEMES && this.query().isEmpty();
    }

    private void openView(View v) {
        this.view = v;
        lastView = v;
        if (v == View.THEMES) {
            this.selected = null;
            this.grid.show(null);
            this.themes.open();
            return;
        }

        List<ClientModule> list = this.modules();
        String remembered = LAST_MODULE.get(v);
        ClientModule pick = remembered == null ? null : ModuleRegistry.byName(remembered);
        if (pick == null || !list.contains(pick)) {
            pick = list.isEmpty() ? null : list.getFirst();
        }

        this.select(pick);
    }

    private void select(ClientModule m) {
        this.selected = m == null ? null : m.name();
        if (m != null && this.query().isEmpty()) {
            LAST_MODULE.put(this.view, m.name());
        }

        this.grid.show(m);
    }

    private void syncSelectionWithSearch() {
        if (this.themesShown()) {
            return;
        }

        List<ClientModule> list = this.modules();
        ClientModule current = this.selected == null ? null : ModuleRegistry.byName(this.selected);
        if (current == null || !list.contains(current)) {
            this.select(list.isEmpty() ? null : list.getFirst());
        }
    }

    private static float tabWidth(ClientModule m) {
        return 4.0F + 9.0F + 3.0F + Ui.width(m.name(), 6.0F) + (m.hasToggle() ? 4.0F + CHECK : 0.0F) + 5.0F;
    }

    private List<Tab> tabs() {
        List<Tab> out = new ArrayList<>();
        float left = this.mainX();
        float right = left + this.mainW();
        float x = left;
        float y = this.mainTop();

        for (ClientModule m : this.modules()) {
            float w = Math.min(tabWidth(m), right - left);
            if (x > left && x + w > right) {
                x = left;
                y += TAB_H + TAB_GAP;
            }

            out.add(new Tab(m, x, y, w));
            x += w + TAB_GAP;
        }

        return out;
    }

    private float tabsBottom(List<Tab> tabs) {
        return tabs.isEmpty() ? this.mainTop() : tabs.getLast().y() + TAB_H;
    }

    private float contentTop(List<Tab> tabs) {
        if (this.themesShown()) {
            return this.mainTop() + TAB_H + 6.0F;
        }

        ClientModule m = this.selected == null ? null : ModuleRegistry.byName(this.selected);
        float top = this.tabsBottom(tabs) + 5.0F;
        return m != null && !m.description().isEmpty() ? top + 10.0F : top;
    }

    @Override
    protected void draw(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        this.dt = Math.min(0.1F, (now - this.lastNs) / 1.0E9F);
        this.lastNs = now;
        ClientAccent.beginFrame();
        if (this.closing && this.openAnim.done()) {
            Backdrop.release();
            Minecraft.getInstance().setScreen(null);
            return;
        }

        Backdrop.capture();
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
            R2D.rect(ctx, -5.0F, -5.0F, this.vw() + 10.0F, this.vh() + 10.0F, 0.0F, Colors.rgba(0, 0, 0, 70.0F * a));
            float motion = 0.97F + 0.03F * a;
            float cx = this.vw() / 2.0F;
            float cy = this.vh() / 2.0F;
            ctx.pose().pushPose();
            ctx.pose().translate(cx, cy, 0.0F);
            ctx.pose().scale(motion, motion, 1.0F);
            ctx.pose().translate(-cx, -cy, 0.0F);
            this.drawWindow(ctx, a);
            ctx.pose().popPose();
            if (this.bind.isVisible()) {
                this.bind.render(ctx, a, this.mx, this.my);
            }
        } finally {
            ctx.pose().popPose();
            R2D.squareCorners = previous;
            Backdrop.release();
        }
    }

    private void drawWindow(GuiGraphics c, float a) {
        float wx = this.winX();
        float wy = this.winY();
        float ww = this.winW();
        float wh = this.winH();
        R2D.glass(c, wx, wy, ww, wh, 0.0F, 0.0F, 0.0F, 0.0F, a, true);
        R2D.rect(c, wx, wy, ww, wh, 0.0F, Colors.rgba(12, 10, 20, 110.0F * a));
        R2D.outline(c, wx, wy, ww, wh, 0.0F, 0.6F, Ui.white(34.0F * a));
        this.drawTitleBar(c, wx, wy, ww, a);
        float ix = this.innerX();
        float iy = this.innerY();
        R2D.rect(c, ix, iy, this.innerW(), this.innerH(), 0.0F, Colors.rgba(0, 0, 0, 55.0F * a));
        R2D.outline(c, ix, iy, this.innerW(), this.innerH(), 0.0F, 0.5F, Ui.white(20.0F * a));
        this.drawRail(c, a);
        if (this.themesShown()) {
            this.drawThemes(c, a);
        } else {
            this.drawModuleView(c, a);
        }
    }

    private void drawTitleBar(GuiGraphics c, float wx, float wy, float ww, float a) {
        float cy = wy + TITLE_H / 2.0F;
        Icons.logo(c, wx + 7.0F, cy - 5.0F, 10.0F, a);
        float[] bb = R2D.bounds(MsdfFont.Font.PIXEL, "SKYSONG", 8.0F);
        R2D.text(c, MsdfFont.Font.PIXEL, "SKYSONG", wx + 21.0F, cy - (bb[1] + bb[3]) / 2.0F, 8.0F, Ui.white(250.0F * a));
        float vx = wx + 21.0F + R2D.width(MsdfFont.Font.PIXEL, "SKYSONG", 8.0F) + 6.0F;
        Ui.text(c, "| " + VERSION, vx, cy - 3.2F, 6.0F, Ui.white(140.0F * a));
        this.search.render(c, wx + ww - PAD - SEARCH_W, cy - 6.0F, SEARCH_W, 12.0F, a, this.mx, this.my, this.dt);
    }

    private float railY(int index) {
        return this.innerY() + PAD + index * (RAIL_ROW_H + 3.0F);
    }

    private void drawRail(GuiGraphics c, float a) {
        float x = this.innerX() + PAD;
        boolean searching = !this.query().isEmpty();
        View[] views = View.values();

        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            float y = this.railY(i);
            boolean active = !searching && v == this.view;
            boolean hover = Ui.inside(this.mx, this.my, x, y, RAIL_W, RAIL_ROW_H);
            if (active) {
                R2D.rect(c, x, y, RAIL_W, RAIL_ROW_H, 0.0F, ClientAccent.accent(46.0F * a));
                R2D.outline(c, x, y, RAIL_W, RAIL_ROW_H, 0.0F, 0.6F, ClientAccent.accentBright(170.0F * a));
                R2D.rect(c, x, y, 1.5F, RAIL_ROW_H, 0.0F, ClientAccent.accentBright(255.0F * a));
            } else {
                R2D.rect(c, x, y, RAIL_W, RAIL_ROW_H, 0.0F, Colors.rgba(0, 0, 0, (hover ? 90.0F : 60.0F) * a));
                R2D.outline(c, x, y, RAIL_W, RAIL_ROW_H, 0.0F, 0.5F, Ui.white((hover ? 34.0F : 18.0F) * a));
            }

            drawItem(c, v.icon, x + 5.0F, y + 4.0F, 10.0F);
            Ui.text(c, v.title, x + 20.0F, y + 5.5F, 6.5F, Ui.white((active ? 255.0F : 190.0F) * a));
        }

        float hintY = this.mainBottom() - 15.0F;
        Ui.text(c, "Right click: on/off", x + 2.0F, hintY, 5.0F, Ui.white(110.0F * a));
        Ui.text(c, "Middle click: keybind", x + 2.0F, hintY + 8.0F, 5.0F, Ui.white(110.0F * a));
    }

    private void drawModuleView(GuiGraphics c, float a) {
        List<Tab> tabs = this.tabs();
        for (Tab t : tabs) {
            this.drawTab(c, t, a);
        }

        float x = this.mainX();
        float w = this.mainW();
        ClientModule m = this.selected == null ? null : ModuleRegistry.byName(this.selected);
        float top = this.tabsBottom(tabs) + 5.0F;
        if (m == null) {
            Ui.text(c, "Nothing matches your search.", x + 2.0F, top + 2.0F, 6.0F, Ui.white(150.0F * a));
            return;
        }

        if (!m.description().isEmpty()) {
            Ui.scrollingText(c, m.description(), x + 1.0F, top, w - 2.0F, 5.5F, Ui.white(135.0F * a));
        }

        float contentTop = this.contentTop(tabs);
        float contentH = this.mainBottom() - contentTop;
        if (this.grid.isEmpty()) {
            Ui.text(c, "No settings for this module.", x + 2.0F, contentTop + 2.0F, 6.0F, Ui.white(150.0F * a));
        } else {
            this.grid.render(c, x, contentTop, w, contentH, a, this.mx, this.my);
        }
    }

    private void drawTab(GuiGraphics c, Tab t, float a) {
        ClientModule m = t.module();
        boolean active = m.name().equals(this.selected);
        boolean hover = Ui.inside(this.mx, this.my, t.x(), t.y(), t.w(), TAB_H);
        if (active) {
            R2D.rect(c, t.x(), t.y(), t.w(), TAB_H, 0.0F, ClientAccent.accent(50.0F * a));
            R2D.outline(c, t.x(), t.y(), t.w(), TAB_H, 0.0F, 0.6F, ClientAccent.accentBright(190.0F * a));
            R2D.rect(c, t.x(), t.y() + TAB_H - 1.0F, t.w(), 1.0F, 0.0F, ClientAccent.accentBright(255.0F * a));
        } else {
            R2D.rect(c, t.x(), t.y(), t.w(), TAB_H, 0.0F, Colors.rgba(0, 0, 0, (hover ? 95.0F : 65.0F) * a));
            R2D.outline(c, t.x(), t.y(), t.w(), TAB_H, 0.0F, 0.5F, Ui.white((hover ? 36.0F : 20.0F) * a));
        }

        drawItem(c, LiveEditorScreen.ICONS.getOrDefault(m.name(), Items.PAPER), t.x() + 4.0F, t.y() + 2.5F, 9.0F);
        float textRoom = t.w() - 16.0F - 5.0F - (m.hasToggle() ? 4.0F + CHECK : 0.0F);
        Ui.scrollingText(c, m.name(), t.x() + 16.0F, t.y() + 3.8F, textRoom, 6.0F, Ui.white((active ? 255.0F : 200.0F) * a));
        if (m.hasToggle()) {
            float bx = t.x() + t.w() - 5.0F - CHECK;
            float by = t.y() + (TAB_H - CHECK) / 2.0F;
            Widget.flatCheck(c, bx, by, CHECK, m.isOn() ? 1.0F : 0.0F, a, hover);
        }
    }

    private float themeButtonX() {
        return this.mainX() + this.mainW() - THEME_BUTTON_W;
    }

    private void drawThemes(GuiGraphics c, float a) {
        float x = this.mainX();
        float top = this.mainTop();
        R2D.text(c, MsdfFont.Font.SEMIBOLD, "Themes", x + 1.0F, top + 3.5F, 7.0F, Ui.white(245.0F * a));
        float bx = this.themeButtonX();
        boolean hover = Ui.inside(this.mx, this.my, bx, top, THEME_BUTTON_W, TAB_H);
        R2D.rect(c, bx, top, THEME_BUTTON_W, TAB_H, 0.0F, ClientAccent.accent((hover ? 110.0F : 75.0F) * a));
        R2D.outline(c, bx, top, THEME_BUTTON_W, TAB_H, 0.0F, 0.6F, ClientAccent.accentBright(190.0F * a));
        String label = "Save look as theme";
        Ui.text(c, label, bx + (THEME_BUTTON_W - Ui.width(label, 6.0F)) / 2.0F, top + 3.8F, 6.0F, Ui.white(250.0F * a));
        float areaTop = this.contentTop(List.of());
        this.themes.render(
            c, x, areaTop, this.mainW(), this.mainBottom() - areaTop, a, 1.0F, this.dt, this.mx, this.my, !this.bind.isOpen(), x + this.mainW(), 0.0F
        );
    }

    private static void drawItem(GuiGraphics c, Item item, float x, float y, float size) {
        ItemStack stack = STACKS.computeIfAbsent(item, ItemStack::new);
        c.pose().pushPose();
        c.pose().translate(x, y, 0.0F);
        c.pose().scale(size / 16.0F, size / 16.0F, 1.0F);
        c.renderItem(stack, 0, 0);
        c.pose().popPose();
    }

    private Tab tabAt(float x, float y) {
        if (this.themesShown()) {
            return null;
        }

        for (Tab t : this.tabs()) {
            if (Ui.inside(x, y, t.x(), t.y(), t.w(), TAB_H)) {
                return t;
            }
        }

        return null;
    }

    private View railAt(float x, float y) {
        float rx = this.innerX() + PAD;
        View[] views = View.values();

        for (int i = 0; i < views.length; i++) {
            if (Ui.inside(x, y, rx, this.railY(i), RAIL_W, RAIL_ROW_H)) {
                return views[i];
            }
        }

        return null;
    }

    private boolean overContent(float x, float y) {
        float top = this.themesShown() ? this.contentTop(List.of()) : this.contentTop(this.tabs());
        return Ui.inside(x, y, this.mainX(), top, this.mainW(), this.mainBottom() - top);
    }

    private static void toggle(ClientModule m) {
        m.toggle();
        Sounds.play(m.isOn() ? "module_on" : "module_off", 0.7F);
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

        if (this.grid.mouseBind(button)) {
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

        Tab tab = this.tabAt(x, y);
        if (button == 2) {
            if (!this.themesShown() && this.grid.middleClick(x, y)) {
                return true;
            }

            if (tab != null) {
                this.bind.open(tab.module(), x, y);
            }

            return true;
        }

        if (button == 0 && !this.themesShown() && this.grid.click(x, y)) {
            return true;
        }

        if (this.search.mouseClicked(x, y, button)) {
            return true;
        }

        if (button == 0) {
            View v = this.railAt(x, y);
            if (v != null) {
                boolean cleared = this.search.hasText();
                if (cleared) {
                    this.search.setText("");
                    this.search.blur();
                }

                if (cleared || v != this.view) {
                    this.openView(v);
                    Sounds.play("select_category");
                }

                return true;
            }
        }

        if (tab != null && (button == 0 || button == 1)) {
            ClientModule m = tab.module();
            boolean onCheck = m.hasToggle() && x >= tab.x() + tab.w() - 5.0F - CHECK - 2.0F;
            if (m.hasToggle() && (onCheck || button == 1)) {
                toggle(m);
            } else if (!m.name().equals(this.selected)) {
                this.select(m);
                Sounds.play("select_category");
            }

            return true;
        }

        if (button == 0 && this.themesShown()) {
            float top = this.mainTop();
            if (Ui.inside(x, y, this.themeButtonX(), top, THEME_BUTTON_W, TAB_H)) {
                SavedThemes.save("");
                SettingsPopup.rebuildSoon();
                Sounds.play("button");
                return true;
            }

            if (this.themes.scrollbarGrab(x, y)) {
                return true;
            }

            float areaTop = this.contentTop(List.of());
            if (this.themes.click(this.mainX(), areaTop, this.mainW(), this.mainBottom() - areaTop, x, y)) {
                return true;
            }
        }

        return super.onClick(mouseX, mouseY, button);
    }

    @Override
    public boolean onRelease(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.grid.releaseDrags();
            this.bind.releaseDrag();
        }

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

        if (this.themesShown()) {
            if (this.overContent(x, y)) {
                float areaTop = this.contentTop(List.of());
                this.themes.scroll(amount, this.mainBottom() - areaTop);
                return true;
            }

            return this.scrolledVanilla(mouseX, mouseY, horizontal, vertical);
        }

        return this.grid.scroll(x, y, amount) || this.scrolledVanilla(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean onKey(int key, int scanCode, int mods) {
        int menuKey = Binds.key(MENU_MODULE);
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

        if (this.grid.keyPressed(key)) {
            return true;
        }

        if (key == 256 && this.grid.hasOpenOverlay()) {
            this.grid.closeOverlays();
            return true;
        }

        if (this.search.isTyping() && this.search.keyPressed(key, mods)) {
            this.syncSelectionWithSearch();
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
                this.openView(this.view);
            } else {
                this.onClose();
            }

            return true;
        }

        return super.onKey(key, scanCode, mods);
    }

    @Override
    public boolean onKeyUp(int key, int scanCode, int mods) {
        if (key == Binds.key(MENU_MODULE)) {
            this.menuKeyReleased = true;
        }

        return super.onKeyUp(key, scanCode, mods);
    }

    @Override
    public boolean onChar(char chr, int modifiers) {
        if (this.closing || this.bind.isOpen()) {
            return true;
        }

        if (this.grid.charTyped(chr)) {
            return true;
        }

        if (!this.search.isTyping() && !this.grid.typing() && Character.isLetterOrDigit(chr)) {
            this.search.focus();
        }

        if (this.search.isTyping() && this.search.charTyped(chr)) {
            this.syncSelectionWithSearch();
            return true;
        }

        return super.onChar(chr, modifiers);
    }
}
