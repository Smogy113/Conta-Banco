package com.brigadajato.screens;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.Settings;
import com.brigadajato.ui.Ui;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Theme;
import com.brigadajato.world.WeaponType;

import java.util.Random;

/** Personalizacao completa do soldado, com varias formas de ver o personagem. */
public class CustomizeScreen extends BaseScreen {
    private final Settings s;
    private final Look look;
    private final SoldierPreview preview;
    private final Table content = new Table();
    private final Label bgLabel;
    private int tab = 0;
    private int weaponIdx = WeaponType.RIFLE.ordinal();

    interface Field {
        int get();

        void set(int v);
    }

    private static String[] n(String... a) {
        return a;
    }

    private static final String[][] HATS = {
            n("Capacete", "Boné", "Bandana", "Boina", "Cartola", "Moicano", "Nenhum", "Cowboy", "Gorro", "Coroa", "Viking", "Headset", "Boné virado", "Capacete espacial"),
            n("Helmet", "Cap", "Bandana", "Beret", "Top hat", "Mohawk", "None", "Cowboy", "Beanie", "Crown", "Viking", "Headset", "Backwards cap", "Space helmet")};
    private static final String[][] HAIR = {
            n("Careca", "Curto", "Espetado", "Comprido", "Black power", "Rabo de cavalo", "Topete"),
            n("Bald", "Short", "Spiky", "Long", "Afro", "Ponytail", "Quiff")};
    private static final String[][] EYES = {
            n("Normal", "Bravo", "Feliz", "Sonolento", "Robô", "Surpreso"),
            n("Normal", "Angry", "Happy", "Sleepy", "Robot", "Surprised")};
    private static final String[][] MOUTH = {
            n("Neutra", "Sorriso", "Dentes cerrados", "Grito", "Assobio", "Língua"),
            n("Neutral", "Smile", "Gritted teeth", "Shout", "Whistle", "Tongue")};
    private static final String[][] BEARD = {
            n("Nenhuma", "Por fazer", "Cheia", "Bigode", "Cavanhaque"),
            n("None", "Stubble", "Full", "Mustache", "Goatee")};
    private static final String[][] EYEWEAR = {
            n("Óculos de proteção", "Nenhum", "Óculos escuros", "Tapa-olho", "Máscara ninja", "Máscara de gás", "Monóculo"),
            n("Goggles", "None", "Sunglasses", "Eye patch", "Ninja mask", "Gas mask", "Monocle")};
    private static final String[][] PATTERN = {
            n("Liso", "Camuflado", "Listras", "Tigre", "Estrelas", "Xadrez", "Raio"),
            n("Plain", "Camo", "Stripes", "Tiger", "Stars", "Checkered", "Lightning")};
    private static final String[][] JETPACK = {
            n("Padrão", "Foguete duplo", "Barril", "Asas", "Tanque"),
            n("Standard", "Twin rocket", "Barrel", "Wings", "Tank")};
    private static final String[][] FLAME = {
            n("Laranja", "Azul", "Verde", "Roxa", "Rosa", "Branca", "Arco-íris"),
            n("Orange", "Blue", "Green", "Purple", "Pink", "White", "Rainbow")};
    private static final String[][] GUNSKIN = {
            n("Padrão", "Ouro", "Camuflada", "Neon", "Gelo", "Lava", "Rosa"),
            n("Default", "Gold", "Camo", "Neon", "Ice", "Lava", "Pink")};
    private static final String[][] ACCESSORY = {
            n("Nenhum", "Cachecol", "Capa", "Colete", "Medalha"),
            n("None", "Scarf", "Cape", "Vest", "Medal")};
    private static final String[][] POSES = {
            n("Parado", "Andando", "Voando", "Atirando", "Agachado", "Festa"),
            n("Idle", "Walk", "Fly", "Shoot", "Crouch", "Party")};
    private static final String[][] BACKGROUNDS = {
            n("Sem fundo", "Deserto", "Fábrica", "Céu", "Gelo", "Treino", "Selva", "Espaço"),
            n("No background", "Desert", "Factory", "Sky", "Ice", "Training", "Jungle", "Space")};
    private static final String[][] TABS = {
            n("Cabeça", "Rosto", "Corpo", "Jetpack", "Arma", "Perfil"),
            n("Head", "Face", "Body", "Jetpack", "Weapon", "Profile")};

