package com.brigadajato.net;

import com.badlogic.gdx.utils.Array;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Look;
import com.brigadajato.world.Pickup;
import com.brigadajato.world.Player;
import com.brigadajato.world.Projectile;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Protocolo do multiplayer local: constantes, mensagens e serializacao do mundo. */
public final class Net {
    private Net() {
    }

    public static final int TCP_PORT = 47130;
    public static final int UDP_PORT = 47131;
    public static final int PROTOCOL = 3;
    public static final String DISCOVER = "BRIGADAJATO?";
    public static final String ANSWER = "BRIGADAJATO!";
    public static final int MAX_PLAYERS = 8;

    // cliente -> anfitriao
    public static final byte C_HELLO = 1, C_INPUT = 2, C_TEAM = 3, C_BYE = 4;
    // anfitriao -> cliente
    public static final byte S_WELCOME = 10, S_LOBBY = 11, S_START = 12, S_SNAP = 13, S_EVENTS = 14, S_ROSTER = 15,
            S_LOBBY_RETURN = 16, S_KICK = 17;

    /** Um pacote recebido (tipo + dados). */
    public static class Packet {
        public final byte type;
        public final byte[] data;
        public final int from;

        public Packet(byte type, byte[] data, int from) {
            this.type = type;
            this.data = data;
            this.from = from;
        }

        public DataInputStream in() {
            return new DataInputStream(new java.io.ByteArrayInputStream(data));
        }
    }

    /** Escritor de mensagem: tipo + corpo. */
    public static class Writer {
        public final ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
        public final DataOutputStream out = new DataOutputStream(bytes);

        public Writer(byte type) {
            try {
                out.writeByte(type);
            } catch (IOException ignored) {
            }
        }

        public byte[] build() {
            return bytes.toByteArray();
        }
    }

    public static void writeFrame(DataOutputStream out, byte[] msg) throws IOException {
        out.writeInt(msg.length);
        out.write(msg);
    }

