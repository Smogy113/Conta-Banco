package com.brigadajato.net;

import com.brigadajato.world.Look;
import com.brigadajato.world.PlayerInput;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Anfitriao (servidor TCP + respondedor de descoberta UDP). */
public class NetHost {
    public static class Conn {
        public final int id;
        final Link link;
        final LinkedBlockingQueue<byte[]> sendQ = new LinkedBlockingQueue<>();
        volatile boolean alive = true;
        public volatile boolean hello;
        public String name = "?";
        public Look look = new Look();
        public int team;
        public final PlayerInput pending = new PlayerInput();
        /** id do jogador no mundo durante a partida */
        public int playerId = -1;
        /** identificador do aparelho (para reconectar no mesmo jogador) */
        public long token;
        /** voto de mapa na sala (-1 = nenhum) */
        public int vote = -1;
        public boolean bluetooth;

        Conn(int id, Link l) {
            this.id = id;
            this.link = l;
        }
    }

    public final List<Conn> conns = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Net.Packet> inbox = new ConcurrentLinkedQueue<>();
    private ServerSocket server;
    private Thread acceptThread;
    private Discovery.Responder responder;
    private volatile boolean running;
    private int nextId = 1;
    public volatile String error;

    // informacoes anunciadas na descoberta
    public volatile String roomName = "?", modeName = "?", mapName = "?";
    public volatile boolean locked, inGame;

    public boolean start() {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(Net.TCP_PORT));
        } catch (IOException e) {
            error = e.getMessage();
            return false;
        }
        running = true;
        acceptThread = new Thread(this::acceptLoop, "bj-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        responder = new Discovery.Responder(this);
        responder.start();
        return true;
    }

    public int playerCount() {
        int n = 1;
        for (Conn c : conns) if (c.alive && c.hello) n++;
        return n;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                if (playerCount() >= Net.MAX_PLAYERS) {
                    s.close();
                    continue;
                }
                addLink(new TcpLink(s), false);
            } catch (IOException e) {
                if (!running) return;
            }
        }
    }

    /** Registra um canal novo (TCP ou Bluetooth) e comeca a ler/escrever nele. */
    public synchronized void addLink(Link l, boolean bluetooth) {
        if (!running || playerCount() >= Net.MAX_PLAYERS) {
            l.close();
            return;
        }
        final Conn c = new Conn(nextId++, l);
        c.bluetooth = bluetooth;
        conns.add(c);
        Thread r = new Thread(() -> readLoop(c), "bj-read-" + c.id);
        r.setDaemon(true);
        r.start();
        Thread w = new Thread(() -> writeLoop(c), "bj-write-" + c.id);
        w.setDaemon(true);
        w.start();
    }

    /** Sem Wi-Fi: so aceita canais adicionados (Bluetooth). */
    public boolean startWithoutTcp() {
        running = true;
        return true;
    }

    private void readLoop(Conn c) {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(c.link.in()));
            while (running && c.alive) {
                byte[] frame = Net.readFrame(in);
                byte type = frame[0];
                if (type == Net.C_INPUT) {
                    DataInputStream d = new DataInputStream(new java.io.ByteArrayInputStream(frame, 1, frame.length - 1));
                    float mx = d.readFloat(), my = d.readFloat(), aim = d.readFloat();
                    int flags = d.readByte();
                    int presses = d.readShort() & 0xffff;
                    int buy = d.readShort();
                    int emote = d.readByte();
                    synchronized (c.pending) {
                        c.pending.moveX = mx;
                        c.pending.moveY = my;
                        c.pending.aim = aim;
                        c.pending.fire = (flags & 1) != 0;
                        c.pending.use = (flags & 2) != 0;
                        c.pending.presses |= presses;
                        if (buy >= 0) c.pending.buy = buy;
                        if (emote >= 0) c.pending.emote = emote;
                    }
                } else {
                    byte[] body = new byte[frame.length - 1];
                    System.arraycopy(frame, 1, body, 0, body.length);
                    inbox.add(new Net.Packet(type, body, c.id));
                }
            }
        } catch (IOException e) {
            // desconectou
        }
        c.alive = false;
        inbox.add(new Net.Packet(Net.C_BYE, new byte[0], c.id));
        c.link.close();
    }

    private void writeLoop(Conn c) {
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(c.link.out(), 16384));
            while (running && c.alive) {
                byte[] msg = c.sendQ.poll(500, TimeUnit.MILLISECONDS);
                if (msg == null) continue;
                Net.writeFrame(out, msg);
                // junta o que mais estiver na fila antes de enviar
                byte[] more;
                while ((more = c.sendQ.poll()) != null) Net.writeFrame(out, more);
                out.flush();
            }
        } catch (Exception e) {
            c.alive = false;
        }
    }

    public Net.Packet poll() {
        return inbox.poll();
    }

    public Conn conn(int id) {
        for (Conn c : conns) if (c.id == id) return c;
        return null;
    }

    public void send(Conn c, byte[] msg) {
        if (c == null || !c.alive) return;
        if (c.sendQ.size() > 400) c.sendQ.clear(); // cliente muito atrasado: descarta
        c.sendQ.add(msg);
    }

    public void broadcast(byte[] msg) {
        for (Conn c : conns) if (c.alive && c.hello) send(c, msg);
    }

    public void removeDead() {
        for (Conn c : conns) if (!c.alive) conns.remove(c);
    }

    public void kick(Conn c) {
        c.alive = false;
        c.link.close();
    }

    /** Envia uma mensagem e fecha logo depois (expulsao). */
    public void sendAndClose(final Conn c, byte[] msg) {
        send(c, msg);
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
            }
            kick(c);
        }, "bj-kick");
        t.setDaemon(true);
        t.start();
    }

    public void close() {
        running = false;
        if (responder != null) responder.stop();
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        for (Conn c : conns) kick(c);
        conns.clear();
    }
}
