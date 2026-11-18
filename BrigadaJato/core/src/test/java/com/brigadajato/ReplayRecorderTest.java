package com.brigadajato;

import com.brigadajato.replay.Clip;
import com.brigadajato.replay.ReplayFrame;
import com.brigadajato.replay.ReplayRecorder;
import com.brigadajato.world.C;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.World;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** O gravador guarda os ultimos segundos e recorta clipes com jogadores, tiros e eventos. */
public class ReplayRecorderTest {
    @Test
    public void recordsAndExtracts() throws Exception {
        World w = WorldSimTest.makeWorld(ModeType.DEATHMATCH, "fortaleza", 8, 11);
        ReplayRecorder rec = new ReplayRecorder();
        int kills = 0;
        for (int i = 0; i < 20 * 60; i++) {
            w.update(C.STEP);
            for (int k = 0; k < w.events.size; k++) {
                rec.addEvent(w.events.get(k));
                if (w.events.get(k).type == com.brigadajato.world.WorldEvent.Type.KILL) kills++;
            }
            w.events.clear();
            rec.update(w, C.STEP);
        }
        assertTrue("buffer cheio", rec.count() == (int) (ReplayRecorder.FPS * ReplayRecorder.SECONDS));
        float now = rec.newestTime();
        assertTrue("guarda so os ultimos 10s", now - rec.get(0).time < 10.1f);
        Clip c = rec.extract(now - 5, now);
        int players = 0, shots = 0, events = 0;
        for (ReplayFrame f : c.frames) {
            players = Math.max(players, f.np);
            shots = Math.max(shots, f.nr);
            events += f.events.size;
        }
        System.out.println("clipe: quadros=" + c.frames.size + " jogadores=" + players + " projeteis=" + shots + " eventos=" + events + " abates na partida=" + kills + " elenco=" + c.roster.size);
        assertTrue(c.frames.size >= 140 && c.frames.size <= 155);
        assertTrue(players > 3);
        assertTrue(events > 10);
        assertTrue(c.roster.size >= 8);
    }
}
