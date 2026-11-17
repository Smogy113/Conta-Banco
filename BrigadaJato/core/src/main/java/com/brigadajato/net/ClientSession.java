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

/** Logica do cliente: sala de espera e recepcao do estado do jogo. */
public class ClientSession {
    public interface MapLoader {
        GameMap load(String id);
    }

    public final NetClient net;
    private final MapLoader loader;
    public int myId = -1;
    public final MatchConfig cfg = new MatchConfig();
    public final List<HostSession.Entry> lobby = new ArrayList<>();
    public World startedWorld;
    public int startedLocalId = -1;
    public boolean backToLobby;
    public String kicked;

    public ClientSession(String host, String name, Look look, MapLoader loader) {
        this.loader = loader;
        net = new NetClient(host, name, look);
    }

    /** Processa a sala de espera. Para ao receber o inicio da partida. */
    public void updateLobby() {
        Net.Packet p;
        while (startedWorld == null && (p = net.poll()) != null) {
            try {
                handleCommon(p);
                if (p.type == Net.S_START) {
                    DataInputStream in = p.in();
                    cfg.read(in);
                    long seed = in.readLong();
                    int localId = in.readShort();
                    World w = new World(loader.load(cfg.mapId), cfg, false, seed);
                    Net.readRoster(in, w);
                    startedLocalId = localId;
                    startedWorld = w;
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
                    lobby.add(e);
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

    /** Durante a partida: aplica estados e coleta eventos. */
    public void receive(World w, Array<WorldEvent> events, int localId) {
        Net.Packet p;
        int guard = 0;
        while ((p = net.poll()) != null && guard++ < 500) {
            try {
                switch (p.type) {
                    case Net.S_SNAP:
                        Net.readSnapshot(p.in(), w, localId);
                        break;
                    case Net.S_EVENTS:
                        Net.readEvents(p.in(), events);
                        break;
                    case Net.S_ROSTER:
                        Net.readRoster(p.in(), w);
                        break;
                    default:
                        handleCommon(p);
                        if (backToLobby) return;
                        break;
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void close() {
        net.close();
    }
}
