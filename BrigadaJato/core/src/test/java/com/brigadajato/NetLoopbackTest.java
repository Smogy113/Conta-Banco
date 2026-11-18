package com.brigadajato;

import com.badlogic.gdx.utils.Array;
import com.brigadajato.net.ClientSession;
import com.brigadajato.net.Discovery;
import com.brigadajato.net.HostSession;
import com.brigadajato.net.NetClient;
import com.brigadajato.world.C;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Anfitriao e cliente na mesma maquina: sala de espera, inicio, snapshots, eventos e saida. */
public class NetLoopbackTest {

    @Test
    public void hostAndClientPlayTogether() throws Exception {
        MatchConfig cfg = new MatchConfig();
        cfg.mode = ModeType.TEAM_DEATHMATCH;
        cfg.mapId = "fabrica";
        cfg.bots = 4;
        HostSession host = new HostSession(cfg, "Anfitriao", new Look());
        assertTrue("servidor nao abriu", host.start());
        Discovery.Finder finder = new Discovery.Finder();
        finder.start();
        ClientSession client = new ClientSession("127.0.0.1", "Cliente", new Look(), id -> {
            try {
                return WorldSimTest.load(id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        try {
            // sala de espera
            long t0 = System.currentTimeMillis();
            while (System.currentTimeMillis() - t0 < 5000 && client.lobby.size() < 2) {
                host.updateLobby();
                client.updateLobby();
                Thread.sleep(20);
            }
            assertEquals(NetClient.State.CONNECTED, client.net.state);
            assertEquals("cliente deveria ver 2 jogadores na sala", 2, client.lobby.size());
            assertTrue(client.myId > 0);

            // descoberta
            t0 = System.currentTimeMillis();
            List<Discovery.Room> rooms = finder.rooms();
            while (System.currentTimeMillis() - t0 < 4000 && rooms.isEmpty()) {
                Thread.sleep(100);
                rooms = finder.rooms();
            }
            System.out.println("salas encontradas: " + rooms.size() + (rooms.isEmpty() ? "" : " -> " + rooms.get(0).ip + " " + rooms.get(0).name));
            assertTrue("descoberta UDP nao achou a sala", !rooms.isEmpty());

            // inicio
            World hw = host.startMatch(WorldSimTest.load(cfg.mapId));
            t0 = System.currentTimeMillis();
            while (System.currentTimeMillis() - t0 < 5000 && client.startedWorld == null) {
                client.updateLobby();
                Thread.sleep(10);
            }
            assertNotNull("cliente nao recebeu o inicio", client.startedWorld);
            World cw = client.startedWorld;
            int myId = client.startedLocalId;
            assertEquals(hw.players.size, cw.players.size);

            // joga 8 segundos: o cliente segura "andar para a direita" e "atirar"
            Array<WorldEvent> events = new Array<>();
            int shots = 0;
            com.brigadajato.world.PlayerInput in = new com.brigadajato.world.PlayerInput();
            for (int step = 0; step < 8 * 60; step++) {
                in.moveX = 1;
                in.fire = true;
                in.aim = 0;
                if (step % 2 == 0) client.net.sendInput(in);
                host.updateInGame(hw);
                host.applyInputs(hw);
                hw.update(C.STEP);
                host.afterStep(hw, hw.events);
                hw.events.clear();
                Thread.sleep(2);
                client.receive(cw, events, myId, 1f / 60f);
                for (WorldEvent e : events) if (e.type == WorldEvent.Type.SHOT) shots++;
                events.clear();
                cw.update(C.STEP);
            }
            Thread.sleep(150);
            client.receive(cw, events, myId, 1f / 60f);
            Player hostView = hw.player(myId), clientView = cw.player(myId);
            System.out.println("cliente no host=(" + (int) hostView.x + "," + (int) hostView.y + ") no cliente=(" + (int) clientView.x + "," + (int) clientView.y
                    + ") tiros recebidos=" + shots + " tempo host=" + hw.time + " cliente=" + cw.time);
            assertTrue("comandos do cliente nao chegaram (tiros do cliente no host)", hostView.kills >= 0 && hw.statShots > 0);
            assertTrue("eventos de tiro nao chegaram ao cliente", shots > 10);
            float dx = Math.abs(hostView.netX - clientView.netX) + Math.abs(hostView.y - clientView.netY);
            assertTrue("estado divergente: " + dx, dx < 150 || !hostView.alive);
            assertEquals(hw.mode.teamScore[1], cw.mode.teamScore[1]);

            // cliente sai -> vira bot no host
            client.close();
            Thread.sleep(300);
            host.updateInGame(hw);
            assertTrue("jogador que saiu deveria virar bot", hw.player(myId).isBot);
        } finally {
            finder.stop();
            client.close();
            host.close();
        }
    }
}
