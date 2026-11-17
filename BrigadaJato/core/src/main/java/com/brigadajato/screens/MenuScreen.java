package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;

/** Menu principal. */
public class MenuScreen extends BaseScreen {
    public MenuScreen(BrigadaJatoGame game) {
        this(game, null);
    }

    public MenuScreen(BrigadaJatoGame game, String notice) {
        super(game);
        Table left = new Table();
        Label title = label(I18n.t("title"), "title");
        left.add(title).left().row();
        left.add(label(I18n.t("subtitle"), "yellow")).left().padBottom(24).row();
        float bw = 440, bh = 62;
        left.add(button(I18n.t("play"), "default", () -> go(new SetupScreen(game, SetupScreen.Kind.OFFLINE)))).width(bw).height(bh).padBottom(10).row();
        left.add(button(I18n.t("multiplayer"), "blue", () -> go(new MultiplayerScreen(game)))).width(bw).height(bh).padBottom(10).row();
        left.add(button(I18n.t("training"), "green", () -> {
            MatchConfig cfg = new MatchConfig();
            cfg.mode = ModeType.TRAINING;
            cfg.applyModeDefaults();
            cfg.bots = 0;
            go(GameScreen.offline(game, cfg));
        })).width(bw).height(bh).padBottom(10).row();
        Table row = new Table();
        row.add(button(I18n.t("soldier"), "purple-small", () -> go(new CustomizeScreen(game)))).width(bw / 2 - 5).height(bh).padRight(10);
        row.add(button(I18n.t("settings"), "gray-small", () -> go(new SettingsScreen(game)))).width(bw / 2 - 5).height(bh);
        left.add(row).padBottom(10).row();
        left.add(button(I18n.t("quit"), "red-small", () -> Gdx.app.exit())).width(160).height(48).left().row();
        if (notice != null) left.add(label(notice, "yellow")).left().padTop(12).row();

        Table right = new Table();
        SoldierPreview preview = new SoldierPreview(game, game.settings.look);
        right.add(preview).size(220, 270).row();
        right.add(label(game.settings.name, "big")).padTop(6).row();
        right.add(label(I18n.f("level", game.settings.level()) + "   •   " + I18n.f("stat_kills", game.settings.totalKills), "gray")).row();

        root.add(left).expand().left().padLeft(70);
        root.add(right).expand().right().padRight(90);
        Label ver = label("v1.0  •  libGDX", "gray");
        ver.setAlignment(Align.right);
        root.row();
        root.add(ver).colspan(2).right().pad(10);
    }

    @Override
    protected void onBack() {
        Gdx.app.exit();
    }
}
