package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.input.TouchControls;
import com.brigadajato.net.ClientSession;
import com.brigadajato.net.HostSession;
import com.brigadajato.net.NetClient;
import com.brigadajato.render.Hud;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.render.WorldRenderer;
import com.brigadajato.world.C;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Names;
import com.brigadajato.world.Player;
import com.brigadajato.world.PlayerInput;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;
import com.brigadajato.world.modes.BombMode;
import com.brigadajato.world.modes.SurvivalMode;

import java.util.Random;

/** A partida em si: simulacao, camera, desenho, HUD, controles e menus sobrepostos. */
public class GameScreen extends ScreenAdapter {
    private final BrigadaJatoGame game;
    private final World world;
    private final HostSession host;
    private final ClientSession client;
    private Player local;
    private final int localId;

    private final WorldRenderer renderer;
    private final Hud hud;
    private final TouchControls controls;
    private final SoldierPainter painter = new SoldierPainter();
    private final OrthographicCamera cam = new OrthographicCamera();
    private final OrthographicCamera hudCam = new OrthographicCamera();
    private final Stage stage;
    private final Table overlay = new Table();
    private final Vector3 tmp = new Vector3();
    private final Array<WorldEvent> netEvents = new Array<>();
    private final PlayerInput clientInput = new PlayerInput();
    private final com.brigadajato.replay.ReplayDirector director;
    private final com.brigadajato.render.ColorFilter filter = new com.brigadajato.render.ColorFilter();
    private final OrthographicCamera hudScaled = new OrthographicCamera();

    private float acc, sendTimer;
    private boolean paused;
    private boolean resultsShown, statsSaved;
    private float overTimer;
    private float zoom = 1f;
    private float camX, camY;
    private boolean buyShown;
    private int lastBombPhase = -1;
    private float fpsSmooth = 60;
    private float crossX, crossY;
    private boolean mobile;
    private float lifeTime;
    private float zoomPunch;
    private float shotWait;
    private float holdTime;
    // medicao de desempenho (--perf=true no PC)
    private static final boolean PERF = "true".equals(System.getProperty("brigadajato.perf"));
    private long pSim, pRender, pLights, pHud, pFrames;
    private float pTimer;
    private static final String SHOT_WHEN = System.getProperty("brigadajato.shotWhen");
    private static final float SHOT_DELAY = Float.parseFloat(System.getProperty("brigadajato.shotDelay", "1.3"));
    /** teste no PC: a IA controla o jogador local (--autoplay) */
    public static final boolean AUTOPLAY = "true".equals(System.getProperty("brigadajato.autoplay"));

    // ------------------------------------------------------------------ fabricas

    public static GameScreen offline(BrigadaJatoGame game, MatchConfig cfg) {
        World w = new World(BrigadaJatoGame.loadMap(cfg.mapId), cfg, true, System.nanoTime());
        Random rng = new Random();
        boolean teams = cfg.mode.chooseTeams();
        int team = teams ? (cfg.playerTeam == 0 ? C.TEAM_RED : cfg.playerTeam) : (cfg.mode.teams ? C.TEAM_RED : C.TEAM_NONE);
        Player me = w.addPlayer(game.settings.name, team, game.settings.look.copy(), false);
        if (AUTOPLAY) me.bot = new com.brigadajato.world.ai.BotBrain(me, 2, rng.nextLong());
        int start = rng.nextInt(Names.BOTS.length);
        int nb = cfg.mode == ModeType.DUEL ? 1 : cfg.bots;
        for (int i = 0; i < nb; i++) {
            int t = teams ? w.balancedTeam() : (cfg.mode.teams ? C.TEAM_RED : C.TEAM_NONE);
            w.addPlayer(Names.BOTS[(start + i) % Names.BOTS.length], t, Look.random(rng), true);
        }
        w.start();
        return new GameScreen(game, w, me.id, null, null);
    }

    public static GameScreen host(BrigadaJatoGame game, HostSession h) {
        World w = h.startMatch(BrigadaJatoGame.loadMap(h.cfg.mapId));
        if (AUTOPLAY) w.player(0).bot = new com.brigadajato.world.ai.BotBrain(w.player(0), 2, 1);
        return new GameScreen(game, w, 0, h, null);
    }

