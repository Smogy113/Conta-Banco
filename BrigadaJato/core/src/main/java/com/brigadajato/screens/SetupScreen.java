package com.brigadajato.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;

/** Configurar partida (offline ou criando sala no Wi-Fi). */
public class SetupScreen extends BaseScreen {
    public enum Kind {OFFLINE, HOST, LOBBY_EDIT}

    private final Kind kind;
    private final MatchConfig cfg;
    private final Runnable onDone;
    private Label modeLabel, modeDesc, mapLabel, botsLabel, timeLabel, scoreLabel, teamLabel, diffLabel;
    private Slider botsSlider;

    private static final ModeType[] MODES = {ModeType.DEATHMATCH, ModeType.TEAM_DEATHMATCH, ModeType.CTF, ModeType.BOMB, ModeType.SURVIVAL,
            ModeType.GUN_GAME, ModeType.KING_HILL, ModeType.INFECTION, ModeType.BATTLE_ROYALE, ModeType.DUEL, ModeType.HIDE_SEEK};
    private static final int[] TIMES = {0, 120, 180, 300, 420, 600};

    public SetupScreen(BrigadaJatoGame game, Kind kind) {
        this(game, kind, game.settings.lastMatch.copy(), null);
    }

    public SetupScreen(BrigadaJatoGame game, Kind kind, MatchConfig cfg, Runnable onDone) {
        super(game);
        this.kind = kind;
        this.cfg = cfg;
        this.onDone = onDone;
        if (cfg.mode == ModeType.TRAINING) {
            cfg.mode = ModeType.DEATHMATCH;
            cfg.applyModeDefaults();
        }
        build();
    }

