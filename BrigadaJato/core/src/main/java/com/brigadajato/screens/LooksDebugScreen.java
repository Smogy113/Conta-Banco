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
        if (System.getProperty("brigadajato.lineup") != null) { // fila de bonecos sem chapeu, fundo claro
            root.addActor(new com.badlogic.gdx.scenes.scene2d.Actor() {
                @Override
                public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
                    game.draw.rgb(0xe9e4da, 1);
                    game.draw.rect(0, 0, 1280, 720);
                }
            });
            int[][] spec = { // hair, hairColor, body, pants, skin, accessoryColor, eyes, mouth
                    {2, 0x1b1b1b, 0x1e88e5, 0x263238, 0xf1c27d, 0xff5722, 0, 0},
                    {5, 0x5d2a2a, 0xeeeeee, 0x4e3b30, 0xffdbac, 0x8d6e63, 0, 1},
                    {3, 0x6d4c41, 0x212121, 0x90a4ae, 0xffdbac, 0x212121, 3, 0},
                    {1, 0x6d4c41, 0x2e7d32, 0x1b1b1b, 0xf1c27d, 0xeeeeee, 0, 0},
                    {6, 0xf9a825, 0x8e3b2e, 0x37474f, 0xc68642, 0x263238, 1, 5},
                    {4, 0x1b1b1b, 0x7d5ba6, 0x2c3e57, 0x8d5524, 0xffeb3b, 2, 1}};
            root.defaults().pad(2);
            for (int i = 0; i < spec.length; i++) {
                int[] q = spec[i];
                Look l = new Look();
                l.hat = 6;
                l.eyewear = 1;
                l.jetpack = 0;
                l.hair = q[0];
                l.hairColor = q[1];
                l.body = q[2];
                l.pants = q[3];
                l.skin = q[4];
                l.accessoryColor = q[5];
                l.eyes = q[6];
                l.mouth = q[7];
                l.jetColor = 0x3a3a40;
                SoldierPreview p = new SoldierPreview(game, l);
                int[] poses = {0, 1, 0, 4, 2, 3};
                p.poseType = poses[i];
                p.weapon = p.poseType >= 3 ? com.brigadajato.world.WeaponType.RIFLE : null;
                p.facing = i % 2 == 0 ? 1 : -1;
                root.add(p).size(200, 420);
            }
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
