package com.brigadajato.net;

import com.badlogic.gdx.utils.Array;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Logica do cliente: sala de espera, recepcao do estado do jogo e reconexao automatica. */
public class ClientSession {
    public interface MapLoader {
        GameMap load(String id);
    }

    public NetClient net;
    private final MapLoader loader;
    private final Link.Factory factory;
    private final String name, password;
    private final Look look;
    /** identificador deste aparelho: o anfitriao devolve o mesmo jogador ao reconectar */
    public final long token;
    public int myId = -1;
    public final MatchConfig cfg = new MatchConfig();
    public final List<HostSession.Entry> lobby = new ArrayList<>();
    public boolean mapVote;
    public final String[] voteMaps = new String[3];
    public World startedWorld;
    public int startedLocalId = -1;
    public boolean backToLobby;
    public String kicked;
    /** reconectando depois de cair (segundos tentando) */
    public float reconnecting = -1;
    private float retryTimer;
    /** mundo novo recebido depois de reconectar no meio da partida */
    public World rejoinedWorld;
    public int rejoinedLocalId = -1;

    public ClientSession(String host, String name, Look look, MapLoader loader) {
        this(TcpLink.to(host), name, look, "", loader);
    }

    public ClientSession(Link.Factory factory, String name, Look look, String password, MapLoader loader) {
        this.loader = loader;
        this.factory = factory;
        this.name = name;
        this.look = look;
        this.password = password == null ? "" : password;
        this.token = new java.util.Random().nextLong() | 1L;
        net = new NetClient(factory, name, look, this.password, token);
    }

    private World readStart(DataInputStream in) throws IOException {
        cfg.read(in);
        long seed = in.readLong();
        int localId = in.readShort();
        World w = new World(loader.load(cfg.mapId), cfg, false, seed);
        Net.readRoster(in, w);
        Net.readWorldState(in, w);
        startedLocalId = localId;
        return w;
    }

    /** Processa a sala de espera. Para ao receber o inicio da partida. */
    public void updateLobby() {
        Net.Packet p;
        while (startedWorld == null && (p = net.poll()) != null) {
            try {
                handleCommon(p);
                if (p.type == Net.S_START) {
                    startedWorld = readStart(p.in());
                    backToLobby = false;
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void handleCommon(Net.Packet p) throws IOException {
        DataInputStream in = p.in();
        switch (p.type) {
            case Net.S_WELCOME:
                myId = in.readShort();
                break;
            case Net.S_LOBBY: {
                cfg.read(in);
                int n = in.readShort();
                lobby.clear();
                for (int i = 0; i < n; i++) {
                    HostSession.Entry e = new HostSession.Entry();
                    e.id = in.readShort();
                    e.name = in.readUTF();
                    e.look = Net.readLook(in);
                    e.team = in.readByte();
                    e.host = in.readBoolean();
                    e.vote = in.readByte();
                    lobby.add(e);
                }
                mapVote = in.readBoolean();
                for (int i = 0; i < 3; i++) {
                    String s = in.readUTF();
                    voteMaps[i] = s.isEmpty() ? null : s;
                }
                break;
            }
            case Net.S_KICK:
                kicked = in.readUTF();
                break;
            case Net.S_LOBBY_RETURN:
                backToLobby = true;
                break;
            default:
                break;
        }
    }

    public int[] voteCounts() {
        int[] v = new int[3];
        for (HostSession.Entry e : lobby) if (e.vote >= 0 && e.vote < 3) v[e.vote]++;
        return v;
    }

    public int myVote() {
        for (HostSession.Entry e : lobby) if (e.id == myId) return e.vote;
        return -1;
    }

    /** Durante a partida: aplica estados e coleta eventos; reconecta sozinho se cair. */
    public void receive(World w, Array<WorldEvent> events, int localId, float dt) {
        if (net.state == NetClient.State.CLOSED || net.state == NetClient.State.FAILED) {
            if (kicked != null) return;
            if (reconnecting < 0) {
                reconnecting = 0;
                retryTimer = 0.5f;
            }
            reconnecting += dt;
            retryTimer -= dt;
            if (retryTimer <= 0 && reconnecting < 30) {
                retryTimer = 2.5f;
                net = new NetClient(factory, name, look, password, token);
            }
            return;
        }
        Net.Packet p;
        int guard = 0;
        while ((p = net.poll()) != null && guard++ < 500) {
            try {
                switch (p.type) {
                    case Net.S_SNAP:
                        if (reconnecting < 0) Net.readSnapshot(p.in(), w, localId);
                        break;
                    case Net.S_EVENTS:
                        if (reconnecting < 0) Net.readEvents(p.in(), events);
                        break;
                    case Net.S_ROSTER:
                        if (reconnecting < 0) Net.readRoster(p.in(), w);
                        break;
                    case Net.S_START:
                        // voltou: o anfitriao mandou o estado atual da partida
                        rejoinedWorld = readStart(p.in());
                        rejoinedLocalId = startedLocalId;
                        reconnecting = -1;
                        return;
                    default:
                        handleCommon(p);
                        if (backToLobby) return;
                        break;
                }
            } catch (IOException ignored) {
            }
        }
        if (net.state == NetClient.State.CONNECTED && reconnecting >= 0 && reconnecting > 30) reconnecting = -1;
    }

    /** Desistiu de reconectar? */
    public boolean gaveUp() {
        return reconnecting >= 30;
    }

    public void vote(int option) {
        net.vote(option);
    }

    public void close() {
        net.close();
    }
}
