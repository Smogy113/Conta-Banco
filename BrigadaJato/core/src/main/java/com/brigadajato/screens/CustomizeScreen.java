package com.brigadajato.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.Settings;
import com.brigadajato.ui.Ui;
import com.brigadajato.world.Look;
import com.brigadajato.world.WeaponType;

import java.util.Random;

/** Personalizacao do soldado + estatisticas. */
public class CustomizeScreen extends BaseScreen {
    private final Settings s;
    private final SoldierPreview preview;
    private Label hatLabel;
    private int weaponIdx = 3;

    public CustomizeScreen(BrigadaJatoGame game) {
        super(game);
        s = game.settings;
        preview = new SoldierPreview(game, s.look);

        Table panel = new Table(skin);
        panel.setBackground("panel");
        panel.pad(22);
        panel.defaults().pad(5);
        panel.add(label(I18n.t("soldier"), "big")).colspan(2).padBottom(8).row();

        final TextField name = new TextField(s.name, skin);
        name.setMaxLength(14);
        name.setTextFieldListener((tf, c) -> s.name = tf.getText().trim().isEmpty() ? "Soldado" : tf.getText().trim());
        panel.add(label(I18n.t("name"), "default")).left();
        panel.add(name).width(380).height(56).left().row();

        panel.add(label(I18n.t("uniform"), "default")).left();
        panel.add(swatches(Look.PALETTE, 0)).left().row();
        panel.add(label(I18n.t("helmet"), "default")).left();
        panel.add(swatches(Look.PALETTE, 1)).left().row();
        panel.add(label(I18n.t("visor"), "default")).left();
        panel.add(swatches(Look.PALETTE, 2)).left().row();
        panel.add(label(I18n.t("skin"), "default")).left();
        panel.add(swatches(Look.SKINS, 3)).left().row();

        hatLabel = label("", "yellow");
        Table hat = new Table();
        hat.add(button("<", "gray", () -> {
            s.look.hat = (s.look.hat + Look.HAT_COUNT - 1) % Look.HAT_COUNT;
            refresh();
        })).size(60, 52);
        hat.add(hatLabel).width(200);
        hat.add(button(">", "gray", () -> {
            s.look.hat = (s.look.hat + 1) % Look.HAT_COUNT;
            refresh();
        })).size(60, 52);
        panel.add(label(I18n.t("hat"), "default")).left();
        panel.add(hat).left().row();

        Table buttons = new Table();
        buttons.add(button(I18n.t("random"), "purple", () -> {
            Look r = Look.random(new Random());
            s.look.body = r.body;
            s.look.helmet = r.helmet;
            s.look.visor = r.visor;
            s.look.skin = r.skin;
            s.look.hat = r.hat;
            refresh();
        })).width(200).height(58).padRight(14);
        buttons.add(button(I18n.t("back"), "green", this::onBack)).width(200).height(58);
        panel.add(buttons).colspan(2).padTop(12);

        Table right = new Table(skin);
        right.add(preview).size(240, 300).row();
        right.add(button("⇄", "gray-small", () -> {
            WeaponType[] all = WeaponType.values();
            weaponIdx = (weaponIdx + 1) % all.length;
            preview.weapon = all[weaponIdx];
        })).width(80).height(44).padBottom(10).row();
        Table stats = new Table(skin);
        stats.setBackground("panel");
        stats.pad(14);
        stats.add(label(I18n.t("stats"), "yellow")).row();
        stats.add(label(I18n.f("level", s.level()), "small")).left().row();
        stats.add(label(I18n.f("stat_matches", s.matches), "small")).left().row();
        stats.add(label(I18n.f("stat_wins", s.wins), "small")).left().row();
        stats.add(label(I18n.f("stat_kills", s.totalKills), "small")).left().row();
        stats.add(label(I18n.f("stat_deaths", s.totalDeaths), "small")).left().row();
        stats.add(label(I18n.f("stat_wave", s.bestWave), "small")).left().row();
        right.add(stats).width(300);

        root.add(panel).padRight(30);
        root.add(right);
        refresh();
    }

    private Table swatches(final int[] colors, final int which) {
        Table t = new Table();
        int perRow = 10;
        for (int i = 0; i < colors.length; i++) {
            final int col = colors[i];
            Image img = new Image(new TextureRegionDrawable(game.assets.rrect).tint(Ui.c(col)));
            img.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.audio.ui("ui_click");
                    switch (which) {
                        case 0: s.look.body = col; break;
                        case 1: s.look.helmet = col; break;
                        case 2: s.look.visor = col; break;
                        default: s.look.skin = col; break;
                    }
                }
            });
            t.add(img).size(34, 34).pad(2);
            if ((i + 1) % perRow == 0) t.row();
        }
        return t;
    }

    private void refresh() {
        hatLabel.setText(I18n.t("hat" + s.look.hat));
    }

    @Override
    protected void onBack() {
        s.save();
        go(new MenuScreen(game));
    }
}