    private static String tr(String[][] a, int i) {
        String[] arr = a[I18n.pt ? 0 : 1];
        return arr[Math.max(0, Math.min(arr.length - 1, i))];
    }

    public CustomizeScreen(BrigadaJatoGame game) {
        super(game);
        s = game.settings;
        look = s.look;
        preview = new SoldierPreview(game, look);
        preview.background = Theme.DESERT.ordinal();
        preview.zoom = 1f;

        // ---------------- esquerda: visualizacao
        Table left = new Table(skin);
        left.setBackground("panel");
        left.pad(12);
        left.add(preview).size(500, 340).colspan(6).row();

        Table poses = new Table();
        TextButton[] poseBtns = new TextButton[SoldierPreview.POSE_COUNT];
        ButtonGroup<TextButton> pg = new ButtonGroup<>();
        for (int i = 0; i < SoldierPreview.POSE_COUNT; i++) {
            final int pi = i;
            poseBtns[i] = new TextButton(tr(POSES, i), skin, "toggle");
            poseBtns[i].addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.audio.ui("ui_click");
                    preview.poseType = pi;
                    preview.showcase = false;
                }
            });
            pg.add(poseBtns[i]);
            poses.add(poseBtns[i]).width(160).height(42).pad(2);
            if (i == 2) poses.row();
        }
        poseBtns[0].setChecked(true);
        left.add(poses).colspan(6).padTop(6).row();

        Table view = new Table();
        view.defaults().pad(2).height(44);
        view.add(button(I18n.pt ? "Virar" : "Flip", "gray-small", () -> preview.facing = -preview.facing)).width(76);
        view.add(button("-", "gray-small", () -> preview.zoom = Math.max(0.6f, preview.zoom - 0.15f))).width(44);
        view.add(button("+", "gray-small", () -> preview.zoom = Math.min(1.6f, preview.zoom + 0.15f))).width(44);
        bgLabel = label("", "yellow-small");
        view.add(button("<", "gray-small", () -> cycleBg(-1))).width(44);
        view.add(bgLabel).width(130);
        view.add(button(">", "gray-small", () -> cycleBg(1))).width(44);
        view.add(button(I18n.pt ? "Vitrine" : "Showcase", "blue-small", () -> preview.showcase = !preview.showcase)).width(96);
        left.add(view).colspan(6).padTop(4).row();

        Table actions = new Table();
        actions.add(button(I18n.t("random"), "purple-small", this::randomize)).width(200).height(50).padRight(8);
        actions.add(button(I18n.pt ? "Testar em jogo" : "Try in game", "green-small", this::tryInGame)).width(250).height(50);
        left.add(actions).colspan(6).padTop(6);

        // ---------------- direita: abas e opcoes
        Table right = new Table(skin);
        right.setBackground("panel");
        right.pad(14);
        right.add(label(I18n.t("soldier"), "heading")).left().padBottom(4).row();
        Table tabs = new Table();
        ButtonGroup<TextButton> tg = new ButtonGroup<>();
        for (int i = 0; i < 6; i++) {
            final int ti = i;
            TextButton b = new TextButton(tr(TABS, i), skin, "toggle");
            b.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.audio.ui("ui_click");
                    tab = ti;
                    rebuild();
                }
            });
            tg.add(b);
            tabs.add(b).width(102).height(46).pad(2);
            if (i == 0) b.setChecked(true);
        }
        right.add(tabs).row();
        ScrollPane sp = new ScrollPane(content, skin);
        sp.setFadeScrollBars(false);
        sp.setScrollingDisabled(true, false);
        right.add(sp).width(640).height(470).padTop(6).row();
        right.add(button(I18n.t("back"), "green", this::onBack)).width(240).height(56).padTop(6);

        root.add(left).padRight(14);
        root.add(right);
        rebuild();
        cycleBg(0);
    }

    private void cycleBg(int d) {
        int count = Theme.values().length + 1;
        int idx = (preview.background + 1 + d + count) % count;
        preview.background = idx - 1;
        // mapeia a ordem do enum para os nomes
        String txt;
        if (idx == 0) txt = tr(BACKGROUNDS, 0);
        else {
            Theme t = Theme.values()[idx - 1];
            switch (t) {
                case DESERT: txt = tr(BACKGROUNDS, 1); break;
                case FACTORY: txt = tr(BACKGROUNDS, 2); break;
                case SKY: txt = tr(BACKGROUNDS, 3); break;
                case ICE: txt = tr(BACKGROUNDS, 4); break;
                case TRAINING: txt = tr(BACKGROUNDS, 5); break;
                case JUNGLE: txt = tr(BACKGROUNDS, 6); break;
                default: txt = tr(BACKGROUNDS, 7); break;
            }
        }
        bgLabel.setText(txt);
    }

    private void randomize() {
        look.set(Look.random(new Random()));
        rebuild();
    }

    private void tryInGame() {
        s.save();
        MatchConfig cfg = new MatchConfig();
        cfg.mode = ModeType.TRAINING;
        cfg.applyModeDefaults();
        cfg.bots = 0;
        go(GameScreen.offline(game, cfg));
    }

    // ---------------------------------------------------------------- abas

    private void rebuild() {
        content.clearChildren();
        content.top().left();
        content.defaults().left().pad(4);
        switch (tab) {
            case 0:
                cycler(I18n.pt ? "Chapéu" : "Hat", HATS, Look.HAT_COUNT, new Field() {
                    public int get() { return look.hat; }
                    public void set(int v) { look.hat = v; }
                });
                palette(I18n.pt ? "Cor do chapéu" : "Hat color", Look.PALETTE, new Field() {
                    public int get() { return look.helmet; }
                    public void set(int v) { look.helmet = v; }
                });
                cycler(I18n.pt ? "Cabelo" : "Hair", HAIR, Look.HAIR_COUNT, new Field() {
                    public int get() { return look.hair; }
                    public void set(int v) { look.hair = v; }
                });
                palette(I18n.pt ? "Cor do cabelo" : "Hair color", Look.HAIR_COLORS, new Field() {
                    public int get() { return look.hairColor; }
                    public void set(int v) { look.hairColor = v; }
                });
                break;
            case 1:
                cycler(I18n.pt ? "Olhos" : "Eyes", EYES, Look.EYES_COUNT, new Field() {
                    public int get() { return look.eyes; }
                    public void set(int v) { look.eyes = v; }
                });
                cycler(I18n.pt ? "Boca" : "Mouth", MOUTH, Look.MOUTH_COUNT, new Field() {
                    public int get() { return look.mouth; }
                    public void set(int v) { look.mouth = v; }
                });
                cycler(I18n.pt ? "Barba" : "Beard", BEARD, Look.BEARD_COUNT, new Field() {
                    public int get() { return look.beard; }
                    public void set(int v) { look.beard = v; }
                });
                cycler(I18n.pt ? "Óculos / máscara" : "Eyewear / mask", EYEWEAR, Look.EYEWEAR_COUNT, new Field() {
                    public int get() { return look.eyewear; }
                    public void set(int v) { look.eyewear = v; }
                });
                palette(I18n.pt ? "Cor dos óculos / máscara" : "Eyewear color", Look.PALETTE, new Field() {
                    public int get() { return look.visor; }
                    public void set(int v) { look.visor = v; }
                });
                palette(I18n.t("skin"), Look.SKINS, new Field() {
                    public int get() { return look.skin; }
                    public void set(int v) { look.skin = v; }
                });
                break;
            case 2:
                palette(I18n.t("uniform"), Look.PALETTE, new Field() {
                    public int get() { return look.body; }
                    public void set(int v) { look.body = v; }
                });
                cycler(I18n.pt ? "Estampa" : "Pattern", PATTERN, Look.PATTERN_COUNT, new Field() {
                    public int get() { return look.pattern; }
                    public void set(int v) { look.pattern = v; }
                });
                palette(I18n.pt ? "Cor da estampa" : "Pattern color", Look.PALETTE, new Field() {
                    public int get() { return look.patternColor; }
                    public void set(int v) { look.patternColor = v; }
                });
                palette(I18n.pt ? "Calça" : "Pants", Look.PALETTE, new Field() {
                    public int get() { return look.pants; }
                    public void set(int v) { look.pants = v; }
                });
                cycler(I18n.pt ? "Acessório" : "Accessory", ACCESSORY, Look.ACCESSORY_COUNT, new Field() {
                    public int get() { return look.accessory; }
                    public void set(int v) { look.accessory = v; }
                });
                palette(I18n.pt ? "Cor do acessório" : "Accessory color", Look.PALETTE, new Field() {
                    public int get() { return look.accessoryColor; }
                    public void set(int v) { look.accessoryColor = v; }
                });
                break;
            case 3:
                cycler(I18n.pt ? "Modelo" : "Model", JETPACK, Look.JETPACK_COUNT, new Field() {
                    public int get() { return look.jetpack; }
                    public void set(int v) { look.jetpack = v; }
                });
                palette(I18n.pt ? "Cor do jetpack" : "Jetpack color", Look.PALETTE, new Field() {
                    public int get() { return look.jetColor; }
                    public void set(int v) { look.jetColor = v; }
                });
                cycler(I18n.pt ? "Chama" : "Flame", FLAME, Look.FLAMES.length, new Field() {
                    public int get() { return look.flame; }
                    public void set(int v) {
                        look.flame = v;
                        preview.poseType = SoldierPreview.POSE_FLY;
                    }
                });
                content.add(label(I18n.pt ? "Dica: escolha a pose \"Voando\" para ver a chama." : "Tip: pick the \"Fly\" pose to see the flame.", "gray")).row();
                break;
            case 4:
                cycler(I18n.pt ? "Skin das armas" : "Weapon skin", GUNSKIN, Look.GUNSKIN_COUNT, new Field() {
                    public int get() { return look.gunSkin; }
                    public void set(int v) { look.gunSkin = v; }
                });
                final WeaponType[] all = WeaponType.values();
                String[][] wn = new String[2][all.length];
                for (int i = 0; i < all.length; i++) {
                    wn[0][i] = all[i].pt;
                    wn[1][i] = all[i].en;
                }
                cycler(I18n.pt ? "Arma na mão (prévia)" : "Weapon in hand (preview)", wn, all.length, new Field() {
                    public int get() { return weaponIdx; }
                    public void set(int v) {
                        weaponIdx = v;
                        preview.weapon = all[v];
                        preview.showcase = false;
                    }
                });
                content.add(label(I18n.pt ? "Use \"Vitrine\" para ver o soldado com todas as armas." : "Use \"Showcase\" to see the soldier with every weapon.", "gray")).row();
                break;
            default:
                content.add(label(I18n.t("name"), "default")).row();
                final TextField name = new TextField(s.name, skin);
                name.setMaxLength(14);
                name.setTextFieldListener((tf, c) -> s.name = tf.getText().trim().isEmpty() ? "Soldado" : tf.getText().trim());
                content.add(name).width(380).height(56).row();
                content.add(label(I18n.t("stats"), "yellow")).padTop(10).row();
                content.add(label(I18n.f("level", s.level()), "small")).row();
                content.add(label(I18n.f("stat_matches", s.matches), "small")).row();
                content.add(label(I18n.f("stat_wins", s.wins), "small")).row();
                content.add(label(I18n.f("stat_kills", s.totalKills), "small")).row();
                content.add(label(I18n.f("stat_deaths", s.totalDeaths), "small")).row();
                content.add(label(I18n.f("stat_wave", s.bestWave), "small")).row();
                break;
        }
    }

    private void cycler(String title, final String[][] names, final int count, final Field f) {
        Table row = new Table();
        row.add(label(title, "default")).width(250).left();
        final Label value = label(tr(names, f.get()), "yellow");
        row.add(button("<", "gray", () -> {
            f.set((f.get() + count - 1) % count);
            value.setText(tr(names, f.get()));
        })).size(54, 48);
        row.add(value).width(250).center();
        row.add(button(">", "gray", () -> {
            f.set((f.get() + 1) % count);
            value.setText(tr(names, f.get()));
        })).size(54, 48);
        content.add(row).row();
    }

    private void palette(String title, final int[] colors, final Field f) {
        content.add(label(title, "default")).padTop(4).row();
        Table t = new Table();
        int perRow = 12;
        for (int i = 0; i < colors.length; i++) {
            final int col = colors[i];
            boolean selected = f.get() == col;
            Stack st = new Stack();
            Image border = new Image(new TextureRegionDrawable(game.assets.rrect).tint(Ui.c(selected ? 0xffffff : 0x15151c)));
            Image sw = new Image(new TextureRegionDrawable(game.assets.rrect).tint(Ui.c(col)));
            Table inner = new Table();
            inner.add(sw).size(selected ? 30 : 34, selected ? 30 : 34);
            st.add(border);
            st.add(inner);
            st.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.audio.ui("ui_click");
                    f.set(col);
                    rebuild();
                }
            });
            t.add(st).size(40, 40).pad(2);
            if ((i + 1) % perRow == 0) t.row();
        }
        content.add(t).row();
    }

    @Override
    protected void onBack() {
        s.save();
        go(new MenuScreen(game));
    }
}
