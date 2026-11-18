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
        public int vote = -1;
        public boolean bluetooth;
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
    /** mensagens do chat da sala (mais recentes no fim) */
    public final Array<String> chat = new Array<>();
    /** senha da sala ("" = aberta) */
    public String password = "";
    /** votacao de mapa ligada e as 3 opcoes */
    public boolean mapVote;
    public final String[] voteMaps = new String[3];
    public int hostVote = -1;
    /** deixa entrar no meio da partida */
    public boolean allowJoinInGame = true;
    /** mundo em andamento (para quem entra no meio) */
    private World world;

    public HostSession(MatchConfig cfg, Settings settings) {
        this(cfg, settings.name, settings.look);
    }

    public HostSession(MatchConfig cfg, String hostName, Look hostLook) {
        this.cfg = cfg;
        this.hostName = hostName;
        this.hostLook = hostLook;
        rollVoteMaps();
    }

    public boolean start() {
        updateInfo();
        return net.start();
    }

    public void updateInfo() {
        net.roomName = hostName;
        net.modeName = I18n.pt ? cfg.mode.pt : cfg.mode.en;
        net.mapName = mapVote ? "?" : cfg.mapId;
        net.locked = password != null && !password.isEmpty();
        net.inGame = inGame;
    }

    /** Sorteia 3 mapas para a votacao (o atual sempre entra). */
    public void rollVoteMaps() {
        List<String> pool = new ArrayList<>();
        for (String id : GameMap.PLAY_IDS) pool.add(id);
        java.util.Collections.shuffle(pool, rng);
        voteMaps[0] = cfg.mapId;
        int k = 1;
        for (String id : pool) {
            if (k >= 3) break;
            if (!id.equals(cfg.mapId)) voteMaps[k++] = id;
        }
        for (NetHost.Conn c : net.conns) c.vote = -1;
        hostVote = -1;
    }

    public int[] voteCounts() {
        int[] v = new int[3];
        if (hostVote >= 0 && hostVote < 3) v[hostVote]++;
        for (NetHost.Conn c : net.conns) if (c.alive && c.hello && c.vote >= 0 && c.vote < 3) v[c.vote]++;
        return v;
    }

    /** Aplica o resultado da votacao em cfg.mapId (empate: sorteio entre os mais votados). */
    public void resolveVote() {
        if (!mapVote) return;
        int[] v = voteCounts();
        int best = Math.max(v[0], Math.max(v[1], v[2]));
        List<Integer> top = new ArrayList<>();
        for (int i = 0; i < 3; i++) if (v[i] == best && voteMaps[i] != null) top.add(i);
        if (top.isEmpty()) return;
        cfg.mapId = voteMaps[top.get(rng.nextInt(top.size()))];
    }

    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        Entry h = new Entry();
        h.id = 0;
        h.name = hostName;
        h.look = hostLook;
        h.team = hostTeam;
        h.host = true;
        h.vote = hostVote;
        out.add(h);
        for (NetHost.Conn c : net.conns) {
            if (!c.alive || !c.hello) continue;
            Entry e = new Entry();
            e.id = c.id;
            e.name = c.name;
            e.look = c.look;
            e.team = c.team;
            e.vote = c.vote;
            e.bluetooth = c.bluetooth;
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

    private void refuse(NetHost.Conn c, String reason) throws IOException {
        Net.Writer w = new Net.Writer(Net.S_KICK);
        w.out.writeUTF(reason);
        net.sendAndClose(c, w.build());
    }

    /** Expulsa alguem da sala (ou da partida). */
    public void kick(int connId) {
        NetHost.Conn c = net.conn(connId);
        if (c == null) return;
        try {
            refuse(c, "kicked");
        } catch (IOException ignored) {
        }
        c.hello = false;
        notices.add(I18n.f("player_kicked", c.name));
        if (world != null && c.playerId >= 0) world.removePlayer(c.playerId);
        broadcastLobby();
    }

    /** Le o "ola" de um aparelho; retorna false se foi recusado. */
    private boolean readHello(NetHost.Conn c, DataInputStream in) throws IOException {
        int proto = in.readInt();
        c.name = in.readUTF();
        c.look = Net.readLook(in);
        String pass = "";
        long token = 0;
        try {
            pass = in.readUTF();
            token = in.readLong();
        } catch (IOException ignored) {
        }
        c.token = token;
        if (proto != Net.PROTOCOL) {
            refuse(c, "version");
            return false;
        }
        if (password != null && !password.isEmpty() && !password.equals(pass)) {
            refuse(c, "password");
            return false;
        }
        return true;
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
                        if (c == null || !readHello(c, in)) break;
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
                    case Net.C_CHAT:
                        if (c != null && c.hello) sayAs(c.name, in.readUTF());
                        break;
                    case Net.C_VOTE:
                        if (c != null) {
                            int v = in.readByte();
                            c.vote = c.vote == v ? -1 : v;
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

    /** Mensagem do anfitriao no chat. */
    public void say(String text) {
        sayAs(hostName, text);
    }

    private void sayAs(String who, String text) {
        text = text.trim();
        if (text.isEmpty()) return;
        if (text.length() > 60) text = text.substring(0, 60);
        String line = who + ": " + text;
        chat.add(line);
        while (chat.size > 30) chat.removeIndex(0);
        try {
            Net.Writer w = new Net.Writer(Net.S_CHAT);
            w.out.writeUTF(line);
            net.broadcast(w.build());
        } catch (IOException ignored) {
        }
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
                w.out.writeByte(e.vote);
            }
            w.out.writeBoolean(mapVote);
            for (int i = 0; i < 3; i++) w.out.writeUTF(voteMaps[i] == null ? "" : voteMaps[i]);
            net.broadcast(w.build());
        } catch (IOException ignored) {
        }
    }

    private int teamFor(int wanted, boolean teams) {
        return teams ? wanted : (cfg.mode.teams ? C.TEAM_RED : C.TEAM_NONE);
    }

    private boolean realTeams() {
        return cfg.mode.chooseTeams();
    }

    /** Cria o mundo da partida e avisa todos os clientes. Retorna o mundo (jogador local = id 0). */
    public World startMatch(GameMap map) {
        long seed = rng.nextLong();
        World w = new World(map, cfg, true, seed);
        boolean teams = realTeams();
        Player hp = w.addPlayerWithId(0, hostName, teamFor(hostTeam, teams), hostLook.copy(), false);
        hp.netClient = -1;
        for (NetHost.Conn c : net.conns) {
            if (!c.alive || !c.hello) continue;
            Player p = w.addPlayerWithId(c.id, c.name, teamFor(c.team, teams), c.look, false);
            p.netClient = c.id;
            p.netToken = c.token;
            p.baseName = c.name;
            c.playerId = p.id;
        }
        int bots = cfg.mode == ModeType.TRAINING ? 0 : cfg.bots;
        if (cfg.mode == ModeType.DUEL) bots = Math.max(0, 2 - w.players.size);
        for (int i = 0; i < bots; i++) {
            int team = teams ? w.balancedTeam() : (cfg.mode.teams ? C.TEAM_RED : C.TEAM_NONE);
            w.addPlayer(Names.BOTS[(i * 7 + rng.nextInt(3)) % Names.BOTS.length], team, Look.random(rng), true);
        }
        w.start();
        for (NetHost.Conn c : net.conns) {
            if (!c.alive || !c.hello) continue;
            sendStart(c, w, seed);
        }
        lastRoster = Net.rosterHash(w);
        inGame = true;
        world = w;
        tick = 0;
        updateInfo();
        return w;
    }

    private long seedOf(World w) {
        return w.rng.nextLong();
    }

    private void sendStart(NetHost.Conn c, World w, long seed) {
        try {
            Net.Writer m = new Net.Writer(Net.S_START);
            cfg.write(m.out);
            m.out.writeLong(seed);
            m.out.writeShort(c.playerId);
            Net.writeRoster(m.out, w);
            Net.writeWorldState(m.out, w);
            net.send(c, m.build());
        } catch (IOException ignored) {
        }
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
                c.pending.emote = -1;
            }
        }
    }

    /** Mensagens durante a partida: saidas viram bots, entradas no meio e reconexoes. */
    public void updateInGame(World w) {
        world = w;
        Net.Packet p;
        while ((p = net.poll()) != null) {
            NetHost.Conn c = net.conn(p.from);
            try {
                if (p.type == Net.C_BYE) {
                    if (c != null && c.playerId >= 0) {
                        Player pl = w.player(c.playerId);
                        if (pl != null && pl.netClient == c.id) {
                            pl.netClient = -1;
                            pl.isBot = true;
                            pl.bot = new BotBrain(pl, cfg.difficulty, rng.nextLong());
                            if (pl.baseName == null) pl.baseName = pl.name;
                            pl.name = pl.baseName + " (bot)";
                            w.message("player_left", pl.id, -1, 1);
                        }
                    }
                    net.removeDead();
                } else if (p.type == Net.C_HELLO && c != null) {
                    if (!readHello(c, p.in())) continue;
                    joinInGame(c, w);
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void joinInGame(NetHost.Conn c, World w) throws IOException {
        // reconexao: mesmo aparelho volta a controlar o mesmo jogador
        Player back = null;
        if (c.token != 0) {
            for (int i = 0; i < w.players.size; i++) {
                Player q = w.players.get(i);
                if (q.netToken == c.token && (q.netClient < 0 || net.conn(q.netClient) == null || !net.conn(q.netClient).alive)) back = q;
            }
        }
        if (back == null && !allowJoinInGame) {
            refuse(c, "ingame");
            return;
        }
        c.hello = true;
        Net.Writer wel = new Net.Writer(Net.S_WELCOME);
        wel.out.writeShort(c.id);
        net.send(c, wel.build());
        Player pl;
        if (back != null) {
            pl = back;
            pl.isBot = false;
            pl.bot = null;
            pl.name = pl.baseName != null ? pl.baseName : c.name;
            w.message("player_back", pl.id, -1, 0);
        } else {
            // substitui um bot se a sala estiver cheia de bots, senao entra como novo
            int id = c.id;
            while (w.player(id) != null) id += 50;
            boolean teams = realTeams();
            int team = teams ? w.balancedTeam() : (cfg.mode.teams ? C.TEAM_RED : C.TEAM_NONE);
            pl = w.addPlayerWithId(id, c.name, team, c.look, false);
            pl.baseName = c.name;
            pl.netToken = c.token;
            pl.respawnTimer = 1.5f;
            w.mode.onJoin(pl);
            w.message("player_joined_game", pl.id, -1, 0);
            Player bot = null;
            for (int i = 0; i < w.players.size; i++) if (w.players.get(i).isBot && w.players.get(i).netToken == 0) bot = w.players.get(i);
            if (bot != null && w.players.size > Math.max(cfg.bots + 1, 4)) w.removePlayer(bot.id);
        }
        pl.netClient = c.id;
        c.playerId = pl.id;
        sendStart(c, w, seedOf(w));
        lastRoster = 0; // forca reenviar a lista de jogadores
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
        world = null;
        for (NetHost.Conn c : net.conns) c.playerId = -1;
        rollVoteMaps();
        net.broadcast(new Net.Writer(Net.S_LOBBY_RETURN).build());
        broadcastLobby();
    }

    public void close() {
        net.close();
    }
}
