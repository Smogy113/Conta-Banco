package com.brigadajato;

import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;
import com.brigadajato.world.modes.BombMode;
import com.brigadajato.world.modes.SurvivalMode;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/** Roda partidas inteiras so com bots para garantir que nada quebra. */
public class WorldSimTest {

    static GameMap load(String id) throws Exception {
        String txt = new String(Files.readAllBytes(Paths.get("maps", id + ".txt")), StandardCharsets.UTF_8);
        return GameMap.parse(id, txt);
    }

    static World makeWorld(ModeType mode, String mapId, int bots, long seed) throws Exception {
        MatchConfig cfg = new MatchConfig();
        cfg.mode = mode;
        cfg.mapId = mapId;
        cfg.applyModeDefaults();
        cfg.bots = bots;
        cfg.difficulty = mode == ModeType.CTF ? 1 : 2;
        World w = new World(load(cfg.mapId), cfg, true, seed);
        Random r = new Random(seed);
        int humanLike = mode == ModeType.SURVIVAL ? 2 : bots;
        for (int i = 0; i < humanLike; i++) {
            int team = mode.teams ? (i % 2 == 0 ? C.TEAM_RED : C.TEAM_BLUE) : C.TEAM_NONE;
            if (mode == ModeType.SURVIVAL) team = C.TEAM_RED;
            w.addPlayer("Bot" + i, team, Look.random(r), true);
        }
        w.start();
        return w;
    }

    static int[] flagEvents = new int[4];

    static int[] run(World w, float seconds) {
        flagEvents = new int[4];
        int kills = 0, shots = 0;
        int steps = (int) (seconds / C.STEP);
        for (int i = 0; i < steps && !w.mode.over; i++) {
            w.update(C.STEP);
            for (WorldEvent e : w.events) {
                if (e.type == WorldEvent.Type.KILL) kills++;
                if (e.type == WorldEvent.Type.SHOT || e.type == WorldEvent.Type.LASER) shots++;
                if (e.type == WorldEvent.Type.FLAG) flagEvents[e.b]++;
            }
            w.events.clear();
            for (Player p : w.players) {
                assertTrue("posicao invalida " + p.name, !Float.isNaN(p.x) && !Float.isNaN(p.y));
                if (p.alive) {
                    assertTrue(p.name + " saiu do mapa: " + p.x + "," + p.y, p.x > 0 && p.x < w.map.worldW() && p.y > 0 && p.y < w.map.worldH());
                }
            }
        }
        return new int[]{kills, shots};
    }

    @Test
    public void allMapsParse() throws Exception {
        for (String id : GameMap.IDS) {
            GameMap m = load(id);
            System.out.println(id + ": " + m.w + "x" + m.h + " spawns=" + m.spawnsFree.size + "/" + m.spawnsRed.size + "/" + m.spawnsBlue.size
                    + " armas=" + m.weaponSpawns.size + " flags=" + m.hasFlags + " sites=" + m.hasSites);
            assertTrue(m.spawnsFree.size > 0);
        }
    }

    @Test
    public void deathmatchAllMaps() throws Exception {
        for (String id : GameMap.PLAY_IDS) {
            World w = makeWorld(ModeType.DEATHMATCH, id, 8, id.hashCode());
            w.config.mapId = id;
            int[] r = run(w, 150);
            System.out.println("DM " + id + ": abates=" + r[0] + " tiros=" + r[1] + " over=" + w.mode.over);
            assertTrue("ninguem atirou em " + id, r[1] > 50);
            assertTrue("ninguem morreu em " + id, r[0] > 3);
        }
    }

    @Test
    public void teamModes() throws Exception {
        ModeType[] modes = {ModeType.TEAM_DEATHMATCH, ModeType.CTF, ModeType.BOMB};
        for (ModeType m : modes) {
            for (String id : GameMap.PLAY_IDS) {
                World w = makeWorld(m, id, 8, (m.name() + id).hashCode());
                int[] r = run(w, 240);
                String extra = "";
                if (w.mode instanceof BombMode) extra = " rodada=" + ((BombMode) w.mode).round;
                if (m == ModeType.CTF) extra = " pegou=" + flagEvents[0] + " caiu=" + flagEvents[1] + " voltou=" + flagEvents[2] + " capturou=" + flagEvents[3];
                System.out.println(m + " " + id + ": abates=" + r[0] + " placar=" + w.mode.teamScore[1] + "x" + w.mode.teamScore[2] + extra + " over=" + w.mode.over);
                assertTrue(r[0] > 2);
            }
        }
    }

    @Test
    public void survival() throws Exception {
        World w = makeWorld(ModeType.SURVIVAL, "fortaleza", 2, 99);
        run(w, 240);
        int wave = ((SurvivalMode) w.mode).wave;
        System.out.println("Sobrevivencia: onda=" + wave + " over=" + w.mode.over + " jogadores=" + w.players.size);
        assertTrue(wave >= 1);
    }

    @Test
    public void training() throws Exception {
        World w = makeWorld(ModeType.TRAINING, "treino", 1, 5);
        run(w, 30);
        System.out.println("Treino: jogadores=" + w.players.size);
        assertTrue(w.players.size > 3);
    }
}
