package com.brigadajato;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.brigadajato.render.Draw;
import com.brigadajato.screens.MenuBackground;
import com.brigadajato.screens.MenuScreen;
import com.brigadajato.ui.Ui;
import com.brigadajato.world.GameMap;

/** Classe principal do jogo (compartilhada entre Android e PC). */
public class BrigadaJatoGame extends Game {
    public final Platform platform;
    public Settings settings;
    public Assets assets;
    public Audio audio;
    public SpriteBatch batch;
    public Draw draw;
    public Skin skin;
    public MenuBackground menuBg;
    /** forca os controles de toque mesmo no PC (testes) */
    public boolean forceTouch;

    public BrigadaJatoGame(Platform platform) {
        this.platform = platform == null ? Platform.DESKTOP : platform;
    }

    @Override
    public void create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        settings = new Settings();
        assets = new Assets();
        assets.load();
        audio = new Audio(assets, settings);
        batch = new SpriteBatch(4000);
        draw = new Draw(batch, assets);
        skin = Ui.build(assets);
        menuBg = new MenuBackground(this);
        forceTouch = "true".equals(System.getProperty("brigadajato.touch"));
        shotPath = System.getProperty("brigadajato.shot");
        try {
            shotTime = Float.parseFloat(System.getProperty("brigadajato.shotTime", "5"));
        } catch (NumberFormatException e) {
            shotTime = 5;
        }
        // testes no PC: --campo=valor sobrescreve opcoes (sem salvar), ex.: --quality=1 --dayNight=2
        for (java.lang.reflect.Field fd : Settings.class.getFields()) {
            String v = System.getProperty("brigadajato." + fd.getName());
            if (v == null) continue;
            try {
                if (fd.getType() == int.class) fd.setInt(settings, Integer.parseInt(v));
                else if (fd.getType() == float.class) fd.setFloat(settings, Float.parseFloat(v));
                else if (fd.getType() == boolean.class) fd.setBoolean(settings, Boolean.parseBoolean(v));
            } catch (Exception ignored) {
            }
        }
        if (settings.qualityUnset && platform.isMobile()) {
            settings.quality = 1; // celular: comeca em qualidade media (mais leve)
            settings.save();
        }
        setScreen(startScreen(System.getProperty("brigadajato.start")));
        audio.music(true);
    }

    private String shotPath;
    private float shotTime, elapsed;

    /** Tela inicial (o PC aceita --start=game:MODO:MAPA:BOTS para testes). */
    private com.badlogic.gdx.Screen startScreen(String start) {
        if (start == null) return new MenuScreen(this);
        String[] p = start.split(":");
        switch (p[0]) {
            case "game": {
                com.brigadajato.world.MatchConfig cfg = new com.brigadajato.world.MatchConfig();
                cfg.mode = p.length > 1 ? com.brigadajato.world.ModeType.valueOf(p[1]) : com.brigadajato.world.ModeType.DEATHMATCH;
                cfg.applyModeDefaults();
                if (p.length > 2 && cfg.mode != com.brigadajato.world.ModeType.TRAINING) cfg.mapId = p[2];
                cfg.bots = p.length > 3 ? Integer.parseInt(p[3]) : 5;
                if (p.length > 4) cfg.scoreLimit = Integer.parseInt(p[4]);
                if (p.length > 5) cfg.timeLimit = Integer.parseInt(p[5]);
                return com.brigadajato.screens.GameScreen.offline(this, cfg);
            }
            case "settings": return new com.brigadajato.screens.SettingsScreen(this, p.length > 1 ? Integer.parseInt(p[1]) : 0);
            case "looks": return new com.brigadajato.screens.LooksDebugScreen(this);
            case "banner": return new com.brigadajato.screens.BannerDebugScreen(this);
            case "soldier": return new com.brigadajato.screens.CustomizeScreen(this);
            case "setup":
                if (p.length > 1) settings.lastMatch.mapId = p[1];
                return new com.brigadajato.screens.SetupScreen(this, com.brigadajato.screens.SetupScreen.Kind.OFFLINE);
            case "modes": {
                com.brigadajato.screens.ModeSelectScreen ms = new com.brigadajato.screens.ModeSelectScreen(this, com.brigadajato.screens.SetupScreen.Kind.OFFLINE);
                if (p.length > 1) ms.autoPickAt = Float.parseFloat(p[1]);
                return ms;
            }
            case "setupmode": return new com.brigadajato.screens.SetupScreen(this, com.brigadajato.screens.SetupScreen.Kind.OFFLINE,
                    com.brigadajato.world.ModeType.valueOf(p.length > 1 ? p[1] : "BOMB"));
            case "multiplayer": return new com.brigadajato.screens.MultiplayerScreen(this);
            case "host": {
                com.brigadajato.world.MatchConfig cfg = settings.lastMatch.copy();
                return com.brigadajato.screens.LobbyScreen.host(this, cfg);
            }
            case "join": return com.brigadajato.screens.LobbyScreen.client(this, p.length > 1 ? p[1] : "127.0.0.1");
            default: return new MenuScreen(this);
        }
    }

    /** Teste: tira a screenshot agora (se --shot foi passado). */
    public void shotNow() {
        if (shotPath != null) elapsed = Math.max(elapsed, shotTime);
    }

    private void checkAutoShot(float dt) {
        if (shotPath == null) return;
        elapsed += dt;
        if (elapsed < shotTime) return;
        String path = shotPath;
        shotPath = null;
        com.badlogic.gdx.graphics.Pixmap pm = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(path), pm, -1, true);
        pm.dispose();
        Gdx.app.log("BrigadaJato", "screenshot salvo: " + path);
        if (System.getProperty("brigadajato.exitAfterShot") != null) Gdx.app.exit();
    }

    public boolean touchUi() {
        return forceTouch || platform.isMobile() || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
    }

    public static GameMap loadMap(String id) {
        String text = Gdx.files.internal("maps/" + id + ".txt").readString("UTF-8");
        GameMap m = GameMap.parse(id, text);
        String force = System.getProperty("brigadajato.theme"); // so para testes no PC
        if (force != null) m.theme = com.brigadajato.world.Theme.parse(force);
        return m;
    }

    @Override
    public void render() {
        audio.update(Gdx.graphics.getDeltaTime());
        super.render();
        checkAutoShot(Math.min(Gdx.graphics.getDeltaTime(), 0.1f));
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        menuBg.dispose();
        batch.dispose();
        assets.dispose();
    }
}
