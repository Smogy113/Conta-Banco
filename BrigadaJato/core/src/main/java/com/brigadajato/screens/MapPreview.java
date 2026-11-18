package com.brigadajato.screens;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.world.C;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Names;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;
import com.brigadajato.render.WorldRenderer;

import java.util.Random;

/**
 * Previa ao vivo de um mapa: uma partidinha de bots rodando de verdade, desenhada numa textura
 * pequena, com a camera passeando e seguindo a acao. Usada no carrossel de mapas e no carregamento.
 */
public class MapPreview implements Disposable {
    private final BrigadaJatoGame game;
    private final WorldRenderer renderer;
    private final OrthographicCamera cam = new OrthographicCamera();
    private final int fw, fh;
    private FrameBuffer fbo;
    private TextureRegion region;
    private boolean failed;
    private World world;
    private String mapId;
    private float acc, followTimer, camX, camY;
    private int follow = -1;
    private final Random rng = new Random();
    /** tempo desde que o mapa trocou (para a transicao de "chiado") */
    public float sinceChange = 99;
    public int bots = 4;
    /** altura da visao (unidades do mundo) */
    public float viewH = 560;

    public MapPreview(BrigadaJatoGame game, int w, int h) {
        this.game = game;
        this.fw = w;
        this.fh = h;
        renderer = new WorldRenderer(game.assets, game.audio, game.settings);
    }

    public String mapId() {
        return mapId;
    }

    public void setMap(String id) {
        if (id.equals(mapId)) return;
        mapId = id;
        MatchConfig cfg = new MatchConfig();
        cfg.mode = ModeType.DEATHMATCH;
        cfg.mapId = id;
        cfg.timeLimit = 0;
        cfg.scoreLimit = 0;
        cfg.difficulty = 2;
        long t0 = System.nanoTime();
        world = new World(BrigadaJatoGame.loadMap(id), cfg, true, rng.nextLong());
        long t1 = System.nanoTime();
        int start = rng.nextInt(Names.BOTS.length);
        for (int i = 0; i < bots; i++) world.addPlayer(Names.BOTS[(start + i) % Names.BOTS.length], C.TEAM_NONE, Look.random(rng), true);
        world.start();
        long t2 = System.nanoTime();
        renderer.setWorld(world);
        long t3 = System.nanoTime();
        camX = world.map.worldW() / 2;
        camY = world.map.worldH() / 2;
        follow = -1;
        followTimer = 0;
        sinceChange = 0;
        // avanca um pouco para ja comecar com todo mundo em campo
        for (int i = 0; i < 90; i++) step();
        long t4 = System.nanoTime();
        if (PERF) com.badlogic.gdx.Gdx.app.log("perf", String.format("setMap %s: mundo=%.1f inicio=%.1f arte=%.1f aquecer=%.1f ms", id, (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t4 - t3) / 1e6));
    }

    private static final boolean PERF = "true".equals(System.getProperty("brigadajato.perf"));

    private void step() {
        world.update(C.STEP);
        world.events.clear();
    }

    public void update(float dt) {
        if (world == null) return;
        sinceChange += dt;
        acc += Math.min(dt, 0.1f);
        int n = 0;
        while (acc >= C.STEP && n < 3) {
            step();
            acc -= C.STEP;
            n++;
        }
        if (n == 3) acc = 0;
        renderer.update(dt);
        // camera: segue um bot por alguns segundos e troca
        followTimer -= dt;
        Player f = follow >= 0 ? world.player(follow) : null;
        if (followTimer <= 0 || f == null || !f.alive) {
            followTimer = 3.5f + rng.nextFloat() * 2;
            Player pick = null;
            for (int tries = 0; tries < 8 && pick == null; tries++) {
                Player q = world.players.get(rng.nextInt(world.players.size));
                if (q.alive) pick = q;
            }
            follow = pick != null ? pick.id : -1;
            f = pick;
        }
        float tx = f != null ? f.x : world.map.worldW() / 2, ty = f != null ? f.centerY() : world.map.worldH() / 2;
        float k = Math.min(1, dt * 2.2f);
        camX += (tx - camX) * k;
        camY += (ty - camY) * k;
    }

    /** Desenha a previa na textura (chamar com o batch fechado). */
    public void render() {
        if (world == null || failed) return;
        if (fbo == null) {
            try {
                fbo = new FrameBuffer(Pixmap.Format.RGB888, fw, fh, false);
            } catch (Exception e) {
                failed = true;
                return;
            }
            fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            region = new TextureRegion(fbo.getColorBufferTexture());
            region.flip(false, true);
        }
        float viewH = this.viewH;
        cam.viewportHeight = viewH;
        cam.viewportWidth = viewH * fw / (float) fh;
        cam.zoom = 1;
        float hw = cam.viewportWidth / 2, hh = cam.viewportHeight / 2;
        float mw = world.map.worldW(), mh = world.map.worldH();
        float cx = hw * 2 >= mw ? mw / 2 : MathUtils.clamp(camX, hw, mw - hw);
        float cy = hh * 2 >= mh ? mh / 2 : MathUtils.clamp(camY, hh, mh - hh);
        cam.position.set(cx, cy, 0);
        cam.update();
        fbo.begin();
        ScreenUtils.clear(0, 0, 0, 1);
        renderer.targetW = fw;
        renderer.targetH = fh;
        game.batch.setProjectionMatrix(cam.combined);
        game.batch.begin();
        renderer.render(game.draw, cam, world, null, false, 0, 0);
        game.batch.end();
        game.batch.setColor(1, 1, 1, 1);
        fbo.end();
    }

    public TextureRegion texture() {
        return failed ? null : region;
    }

    @Override
    public void dispose() {
        if (fbo != null) fbo.dispose();
        renderer.dispose();
    }
}
