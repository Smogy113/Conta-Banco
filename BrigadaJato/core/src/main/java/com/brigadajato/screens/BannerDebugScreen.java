package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.render.KillBanner;
import com.brigadajato.world.C;
import com.brigadajato.world.WeaponType;

/**
 * Tela de teste (so no PC, --start=banner): dispara os 5 niveis do emblema de abates em
 * sequencia. Com --seq=pasta grava cada quadro (30 por segundo) para montar um video.
 */
public class BannerDebugScreen extends BaseScreen {
    public static final float[] TIMES = {0.4f, 2.7f, 5.0f, 7.3f, 9.7f};
    private static final int[] CODES = {WeaponType.RIFLE.ordinal(), WeaponType.SNIPER.ordinal(), C.KILL_MELEE,
            WeaponType.ROCKET.ordinal(), WeaponType.SMG.ordinal()};
    private final KillBanner banner;
    private final String seqDir = System.getProperty("brigadajato.seq");
    private float time;
    private int next, frame;
    private float shake;

    public BannerDebugScreen(final BrigadaJatoGame game) {
        super(game);
        banner = new KillBanner(game.assets);
        stage.addActor(new Actor() {
            @Override
            public void draw(Batch batch, float parentAlpha) {
                float w = getStage().getViewport().getWorldWidth(), h = getStage().getViewport().getWorldHeight();
                float sx = MathUtils.random(-shake, shake), sy = MathUtils.random(-shake, shake);
                banner.drawEdgeGlow(game.draw, w, h);
                banner.draw(game.draw, w / 2 + sx, h * 0.5f + sy);
                batch.setColor(1, 1, 1, 1);
            }
        });
    }

    @Override
    public void render(float delta) {
        float dt = seqDir != null ? 1f / 30f : delta;
        time += dt;
        if (next < TIMES.length && time >= TIMES[next]) {
            banner.trigger(next + 1, CODES[next], next == 1);
            if (seqDir == null) game.audio.ui("kill" + (next + 1));
            next++;
        }
        banner.update(dt);
        float imp = banner.consumeImpact();
        if (imp > 0) shake = 4 + imp * 4;
        shake = Math.max(0, shake - dt * 40);
        super.render(dt);
        if (seqDir != null) {
            Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            PixmapIO.writePNG(Gdx.files.absolute(seqDir + String.format("/frame_%04d.png", frame++)), pm, 1, true);
            pm.dispose();
            if (time > 12.6f) Gdx.app.exit();
        }
    }
}
