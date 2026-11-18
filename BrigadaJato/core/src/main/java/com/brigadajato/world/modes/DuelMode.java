package com.brigadajato.world.modes;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.Gun;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Duelo: rodadas 1x1 (ou o ultimo de pe), todos com a mesma arma sorteada. Vence quem ganhar 3. */
public class DuelMode extends GameMode {
    public static final int PREP = 0, FIGHT = 1, END = 2;
    public int state = PREP;
    public float timer = 3f;
    public int round = 1;
    public int roundWeapon;
    public int lastWinner = -1;
    private final int[] wins = new int[512];

    public DuelMode(World w) {
        super(w, ModeType.DUEL);
    }

    @Override
    public void init() {
        pickWeapon();
    }

    private void pickWeapon() {
        WeaponType t;
        int guard = 0;
        do {
            t = WeaponType.random(w.rng);
        } while ((t.tier > 2 || t.isShield() || t == WeaponType.GRAVITY) && guard++ < 40);
        roundWeapon = t.ordinal();
    }

    public int wins(Player p) {
        return p.id < wins.length ? wins[p.id] : 0;
    }

    @Override
    public boolean usesMapItems() {
        return false;
    }

    @Override
    public boolean canPickup(Player p) {
        return false;
    }

    @Override
    public boolean dropsWeapons() {
        return false;
    }

    @Override
    public boolean canRespawn(Player p) {
        return !over && state == PREP;
    }

    @Override
    public float respawnDelay(Player p) {
        return 0.1f;
    }

    @Override
    public boolean isFrozen(Player p) {
        return over || state == PREP;
    }

    @Override
    public void giveLoadout(Player p) {
        p.guns[0] = new Gun(WeaponType.of(roundWeapon));
        p.guns[1] = new Gun(WeaponType.PISTOL);
        p.slot = 0;
        p.frags = 1;
        p.mines = 0;
        p.armor = 0;
        p.health = C.HEALTH_MAX;
    }

    @Override
    protected Array<Vector2> spawnList(Player p) {
        Array<Vector2> all = new Array<>();
        all.addAll(w.map.spawnsRed);
        all.addAll(w.map.spawnsBlue);
        if (all.size == 0) all.addAll(w.map.spawnsFree);
        return all;
    }

    @Override
    public void update(float dt) {
        if (over) return;
        timer -= dt;
        if (state == PREP) {
            if (timer <= 0) {
                state = FIGHT;
                timer = 90f;
                WorldEvent e = w.emit(WorldEvent.Type.MESSAGE);
                e.text = "duel_fight";
                e.a = -1;
                e.b = -1;
                e.c = 2;
            }
            return;
        }
        if (state == FIGHT) {
            int alive = 0;
            Player last = null;
            for (int i = 0; i < w.players.size; i++) {
                Player p = w.players.get(i);
                if (p.alive) {
                    alive++;
                    last = p;
                }
            }
            if (alive <= 1 || timer <= 0) {
                if (alive > 1) { // tempo acabou: vence quem tem mais vida
                    for (int i = 0; i < w.players.size; i++) {
                        Player p = w.players.get(i);
                        if (p.alive && (last == null || p.health > last.health)) last = p;
                    }
                }
                lastWinner = last != null ? last.id : -1;
                if (last != null && last.id < wins.length) {
                    wins[last.id]++;
                    last.score++;
                    if (wins[last.id] >= Math.max(1, w.config.scoreLimit)) {
                        over = true;
                        winnerId = last.id;
                        endReason = "duel_win";
                        return;
                    }
                }
                state = END;
                timer = 3f;
                WorldEvent e = w.emit(WorldEvent.Type.ROUND_END);
                e.a = lastWinner;
            }
            return;
        }
        if (timer <= 0) {
            round++;
            state = PREP;
            timer = 3f;
            pickWeapon();
            for (int i = 0; i < w.players.size; i++) {
                Player p = w.players.get(i);
                p.alive = false;
                p.respawnTimer = 0.05f + i * 0.05f;
            }
            w.projectiles.clear();
        }
    }

    @Override
    public String hud(Player local) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.t("round")).append(' ').append(round).append("   ");
        for (int i = 0; i < w.players.size && i < 4; i++) {
            Player p = w.players.get(i);
            if (i > 0) sb.append("  x  ");
            sb.append(p.name).append(' ').append(wins(p));
        }
        return sb.toString();
    }

    @Override
    public String banner(Player local) {
        if (state == PREP) {
            WeaponType t = WeaponType.of(roundWeapon);
            return I18n.f("duel_prep", round, I18n.pt ? t.pt : t.en, (int) Math.ceil(Math.max(0, timer)));
        }
        if (state == END) {
            Player p = w.player(lastWinner);
            return p != null ? I18n.f("duel_round_won", p.name) : I18n.t("duel_draw");
        }
        return null;
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        o.writeByte(state);
        o.writeFloat(timer);
        o.writeShort(round);
        o.writeByte(roundWeapon);
        o.writeShort(lastWinner);
        o.writeByte(w.players.size);
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            o.writeShort(p.id);
            o.writeByte(wins(p));
        }
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        state = in.readByte();
        timer = in.readFloat();
        round = in.readShort();
        roundWeapon = in.readByte();
        lastWinner = in.readShort();
        int n = in.readByte();
        for (int i = 0; i < n; i++) {
            int id = in.readShort();
            int v = in.readByte();
            if (id >= 0 && id < wins.length) wins[id] = v;
        }
    }
}
