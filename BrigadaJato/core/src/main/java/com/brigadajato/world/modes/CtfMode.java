package com.brigadajato.world.modes;

import com.badlogic.gdx.math.Vector2;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Capture a bandeira. */
public class CtfMode extends GameMode {
    public static final int HOME = 0, CARRIED = 1, DROPPED = 2;
    public static final int EV_TAKEN = 0, EV_DROPPED = 1, EV_RETURNED = 2, EV_CAPTURED = 3;

    /** indice = time dono da bandeira (1 vermelho, 2 azul) */
    public final int[] state = new int[3];
    public final int[] carrier = {-1, -1, -1};
    public final float[] fx = new float[3], fy = new float[3];
    public final float[] returnTimer = new float[3];

    public CtfMode(World w) {
        super(w, ModeType.CTF);
    }

    @Override
    public void init() {
        for (int t = 1; t <= 2; t++) resetFlag(t);
    }

    private Vector2 base(int team) {
        if (!w.map.hasFlags) {
            // mapas sem base: usa o primeiro spawn de cada time
            return team == C.TEAM_RED ? w.map.spawnsRed.first() : w.map.spawnsBlue.first();
        }
        return team == C.TEAM_RED ? w.map.flagRed : w.map.flagBlue;
    }

    private void resetFlag(int t) {
        state[t] = HOME;
        carrier[t] = -1;
        Vector2 b = base(t);
        fx[t] = b.x;
        fy[t] = b.y;
    }

    private void flagEvent(int team, int action, int playerId) {
        WorldEvent e = w.emit(WorldEvent.Type.FLAG);
        e.a = team;
        e.b = action;
        e.c = playerId;
        e.x = fx[team];
        e.y = fy[team];
    }

    @Override
    public float respawnDelay(Player p) {
        return 4f;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        if (over) return;
        for (int t = 1; t <= 2; t++) {
            int enemy = t == 1 ? 2 : 1;
            if (state[t] == CARRIED) {
                Player c = w.player(carrier[t]);
                if (c == null || !c.alive) {
                    drop(t, c);
                    continue;
                }
                fx[t] = c.x;
                fy[t] = c.y;
                // capturou? (precisa da propria bandeira em casa)
                Vector2 home = base(enemy);
                if (state[enemy] == HOME && near(c, home.x, home.y, 40)) {
                    teamScore[enemy]++;
                    c.score += 5;
                    c.carryingFlag = 0;
                    flagEvent(t, EV_CAPTURED, c.id);
                    resetFlag(t);
                    if (w.config.scoreLimit > 0 && teamScore[enemy] >= w.config.scoreLimit) finishByScore("end_score");
                }
                continue;
            }
            if (state[t] == DROPPED) {
                // cai ate o chao
                if (!w.map.solidAt(fx[t], fy[t] - 2)) fy[t] = Math.max(0, fy[t] - 400 * dt);
                returnTimer[t] -= dt;
                if (returnTimer[t] <= 0) {
                    resetFlag(t);
                    flagEvent(t, EV_RETURNED, -1);
                    continue;
                }
            }
            for (Player p : w.players) {
                if (!p.alive || p.dummy) continue;
                if (!near(p, fx[t], fy[t], 34)) continue;
                if (p.team == enemy && p.carryingFlag == 0) {
                    state[t] = CARRIED;
                    carrier[t] = p.id;
                    p.carryingFlag = t;
                    p.spawnShield = 0;
                    flagEvent(t, EV_TAKEN, p.id);
                    break;
                } else if (p.team == t && state[t] == DROPPED) {
                    p.score += 2;
                    resetFlag(t);
                    flagEvent(t, EV_RETURNED, p.id);
                    break;
                }
            }
        }
    }

    private void drop(int t, Player c) {
        state[t] = DROPPED;
        carrier[t] = -1;
        returnTimer[t] = 15f;
        if (c != null) {
            c.carryingFlag = 0;
            fx[t] = c.x;
            fy[t] = c.y;
        }
        flagEvent(t, EV_DROPPED, c != null ? c.id : -1);
    }

    @Override
    public void onKill(Player victim, Player attacker) {
        if (victim.carryingFlag != 0) {
            int t = victim.carryingFlag;
            fx[t] = victim.x;
            fy[t] = victim.y;
            drop(t, victim);
            if (attacker != null) attacker.score += 1;
        }
    }

    private static boolean near(Player p, float x, float y, float r) {
        float dx = p.x - x, dy = p.y + 20 - (y + 20);
        return dx * dx + dy * dy < r * r;
    }

    @Override
    public boolean botGoal(Player bot, Vector2 out) {
        int own = bot.team, enemy = own == 1 ? 2 : 1;
        if (bot.carryingFlag != 0) {
            out.set(base(own));
            return true;
        }
        if (state[own] == CARRIED) {
            Player c = w.player(carrier[own]);
            if (c != null) {
                out.set(c.x, c.y);
                return true;
            }
        }
        if (state[own] == DROPPED) {
            out.set(fx[own], fy[own]);
            return true;
        }
        if (bot.id % 4 != 0 || bot.id == 0 && countTeam(own) <= 2) { // atacantes
            if (state[enemy] == CARRIED) {
                Player c = w.player(carrier[enemy]);
                if (c != null) {
                    out.set(c.x, c.y + 60);
                    return true;
                }
            }
            out.set(fx[enemy], fy[enemy]);
            return true;
        }
        // defensor: patrulha em volta da base (sem ficar parado em cima da bandeira)
        Vector2 b = base(own);
        float side = b.x < w.map.worldW() / 2 ? 1 : -1;
        float phase = (w.time * 0.15f + bot.id) % 3f;
        out.set(b.x + side * (120 + phase * 140), b.y + (phase > 1.5f ? 96 : 0));
        return true;
    }

    private int countTeam(int team) {
        int n = 0;
        for (Player p : w.players) if (p.team == team) n++;
        return n;
    }

    @Override
    public String hud(Player local) {
        String s = teamScoreLine();
        if (w.config.timeLimit > 0) s = clock(timeLeft) + "     " + s;
        return s;
    }

    @Override
    public String banner(Player local) {
        if (local != null && local.carryingFlag != 0) return I18n.t("ctf_carry");
        return null;
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        for (int t = 1; t <= 2; t++) {
            o.writeByte(state[t]);
            o.writeShort(carrier[t]);
            o.writeFloat(fx[t]);
            o.writeFloat(fy[t]);
        }
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        for (int t = 1; t <= 2; t++) {
            state[t] = in.readByte();
            carrier[t] = in.readShort();
            fx[t] = in.readFloat();
            fy[t] = in.readFloat();
        }
    }
}
