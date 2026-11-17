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
    /** teste no PC: a IA controla o jogador local (--autoplay) */
    public static final boolean AUTOPLAY = "true".equals(System.getProperty("brigadajato.autoplay"));

    // ------------------------------------------------------------------ fabricas

    public static GameScreen offline(BrigadaJatoGame game, MatchConfig cfg) {
        World w = new World(BrigadaJatoGame.loadMap(cfg.mapId), cfg, true, System.nanoTime());
        Random rng = new Random();
        boolean teams = cfg.mode.teams && cfg.mode != ModeType.SURVIVAL && cfg.mode != ModeType.TRAINING;
        int team = teams ? (cfg.playerTeam == 0 ? C.TEAM_RED : cfg.playerTeam) : (cfg.mode.teams ? C.TEAM_RED : C.TEAM_NONE);
        Player me = w.addPlayer(game.settings.name, team, game.settings.look.copy(), false);
        if (AUTOPLAY) me.bot = new com.brigadajato.world.ai.BotBrain(me, 2, rng.nextLong());
        int start = rng.nextInt(Names.BOTS.length);
        for (int i = 0; i < cfg.bots; i++) {
            int t = teams ? w.balancedTeam() : (cfg.mode == ModeType.SURVIVAL ? C.TEAM_RED : C.TEAM_NONE);
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
        Player p = local;
        camX = p != null ? p.x : world.map.worldW() / 2;
        camY = p != null ? p.y : world.map.worldH() / 2;
        if (p != null) controls.aimAngle = p.x < world.map.worldW() / 2 ? 0 : MathUtils.PI;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, controls));
        game.audio.music(false);
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
        cam.viewportHeight = 600;
        cam.viewportWidth = 600 * aspect;
        hudCam.viewportHeight = 720;
        hudCam.viewportWidth = 720 * aspect;
        hudCam.position.set(hudCam.viewportWidth / 2, 360, 0);
        hudCam.update();
        hud.resize(hudCam.viewportWidth, 720);
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
            client.receive(world, netEvents, localId);
            for (WorldEvent e : netEvents) dispatch(e);
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
            if (client.net.state == NetClient.State.CLOSED || client.net.state == NetClient.State.FAILED) {
                go(new MultiplayerScreen(game, I18n.t("disconnected")));
                return;
            }
        } else if (!paused || host != null) {
            acc += dt;
            int steps = 0;
            while (acc >= C.STEP && steps < 6) {
                if (host != null) {
                    host.updateInGame(world);
                    host.applyInputs(world);
                }
                world.update(C.STEP);
                for (WorldEvent e : world.events) dispatch(e);
                if (host != null) host.afterStep(world, world.events);
                world.events.clear();
                acc -= C.STEP;
                steps++;
            }
            if (steps == 6) acc = 0;
        }

        renderer.update(dt);
        hud.update(dt);
        updateCamera(dt);
        updateBuyMenu();
        game.audio.jetpack(local != null && local.alive && local.jetting && !paused);

        // ---- desenho
        ScreenUtils.clear(0, 0, 0, 1);
        game.batch.setProjectionMatrix(cam.combined);
        game.batch.begin();
        renderer.render(game.draw, cam, world, local, true, crossX, crossY);
        game.batch.end();

        game.batch.setProjectionMatrix(hudCam.combined);
        game.batch.begin();
        boolean nearWeapon = local != null && local.alive && world.nearestWeaponPickup(local) != null;
        boolean useAvail = local != null && local.alive && useAvailable(local);
        controls.updateVisibility(local, nearWeapon, useAvail);
        hud.draw(game.draw, world, local, fpsSmooth, controls.scoreboardVisible() || (world.mode.over && !resultsShown && overTimer > 0.5f), mobile);
        if (!paused && !resultsShown) controls.draw(game.draw, game.assets.small, game.assets.normal, local, painter);
        if (!mobile && local != null && local.alive && nearWeapon) {
            game.assets.small.draw(game.batch, "[E] " + I18n.t("pickup"), hudCam.viewportWidth / 2, 160, 0, com.badlogic.gdx.utils.Align.center, false);
        }
        if (!mobile && useAvail) {
            game.assets.small.draw(game.batch, "[E] " + I18n.t("use"), hudCam.viewportWidth / 2, 135, 0, com.badlogic.gdx.utils.Align.center, false);
        }
        game.batch.end();
        game.batch.setColor(1, 1, 1, 1);

        stage.getViewport().apply();
        stage.act(dt);
        stage.draw();

        // ---- fim de partida
        if (world.mode.over) {
            overTimer += dt;
            if (!statsSaved) saveStats();
            if (overTimer > 2.5f && !resultsShown) showResults();
        }
    }

    private void dispatch(WorldEvent e) {
        renderer.handle(e, local);
        hud.handle(e, local);
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
        for (Player q : world.players) {
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
        if (focus == null || !focus.alive) {
            // espectador: segue um aliado vivo (modo bomba) ou fica parado
            if (world.mode instanceof BombMode || world.mode instanceof SurvivalMode) {
                for (Player p : world.players) if (p.alive && (local == null || p.team == local.team)) {
                    focus = p;
                    break;
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
        cam.zoom = zoom;
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
        float s = renderer.shake;
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
                WeaponType.SAW, WeaponType.GRENADE_LAUNCHER, WeaponType.ROCKET, WeaponType.LASER, WeaponType.SNIPER, WeaponType.LMG};
        buyCodes = new int[list.length + 3];
        int i = 0;
        Table grid = new Table();
        for (WeaponType t : list) {
            final int code = t.ordinal();
            buyCodes[i++] = code;
            grid.add(button((I18n.pt ? t.pt : t.en) + "  $" + t.price, "blue-small", () -> buy(code))).width(250).height(44).pad(2);
            if (i % 3 == 0) grid.row();
        }
        buyCodes[i++] = BombMode.ITEM_ARMOR;
        grid.add(button(I18n.t("armor") + "  $" + BombMode.PRICE_ARMOR, "green-small", () -> buy(BombMode.ITEM_ARMOR))).width(250).height(44).pad(2);
        if (i % 3 == 0) grid.row();
        buyCodes[i++] = BombMode.ITEM_FRAG;
        grid.add(button(I18n.t("frag") + "  $" + BombMode.PRICE_FRAG, "green-small", () -> buy(BombMode.ITEM_FRAG))).width(250).height(44).pad(2);
        if (i % 3 == 0) grid.row();
        buyCodes[i++] = BombMode.ITEM_MINE;
        grid.add(button(I18n.t("mine") + "  $" + BombMode.PRICE_MINE, "green-small", () -> buy(BombMode.ITEM_MINE))).width(250).height(44).pad(2);
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
        s.xp += local.kills * 10 + (win ? 60 : 15);
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
        game.setScreen(next);
        dispose();
    }

    @Override
    public void dispose() {
        stage.dispose();
        renderer.dispose();
        hud.dispose();
    }
}
