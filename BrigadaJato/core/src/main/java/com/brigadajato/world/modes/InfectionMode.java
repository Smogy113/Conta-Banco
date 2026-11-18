package com.brigadajato.world.modes;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Zumbis: um jogador vira zumbi depois da contagem. Zumbi so ataca no soco (forte), aguenta
 * mais e corre mais. Quem morre para zumbi vira zumbi. Humanos vencem se alguem sobreviver.
 */
public class InfectionMode extends GameMode {
    public float countdown = 10f;
    public boolean started;

    public InfectionMode(World w) {
        super(w, ModeType.INFECTION);
    }

    @Override
    public void init() {
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            p.team = C.TEAM_RED;
            p.zombie = false;
        }
    }

    @Override
    public void onJoin(Player p) {
        if (started) {
            p.team = C.TEAM_BLUE;
            p.zombie = true;
        } else {
            p.team = C.TEAM_RED;
        }
    }

    @Override
    public void giveLoadout(Player p) {
        if (p.zombie) {
            p.guns[0] = null;
            p.guns[1] = null;
            p.frags = 0;
            p.mines = 0;
            p.armor = 0;
        } else p.giveDefaultLoadout(w.rng);
    }

    @Override
    public boolean canPickup(Player p) {
        return !p.zombie;
    }

    @Override
    public boolean allowGrenades(Player p) {
        return !p.zombie;
    }

    @Override
    public float speedMul(Player p) {
        return p.zombie ? 1.18f : 1f;
    }

    @Override
    public float fuelMul(Player p) {
        return p.zombie ? 1.5f : 1f;
    }

    @Override
    public float damageMul(Player victim, Player attacker) {
        return victim.zombie ? 0.45f : 1f;
    }

    @Override
    public float damageDealtMul(Player p) {
        return p.zombie ? 1.6f : 1f;
    }

    @Override
    public float respawnDelay(Player p) {
        return 2.5f;
    }

    @Override
    public boolean dropsWeapons() {
        return true;
    }

    private void infect(Player p) {
        p.zombie = true;
        p.team = C.TEAM_BLUE;
        WorldEvent e = w.emit(WorldEvent.Type.INFECT);
        e.a = p.id;
        e.x = p.x;
        e.y = p.y;
    }

    @Override
    public void onKill(Player victim, Player attacker, int code) {
        if (!started) return;
        if (!victim.zombie) {
            infect(victim);
            if (attacker != null && attacker.zombie) attacker.score += 2;
            w.message("inf_infected", victim.id, -1, 1);
        } else if (attacker != null && !attacker.zombie) {
            attacker.score += 1;
        }
    }

    @Override
    public void update(float dt) {
        if (over) return;
        if (!started) {
            countdown -= dt;
            if (countdown <= 0 && w.players.size > 1) {
                started = true;
                // primeiro zumbi: sorteado entre os vivos
                Array<Player> alive = new Array<>();
                for (int i = 0; i < w.players.size; i++) if (w.players.get(i).alive) alive.add(w.players.get(i));
                if (alive.size == 0) alive.addAll(w.players);
                int n = Math.max(1, w.players.size / 6);
                for (int k = 0; k < n && alive.size > 0; k++) {
                    Player z = alive.removeIndex(w.rng.nextInt(alive.size));
                    infect(z);
                    giveLoadout(z);
                    z.health = C.HEALTH_MAX;
                    z.reloadTimer = 0;
                }
                w.message("inf_start", -1, -1, 1);
            }
            return;
        }
        super.update(dt);
        if (over) {
            winnerTeam = C.TEAM_RED;
            endReason = "inf_survived";
            return;
        }
        int humans = 0;
        for (int i = 0; i < w.players.size; i++) if (!w.players.get(i).zombie) humans++;
        if (humans == 0) {
            over = true;
            winnerTeam = C.TEAM_BLUE;
            endReason = "inf_all";
        }
        // humanos ganham pontos por sobreviver
        if (((int) (timeLeft * 1)) % 10 == 0 && ((int) ((timeLeft + dt) * 1)) % 10 != 0) {
            for (int i = 0; i < w.players.size; i++) {
                Player p = w.players.get(i);
                if (p.alive && !p.zombie) p.score++;
            }
        }
    }

    @Override
    protected void onTimeUp() {
        over = true;
    }

    @Override
    public boolean botGoal(Player bot, Vector2 out) {
        if (!bot.zombie) return false;
        Player best = null;
        float bd = 1e9f;
        for (int i = 0; i < w.players.size; i++) {
            Player q = w.players.get(i);
            if (!q.alive || q.zombie) continue;
            float d = bot.distTo(q);
            if (d < bd) {
                bd = d;
                best = q;
            }
        }
        if (best == null) return false;
        out.set(best.x, best.y);
        return true;
    }

    @Override
    public String hud(Player local) {
        int humans = 0, zombies = 0;
        for (int i = 0; i < w.players.size; i++) {
            if (w.players.get(i).zombie) zombies++;
            else humans++;
        }
        String s = I18n.t("inf_humans") + ": " + humans + "   " + I18n.t("inf_zombies") + ": " + zombies;
        if (started && w.config.timeLimit > 0) s = clock(timeLeft) + "   " + s;
        return s;
    }

    @Override
    public String banner(Player local) {
        if (!started) return I18n.f("inf_countdown", (int) Math.ceil(Math.max(0, countdown)));
        return null;
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        o.writeFloat(countdown);
        o.writeBoolean(started);
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        countdown = in.readFloat();
        started = in.readBoolean();
    }
}
