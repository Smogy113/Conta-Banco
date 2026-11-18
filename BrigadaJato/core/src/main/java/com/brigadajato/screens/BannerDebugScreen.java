package com.brigadajato.screens;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.render.KillBanner;
import com.brigadajato.world.C;
import com.brigadajato.world.WeaponType;

/** Tela de teste (so no PC, --start=banner): os 5 niveis do emblema de abates. */
public class BannerDebugScreen extends BaseScreen {
    public BannerDebugScreen(final BrigadaJatoGame game) {
        super(game);
        final KillBanner[] b = new KillBanner[5];
        int[] codes = {WeaponType.RIFLE.ordinal(), WeaponType.SNIPER.ordinal(), C.KILL_MELEE, WeaponType.ROCKET.ordinal(), WeaponType.SMG.ordinal()};
        for (int i = 0; i < 5; i++) {
            b[i] = new KillBanner(game.assets);
            b[i].trigger(i + 1, codes[i], i == 1);
        }
        stage.addActor(new Actor() {
            @Override
            public void act(float delta) {
                for (KillBanner k : b) k.update(delta);
            }

            @Override
            public void draw(Batch batch, float parentAlpha) {
                float w = getStage().getViewport().getWorldWidth();
                for (int i = 0; i < 5; i++) b[i].draw(game.draw, w * (i + 0.5f) / 5f, 400);
                batch.setColor(1, 1, 1, 1);
            }
        });
    }
}
