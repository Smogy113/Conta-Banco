package com.brigadajato;

import com.brigadajato.world.C;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;
import com.brigadajato.world.modes.CtfMode;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertTrue;

/** Um bot sozinho precisa conseguir buscar a bandeira inimiga e capturar. */
public class BotNavigationTest {
    @Test
    public void loneBotCapturesFlag() throws Exception {
        com.brigadajato.world.ai.BotBrain.DEBUG = true;
        String[] maps = {"fortaleza", "fabrica", "ilhas", "caverna"};
        for (String id : maps) {
            MatchConfig cfg = new MatchConfig();
            cfg.mode = ModeType.CTF;
            cfg.mapId = id;
            cfg.applyModeDefaults();
            World w = new World(WorldSimTest.load(id), cfg, true, 7);
            Player b = w.addPlayer("Teste", C.TEAM_RED, Look.random(new Random(1)), true);
            w.addPlayer("Dummy", C.TEAM_BLUE, Look.random(new Random(2)), true);
            Player bot2 = w.addPlayer("Teste2", C.TEAM_RED, Look.random(new Random(3)), true);
            w.start();
            CtfMode m = (CtfMode) w.mode;
            int steps = (int) (90 / C.STEP);
            for (int i = 0; i < steps; i++) {
                // o azul fica parado longe (sem atirar): remove para simplificar
                Player blue = w.player(1);
                if (blue != null && blue.alive) { blue.x = 40; blue.y = w.map.worldH() - 80; blue.vx = blue.vy = 0; blue.alive = false; blue.respawnTimer = 9999; }
                w.update(C.STEP);
                w.events.clear();
                if (i % 300 == 0) System.out.println(id + " t=" + (int) (i * C.STEP) + " bot2=(" + (int) bot2.x + "," + (int) bot2.y + ") in=" + bot2.input.moveX + "/" + bot2.input.moveY + " fuel=" + (int) bot2.fuel + " gnd=" + bot2.onGround + " " + bot2.bot.debug + " flagAzul=" + m.state[2] + " pos=(" + (int) m.fx[2] + "," + (int) m.fy[2] + ") placar=" + m.teamScore[1]);
                if (m.teamScore[1] > 0) break;
            }
            System.out.println(id + " CAPTURAS: " + m.teamScore[1]);
            assertTrue("bot nao capturou em " + id, m.teamScore[1] > 0);
        }
    }
}
