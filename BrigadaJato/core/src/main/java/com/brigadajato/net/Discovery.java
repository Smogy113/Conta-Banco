package com.brigadajato.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Descoberta automatica de salas na rede local via broadcast UDP. */
public final class Discovery {
    private Discovery() {
    }

    public static class Room {
        public String ip, name, mode, map;
        public int players, max;
        public long seen;
    }

    /** Roda no anfitriao: responde quem procura salas. */
    public static class Responder {
        private final NetHost host;
        private DatagramSocket socket;
        private volatile boolean running;

        Responder(NetHost host) {
            this.host = host;
        }

        public void start() {
            running = true;
            Thread t = new Thread(() -> {
                try {
                    socket = new DatagramSocket(null);
                    socket.setReuseAddress(true);
                    socket.setBroadcast(true);
                    socket.bind(new InetSocketAddress(Net.UDP_PORT));
                    byte[] buf = new byte[256];
                    while (running) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        socket.receive(p);
                        String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                        if (!msg.startsWith(Net.DISCOVER)) continue;
                        String ans = Net.ANSWER + "|" + host.roomName + "|" + host.modeName + "|" + host.mapName + "|"
                                + host.playerCount() + "|" + Net.MAX_PLAYERS + "|" + Net.PROTOCOL;
                        byte[] out = ans.getBytes(StandardCharsets.UTF_8);
                        socket.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                    }
                } catch (Exception e) {
                    // socket fechado
                }
            }, "bj-discovery-responder");
            t.setDaemon(true);
            t.start();
        }

        public void stop() {
            running = false;
            if (socket != null) socket.close();
        }
    }

    /** Roda no cliente: procura salas periodicamente. */
    public static class Finder {
        private final Map<String, Room> rooms = new LinkedHashMap<>();
        private volatile boolean running;
        private DatagramSocket socket;

        public void start() {
            running = true;
            Thread t = new Thread(() -> {
                try {
                    socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    socket.setSoTimeout(400);
                    byte[] ping = Net.DISCOVER.getBytes(StandardCharsets.UTF_8);
                    byte[] buf = new byte[512];
                    long lastPing = 0;
                    while (running) {
                        long now = System.currentTimeMillis();
                        if (now - lastPing > 1200) {
                            lastPing = now;
                            for (InetAddress b : Net.broadcastAddresses()) {
                                try {
                                    socket.send(new DatagramPacket(ping, ping.length, b, Net.UDP_PORT));
                                } catch (Exception ignored) {
                                }
                            }
                            try { // tambem testa a propria maquina (dois jogos no mesmo PC)
                                socket.send(new DatagramPacket(ping, ping.length, InetAddress.getByName("127.0.0.1"), Net.UDP_PORT));
                            } catch (Exception ignored) {
                            }
                        }
                        try {
                            DatagramPacket p = new DatagramPacket(buf, buf.length);
                            socket.receive(p);
                            String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                            if (!msg.startsWith(Net.ANSWER)) continue;
                            String[] parts = msg.split("\\|");
                            if (parts.length < 7) continue;
                            Room r = new Room();
                            r.ip = p.getAddress().getHostAddress();
                            r.name = parts[1];
                            r.mode = parts[2];
                            r.map = parts[3];
                            r.players = Integer.parseInt(parts[4]);
                            r.max = Integer.parseInt(parts[5]);
                            r.seen = System.currentTimeMillis();
                            synchronized (rooms) {
                                // a mesma sala pode responder por 127.0.0.1 e pelo IP real
                                if (r.ip.startsWith("127.") && hasRealIpFor(r)) continue;
                                rooms.put(r.ip, r);
                            }
                        } catch (java.net.SocketTimeoutException ignored) {
                        }
                    }
                } catch (Exception e) {
                    // fim
                } finally {
                    if (socket != null) socket.close();
                }
            }, "bj-discovery-finder");
            t.setDaemon(true);
            t.start();
        }

        private boolean hasRealIpFor(Room r) {
            for (Room o : rooms.values()) if (!o.ip.startsWith("127.") && o.name.equals(r.name)) return true;
            return false;
        }

        public List<Room> rooms() {
            List<Room> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            synchronized (rooms) {
                for (Room r : rooms.values()) if (now - r.seen < 4500) out.add(r);
            }
            return out;
        }

        public void stop() {
            running = false;
            if (socket != null) socket.close();
        }
    }
}
