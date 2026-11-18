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
        boolean big = System.getProperty("brigadajato.big") != null;
        for (int i = 0; i < (big ? 4 : 24); i++) {
            Look l = Look.random(r);
            l.hat = i % Look.HAT_COUNT;
            l.eyewear = i % Look.EYEWEAR_COUNT;
            l.accessory = i % Look.ACCESSORY_COUNT;
            l.jetpack = i % Look.JETPACK_COUNT;
            l.hair = i % Look.HAIR_COUNT;
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