    public static byte[] readFrame(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > 4 * 1024 * 1024) throw new IOException("frame invalido: " + len);
        byte[] b = new byte[len];
        in.readFully(b);
        return b;
    }

    // ------------------------------------------------------------------ aparencia

    public static void writeLook(DataOutputStream o, Look l) throws IOException {
        o.writeInt(l.body);
        o.writeInt(l.helmet);
        o.writeInt(l.visor);
        o.writeInt(l.skin);
        o.writeByte(l.hat);
    }

    public static Look readLook(DataInputStream in) throws IOException {
        Look l = new Look();
        l.body = in.readInt();
        l.helmet = in.readInt();
        l.visor = in.readInt();
        l.skin = in.readInt();
        l.hat = in.readByte();
        return l;
    }

    // ------------------------------------------------------------------ elenco (quem esta na partida)

    public static void writeRoster(DataOutputStream o, World w) throws IOException {
        o.writeShort(w.players.size);
        for (Player p : w.players) {
            o.writeShort(p.id);
            o.writeUTF(p.name);
            writeLook(o, p.look);
            o.writeByte(p.team);
            o.writeBoolean(p.isBot);
            o.writeBoolean(p.dummy);
        }
    }

    public static void readRoster(DataInputStream in, World w) throws IOException {
        int n = in.readShort();
        Array<Integer> seen = new Array<>();
        for (int i = 0; i < n; i++) {
            int id = in.readShort();
            String name = in.readUTF();
            Look look = readLook(in);
            int team = in.readByte();
            boolean bot = in.readBoolean();
            boolean dummy = in.readBoolean();
            seen.add(id);
            Player p = w.player(id);
            if (p == null) {
                p = new Player(id, name, team, look, bot);
                p.alive = false;
                w.players.add(p);
            }
            p.name = name;
            p.look = look;
            p.team = team;
            p.isBot = bot;
            p.dummy = dummy;
        }
        for (int i = w.players.size - 1; i >= 0; i--) {
            if (!seen.contains(w.players.get(i).id, false)) w.players.removeIndex(i);
        }
    }

    /** Assinatura simples do elenco para saber quando reenviar. */
    public static int rosterHash(World w) {
        int h = w.players.size;
        for (Player p : w.players) h = h * 31 + p.id * 7 + p.team + (p.isBot ? 1 : 0);
        return h;
    }

    // ------------------------------------------------------------------ snapshot

    public static void writeSnapshot(DataOutputStream o, World w) throws IOException {
        o.writeFloat(w.time);
        o.writeShort(w.players.size);
        for (Player p : w.players) {
            o.writeShort(p.id);
            o.writeFloat(p.x);
            o.writeFloat(p.y);
            o.writeFloat(p.vx);
            o.writeFloat(p.vy);
            o.writeFloat(p.aim);
            int flags = (p.alive ? 1 : 0) | (p.onGround ? 2 : 0) | (p.crouching ? 4 : 0) | (p.jetting ? 8 : 0)
                    | (p.facing > 0 ? 16 : 0) | (p.spawnShield > 0 ? 32 : 0) | (p.hasBomb ? 64 : 0)
                    | (p.recentlyFired ? 128 : 0) | (p.fuelLocked ? 256 : 0);
            o.writeShort(flags);
            o.writeByte(p.carryingFlag);
            o.writeByte(clampByte(p.health));
            o.writeByte(clampByte(p.armor));
            o.writeByte(clampByte(p.fuel));
            for (int s = 0; s < 2; s++) {
                Gun g = p.guns[s];
                o.writeByte(g == null ? -1 : g.type.ordinal());
                o.writeShort(g == null ? 0 : g.mag);
                o.writeShort(g == null ? 0 : g.reserve);
            }
            o.writeByte(p.slot);
            o.writeByte(p.frags);
            o.writeByte(p.mines);
            o.writeByte(p.grenadeType);
            o.writeShort(p.kills);
            o.writeShort(p.deaths);
            o.writeShort(p.score);
            o.writeInt(p.money);
            o.writeByte(p.lives);
            o.writeFloat(p.respawnTimer);
            o.writeFloat(p.reloadTimer);
            o.writeByte((int) (p.actionProgress * 100));
            o.writeByte((int) Math.ceil(p.dmgBoost));
            o.writeByte((int) Math.ceil(p.jetBoost));
            o.writeByte((int) Math.ceil(p.speedBoost));
        }
        int synced = 0;
        for (Projectile pr : w.projectiles) if (pr.synced() && !pr.dead) synced++;
        o.writeShort(synced);
        for (Projectile pr : w.projectiles) {
            if (!pr.synced() || pr.dead) continue;
            o.writeInt(pr.id);
            o.writeByte(pr.kind.ordinal());
            o.writeFloat(pr.x);
            o.writeFloat(pr.y);
            o.writeFloat(pr.vx);
            o.writeFloat(pr.vy);
            o.writeShort(pr.owner);
            o.writeByte(pr.team);
            o.writeByte((pr.armed ? 1 : 0) | (pr.stuck ? 2 : 0));
            o.writeFloat(pr.rot);
            o.writeFloat(pr.fuse);
        }
        o.writeShort(w.pickups.size);
        for (Pickup pk : w.pickups) {
            o.writeInt(pk.id);
            o.writeByte(pk.type.ordinal());
            o.writeByte(pk.weapon == null ? -1 : pk.weapon.ordinal());
            o.writeFloat(pk.x);
            o.writeFloat(pk.y);
            o.writeBoolean(pk.active);
            o.writeBoolean(pk.dropped);
            o.writeFloat(pk.life);
        }
        w.mode.write(o);
    }

    private static int clampByte(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    public static void readSnapshot(DataInputStream in, World w, int localId) throws IOException {
        w.time = Math.max(w.time, in.readFloat());
        int n = in.readShort();
        for (int i = 0; i < n; i++) {
            int id = in.readShort();
            Player p = w.player(id);
            Player dummy = p == null ? new Player(id, "?", 0, new Look(), false) : null;
            if (p == null) p = dummy;
            float x = in.readFloat(), y = in.readFloat();
            p.vx = in.readFloat();
            p.vy = in.readFloat();
            float aim = in.readFloat();
            if (p.id != localId) p.aim = aim;
            int flags = in.readShort();
            boolean wasAlive = p.alive;
            p.alive = (flags & 1) != 0;
            p.onGround = (flags & 2) != 0;
            p.crouching = (flags & 4) != 0;
            p.jetting = (flags & 8) != 0;
            if (p.id != localId) p.facing = (flags & 16) != 0 ? 1 : -1;
            p.spawnShield = (flags & 32) != 0 ? 1 : 0;
            p.hasBomb = (flags & 64) != 0;
            p.recentlyFired = (flags & 128) != 0;
            p.fuelLocked = (flags & 256) != 0;
            p.carryingFlag = in.readByte();
            p.health = in.readUnsignedByte();
            p.armor = in.readUnsignedByte();
            p.fuel = in.readUnsignedByte();
            for (int s = 0; s < 2; s++) {
                int t = in.readByte();
                int mag = in.readShort(), res = in.readShort();
                if (t < 0) p.guns[s] = null;
                else {
                    WeaponType wt = WeaponType.of(t);
                    if (p.guns[s] == null || p.guns[s].type != wt) p.guns[s] = new Gun(wt, mag, res);
                    p.guns[s].mag = mag;
                    p.guns[s].reserve = res;
                }
            }
            p.slot = in.readByte();
            p.frags = in.readByte();
            p.mines = in.readByte();
            p.grenadeType = in.readByte();
            p.kills = in.readShort();
            p.deaths = in.readShort();
            p.score = in.readShort();
            p.money = in.readInt();
            p.lives = in.readByte();
            p.respawnTimer = in.readFloat();
            p.reloadTimer = in.readFloat();
            p.actionProgress = in.readByte() / 100f;
            p.dmgBoost = in.readByte();
            p.jetBoost = in.readByte();
            p.speedBoost = in.readByte();
            p.netX = x;
            p.netY = y;
            if (!wasAlive && p.alive) {
                p.x = x;
                p.y = y;
            }
        }
        // projeteis sincronizados
        int np = in.readShort();
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < np; i++) {
            int id = in.readInt();
            Projectile.Kind kind = Projectile.Kind.values()[in.readByte()];
            float x = in.readFloat(), y = in.readFloat(), vx = in.readFloat(), vy = in.readFloat();
            int owner = in.readShort();
            int team = in.readByte();
            int fl = in.readByte();
            float rot = in.readFloat(), fuse = in.readFloat();
            seen.add(id);
            Projectile pr = null;
            for (Projectile q : w.projectiles) if (!q.visual && q.id == id) {
                pr = q;
                break;
            }
            if (pr == null) {
                pr = new Projectile();
                pr.id = id;
                pr.kind = kind;
                pr.x = x;
                pr.y = y;
                w.projectiles.add(pr);
            }
            pr.owner = owner;
            pr.team = team;
            float dx = x - pr.x, dy = y - pr.y;
            if (dx * dx + dy * dy > 60 * 60) {
                pr.x = x;
                pr.y = y;
            } else {
                pr.x += dx * 0.5f;
                pr.y += dy * 0.5f;
            }
            pr.vx = vx;
            pr.vy = vy;
            pr.armed = (fl & 1) != 0;
            pr.stuck = (fl & 2) != 0;
            if (kind == Projectile.Kind.MINE) pr.rot = rot;
            pr.fuse = fuse;
        }
        for (Projectile q : w.projectiles) {
            if (q.visual) continue;
            if (!seen.contains(q.id)) q.dead = true;
        }
        // itens
        int nk = in.readShort();
        Array<Pickup> old = new Array<>(w.pickups);
        w.pickups.clear();
        for (int i = 0; i < nk; i++) {
            int id = in.readInt();
            Pickup pk = null;
            for (Pickup q : old) if (q.id == id) {
                pk = q;
                break;
            }
            if (pk == null) {
                pk = new Pickup();
                pk.id = id;
                pk.bob = (id * 0.37f) % 6;
            }
            pk.type = Pickup.Type.values()[in.readByte()];
            pk.weapon = WeaponType.of(in.readByte());
            pk.x = in.readFloat();
            pk.y = in.readFloat();
            pk.active = in.readBoolean();
            pk.dropped = in.readBoolean();
            pk.life = in.readFloat();
            w.pickups.add(pk);
        }
        w.mode.read(in);
    }

    // ------------------------------------------------------------------ eventos

    public static void writeEvents(DataOutputStream o, Array<WorldEvent> events) throws IOException {
        o.writeShort(events.size);
        for (WorldEvent e : events) {
            o.writeByte(e.type.ordinal());
            o.writeInt(e.a);
            o.writeInt(e.b);
            o.writeInt(e.c);
            o.writeFloat(e.x);
            o.writeFloat(e.y);
            o.writeFloat(e.x2);
            o.writeFloat(e.y2);
            o.writeFloat(e.f);
            int na = e.angles == null ? 0 : e.angles.length;
            o.writeByte(na);
            for (int i = 0; i < na; i++) o.writeFloat(e.angles[i]);
            o.writeBoolean(e.text != null);
            if (e.text != null) o.writeUTF(e.text);
        }
    }

    public static void readEvents(DataInputStream in, Array<WorldEvent> out) throws IOException {
        int n = in.readShort();
        WorldEvent.Type[] types = WorldEvent.Type.values();
        for (int i = 0; i < n; i++) {
            WorldEvent e = new WorldEvent(types[in.readByte()]);
            e.a = in.readInt();
            e.b = in.readInt();
            e.c = in.readInt();
            e.x = in.readFloat();
            e.y = in.readFloat();
            e.x2 = in.readFloat();
            e.y2 = in.readFloat();
            e.f = in.readFloat();
            int na = in.readByte();
            if (na > 0) {
                e.angles = new float[na];
                for (int k = 0; k < na; k++) e.angles[k] = in.readFloat();
            }
            if (in.readBoolean()) e.text = in.readUTF();
            out.add(e);
        }
    }

    // ------------------------------------------------------------------ rede local

    /** IPs IPv4 locais (Wi-Fi / hotspot). */
    public static List<String> localIps() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            while (it != null && it.hasMoreElements()) {
                NetworkInterface ni = it.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress ad = addrs.nextElement();
                    if (ad instanceof Inet4Address && !ad.isLoopbackAddress()) ips.add(ad.getHostAddress());
                }
            }
        } catch (Exception ignored) {
        }
        return ips;
    }

    /** Enderecos de broadcast de todas as redes ativas (+ 255.255.255.255). */
    public static List<InetAddress> broadcastAddresses() {
        List<InetAddress> out = new ArrayList<>();
        try {
            out.add(InetAddress.getByName("255.255.255.255"));
            Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            while (it != null && it.hasMoreElements()) {
                NetworkInterface ni = it.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getBroadcast() != null && !out.contains(ia.getBroadcast())) out.add(ia.getBroadcast());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
