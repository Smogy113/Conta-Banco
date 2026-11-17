package com.brigadajato.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.Settings;

/** Configuracoes. */
public class SettingsScreen extends BaseScreen {
    private final Settings s;

    public SettingsScreen(BrigadaJatoGame game) {
        super(game);
        s = game.settings;
        Table panel = new Table(skin);
        panel.setBackground("panel");
        panel.pad(22);
        panel.defaults().pad(6).left();
        panel.add(label(I18n.t("settings"), "big")).colspan(2).center().padBottom(8).row();

        // idioma
        panel.add(label(I18n.t("language"), "default"));
        Table lang = new Table();
        final TextButton pt = new TextButton(I18n.t("lang_pt"), skin, "toggle");
        final TextButton en = new TextButton(I18n.t("lang_en"), skin, "toggle");
        group(pt, en, s.portuguese);
        pt.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (pt.isChecked() && !s.portuguese) {
                    s.portuguese = true;
                    s.save();
                    go(new SettingsScreen(game));
                }
            }
        });
        en.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (en.isChecked() && s.portuguese) {
                    s.portuguese = false;
                    s.save();
                    go(new SettingsScreen(game));
                }
            }
        });
        lang.add(pt).width(180).height(50).padRight(8);
        lang.add(en).width(180).height(50);
        panel.add(lang).row();

        // modo de tiro
        panel.add(label(I18n.t("fire_mode"), "default"));
        Table fire = new Table();
        final TextButton auto = new TextButton(I18n.t("fire_auto"), skin, "toggle");
        final TextButton btn = new TextButton(I18n.t("fire_button"), skin, "toggle");
        group(auto, btn, s.aimToFire);
        auto.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                s.aimToFire = auto.isChecked();
            }
        });
        fire.add(auto).width(180).height(50).padRight(8);
        fire.add(btn).width(180).height(50);
        panel.add(fire).row();

        slider(panel, I18n.t("volume_sfx"), 0, 1, s.sfxVolume, v -> s.sfxVolume = v);
        slider(panel, I18n.t("volume_music"), 0, 1, s.musicVolume, v -> {
            s.musicVolume = v;
            game.audio.refreshMusic();
        });
        slider(panel, I18n.t("controls_size"), 0.7f, 1.4f, s.controlsScale, v -> s.controlsScale = v);
        slider(panel, I18n.t("controls_opacity"), 0.2f, 1f, s.controlsOpacity, v -> s.controlsOpacity = v);

        Table checks = new Table();
        checks.defaults().left().pad(4, 0, 4, 24);
        check(checks, I18n.t("vibration"), s.vibration, v -> s.vibration = v);
        check(checks, I18n.t("left_handed"), s.leftHanded, v -> s.leftHanded = v);
        checks.row();
        check(checks, I18n.t("show_fps"), s.showFps, v -> s.showFps = v);
        check(checks, I18n.t("damage_numbers"), s.damageNumbers, v -> s.damageNumbers = v);
        checks.row();
        check(checks, I18n.t("quality") + ": " + I18n.t("q_high"), s.highQuality, v -> s.highQuality = v);
        check(checks, I18n.t("auto_aim_help"), s.aimAssist, v -> s.aimAssist = v);
        panel.add(checks).colspan(2).row();

        panel.add(button(I18n.t("back"), "green", this::onBack)).colspan(2).center().width(260).height(60).padTop(10);
        root.add(panel);
    }

    private void group(TextButton a, TextButton b, boolean firstChecked) {
        ButtonGroup<TextButton> g = new ButtonGroup<>(a, b);
        g.setMaxCheckCount(1);
        g.setMinCheckCount(1);
        if (firstChecked) a.setChecked(true);
        else b.setChecked(true);
    }

    interface FloatSetter {
        void set(float v);
    }

    interface BoolSetter {
        void set(boolean v);
    }

    private void slider(Table t, String text, float min, float max, float value, final FloatSetter setter) {
        t.add(label(text, "default"));
        final Slider sl = new Slider(min, max, 0.01f, false, skin);
        sl.setValue(value);
        sl.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.set(sl.getValue());
            }
        });
        t.add(sl).width(380).row();
    }

    private void check(Table t, String text, boolean value, final BoolSetter setter) {
        final CheckBox cb = new CheckBox(" " + text, skin);
        cb.setChecked(value);
        cb.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.set(cb.isChecked());
            }
        });
        t.add(cb);
    }

    @Override
    protected void onBack() {
        s.save();
        go(new MenuScreen(game));
    }
}
