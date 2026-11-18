package com.brigadajato.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.Settings;
import com.brigadajato.render.ColorFilter;

/** Configuracoes em abas: geral, visual, controles e som. */
public class SettingsScreen extends BaseScreen {
    private final Settings s;
    private final Table content = new Table();
    private int tab;

    interface FloatSetter {
        void set(float v);
    }

    interface BoolSetter {
        void set(boolean v);
    }

    interface IntSetter {
        void set(int v);
    }

    private static String t2(String pt, String en) {
        return I18n.pt ? pt : en;
    }

    public SettingsScreen(BrigadaJatoGame game) {
        this(game, 0);
    }

    public SettingsScreen(BrigadaJatoGame game, int startTab) {
        super(game);
        s = game.settings;
        tab = startTab;
        Table panel = new Table(skin);
        panel.setBackground("panel");
        panel.pad(18);
        panel.add(label(I18n.t("settings"), "heading")).padBottom(4).row();
        Table tabs = new Table();
        ButtonGroup<TextButton> g = new ButtonGroup<>();
        String[] names = {t2("Geral", "General"), "Visual", t2("Controles", "Controls"), t2("Som", "Sound")};
        for (int i = 0; i < names.length; i++) {
            final int ti = i;
            TextButton b = new TextButton(names[i], skin, "toggle");
            b.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.audio.ui("ui_click");
                    tab = ti;
                    rebuild();
                }
            });
            g.add(b);
            if (i == tab) b.setChecked(true);
            tabs.add(b).width(170).height(48).pad(3);
        }
        panel.add(tabs).row();
        ScrollPane sp = new ScrollPane(content, skin);
        sp.setFadeScrollBars(false);
        sp.setScrollingDisabled(true, false);
        panel.add(sp).width(900).height(480).padTop(6).row();
        panel.add(button(I18n.t("back"), "green", this::onBack)).width(260).height(56).padTop(8);
        root.add(panel);
        rebuild();
    }

    private void rebuild() {
        content.clearChildren();
        content.top();
        content.defaults().pad(5).left();
        switch (tab) {
            case 0: {
                choice(I18n.t("language"), new String[]{I18n.t("lang_pt"), I18n.t("lang_en")}, s.portuguese ? 0 : 1, v -> {
                    boolean pt = v == 0;
                    if (pt != s.portuguese) {
                        s.portuguese = pt;
                        s.save();
                        go(new SettingsScreen(game, 0));
                    }
                });
                choice(I18n.t("fire_mode"), new String[]{I18n.t("fire_auto"), I18n.t("fire_button")}, s.aimToFire ? 0 : 1, v -> s.aimToFire = v == 0);
                check(I18n.t("vibration"), s.vibration, v -> s.vibration = v);
                check(I18n.t("auto_aim_help"), s.aimAssist, v -> s.aimAssist = v);
                check(t2("Killcam (ver quem te matou)", "Killcam (see who killed you)"), s.killcam, v -> s.killcam = v);
                check(t2("Melhores jogadas no fim da partida", "Best plays at the end of the match"), s.highlights, v -> s.highlights = v);
                check(t2("Banner e som de abates em sequência", "Kill streak banner and sound"), s.killBanner, v -> s.killBanner = v);
                break;
            }
            case 1: {
                choice(t2("Qualidade", "Quality"), new String[]{t2("Baixa", "Low"), t2("Média", "Medium"), t2("Alta", "High"), "Ultra"}, s.quality, v -> s.quality = v);
                choice(t2("Limite de FPS", "FPS limit"), new String[]{"30", "60", t2("Sem limite", "Unlimited")}, s.fpsLimit, v -> {
                    s.fpsLimit = v;
                    s.applyFps();
                });
                choice(t2("Sangue", "Blood"), new String[]{t2("Vermelho", "Red"), t2("Tinta verde", "Green paint"), t2("Desligado", "Off")}, s.blood, v -> s.blood = v);
                cycle(t2("Filtro de cor", "Color filter"), I18n.pt ? ColorFilter.NAMES_PT : ColorFilter.NAMES_EN, s.colorFilter, v -> s.colorFilter = v);
                cycle(t2("Contorno dos bonecos", "Character outline"), new String[]{t2("Sem", "None"), t2("Fino", "Thin"), t2("Normal", "Normal"), t2("Grosso", "Thick")}, s.outline, v -> s.outline = v);
                cycle(t2("Estilo da mira", "Crosshair style"), new String[]{t2("Clássica", "Classic"), t2("Ponto", "Dot"), t2("Cruz", "Cross"), t2("Círculo", "Circle"), "Chevron"}, s.crosshairStyle, v -> s.crosshairStyle = v);
                cycle(t2("Cor da mira", "Crosshair color"), new String[]{t2("Branca", "White"), t2("Verde", "Green"), t2("Vermelha", "Red"), t2("Ciano", "Cyan"), t2("Amarela", "Yellow"), t2("Rosa", "Pink")}, s.crosshairColor, v -> s.crosshairColor = v);
                slider(t2("Quantidade de partículas", "Particle amount"), 0.25f, 1f, s.particles, v -> s.particles = v);
                slider(t2("Tremor da tela", "Screen shake"), 0f, 1.5f, s.shake, v -> s.shake = v);
                slider(t2("Distância da câmera", "Camera distance"), 0.8f, 1.3f, s.cameraZoom, v -> s.cameraZoom = v);
                slider(t2("Tamanho do HUD", "HUD size"), 0.75f, 1.25f, s.hudScale, v -> s.hudScale = v);
                check(t2("Minimapa", "Minimap"), s.minimap, v -> s.minimap = v);
                check(t2("Nomes acima dos jogadores", "Names above players"), s.showNames, v -> s.showNames = v);
                check(I18n.t("damage_numbers"), s.damageNumbers, v -> s.damageNumbers = v);
                check(t2("Clima e partículas de ambiente (neve, folhas, poeira)", "Weather and ambient particles"), s.ambient, v -> s.ambient = v);
                choice(I18n.t("day_night"), new String[]{I18n.t("dn_day"), I18n.t("dn_cycle"), I18n.t("dn_night")}, s.dayNight, v -> s.dayNight = v);
                check(I18n.t("dyn_lights"), s.dynamicLights, v -> s.dynamicLights = v);
                check(I18n.t("show_fps"), s.showFps, v -> s.showFps = v);
                content.add(button(t2("Modo desempenho (celular fraco)", "Performance mode (weak phone)"), "purple-small", () -> {
                    s.quality = 0;
                    s.particles = 0.4f;
                    s.ambient = false;
                    s.fpsLimit = 1;
                    s.colorFilter = 0;
                    s.save();
                    go(new SettingsScreen(game, 1));
                })).height(48).padTop(8).row();
                break;
            }
            case 2: {
                slider(I18n.t("controls_size"), 0.7f, 1.4f, s.controlsScale, v -> s.controlsScale = v);
                slider(I18n.t("controls_opacity"), 0.2f, 1f, s.controlsOpacity, v -> s.controlsOpacity = v);
                check(I18n.t("left_handed"), s.leftHanded, v -> s.leftHanded = v);
                slider(t2("Suavização da mira (0 = instantânea)", "Aim smoothing (0 = instant)"), 0f, 1f, s.aimSmooth, v -> s.aimSmooth = v);
                slider(t2("Zona morta dos joysticks", "Joystick dead zone"), 0.05f, 0.35f, s.deadzone, v -> s.deadzone = v);
                slider(t2("Ponto do tiro automático", "Auto-fire point"), 0.45f, 0.95f, s.fireThreshold, v -> s.fireThreshold = v);
                slider(t2("Curva do movimento (1 = linear)", "Movement curve (1 = linear)"), 0.6f, 2.2f, s.moveCurve, v -> s.moveCurve = v);
                content.add(button(I18n.t("edit_layout"), "purple-small", () -> {
                    s.save();
                    go(new ControlsEditorScreen(game));
                })).height(52).padTop(8).row();
                content.add(label(t2("PC: WASD/espaço move e voa, mouse mira e atira, G granada, Q troca, R recarrega,\nF soco, E pega/usa, C gancho, V emotes, Z zoom, Tab placar, Esc pausa.",
                        "PC: WASD/space move and fly, mouse aims and shoots, G grenade, Q swap, R reload,\nF melee, E pick up/use, C hook, V emotes, Z zoom, Tab scoreboard, Esc pause."), "gray")).padTop(10).row();
                break;
            }
            default: {
                slider(I18n.t("volume_sfx"), 0, 1, s.sfxVolume, v -> s.sfxVolume = v);
                slider(I18n.t("volume_music"), 0, 1, s.musicVolume, v -> {
                    s.musicVolume = v;
                    game.audio.refreshMusic();
                });
                check(I18n.t("map_music"), s.mapMusic, v -> s.mapMusic = v);
                break;
            }
        }
    }

    private void choice(String title, String[] options, int current, final IntSetter setter) {
        Table row = new Table();
        row.add(label(title, "default")).width(330).left();
        ButtonGroup<TextButton> g = new ButtonGroup<>();
        g.setMinCheckCount(1);
        g.setMaxCheckCount(1);
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            final TextButton b = new TextButton(options[i], skin, "toggle");
            g.add(b);
            if (i == current) b.setChecked(true);
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (b.isChecked()) setter.set(idx);
                }
            });
            row.add(b).width(Math.min(170, 520f / options.length)).height(46).pad(2);
        }
        content.add(row).row();
    }

    private void cycle(String title, final String[] options, int current, final IntSetter setter) {
        Table row = new Table();
        row.add(label(title, "default")).width(330).left();
        final int[] cur = {Math.max(0, Math.min(options.length - 1, current))};
        final Label value = label(options[cur[0]], "yellow");
        row.add(button("<", "gray", () -> {
            cur[0] = (cur[0] + options.length - 1) % options.length;
            value.setText(options[cur[0]]);
            setter.set(cur[0]);
        })).size(54, 46);
        row.add(value).width(240).center();
        row.add(button(">", "gray", () -> {
            cur[0] = (cur[0] + 1) % options.length;
            value.setText(options[cur[0]]);
            setter.set(cur[0]);
        })).size(54, 46);
        content.add(row).row();
    }

    private void slider(String text, float min, float max, float value, final FloatSetter setter) {
        Table row = new Table();
        row.add(label(text, "default")).width(330).left();
        final Slider sl = new Slider(min, max, 0.01f, false, skin);
        sl.setValue(value);
        sl.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.set(sl.getValue());
            }
        });
        row.add(sl).width(420);
        content.add(row).row();
    }

    private void check(String text, boolean value, final BoolSetter setter) {
        final CheckBox cb = new CheckBox("  " + text, skin);
        cb.setChecked(value);
        cb.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.set(cb.isChecked());
            }
        });
        content.add(cb).row();
    }

    @Override
    protected void onBack() {
        s.save();
        go(new MenuScreen(game));
    }
}
