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
    private MapCard mapCard;

    private static final ModeType[] MODES = {ModeType.DEATHMATCH, ModeType.TEAM_DEATHMATCH, ModeType.CTF, ModeType.BOMB, ModeType.SURVIVAL,
            ModeType.GUN_GAME, ModeType.KING_HILL, ModeType.INFECTION, ModeType.BATTLE_ROYALE, ModeType.DUEL, ModeType.HIDE_SEEK};
    private static final int[] TIMES = {0, 120, 180, 300, 420, 600};

    public SetupScreen(BrigadaJatoGame game, Kind kind) {
        this(game, kind, game.settings.lastMatch.copy(), null);
    }

    /** Depois da tela de modos: ja vem com o modo escolhido. */
    public SetupScreen(BrigadaJatoGame game, Kind kind, ModeType mode) {
        this(game, kind, withMode(game.settings.lastMatch.copy(), mode), null);
    }

    private static MatchConfig withMode(MatchConfig cfg, ModeType mode) {
        if (cfg.mode != mode) {
            cfg.mode = mode;
            cfg.applyModeDefaults();
        }
        return cfg;
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
        panel.pad(14, 26, 14, 26);
        panel.defaults().pad(3);
        final int[] row = {0};

        // cabecalho: modo escolhido (ou seletor de modo, ao editar a sala)
        modeLabel = label("", "yellow");
        modeDesc = label("", "small");
        modeDesc.setWrap(true);
        Table head = new Table();
        if (kind == Kind.LOBBY_EDIT) {
            head.add(label(I18n.t("mode"), "default")).left().padRight(12);
            head.add(button("<", "gray", () -> cycleMode(-1))).size(56, 40);
            head.add(new ModeBadge()).size(64, 64).padLeft(10).padRight(10);
            Table txt = new Table();
            txt.add(modeLabel).left().row();
            txt.add(modeDesc).width(560).left();
            head.add(txt).width(570).left();
            head.add(button(">", "gray", () -> cycleMode(1))).size(56, 40);
        } else {
            head.add(new ModeBadge()).size(78, 78).padRight(14);
            Table txt = new Table();
            String step = (I18n.pt ? "2/2  " : "2/2  ") + (kind == Kind.OFFLINE ? I18n.t("match_setup") : I18n.t("create_room")).toUpperCase();
            txt.add(label(step, "gray")).left().row();
            txt.add(modeLabel).left().row();
            txt.add(modeDesc).width(640).left();
            head.add(txt).width(650).left();
            head.add(button(I18n.pt ? "TROCAR MODO" : "CHANGE MODE", "gray-small", () -> go(new ModeSelectScreen(game, kind))))
                    .width(190).height(48).padLeft(12);
        }
        fadeRow(panel.add(head).colspan(2).padBottom(6).growX(), row);
        panel.row();

        // mapa
        mapLabel = label("", "yellow");
        Table mapRow = new Table();
        mapRow.add(label(I18n.t("map"), "default")).width(110).left();
        mapRow.add(button("<", "gray", () -> cycleMap(-1))).size(56, 40);
        mapLabel.setAlignment(Align.center);
        mapRow.add(mapLabel).width(560);
        mapRow.add(button(">", "gray", () -> cycleMap(1))).size(56, 40);
        fadeRow(panel.add(mapRow).colspan(2), row);
        panel.row();
        mapCard = new MapCard(game.draw, game.assets);
        fadeRow(panel.add(mapCard).colspan(2).size(760, 112), row);
        panel.row();

        // bots + fogo amigo
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
        final CheckBox ff = new CheckBox(" " + I18n.t("friendly_fire"), skin);
        ff.setChecked(cfg.friendlyFire);
        ff.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                cfg.friendlyFire = ff.isChecked();
            }
        });
        Table botsRow = new Table();
        botsRow.add(botsLabel).width(170).left();
        botsRow.add(botsSlider).width(400).padRight(30);
        botsRow.add(ff);
        fadeRow(panel.add(botsRow).colspan(2).padTop(4), row);
        panel.row();

        // grade 2x2 de opcoes
        diffLabel = label("", "yellow");
        timeLabel = label("", "yellow");
        scoreLabel = label("", "yellow");
        teamLabel = label("", "yellow");
        fadeRow(panel.add(option(I18n.t("difficulty"), diffLabel, () -> {
            cfg.difficulty = (cfg.difficulty + 2) % 3;
            refresh();
        }, () -> {
            cfg.difficulty = (cfg.difficulty + 1) % 3;
            refresh();
        })), row);
        fadeRow(panel.add(option(I18n.t("time_limit"), timeLabel, () -> cycleTime(-1), () -> cycleTime(1))), row);
        panel.row();
        fadeRow(panel.add(option(I18n.t("score_limit"), scoreLabel, () -> cycleScore(-1), () -> cycleScore(1))), row);
        fadeRow(panel.add(option(I18n.t("your_team"), teamLabel, () -> {
            cfg.playerTeam = (cfg.playerTeam + 2) % 3;
            refresh();
        }, () -> {
            cfg.playerTeam = (cfg.playerTeam + 1) % 3;
            refresh();
        })), row);
        panel.row();

        Table buttons = new Table();
        buttons.add(button(I18n.t("back"), "gray", this::onBack)).width(220).height(60).padRight(20);
        String startText = kind == Kind.OFFLINE ? I18n.t("start") : (kind == Kind.HOST ? I18n.t("create_room") : "OK");
        TextButton start = button(startText, "green", this::start);
        buttons.add(start).width(320).height(64);
        fadeRow(panel.add(buttons).colspan(2).padTop(10), row);
        root.add(panel);
        refresh();
    }

    /** Cada linha aparece um pouco depois da anterior. */
    private static void fadeRow(com.badlogic.gdx.scenes.scene2d.ui.Cell<?> c, int[] row) {
        Actor a = c.getActor();
        a.getColor().a = 0;
        a.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.06f + row[0]++ * 0.05f),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.25f, com.badlogic.gdx.math.Interpolation.pow2Out)));
    }

    /** Bloco de opcao: titulo pequeno em cima e "<  valor  >" embaixo. */
    private Table option(String title, Label value, Runnable prev, Runnable next) {
        Table t = new Table(skin);
        t.setBackground(skin.newDrawable("field", new com.badlogic.gdx.graphics.Color(0.11f, 0.13f, 0.17f, 0.95f)));
        t.pad(6, 10, 8, 10);
        t.add(label(title, "gray")).colspan(3).left().padBottom(2).row();
        t.add(button("<", "gray", prev)).size(50, 38);
        value.setAlignment(Align.center);
        t.add(value).width(300);
        t.add(button(">", "gray", next)).size(50, 38);
        return t;
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
        if (mapCard != null) mapCard.setMap(m);
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
        if (kind == Kind.OFFLINE || kind == Kind.HOST) go(new ModeSelectScreen(game, kind));
        else if (onDone != null) onDone.run();
    }

    /** Icone animado do modo escolhido. */
    private class ModeBadge extends Actor {
        private final com.brigadajato.render.SoldierPainter painter = new com.brigadajato.render.SoldierPainter();
        private float time;

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
        }

        @Override
        public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            com.brigadajato.render.Draw d = game.draw;
            float cx = getX() + getWidth() / 2, cy = getY() + getHeight() / 2, s = getWidth();
            int col = ModeSelectScreen.colorOf(cfg.mode);
            d.rgb(com.brigadajato.render.SoldierPainter.OUTLINE, parentAlpha);
            d.region(game.assets.rrect, cx, cy, s + 4, s + 4, 0);
            d.rgb(com.brigadajato.render.SoldierPainter.mix(col, 0x0b0e14, 0.55f), parentAlpha);
            d.region(game.assets.rrect, cx, cy, s, s, 0);
            d.rgb(col, 0.7f * parentAlpha);
            d.region(game.assets.rrect, cx, cy + s * 0.18f, s - 6, s * 0.6f, 0);
            ModeSelectScreen.drawModeIcon(d, game.assets, painter, cfg.mode, cx, cy + 2, s * 0.62f, time);
            batch.setColor(1, 1, 1, 1);
        }
    }

    @Override
    public void dispose() {
        if (mapCard != null) mapCard.dispose();
        super.dispose();
    }
}
