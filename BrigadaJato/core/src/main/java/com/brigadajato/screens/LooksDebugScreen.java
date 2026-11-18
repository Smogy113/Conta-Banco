package com.brigadajato.screens;

import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.world.Look;
import com.brigadajato.world.WeaponType;

import java.util.Random;

/** Tela de teste (so no PC, --start=looks): grade de soldados com visuais aleatorios. */
public class LooksDebugScreen extends BaseScreen {
    public LooksDebugScreen(BrigadaJatoGame game) {
        super(game);
        background = false;
        Random r = new Random(42);
        WeaponType[] all = WeaponType.values();
        if (System.getProperty("brigadajato.guns") != null) {
            final com.brigadajato.render.SoldierPainter painter = new com.brigadajato.render.SoldierPainter();
            final WeaponType[] ws = WeaponType.values();
            root.addActor(new com.badlogic.gdx.scenes.scene2d.Actor() {
                @Override
                public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
                    for (int i = 0; i < ws.length; i++) {
                        float cx = 150 + (i % 5) * 245, cy = 640 - (i / 5) * 140;
                        game.draw.color(0.2f, 0.22f, 0.3f, 1);
                        game.draw.rect(cx - 115, cy - 60, 230, 125);
                        painter.drawGun(game.draw, ws[i], cx, cy, 0, 3.2f, 1);
                        game.assets.small.draw(batch, ws[i].pt, cx - 105, cy - 38);
                    }
                    batch.setColor(1, 1, 1, 1);
                }
            });
            return;
        }
        boolean big = System.getProperty("brigadajato.big") != null;
        for (int i = 0; i < (big ? 4 : 24); i++) {
            Look l = Look.random(r);
            l.hat = i % Look.HAT_COUNT;
            l.eyewear = i % Look.EYEWEAR_COUNT;
            l.accessory = i % Look.ACCESSORY_COUNT;
            l.jetpack = i % Look.JETPACK_COUNT;
            l.hair = i % Look.HAIR_COUNT;
            l.costume = i < 6 ? i : 0;
            SoldierPreview p = new SoldierPreview(game, l);
            p.weapon = all[i % all.length];
            p.poseType = i % SoldierPreview.POSE_COUNT;
            p.facing = i % 2 == 0 ? 1 : -1;
            if (big) {
                p.poseType = 0;
                root.add(p).size(300, 360).pad(4);
            } else {
                root.add(p).size(150, 170).pad(4);
                if ((i + 1) % 8 == 0) root.row();
            }
        }
    }
}
