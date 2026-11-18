package com.brigadajato.screens;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.brigadajato.Audio;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.render.WorldRenderer;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Names;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.util.Random;

/** Partida de bots rodando atras dos menus. */
public class MenuBackground implements Disposable {
    private final BrigadaJatoGame game;
    private final OrthographicCamera cam = new OrthographicCamera();
    private final WorldRenderer renderer;
    private final Audio mutedAudio;
    private World world;
    private Player follow;
    private float followTimer, acc;
    private final Random rng = new Random();

    public MenuBackground(BrigadaJatoGame game) {
        this.game = game;
        mutedAudio = new Audio(game.assets, game.settings);
        mutedAudio.muted = true;
        renderer = new WorldRenderer(game.assets, mutedAudio, game.settings);
        restart();
    }

    private void restart() {
        MatchConfig cfg = new MatchConfig();
        cfg.mode = rng.nextBoolean() ? ModeType.TEAM_DEATHMATCH : ModeType.DEATHMATCH;
        cfg.mapId = GameMap.PLAY_IDS[rng.nextInt(GameMap.PLAY_IDS.length)];
        cfg.timeLimit = 0;
        cfg.scoreLimit = 0;
        cfg.difficulty = 1;
        world = new World(BrigadaJatoGame.loadMap(cfg.mapId), cfg, true, rng.nextLong());
        for (int i = 0; i < 7; i++) {
            int team = cfg.mode.teams ? (i % 2 == 0 ? C.TEAM_RED : C.TEAM_BLUE) : C.TEAM_NONE;
            world.addPlayer(Names.BOTS[rng.nextInt(Names.BOTS.length)], team, Look.random(rng), true);
        }
        world.start();
        renderer.setWorld(world);
        follow = null;
        cam.position.set(world.map.worldW() / 2, world.map.worldH() / 2, 0);
    }

    public void resize(int w, int h) {
        cam.viewportHeight = 600;
        cam.viewportWidth = 600f * w / Math.max(1, h);
        cam.update();
    }

    public void render(float dt) {
        if (cam.viewportWidth <= 1) resize(com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight());
        acc += Math.min(dt, 0.1f);
        while (acc >= C.STEP) {
            world.update(C.STEP);
            for (int i66_ = 0; i66_ < world.events.size; i66_++) {
                WorldEvent e = world.events.get(i66_); renderer.handle(e, null);
            }
            world.events.clear();
            acc -= C.STEP;
        }
        if (world.time > 240) restart();
        followTimer -= dt;
        if (follow == null || !follow.alive || followTimer <= 0) {
            followTimer = 7f;
            if (world.players.size > 0) follow = world.players.get(rng.nextInt(world.players.size));
        }
        if (follow != null && follow.alive) {
            float k = Math.min(1, dt * 1.5f);
            cam.position.x += (follow.x - cam.position.x) * k;
            cam.position.y += (follow.centerY() + 40 - cam.position.y) * k;
        }
        float hw = cam.viewportWidth / 2, hh = cam.viewportHeight / 2;
        cam.position.x = MathUtils.clamp(cam.position.x, hw, Math.max(hw, world.map.worldW() - hw));
        cam.position.y = MathUtils.clamp(cam.position.y, hh, Math.max(hh, world.map.worldH() - hh));
        cam.update();
        renderer.update(dt);
        game.batch.setProjectionMatrix(cam.combined);
        game.batch.begin();
        renderer.render(game.draw, cam, world, null, false, 0, 0);
        game.draw.color(0.03f, 0.04f, 0.08f, 0.55f);
        game.draw.rect(cam.position.x - hw - 10, cam.position.y - hh - 10, hw * 2 + 20, hh * 2 + 20);
        game.batch.end();
        game.batch.setColor(1, 1, 1, 1);
    }

    @Override
    public void dispose() {
        renderer.dispose();
    }
}
