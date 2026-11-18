package com.brigadajato.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.ScreenUtils;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.render.WorldRenderer;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.World;

/** Teste de desempenho/igualdade visual: desenha um mapa parado de um ponto fixo (--start=rendertest:mapa:x:y). */
public class RenderTestScreen extends ScreenAdapter {
    private final BrigadaJatoGame game;
    private final World world;
    private final WorldRenderer renderer;
    private final OrthographicCamera cam = new OrthographicCamera();
    private final float cx, cy;

    public RenderTestScreen(BrigadaJatoGame game, String map, float cx, float cy) {
        this.game = game;
        MatchConfig cfg = new MatchConfig();
        cfg.mapId = map;
        cfg.bots = 0;
        world = new World(BrigadaJatoGame.loadMap(map), cfg, true, 1);
        world.start();
        renderer = new WorldRenderer(game.assets, game.audio, game.settings);
        renderer.setWorld(world);
        this.cx = cx;
        this.cy = cy;
        com.badlogic.gdx.Gdx.graphics.setVSync(false);
        com.badlogic.gdx.Gdx.graphics.setForegroundFPS(0);
    }

    private int frames;
    private long acc, last;

    @Override
    public void render(float delta) {
        com.badlogic.gdx.Gdx.gl.glFinish();
        long now = System.nanoTime();
        if (last != 0 && frames > 20) acc += now - last;
        last = now;
        if (++frames == 220) {
            com.badlogic.gdx.Gdx.app.log("rendertest", String.format("%.2f ms/quadro", acc / 1e6 / 199));
            if (com.brigadajato.Perf.ON) com.badlogic.gdx.Gdx.app.log("rendertest", com.brigadajato.Perf.report(220));
        }
        com.brigadajato.Perf.start(game.batch);
        ScreenUtils.clear(0, 0, 0, 1);
        float aspect = com.badlogic.gdx.Gdx.graphics.getWidth() / (float) com.badlogic.gdx.Gdx.graphics.getHeight();
        cam.viewportHeight = 520;
        cam.viewportWidth = 520 * aspect;
        cam.position.set(cx, cy, 0);
        cam.update();
        game.batch.setProjectionMatrix(cam.combined);
        game.batch.begin();
        renderer.render(game.draw, cam, world, null, false, 0, 0);
        game.batch.end();
        renderer.renderLights(game.batch, cam, null);
        com.brigadajato.Perf.mark(game.batch, "lights");
    }
}
