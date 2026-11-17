package com.brigadajato.net;

import com.badlogic.gdx.utils.Array;
import com.brigadajato.I18n;
import com.brigadajato.Settings;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Names;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;
import com.brigadajato.world.ai.BotBrain;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Logica do anfitriao: sala de espera, inicio da partida e sincronizacao. */
public class HostSession {
    public static class Entry {
        public int id;
        public String name;
        public Look look;
        public int team;
        public boolean host;
    }

    public final NetHost net = new NetHost();
    public final MatchConfig cfg;
    private final String hostName;
    private final Look hostLook;
    public int hostTeam = C.TEAM_RED;
    public boolean inGame;
    private int lastRoster;
    private int tick;
    private final Random rng = new Random();
    public final Array<String> notices = new Array<>();

    public HostSession(MatchConfig cfg, Settings settings) {
        this(cfg, settings.name, settings.look);
    }

    public HostSession(MatchConfig cfg, String hostName, Look hostLook) {
        this.cfg = cfg;
        this.hostName = hostName;
        this.hostLook = hostLook;
    }

    public boolean start() {
        updateInfo();
        return net.start();
    }

    public void updateInfo() {
        net.roomName = hostName;
        net.modeName = I18n.pt ? cfg.mode.pt : cfg.mode.en;
        net.mapName = cfg.mapId;
    }

    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        Entry h = new Entry();
        h.id = 0;
        h.name = hostName;
        h.look = hostLook;
        h.team = hostTeam;
        h.host = true;
        out.add(h);
        for (NetHost.Conn c : net.conns) {
            if (!c.alive || !c.hello) continue;
            Entry e = new Entry();
            e.id = c.id;
            e.name = c.name;
            e.look = c.look;
            e.team = c.team;
            out.add(e);
        }
        return out;
    }

    private int balancedTeam() {
        int r = 0, b = 0;
        for (Entry e : entries()) {
            if (e.team == C.TEAM_RED) r++;
            else b++;
        }
        return r <= b ? C.TEAM_RED : C.TEAM_BLUE;
    }

    /** Processa mensagens na sala de espera. */
    public void updateLobby() {
        Net.Packet p;
        boolean changed = false;
        while ((p = net.poll()) != null) {
            try {
                NetHost.Conn c = net.conn(p.from);
                DataInputStream in = p.in();
                switch (p.type) {
                    case Net.C_HELLO: {
                        if (c == null) break;
                        int proto = in.readInt();
                        c.name = in.readUTF();
                        c.look = Net.readLook(in);
                        if (proto != Net.PROTOCOL) {
                            Net.Writer w = new Net.Writer(Net.S_KICK);
                            w.out.writeUTF("version");
                            net.send(c, w.build());
                            break;
                        }
                        if (inGame) {
                            Net.Writer w = new Net.Writer(Net.S_KICK);
                            w.out.writeUTF("ingame");
                            net.send(c, w.build());
                            break;
                        }
                        c.team = balancedTeam();
                        c.hello = true;
                        Net.Writer w = new Net.Writer(Net.S_WELCOME);
                        w.out.writeShort(c.id);
                        net.send(c, w.build());
                        notices.add(I18n.f("player_joined", c.name));
                        changed = true;
                        break;
                    }
                    case Net.C_TEAM:
                        if (c != null) {
                            c.team = in.readByte();
                            changed = true;
                        }
                        break;
                    case Net.C_BYE:
                        if (c != null && c.hello) notices.add(I18n.f("player_left", c.name));
                        net.removeDead();
                        changed = true;
                        break;
                    default:
                        break;
                }
            } catch (IOException ignored) {
            }
        }
        if (changed) broadcastLobby();
    }

    public void broadcastLobby() {
        updateInfo();
        try {
            Net.Writer w = new Net.Writer(Net.S_LOBBY);
            cfg.write(w.out);
            List<Entry> list = entries();
            w.out.writeShort(list.size());
            for (Entry e : list) {
                w.out.writeShort(e.id);
                w.out.writeUTF(e.name);
                Net.writeLook(w.out, e.look);
                w.out.writeByte(e.team);
                w.out.writeBoolean(e.host);
            }
            net.broadcast(w.build());
        } catch (IOException ignored) {
        }
    }

    /** Cria o mundo da partida e avisa todos os clientes. Retorna o mundo (jogador local = id 0). */
    public World startMatch(GameMap map) {
        long seed = rng.nextLong();
        World w = new World(map, cfg, true, seed);
        boolean teams = cfg.mode.teams && cfg.mode != ModeType.SURVIVAL && cfg.mode != ModeType.TRAINING;
        Player hp = w.addPlayerWithId(0, hostName, teams ? hostTeam : (cfg.mode.teams ? C.TEAM_RED : C.TEAM_NONE), hostLook.copy(), false);
        hp.netClient = -1;
        for (NetHost.Conn c : net.conns) {
            if (!c.alive || !c.hello) continue;
            int team = teams ? c.team : (cfg.mode.teams ? C.TEAM_RED : C.TEAM_NONE);
            Player p = w.addPlayerWithId(c.id, c.name, team, c.look, false);
            p.netClient = c.id;
            c.playerId = p.id;
        }
        int bots = cfg.mode == ModeType.TRAINING ? 0 : cfg.bots;
        for (int i = 0; i < bots; i++) {
            int team = teams ? w.balancedTeam() : (cfg.mode == ModeType.SURVIVAL ? C.TEAM_RED : C.TEAM_NONE);
            w.addPlayer(Names.BOTS[(i * 7 + rng.nextInt(3)) % Names.BOTS.length], team, Look.random(rng), true);
        }
        w.start();
        for (NetHost.Conn c : net.conns) {
            if (!c.alive || !c.hello) continue;
            try {
                Net.Writer m = new Net.Writer(Net.S_START);
                cfg.write(m.out);
                m.out.writeLong(seed);
                m.out.writeShort(c.playerId);
                Net.writeRoster(m.out, w);
                net.send(c, m.build());
            } catch (IOException ignored) {
            }
        }
        lastRoster = Net.rosterHash(w);
        inGame = true;
        tick = 0;
        return w;
    }

    /** Copia os comandos recebidos dos clientes para os jogadores. */
    public void applyInputs(World w) {
        for (NetHost.Conn c : net.conns) {
            if (c.playerId < 0) continue;
            Player p = w.player(c.playerId);
            if (p == null || p.netClient != c.id) continue;
            synchronized (c.pending) {
                p.input.copyFrom(c.pending);
                c.pending.presses = 0;
                c.pending.buy = -1;
            }
        }
    }

    /** Mensagens durante a partida (saidas viram bots). */
    public void updateInGame(World w) {
        Net.Packet p;
        while ((p = net.poll()) != null) {
            NetHost.Conn c = net.conn(p.from);
            if (p.type == Net.C_BYE) {
                if (c != null && c.playerId >= 0) {
                    Player pl = w.player(c.playerId);
                    if (pl != null) {
                        pl.netClient = -1;
                        pl.isBot = true;
                        pl.bot = new BotBrain(pl, cfg.difficulty, rng.nextLong());
                        pl.name = pl.name + " (bot)";
                        w.message("player_left", pl.id, -1, 1);
                    }
                }
                net.removeDead();
            } else if (p.type == Net.C_HELLO && c != null) {
                try {
                    Net.Writer m = new Net.Writer(Net.S_KICK);
                    m.out.writeUTF("ingame");
                    net.send(c, m.build());
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Envia eventos (todo passo) e o estado completo (20x por segundo). */
    public void afterStep(World w, Array<WorldEvent> events) {
        try {
            if (events.size > 0) {
                Net.Writer m = new Net.Writer(Net.S_EVENTS);
                Net.writeEvents(m.out, events);
                net.broadcast(m.build());
            }
            int rh = Net.rosterHash(w);
            if (rh != lastRoster) {
                lastRoster = rh;
                Net.Writer m = new Net.Writer(Net.S_ROSTER);
                Net.writeRoster(m.out, w);
                net.broadcast(m.build());
            }
            if (tick++ % 3 == 0) {
                Net.Writer m = new Net.Writer(Net.S_SNAP);
                Net.writeSnapshot(m.out, w);
                net.broadcast(m.build());
            }
        } catch (IOException ignored) {
        }
    }

    public void returnToLobby() {
        inGame = false;
        for (NetHost.Conn c : net.conns) c.playerId = -1;
        net.broadcast(new Net.Writer(Net.S_LOBBY_RETURN).build());
        broadcastLobby();
    }

    public void close() {
        net.close();
    }
}
