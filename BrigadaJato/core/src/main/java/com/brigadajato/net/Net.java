package com.brigadajato.net;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.brigadajato.world.Boss;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Prop;
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
    public static final int PROTOCOL = 8;
    public static final String DISCOVER = "BRIGADAJATO?";
    public static final String ANSWER = "BRIGADAJATO!";
    public static final int MAX_PLAYERS = 8;

    // cliente -> anfitriao
    public static final byte C_HELLO = 1, C_INPUT = 2, C_TEAM = 3, C_BYE = 4, C_VOTE = 5, C_CHAT = 6;
    // anfitriao -> cliente
    public static final byte S_WELCOME = 10, S_LOBBY = 11, S_START = 12, S_SNAP = 13, S_EVENTS = 14, S_ROSTER = 15,
            S_LOBBY_RETURN = 16, S_KICK = 17, S_CHAT = 18;

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
        o.writeByte(l.hair);
        o.writeInt(l.hairColor);
        o.writeByte(l.eyes);
        o.writeByte(l.mouth);
        o.writeByte(l.beard);
        o.writeByte(l.eyewear);
        o.writeByte(l.pattern);
        o.writeInt(l.patternColor);
        o.writeInt(l.pants);
        o.writeByte(l.jetpack);
        o.writeInt(l.jetColor);
        o.writeByte(l.flame);
        o.writeByte(l.gunSkin);
        o.writeByte(l.accessory);
        o.writeInt(l.accessoryColor);
        o.writeByte(l.costume);
        o.writeByte(l.killFx);
        o.writeByte(l.trail);
    }

    public static Look readLook(DataInputStream in) throws IOException {
        Look l = new Look();
        l.body = in.readInt();
        l.helmet = in.readInt();
        l.visor = in.readInt();
        l.skin = in.readInt();
        l.hat = in.readByte();
        l.hair = in.readByte();
        l.hairColor = in.readInt();
        l.eyes = in.readByte();
        l.mouth = in.readByte();
        l.beard = in.readByte();
        l.eyewear = in.readByte();
        l.pattern = in.readByte();
        l.patternColor = in.readInt();
        l.pants = in.readInt();
        l.jetpack = in.readByte();
        l.jetColor = in.readInt();
        l.flame = in.readByte();
        l.gunSkin = in.readByte();
        l.accessory = in.readByte();
        l.accessoryColor = in.readInt();
        l.costume = in.readByte();
        l.killFx = in.readByte();
        l.trail = in.readByte();
        return l;
    }

    // ------------------------------------------------------------------ elenco (quem esta na partida)

    public static void writeRoster(DataOutputStream o, World w) throws IOException {
        o.writeShort(w.players.size);
        for (int i67_ = 0; i67_ < w.players.size; i67_++) {
            Player p = w.players.get(i67_);
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
        for (int i68_ = 0; i68_ < w.players.size; i68_++) {
            Player p = w.players.get(i68_); h = h * 31 + p.id * 7 + p.team + (p.isBot ? 1 : 0);
        }
        return h;
    }

    // ------------------------------------------------------------------ snapshot

    public static void writeSnapshot(DataOutputStream o, World w) throws IOException {
        o.writeFloat(w.time);
        o.writeShort(w.players.size);
        for (int i69_ = 0; i69_ < w.players.size; i69_++) {
            Player p = w.players.get(i69_);
            o.writeShort(p.id);
            o.writeFloat(p.x);
            o.writeFloat(p.y);
            o.writeFloat(p.vx);
            o.writeFloat(p.vy);
            o.writeFloat(p.aim);
            int flags = (p.alive ? 1 : 0) | (p.onGround ? 2 : 0) | (p.crouching ? 4 : 0) | (p.jetting ? 8 : 0)
                    | (p.facing > 0 ? 16 : 0) | (p.spawnShield > 0 ? 32 : 0) | (p.hasBomb ? 64 : 0)
                    | (p.recentlyFired ? 128 : 0) | (p.fuelLocked ? 256 : 0) | (p.frozen > 0 ? 512 : 0)
                    | (p.zombie ? 1024 : 0) | (p.hookState != 0 ? 2048 : 0) | (p.stun > 0 ? 4096 : 0) | (p.dualFlip ? 8192 : 0) | (p.rollTimer > 0 ? 16384 : 0);
            o.writeChar(flags);
            if (p.hookState != 0) {
                o.writeByte(p.hookState);
                o.writeFloat(p.hookX);
                o.writeFloat(p.hookY);
            }
            o.writeByte(p.emoteTimer > 0 ? p.emote : -1);
            o.writeByte(clampByte(p.emoteTimer * 50));
            o.writeByte(clampByte(p.chill * 200));
            o.writeByte(p.gunLevel);
            o.writeByte(p.smokes);
            o.writeByte(p.flashes);
            o.writeByte(p.stickies);
            o.writeByte(clampByte(p.flash * 40));
            o.writeByte(p.disguise);
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
        for (int i70_ = 0; i70_ < w.projectiles.size; i70_++) {
            Projectile pr = w.projectiles.get(i70_); if (pr.synced() && !pr.dead) synced++;
        }
        o.writeShort(synced);
        for (int i71_ = 0; i71_ < w.projectiles.size; i71_++) {
            Projectile pr = w.projectiles.get(i71_);
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
        for (int i72_ = 0; i72_ < w.pickups.size; i72_++) {
            Pickup pk = w.pickups.get(i72_);
            o.writeInt(pk.id);
            o.writeByte(pk.type.ordinal());
            o.writeByte(pk.weapon == null ? -1 : pk.weapon.ordinal());
            o.writeFloat(pk.x);
            o.writeFloat(pk.y);
            o.writeBoolean(pk.active);
            o.writeBoolean(pk.dropped);
            o.writeFloat(pk.life);
        }
        o.writeByte(w.bosses.size);
        for (int i = 0; i < w.bosses.size; i++) {
            Boss b = w.bosses.get(i);
            o.writeInt(b.id);
            o.writeByte(b.type);
            o.writeFloat(b.x);
            o.writeFloat(b.y);
            o.writeFloat(b.vx);
            o.writeFloat(b.vy);
            o.writeFloat(b.hp);
            o.writeFloat(b.maxHp);
            o.writeFloat(b.aim);
            o.writeByte(b.facing);
            o.writeFloat(b.laserCharge);
            o.writeFloat(b.laserAim);
        }
        o.writeShort(w.props.size);
        for (int i = 0; i < w.props.size; i++) {
            Prop pr = w.props.get(i);
            o.writeBoolean(pr.alive);
            o.writeByte(clampByte(pr.hp));
        }
        o.writeByte(w.leverOn.length);
        for (boolean b : w.leverOn) o.writeBoolean(b);
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
            int flags = in.readChar();
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
            p.frozen = (flags & 512) != 0 ? 0.5f : 0;
            p.zombie = (flags & 1024) != 0;
            p.stun = (flags & 4096) != 0 ? 0.3f : 0;
            p.dualFlip = (flags & 8192) != 0;
            if ((flags & 2048) != 0) {
                p.hookState = in.readByte();
                p.hookX = in.readFloat();
                p.hookY = in.readFloat();
            } else p.hookState = 0;
            int emote = in.readByte();
            float et = in.readUnsignedByte() / 50f;
            if (emote != p.emote || Math.abs(et - p.emoteTimer) > 0.3f) {
                p.emote = emote;
                p.emoteTimer = emote >= 0 ? et : 0;
            }
            p.chill = in.readUnsignedByte() / 200f;
            p.gunLevel = in.readByte();
            p.smokes = in.readByte();
            p.flashes = in.readByte();
            p.stickies = in.readByte();
            p.flash = in.readUnsignedByte() / 40f;
            p.disguise = in.readByte();
            boolean rolling = (flags & 16384) != 0;
            if (rolling && p.rollTimer <= 0) {
                p.rollTimer = 0.34f;
                p.rollDir = p.vx >= 0 ? 1 : -1;
            } else if (!rolling) p.rollTimer = 0;
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
            for (int i73_ = 0; i73_ < w.projectiles.size; i73_++) {
                Projectile q = w.projectiles.get(i73_); if (!q.visual && q.id == id) {
                pr = q;
                break;
            }
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
        for (int i74_ = 0; i74_ < w.projectiles.size; i74_++) {
            Projectile q = w.projectiles.get(i74_);
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
            for (int i75_ = 0; i75_ < old.size; i75_++) {
                Pickup q = old.get(i75_); if (q.id == id) {
                pk = q;
                break;
            }
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
        int nb = in.readByte();
        Array<Boss> oldB = new Array<>(w.bosses);
        w.bosses.clear();
        for (int i = 0; i < nb; i++) {
            int id = in.readInt();
            Boss b = null;
            for (int k = 0; k < oldB.size; k++) if (oldB.get(k).id == id) b = oldB.get(k);
            if (b == null) {
                b = new Boss();
                b.id = id;
            }
            b.type = in.readByte();
            float bx = in.readFloat(), by = in.readFloat();
            if (Math.abs(bx - b.x) + Math.abs(by - b.y) > 120 || b.maxHp == 0) {
                b.x = bx;
                b.y = by;
            } else {
                b.x += (bx - b.x) * 0.5f;
                b.y += (by - b.y) * 0.5f;
            }
            b.vx = in.readFloat();
            b.vy = in.readFloat();
            float hp = in.readFloat();
            if (hp < b.hp) b.hurtFlash = 0.08f;
            b.hp = hp;
            b.maxHp = in.readFloat();
            b.aim = in.readFloat();
            b.facing = in.readByte();
            b.laserCharge = in.readFloat();
            b.laserAim = in.readFloat();
            w.bosses.add(b);
        }
        int npr = in.readShort();
        for (int i = 0; i < npr; i++) {
            boolean alive = in.readBoolean();
            int hp = in.readUnsignedByte();
            if (i < w.props.size) {
                Prop pr = w.props.get(i);
                if (hp < pr.hp && alive) pr.hurtFlash = 0.1f;
                pr.alive = alive;
                pr.hp = hp;
            }
        }
        int nl = in.readByte();
        for (int i = 0; i < nl; i++) {
            boolean b = in.readBoolean();
            if (i < w.leverOn.length) w.leverOn[i] = b;
        }
        w.mode.read(in);
    }

    /** Estado do cenario para quem entra no meio da partida (blocos quebrados, portas). */
    public static void writeWorldState(DataOutputStream o, World w) throws IOException {
        o.writeFloat(w.time);
        o.writeInt(w.changedTiles.size);
        for (IntIntMap.Entry e : w.changedTiles.entries()) {
            o.writeInt(e.key);
            o.writeByte(e.value);
        }
    }

    public static void readWorldState(DataInputStream in, World w) throws IOException {
        w.time = in.readFloat();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            int idx = in.readInt();
            int t = in.readByte();
            w.applyTile(idx, t);
        }
    }

    // ------------------------------------------------------------------ eventos

    public static void writeEvents(DataOutputStream o, Array<WorldEvent> events) throws IOException {
        o.writeShort(events.size);
        for (int i76_ = 0; i76_ < events.size; i76_++) {
            WorldEvent e = events.get(i76_);
            o.writeByte(e.type.ordinal());
            o.writeInt(e.a);
            o.writeInt(e.b);
            o.writeInt(e.c);
            o.writeShort(e.d);
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
            e.d = in.readShort();
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

    /** IPs IPv4 locais, os de rede local (Wi-Fi / hotspot) primeiro. */
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
        java.util.Collections.sort(ips, (x, y) -> ipRank(x) - ipRank(y));
        return ips;
    }

    /** 0 = 192.168 (hotspot/Wi-Fi), 1 = 172.16-31, 2 = 10.x, 3 = outros, 9 = dados moveis (100.64/10). */
    public static int ipRank(String ip) {
        if (ip.startsWith("192.168.")) return 0;
        if (ip.startsWith("172.")) {
            try {
                int b = Integer.parseInt(ip.split("\\.")[1]);
                if (b >= 16 && b <= 31) return 1;
            } catch (Exception ignored) {
            }
        }
        if (ip.startsWith("10.")) return 2;
        if (ip.startsWith("100.")) {
            try {
                int b = Integer.parseInt(ip.split("\\.")[1]);
                if (b >= 64 && b <= 127) return 9;
            } catch (Exception ignored) {
            }
        }
        return 3;
    }

    /** O aparelho esta numa rede local (Wi-Fi ou hotspot)? */
    public static boolean onLocalNetwork() {
        for (String ip : localIps()) if (ipRank(ip) <= 2) return true;
        return false;
    }

    /** Todos os enderecos da sub-rede /24 de cada IP local (varredura quando o broadcast e bloqueado). */
    public static List<InetAddress> sweepTargets() {
        List<InetAddress> out = new ArrayList<>();
        for (String ip : localIps()) {
            if (ipRank(ip) > 2) continue;
            String[] p = ip.split("\\.");
            if (p.length != 4) continue;
            String base = p[0] + "." + p[1] + "." + p[2] + ".";
            for (int i = 1; i < 255; i++) {
                String t = base + i;
                if (t.equals(ip)) continue;
                try {
                    out.add(InetAddress.getByName(t));
                } catch (Exception ignored) {
                }
            }
            if (out.size() > 1000) break;
        }
        return out;
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