    private void build() {
        Table panel = new Table(skin);
        panel.setBackground("panel");
        panel.pad(24);
        panel.defaults().pad(6);
        panel.add(label(kind == Kind.OFFLINE ? I18n.t("match_setup") : I18n.t("create_room"), "big")).colspan(4).padBottom(10).row();

        // modo
        modeLabel = label("", "yellow");
        modeDesc = label("", "small");
        modeDesc.setWrap(true);
        modeDesc.setAlignment(Align.center);
        panel.add(label(I18n.t("mode"), "default")).left();
        panel.add(button("<", "gray", () -> cycleMode(-1))).size(60, 52);
        panel.add(modeLabel).width(360);
        panel.add(button(">", "gray", () -> cycleMode(1))).size(60, 52).row();
        panel.add(modeDesc).colspan(4).width(700).padBottom(6).row();

        // mapa
        mapLabel = label("", "yellow");
        panel.add(label(I18n.t("map"), "default")).left();
        panel.add(button("<", "gray", () -> cycleMap(-1))).size(60, 52);
        panel.add(mapLabel).width(360);
        panel.add(button(">", "gray", () -> cycleMap(1))).size(60, 52).row();

        // bots
        botsLabel = label("", "yellow");
        botsSlider = new Slider(0, 11, 1, false, skin);
        botsSlider.setValue(cfg.bots);
        botsSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                cfg.bots = (int) botsSlider.getValue();
                refresh();
            }
        });
        panel.add(botsLabel).left();
        panel.add(botsSlider).colspan(3).width(480).row();

        // dificuldade
        diffLabel = label("", "yellow");
        panel.add(label(I18n.t("difficulty"), "default")).left();
        panel.add(button("<", "gray", () -> {
            cfg.difficulty = (cfg.difficulty + 2) % 3;
            refresh();
        })).size(60, 52);
        panel.add(diffLabel).width(360);
        panel.add(button(">", "gray", () -> {
            cfg.difficulty = (cfg.difficulty + 1) % 3;
            refresh();
        })).size(60, 52).row();

        // tempo
        timeLabel = label("", "yellow");
        panel.add(label(I18n.t("time_limit"), "default")).left();
        panel.add(button("<", "gray", () -> cycleTime(-1))).size(60, 52);
        panel.add(timeLabel).width(360);
        panel.add(button(">", "gray", () -> cycleTime(1))).size(60, 52).row();

        // placar
        scoreLabel = label("", "yellow");
        panel.add(label(I18n.t("score_limit"), "default")).left();
        panel.add(button("<", "gray", () -> cycleScore(-1))).size(60, 52);
        panel.add(scoreLabel).width(360);
        panel.add(button(">", "gray", () -> cycleScore(1))).size(60, 52).row();

        // time do jogador
        teamLabel = label("", "yellow");
        panel.add(label(I18n.t("your_team"), "default")).left();
        panel.add(button("<", "gray", () -> {
            cfg.playerTeam = (cfg.playerTeam + 2) % 3;
            refresh();
        })).size(60, 52);
        panel.add(teamLabel).width(360);
        panel.add(button(">", "gray", () -> {
            cfg.playerTeam = (cfg.playerTeam + 1) % 3;
            refresh();
        })).size(60, 52).row();

        final CheckBox ff = new CheckBox(" " + I18n.t("friendly_fire"), skin);
        ff.setChecked(cfg.friendlyFire);
        ff.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                cfg.friendlyFire = ff.isChecked();
            }
        });
        panel.add(ff).colspan(4).row();

        Table buttons = new Table();
        buttons.add(button(I18n.t("back"), "gray", this::onBack)).width(220).height(60).padRight(20);
        String startText = kind == Kind.OFFLINE ? I18n.t("start") : (kind == Kind.HOST ? I18n.t("create_room") : "OK");
        TextButton start = button(startText, "green", this::start);
        buttons.add(start).width(300).height(64);
        panel.add(buttons).colspan(4).padTop(14);
        root.add(panel);
        refresh();
    }

    private void cycleMode(int d) {
        int i = 0;
        for (int k = 0; k < MODES.length; k++) if (MODES[k] == cfg.mode) i = k;
        i = (i + d + MODES.length) % MODES.length;
        cfg.mode = MODES[i];
        cfg.applyModeDefaults();
        refresh();
    }

    private void cycleMap(int d) {
        int i = 0;
        for (int k = 0; k < GameMap.PLAY_IDS.length; k++) if (GameMap.PLAY_IDS[k].equals(cfg.mapId)) i = k;
        i = (i + d + GameMap.PLAY_IDS.length) % GameMap.PLAY_IDS.length;
        cfg.mapId = GameMap.PLAY_IDS[i];
        refresh();
    }

    private void cycleTime(int d) {
        int i = 0;
        for (int k = 0; k < TIMES.length; k++) if (TIMES[k] == cfg.timeLimit) i = k;
        i = (i + d + TIMES.length) % TIMES.length;
        cfg.timeLimit = TIMES[i];
        refresh();
    }

    private int[] scoreOptions() {
        switch (cfg.mode) {
            case TEAM_DEATHMATCH: return new int[]{0, 20, 30, 40, 60, 100};
            case CTF: return new int[]{0, 1, 3, 5, 10};
            case BOMB: return new int[]{3, 5, 8, 10};
            case SURVIVAL: return new int[]{0};
            case GUN_GAME: case INFECTION: case BATTLE_ROYALE: case HIDE_SEEK: return new int[]{0};
            case KING_HILL: return new int[]{60, 120, 180, 300};
            case DUEL: return new int[]{1, 2, 3, 5, 7};
            default: return new int[]{0, 10, 15, 20, 30, 50};
        }
    }

    private void cycleScore(int d) {
        int[] opts = scoreOptions();
        int i = 0;
        for (int k = 0; k < opts.length; k++) if (opts[k] == cfg.scoreLimit) i = k;
        i = (i + d + opts.length) % opts.length;
        cfg.scoreLimit = opts[i];
        refresh();
    }

    private static String modeKey(ModeType m) {
        switch (m) {
            case TEAM_DEATHMATCH: return "team";
            case CTF: return "ctf";
            case BOMB: return "bomb";
            case SURVIVAL: return "survival";
            case TRAINING: return "training";
            case GUN_GAME: return "gungame";
            case KING_HILL: return "koth";
            case INFECTION: return "infection";
            case BATTLE_ROYALE: return "br";
            case DUEL: return "duel";
            case HIDE_SEEK: return "hideseek";
            default: return "deathmatch";
        }
    }

    private void refresh() {
        modeLabel.setText(I18n.t("mode_" + modeKey(cfg.mode)));
        modeDesc.setText(I18n.t("desc_" + modeKey(cfg.mode)));
        GameMap m = BrigadaJatoGame.loadMap(cfg.mapId);
        mapLabel.setText(I18n.pt ? m.name : m.nameEn);
        boolean surv = cfg.mode == ModeType.SURVIVAL;
        if (surv && cfg.bots > 3) {
            cfg.bots = 1;
            botsSlider.setValue(1);
        }
        botsSlider.setRange(0, surv ? 3 : 11);
        botsLabel.setText((surv ? I18n.t("allies") : I18n.t("bots")) + ": " + cfg.bots);
        diffLabel.setText(I18n.t("diff" + cfg.difficulty));
        timeLabel.setText(cfg.timeLimit == 0 ? I18n.t("none") : (cfg.timeLimit / 60) + " " + I18n.t("min"));
        if (cfg.mode == ModeType.BOMB) timeLabel.setText("-");
        scoreLabel.setText(cfg.scoreLimit == 0 ? I18n.t("none") : "" + cfg.scoreLimit);
        if (surv || scoreOptions().length == 1) scoreLabel.setText("-");
        if (cfg.mode == ModeType.DUEL) botsLabel.setText(I18n.t("bots") + ": 1");
        if (cfg.mode == ModeType.BATTLE_ROYALE || cfg.mode == ModeType.DUEL) timeLabel.setText("-");
        teamLabel.setText(!cfg.mode.chooseTeams() ? "-" : (cfg.playerTeam == 0 ? I18n.t("auto") : (cfg.playerTeam == C.TEAM_RED ? I18n.t("team_red") : I18n.t("team_blue"))));
    }

    private void start() {
        if (cfg.mode == ModeType.BOMB || cfg.mode == ModeType.BATTLE_ROYALE || cfg.mode == ModeType.DUEL) cfg.timeLimit = 0;
        game.settings.lastMatch.mode = cfg.mode;
        game.settings.lastMatch.mapId = cfg.mapId;
        game.settings.lastMatch.bots = cfg.bots;
        game.settings.lastMatch.difficulty = cfg.difficulty;
        game.settings.lastMatch.timeLimit = cfg.timeLimit;
        game.settings.lastMatch.scoreLimit = cfg.scoreLimit;
        game.settings.lastMatch.friendlyFire = cfg.friendlyFire;
        game.settings.lastMatch.playerTeam = cfg.playerTeam;
        game.settings.save();
        if (kind == Kind.OFFLINE) go(GameScreen.offline(game, cfg));
        else if (kind == Kind.HOST) go(LobbyScreen.host(game, cfg));
        else if (onDone != null) onDone.run();
    }

    @Override
    protected void onBack() {
        if (kind == Kind.OFFLINE) go(new MenuScreen(game));
        else if (kind == Kind.HOST) go(new MultiplayerScreen(game));
        else if (onDone != null) onDone.run();
    }
}