    public static GameScreen client(BrigadaJatoGame game, ClientSession c) {
        World w = c.startedWorld;
        int id = c.startedLocalId;
        c.startedWorld = null;
        return new GameScreen(game, w, id, null, c);
    }

    private GameScreen(BrigadaJatoGame game, World world, int localId, HostSession host, ClientSession client) {
        this.game = game;
        this.world = world;
        this.localId = localId;
        this.host = host;
        this.client = client;
        local = world.player(localId);
        mobile = game.touchUi();
        renderer = new WorldRenderer(game.assets, game.audio, game.settings);
        renderer.setWorld(world);
        hud = new Hud(game.assets, game.settings);
        hud.setWorld(world);
        controls = new TouchControls(game.settings, mobile);
        stage = new Stage(new ExtendViewport(1280, 720), game.batch);
        overlay.setFillParent(true);
        stage.addActor(overlay);
        director = new com.brigadajato.replay.ReplayDirector(world, game.assets, game.settings);
        game.settings.applyFps();
        Player p = local;
        camX = p != null ? p.x : world.map.worldW() / 2;
        camY = p != null ? p.y : world.map.worldH() / 2;
        if (p != null) controls.aimAngle = p.x < world.map.worldW() / 2 ? 0 : MathUtils.PI;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, controls));
        game.audio.music(false);
        game.audio.mapMusic(com.brigadajato.Audio.trackFor(world.map.theme));
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void pause() {
        // app foi para segundo plano: pausa partidas offline
        game.audio.jetpack(false);
        if (!paused && !resultsShown && host == null && client == null) togglePause();
    }

    @Override
    public void hide() {
        game.audio.jetpack(false);
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void resize(int width, int height) {
        float aspect = width / (float) Math.max(1, height);
        float viewH = 520 * game.settings.cameraZoom;
        cam.viewportHeight = viewH;
        cam.viewportWidth = viewH * aspect;
        director.resize(600 * aspect, 600);
        hudCam.viewportHeight = 720;
        hudCam.viewportWidth = 720 * aspect;
        hudCam.position.set(hudCam.viewportWidth / 2, 360, 0);
        hudCam.update();
        float hs = game.settings.hudScale;
        hudScaled.viewportHeight = 720 / hs;
        hudScaled.viewportWidth = 720 * aspect / hs;
        hudScaled.position.set(hudScaled.viewportWidth / 2, hudScaled.viewportHeight / 2, 0);
        hudScaled.update();
        hud.resize(hudScaled.viewportWidth, hudScaled.viewportHeight);
        controls.resize(hudCam.viewportWidth, 720);
        stage.getViewport().update(width, height, true);
    }

    // ------------------------------------------------------------------ loop

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.1f);
        lifeTime += dt;
        fpsSmooth += (Gdx.graphics.getFramesPerSecond() - fpsSmooth) * 0.05f;
        local = world.player(localId);

        if (controls.pauseRequested) {
            controls.pauseRequested = false;
            if (!resultsShown) togglePause();
        }

        if (TOUCH_TEST && mobile) fakeFingers();
        // ---- entrada do jogador local
        PlayerInput target = client != null ? clientInput : (local != null ? local.input : null);
        if (target != null && local != null && !paused && !AUTOPLAY) {
            float sx = 0, sy = 0;
            if (!mobile) {
                tmp.set(local.x, local.shoulderY(), 0);
                cam.project(tmp);
                sx = tmp.x;
                sy = Gdx.graphics.getHeight() - tmp.y;
            }
            controls.fill(target, sx, sy);
            if (mobile && game.settings.aimAssist) target.aim = aimAssist(local, target.aim);
            if (client != null) {
                local.aim = target.aim;
                local.facing = MathUtils.cos(target.aim) >= 0 ? 1 : -1;
            }
        } else if (target != null && paused) {
            target.moveX = target.moveY = 0;
            target.fire = false;
        }

        // ---- simulacao
        if (client != null) {
            client.receive(world, netEvents, localId, dt);
            for (int i62_ = 0; i62_ < netEvents.size; i62_++) {
                WorldEvent e = netEvents.get(i62_); dispatch(e);
            }
            netEvents.clear();
            world.update(dt);
            sendTimer -= dt;
            if (sendTimer <= 0) {
                sendTimer = 1f / 30f;
                client.net.sendInput(clientInput);
                clientInput.clearPresses();
            }
            if (client.backToLobby) {
                go(LobbyScreen.resume(game, null, client));
                return;
            }
            if (client.rejoinedWorld != null) { // voltou depois de cair
                World nw = client.rejoinedWorld;
                client.rejoinedWorld = null;
                go(new GameScreen(game, nw, client.rejoinedLocalId, null, client));
                return;
            }
            if (client.kicked != null) {
                go(new MultiplayerScreen(game, I18n.t("kicked".equals(client.kicked) ? "kicked" : "disconnected")));
                return;
            }
            if (client.gaveUp()) {
                go(new MultiplayerScreen(game, I18n.t("disconnected")));
                return;
            }
        } else if (!paused || host != null) {
            long t0 = System.nanoTime();
            if (local != null && !local.alive && director.holdRespawn() && holdTime < 2.5f) {
                holdTime += dt;
                local.respawnTimer = Math.max(local.respawnTimer, 0.15f);
            } else if (local != null && local.alive) holdTime = 0;
            acc += dt;
            int steps = 0;
            while (acc >= C.STEP && steps < 6) {
                if (host != null) {
                    host.updateInGame(world);
                    host.applyInputs(world);
                }
                world.update(C.STEP);
                for (int i63_ = 0; i63_ < world.events.size; i63_++) {
                    WorldEvent e = world.events.get(i63_); dispatch(e);
                }
                if (host != null) host.afterStep(world, world.events);
                world.events.clear();
                acc -= C.STEP;
                steps++;
            }
            if (steps == 6) acc = 0;
            pSim += System.nanoTime() - t0;
        }

        renderer.update(dt);
        hud.update(dt);
        float impact = hud.consumeImpact();
        if (impact > 0) {
            renderer.shake = Math.max(renderer.shake, 3 + impact * 4);
            zoomPunch = Math.min(0.09f, 0.035f * impact);
        }
        zoomPunch = Math.max(0, zoomPunch - dt * 0.35f);
        director.update(dt, local);
        if (director.active() && Gdx.input.justTouched() && !paused) director.skip();
        if (SHOT_WHEN != null && director.active() && director.mode.name().equalsIgnoreCase(SHOT_WHEN)) {
            shotWait += dt;
            if (shotWait > SHOT_DELAY) game.shotNow();
        }
        if (world.mode.over && !director.reelDone && overTimer > 1.2f) {
            if (game.settings.highlights) director.startReel();
            else director.reelDone = true;
        }
        updateCamera(dt);
        updateBuyMenu();
        game.audio.jetpack(local != null && local.alive && local.jetting && !paused);

        // ---- desenho
        ScreenUtils.clear(0, 0, 0, 1);
        int fmode = game.settings.colorFilter;
        if (director.active()) {
            director.renderWorld(game.draw, filter, fmode);
            game.batch.setProjectionMatrix(hudCam.combined);
            game.batch.begin();
            director.drawOverlay(game.draw, hudCam.viewportWidth, 720);
            game.batch.end();
            game.batch.setColor(1, 1, 1, 1);
            stage.getViewport().apply();
            stage.act(dt);
            stage.draw();
            if (world.mode.over) {
                overTimer += dt;
                if (!statsSaved) saveStats();
            }
            return;
        }
        boolean filtered = filter.begin(game.batch, fmode);
        long r0 = System.nanoTime();
        // qualidade baixa/media: desenha o mundo numa resolucao menor e amplia (bem mais leve para a GPU)
        float scale = game.settings.quality <= 0 ? 0.6f : (game.settings.quality == 1 ? 0.8f : 1f);
        boolean scaled = scale < 0.99f && beginWorldFbo(scale);
        game.batch.setProjectionMatrix(cam.combined);
        game.batch.begin();
        if (filtered) filter.uniforms(fmode);
        renderer.render(game.draw, cam, world, local, true, crossX, crossY);
        game.batch.end();
        if (filtered) filter.end(game.batch);
        long r1 = System.nanoTime();
        renderer.renderLights(game.batch, cam, local);
        if (scaled) endWorldFbo();
        long r2 = System.nanoTime();
        pRender += r1 - r0;
        pLights += r2 - r1;

        game.batch.setProjectionMatrix(hudScaled.combined);
        game.batch.begin();
        boolean nearWeapon = local != null && local.alive && world.nearestWeaponPickup(local) != null;
        boolean useAvail = local != null && local.alive && useAvailable(local);
        controls.updateVisibility(local, nearWeapon, useAvail);
        hud.draw(game.draw, world, local, fpsSmooth, controls.scoreboardVisible() || (world.mode.over && !resultsShown && overTimer > 0.5f), mobile);
        game.batch.setProjectionMatrix(hudCam.combined);
        if (!paused && !resultsShown) controls.draw(game.draw, game.assets.small, game.assets.normal, local, painter);
        if (!paused && !resultsShown && local != null && local.alive) controls.drawWheel(game.draw, game.assets.small, game.assets.normal);
        if (client != null && client.reconnecting >= 0) {
            game.draw.color(0, 0, 0, 0.55f);
            game.draw.rect(0, 300, hudCam.viewportWidth, 120);
            game.assets.big.draw(game.batch, I18n.t("reconnecting"), hudCam.viewportWidth / 2, 380, 0, com.badlogic.gdx.utils.Align.center, false);
        }
        if (!mobile && local != null && local.alive && nearWeapon) {
            game.assets.small.draw(game.batch, "[E] " + I18n.t("pickup"), hudCam.viewportWidth / 2, 160, 0, com.badlogic.gdx.utils.Align.center, false);
        }
        if (!mobile && useAvail) {
            game.assets.small.draw(game.batch, "[E] " + I18n.t("use"), hudCam.viewportWidth / 2, 135, 0, com.badlogic.gdx.utils.Align.center, false);
        }
        game.batch.end();
        game.batch.setColor(1, 1, 1, 1);
        pHud += System.nanoTime() - r2;
        if (PERF) {
            pFrames++;
            pTimer += dt;
            if (pTimer > 5) {
                Gdx.app.log("perf", String.format("fps=%.0f sim=%.2fms render=%.2fms luz=%.2fms hud=%.2fms render_calls=%d particulas=%d",
                        fpsSmooth, pSim / 1e6 / pFrames, pRender / 1e6 / pFrames, pLights / 1e6 / pFrames, pHud / 1e6 / pFrames,
                        game.batch.renderCalls, renderer.fx.activeCount()));
                pSim = pRender = pLights = pHud = pFrames = 0;
                pTimer = 0;
            }
        }

        stage.getViewport().apply();
        stage.act(dt);
        stage.draw();

        // ---- fim de partida
        if (world.mode.over) {
            overTimer += dt;
            if (!statsSaved) saveStats();
            if (overTimer > 2.5f && !resultsShown && director.reelDone && !director.active()) showResults();
        }
    }

    private com.badlogic.gdx.graphics.glutils.FrameBuffer worldFbo;
    private com.badlogic.gdx.graphics.g2d.TextureRegion worldRegion;
    private boolean fboFailed;
    private final com.badlogic.gdx.math.Matrix4 screenMat = new com.badlogic.gdx.math.Matrix4();

    private boolean beginWorldFbo(float scale) {
        if (fboFailed) return false;
        int w = Math.max(64, Math.round(Gdx.graphics.getBackBufferWidth() * scale));
        int h = Math.max(36, Math.round(Gdx.graphics.getBackBufferHeight() * scale));
        if (worldFbo == null || worldFbo.getWidth() != w || worldFbo.getHeight() != h) {
            if (worldFbo != null) worldFbo.dispose();
            try {
                worldFbo = new com.badlogic.gdx.graphics.glutils.FrameBuffer(com.badlogic.gdx.graphics.Pixmap.Format.RGB888, w, h, false);
            } catch (Exception e) {
                fboFailed = true;
                worldFbo = null;
                return false;
            }
            worldFbo.getColorBufferTexture().setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
            worldRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(worldFbo.getColorBufferTexture());
            worldRegion.flip(false, true);
        }
        worldFbo.begin();
        ScreenUtils.clear(0, 0, 0, 1);
        return true;
    }

    private void endWorldFbo() {
        worldFbo.end();
        screenMat.setToOrtho2D(0, 0, 1, 1);
        game.batch.setProjectionMatrix(screenMat);
        game.batch.disableBlending();
        game.batch.begin();
        game.batch.setColor(1, 1, 1, 1);
        game.batch.draw(worldRegion, 0, 0, 1, 1);
        game.batch.end();
        game.batch.enableBlending();
    }

    private int killerId = -1;

    private void dispatch(WorldEvent e) {
        renderer.handle(e, local);
        hud.handle(e, local);
        director.onEvent(e, local);
        if (e.type == WorldEvent.Type.KILL && e.b == localId) killerId = e.a;
    }

    /** Teste dos joysticks no PC: dois "dedos" falsos (--touchtest=true --touch=true). */
    private static final boolean TOUCH_TEST = "true".equals(System.getProperty("brigadajato.touchtest"));
    private boolean fingersDown;

    private void fakeFingers() {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        int lx = (int) (w * 0.14f), ly = (int) (h * 0.76f), rx = (int) (w * 0.74f), ry = (int) (h * 0.7f);
        if (!fingersDown && lifeTime > 1) {
            controls.touchDown(lx, ly, 0, 0);
            controls.touchDown(rx, ry, 1, 0);
            fingersDown = true;
        }
        if (fingersDown) {
            float t = lifeTime;
            // esquerdo: anda para a direita e liga o jetpack de vez em quando
            controls.touchDragged(lx + 70, ly - (int) (Math.sin(t * 1.5f) > 0 ? 75 : 10), 0);
            // direito: gira a mira e empurra ate a borda (atira)
            controls.touchDragged(rx + (int) (MathUtils.cos(t * 0.8f) * 110), ry - (int) (MathUtils.sin(t * 0.8f) * 110), 1);
        }
    }

    private float aimAssist(Player me, float aim) {
        Player best = null;
        float bestDiff = 0.22f;
        for (int i64_ = 0; i64_ < world.players.size; i64_++) {
            Player q = world.players.get(i64_);
            if (!q.alive || !world.enemies(me, q) || q.dummy && world.config.mode != ModeType.TRAINING) continue;
            float d = me.distTo(q);
            if (d > 950) continue;
            float ang = MathUtils.atan2(q.centerY() - me.shoulderY(), q.x - me.x);
            float diff = Math.abs(angleDiff(aim, ang));
            if (diff < bestDiff && world.lineOfSight(me.x, me.shoulderY(), q.x, q.centerY())) {
                bestDiff = diff;
                best = q;
            }
        }
        if (best == null) return aim;
        float ang = MathUtils.atan2(best.centerY() - me.shoulderY(), best.x - me.x);
        return aim + angleDiff(aim, ang) * 0.35f;
    }

    private static float angleDiff(float a, float b) {
        float d = b - a;
        while (d > MathUtils.PI) d -= MathUtils.PI2;
        while (d < -MathUtils.PI) d += MathUtils.PI2;
        return d;
    }

    private boolean useAvailable(Player p) {
        if (world.nearLever(p) >= 0) return true;
        if (!(world.mode instanceof BombMode)) return false;
        BombMode bm = (BombMode) world.mode;
        if (p.hasBomb && bm.siteAt(p.x, p.y) >= 0) return true;
        if (bm.bombState == BombMode.B_PLANTED && p.team == bm.defendTeam()) {
            float dx = p.x - bm.bombX, dy = p.y - bm.bombY;
            return dx * dx + dy * dy < 60 * 60;
        }
        return false;
    }

    private void updateCamera(float dt) {
        Player focus = local;
        if (focus != null && !focus.alive && killerId >= 0 && killerId != localId) {
            // camera mostra quem te abateu enquanto espera renascer
            Player k = world.player(killerId);
            if (k != null && k.alive) focus = k;
        }
        if (focus == null || !focus.alive) {
            // espectador: segue um aliado vivo (modo bomba) ou fica parado
            if (world.mode instanceof BombMode || world.mode instanceof SurvivalMode || world.mode instanceof com.brigadajato.world.modes.BattleRoyaleMode
                    || world.mode instanceof com.brigadajato.world.modes.DuelMode) {
                for (int i65_ = 0; i65_ < world.players.size; i65_++) {
                    Player p = world.players.get(i65_); if (p.alive && (local == null || p.team == local.team)) {
                    focus = p;
                    break;
                }
}
            }
        }
        float wantZoom = 1f;
        if (local != null && local.alive) {
            Gun g = local.gun();
            float wz = g != null ? g.type.zoom : 1f;
            wantZoom = controls.zoomToggled ? Math.max(1.45f, wz * 1.3f) : (wz > 1.3f ? 1.15f : 1f);
        }
        zoom += (wantZoom - zoom) * Math.min(1, dt * 4);
        cam.zoom = zoom * (1 - zoomPunch);
        if (focus != null && (focus.alive || focus == local)) {
            float look = 110 * zoom;
            float ax = MathUtils.cos(focus.aim), ay = MathUtils.sin(focus.aim);
            float tx = focus.x + ax * look, ty = focus.centerY() + ay * look * 0.6f;
            float k = Math.min(1, dt * 7);
            camX += (tx - camX) * k;
            camY += (ty - camY) * k;
        }
        float hw = cam.viewportWidth * zoom / 2, hh = cam.viewportHeight * zoom / 2;
        float mw = world.map.worldW(), mh = world.map.worldH();
        float margin = 140; // deixa ver um pouco alem da borda para o HUD nao cobrir o jogador
        float cx = hw * 2 >= mw + margin * 2 ? mw / 2 : MathUtils.clamp(camX, hw - margin, mw - hw + margin);
        float cy = hh * 2 >= mh + margin * 2 ? mh / 2 : MathUtils.clamp(camY, hh - margin, mh - hh + margin);
        float s = renderer.shake * game.settings.shake;
        cam.position.set(cx + MathUtils.random(-s, s), cy + MathUtils.random(-s, s), 0);
        cam.update();
        // mira
        if (local != null) {
            if (!mobile) {
                tmp.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                cam.unproject(tmp);
                crossX = tmp.x;
                crossY = tmp.y;
            } else {
                float r = 150;
                crossX = local.x + MathUtils.cos(local.aim) * r;
                crossY = local.shoulderY() + MathUtils.sin(local.aim) * r;
            }
        }
    }

    // ------------------------------------------------------------------ menus sobrepostos

    private TextButton button(String text, String style, final Runnable r) {
        TextButton b = new TextButton(text, game.skin, style);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.audio.ui("ui_click");
                r.run();
            }
        });
        return b;
    }

    private void togglePause() {
        paused = !paused;
        overlay.clearChildren();
        controls.release();
        if (!paused) return;
        Table p = new Table(game.skin);
        p.setBackground("panel");
        p.pad(26);
        p.defaults().pad(8);
        p.add(new Label(I18n.t("paused"), game.skin, "big")).row();
        if (host != null || client != null) p.add(new Label(I18n.t("multiplayer"), game.skin, "gray")).row();
        p.add(button(I18n.t("resume"), "green", this::togglePause)).width(320).height(64).row();
        p.add(button(I18n.t("exit_match"), "red", this::leave)).width(320).height(64).row();
        overlay.add(p);
    }

    private void leave() {
        if (host != null) {
            host.close();
            game.platform.setMulticastLock(false);
        }
        if (client != null) client.close();
        game.audio.music(true);
        go(new MenuScreen(game));
    }

    private void updateBuyMenu() {
        if (!(world.mode instanceof BombMode) || local == null || resultsShown || paused) {
            if (buyShown && !paused && !resultsShown) {
                overlay.clearChildren();
                buyShown = false;
            }
            return;
        }
        BombMode bm = (BombMode) world.mode;
        boolean want = bm.phase == BombMode.BUY && local.alive;
        if (want && (!buyShown || bm.phase != lastBombPhase)) showBuy();
        else if (!want && buyShown) {
            overlay.clearChildren();
            buyShown = false;
        }
        lastBombPhase = bm.phase;
        if (buyShown) {
            // atualiza texto do dinheiro
            if (moneyLabel != null) moneyLabel.setText(I18n.f("money", local.money));
            // atalhos no teclado
            for (int k = 0; k < 9; k++) if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_1 + k) && k < buyCodes.length) buy(buyCodes[k]);
        }
    }

    private Label moneyLabel;
    private int[] buyCodes = {};

    private void showBuy() {
        buyShown = true;
        overlay.clearChildren();
        Table p = new Table(game.skin);
        p.setBackground("panel");
        p.pad(14);
        p.defaults().pad(3);
        moneyLabel = new Label(I18n.f("money", local.money), game.skin, "yellow");
        p.add(new Label(I18n.t("buy"), game.skin, "default")).left();
        p.add(moneyLabel).right().row();
        WeaponType[] list = {WeaponType.MAGNUM, WeaponType.SMG, WeaponType.SHOTGUN, WeaponType.FLAMETHROWER, WeaponType.RIFLE,
                WeaponType.SAW, WeaponType.GRENADE_LAUNCHER, WeaponType.ROCKET, WeaponType.LASER, WeaponType.SNIPER, WeaponType.LMG,
                WeaponType.DUAL_PISTOLS, WeaponType.DUAL_SMG, WeaponType.SHIELD, WeaponType.BOW, WeaponType.BOOMERANG, WeaponType.FROST,
                WeaponType.TESLA, WeaponType.GRAVITY, WeaponType.HARPOON};
        buyCodes = new int[list.length + 3];
        int i = 0;
        Table grid = new Table();
        for (WeaponType t : list) {
            final int code = t.ordinal();
            buyCodes[i++] = code;
            grid.add(button((I18n.pt ? t.pt : t.en) + "  $" + t.price, "blue-small", () -> buy(code))).width(225).height(42).pad(2);
            if (i % 4 == 0) grid.row();
        }
        buyCodes[i++] = BombMode.ITEM_ARMOR;
        grid.add(button(I18n.t("armor") + "  $" + BombMode.PRICE_ARMOR, "green-small", () -> buy(BombMode.ITEM_ARMOR))).width(225).height(42).pad(2);
        if (i % 4 == 0) grid.row();
        buyCodes[i++] = BombMode.ITEM_FRAG;
        grid.add(button(I18n.t("frag") + "  $" + BombMode.PRICE_FRAG, "green-small", () -> buy(BombMode.ITEM_FRAG))).width(225).height(42).pad(2);
        if (i % 4 == 0) grid.row();
        buyCodes[i++] = BombMode.ITEM_MINE;
        grid.add(button(I18n.t("mine") + "  $" + BombMode.PRICE_MINE, "green-small", () -> buy(BombMode.ITEM_MINE))).width(225).height(42).pad(2);
        if (i % 4 == 0) grid.row();
        buyCodes = java.util.Arrays.copyOf(buyCodes, buyCodes.length + 3);
        buyCodes[i++] = BombMode.ITEM_SMOKE;
        grid.add(button(I18n.t("nade_2") + "  $" + BombMode.PRICE_SMOKE, "green-small", () -> buy(BombMode.ITEM_SMOKE))).width(225).height(42).pad(2);
        if (i % 4 == 0) grid.row();
        buyCodes[i++] = BombMode.ITEM_FLASH;
        grid.add(button(I18n.t("nade_3") + "  $" + BombMode.PRICE_FLASH, "green-small", () -> buy(BombMode.ITEM_FLASH))).width(225).height(42).pad(2);
        if (i % 4 == 0) grid.row();
        buyCodes[i++] = BombMode.ITEM_STICKY;
        grid.add(button(I18n.t("nade_4") + "  $" + BombMode.PRICE_STICKY, "green-small", () -> buy(BombMode.ITEM_STICKY))).width(225).height(42).pad(2);
        p.add(grid).colspan(2);
        overlay.add(p).expand().bottom().padBottom(mobile ? 250 : 120);
    }

    private void buy(int code) {
        if (client != null) clientInput.buy = code;
        else if (local != null) local.input.buy = code;
    }

    private void saveStats() {
        statsSaved = true;
        game.audio.jetpack(false);
        if (local == null || world.config.mode == ModeType.TRAINING) return;
        com.brigadajato.Settings s = game.settings;
        s.matches++;
        s.totalKills += local.kills;
        s.totalDeaths += local.deaths;
        boolean win = isWin();
        if (win) s.wins++;
        int gained = com.brigadajato.Progression.matchXp(local.kills, win, 0, 0);
        s.xp += gained;
        hud.toast("+" + gained + " XP", 0x80d8ff);
        if (world.mode instanceof SurvivalMode) {
            int wave = ((SurvivalMode) world.mode).wave;
            if (wave > s.bestWave) {
                s.bestWave = wave;
                hud.big(I18n.t("new_record"), 0xffd740, 3);
            }
            s.xp += wave * 20;
        }
        s.save();
        game.audio.ui(win ? "win" : "lose");
    }

    private boolean isWin() {
        if (local == null) return false;
        if (world.mode instanceof SurvivalMode) return ((SurvivalMode) world.mode).wave >= 5;
        if (world.config.mode.teams) return world.mode.winnerTeam == local.team;
        return world.mode.winnerId == local.id;
    }

    private void showResults() {
        resultsShown = true;
        paused = false;
        buyShown = false;
        overlay.clearChildren();
        controls.release();
        Table p = new Table(game.skin);
        p.setBackground("panel");
        p.pad(26);
        p.defaults().pad(6);
        String title;
        if (world.mode instanceof SurvivalMode) {
            title = I18n.f("surv_over", ((SurvivalMode) world.mode).wave);
        } else if (world.config.mode.teams) {
            if (world.mode.winnerTeam <= 0) title = I18n.t("draw");
            else title = I18n.f("team_wins", world.mode.winnerTeam == C.TEAM_RED ? I18n.t("team_red") : I18n.t("team_blue"));
        } else {
            Player w = world.player(world.mode.winnerId);
            title = w == null ? I18n.t("draw") : I18n.f("winner", w.name);
        }
        Label big = new Label(isWin() ? I18n.t("victory") : I18n.t("defeat"), game.skin, "title");
        if (world.mode instanceof SurvivalMode) big.setText(I18n.t("mode_survival"));
        p.add(big).row();
        if (!(world.mode instanceof SurvivalMode)) p.add(new PodiumActor(game.draw, game.assets, world.ranking(), world.config.mode.teams)).size(520, 175).padTop(0).row();
        p.add(new Label(title, game.skin, "big")).row();
        p.add(new Label(I18n.t(world.mode.endReason), game.skin, "gray")).row();
        Table score = new Table(game.skin);
        score.defaults().pad(2, 12, 2, 12);
        score.add(new Label(I18n.t("players"), game.skin, "gray")).left();
        score.add(new Label(I18n.t("kills"), game.skin, "gray"));
        score.add(new Label(I18n.t("deaths"), game.skin, "gray"));
        score.add(new Label(I18n.t("score"), game.skin, "gray")).row();
        int n = 0;
        for (Player pl : world.ranking()) {
            if (pl.dummy || n++ >= 8) continue;
            String style = pl == local ? "yellow" : "small";
            score.add(new Label(pl.name, game.skin, style)).left();
            score.add(new Label("" + pl.kills, game.skin, style));
            score.add(new Label("" + pl.deaths, game.skin, style));
            score.add(new Label("" + pl.score, game.skin, style)).row();
        }
        p.add(score).padTop(6).padBottom(10).row();
        Table buttons = new Table();
        buttons.defaults().pad(6);
        if (host == null && client == null) {
            buttons.add(button(I18n.t("play_again"), "green", () -> go(GameScreen.offline(game, world.config.copy())))).width(280).height(62);
        } else if (host != null) {
            buttons.add(button(I18n.t("lobby"), "green", () -> {
                host.returnToLobby();
                go(LobbyScreen.resume(game, host, null));
            })).width(280).height(62);
        } else {
            buttons.add(new Label(I18n.t("waiting_host"), game.skin, "small"));
        }
        buttons.add(button(I18n.t("menu"), "gray", this::leave)).width(200).height(62);
        p.add(buttons);
        overlay.add(p);
    }

    private void go(com.badlogic.gdx.Screen next) {
        if (!(next instanceof GameScreen)) game.audio.music(true);
        game.setScreen(next);
        dispose();
    }

    @Override
    public void dispose() {
        if (worldFbo != null) worldFbo.dispose();
        director.dispose();
        filter.dispose();
        stage.dispose();
        renderer.dispose();
        hud.dispose();
    }
}
