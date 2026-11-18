package com.brigadajato.world.modes;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.PlayerInput;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Pique-esconde: os escondidos (vermelho) viram objetos do cenario (caixa, barril, arbusto, vaso)
 * e nao atiram. Os cacadores (azul) ficam presos e cegos no comeco. Cada tiro errado custa vida
 * ao cacador. Escondido abatido vira cacador. Se alguem sobreviver ate o fim, os escondidos vencem.
 */
public class HideSeekMode extends GameMode {
    public static final int DISGUISES = 4;
    public static final float HIDE_TIME = 20f;
    public float hideTimer = HIDE_TIME;
    private final Array<Vector2> spots = new Array<>();

    public HideSeekMode(World w) {
        super(w, ModeType.HIDE_SEEK);
    }

    @Override
    public void init() {
        int n = w.players.size;
        int seekers = Math.max(1, n / 3);
        Array<Player> all = new Array<>(w.players);
        all.shuffle();
        for (int i = 0; i < all.size; i++) {
            Player p = all.get(i);
            if (i < seekers) {
                p.team = C.TEAM_BLUE;
                p.disguise = -1;
            } else {
                p.team = C.TEAM_RED;
                p.disguise = w.rng.nextInt(DISGUISES);
            }
        }
        // lugares para os bots se esconderem: pontos de arma, vida, spawn
        spots.addAll(w.map.weaponSpawns);
        spots.addAll(w.map.healthSpawns);
        spots.addAll(w.map.spawnsFree);
    }

    @Override
    public void onJoin(Player p) {
        p.team = C.TEAM_BLUE;
        p.disguise = -1;
    }

    private boolean seeker(Player p) {
        return p.team == C.TEAM_BLUE;
    }

    @Override
    public boolean usesMapItems() {
        return false;
    }

    @Override
    public boolean canPickup(Player p) {
        return seeker(p);
    }

    @Override
    public boolean allowGrenades(Player p) {
        return seeker(p);
    }

    @Override
    public boolean dropsWeapons() {
        return false;
    }

    @Override
    public void giveLoadout(Player p) {
        if (seeker(p)) {
            p.giveDefaultLoadout(w.rng);
            p.disguise = -1;
            p.smokes = 0;
            p.mines = 0;
            p.stickies = 0;
        } else {
            p.guns[0] = null;
            p.guns[1] = null;
            p.frags = p.mines = p.smokes = p.flashes = p.stickies = 0;
            if (p.disguise < 0) p.disguise = w.rng.nextInt(DISGUISES);
        }
    }

    @Override
    public boolean isFrozen(Player p) {
        return over || (seeker(p) && hideTimer > 0);
    }

    @Override
    public float speedMul(Player p) {
        return seeker(p) ? 1f : 0.85f;
    }

    @Override
    public float respawnDelay(Player p) {
        return 3f;
    }

    @Override
    public void onShotFired(Player p) {
        // cada tiro custa um pouco de vida ao cacador (nada de atirar em tudo)
        if (seeker(p) && p.health > 12) p.health -= 1.2f;
    }

    @Override
    public void onKill(Player victim, Player attacker, int code) {
        if (!seeker(victim)) {
            victim.team = C.TEAM_BLUE;
            victim.disguise = -1;
            if (attacker != null) attacker.score += 2;
            w.message("hs_found", victim.id, -1, 1);
        }
    }

    @Override
    public void update(float dt) {
        if (over) return;
        if (hideTimer > 0) {
            hideTimer -= dt;
            for (int i = 0; i < w.players.size; i++) {
                Player p = w.players.get(i);
                if (seeker(p)) p.flash = Math.max(p.flash, 0.9f); // cacadores de olhos fechados
            }
            if (hideTimer <= 0) {
                WorldEvent e = w.emit(WorldEvent.Type.MESSAGE);
                e.text = "hs_go";
                e.a = -1;
                e.b = -1;
                e.c = 1;
            }
            return;
        }
        // escondido aperta "trocar" para mudar de disfarce
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            if (!seeker(p) && p.alive && p.input.pressed(PlayerInput.P_SWAP)) p.disguise = (p.disguise + 1) % DISGUISES;
            if (!seeker(p) && p.alive && ((int) (w.time * 10)) % 100 == 0) p.score++;
        }
        super.update(dt);
        int hiders = 0;
        for (int i = 0; i < w.players.size; i++) if (!seeker(w.players.get(i))) hiders++;
        if (hiders == 0) {
            over = true;
            winnerTeam = C.TEAM_BLUE;
            endReason = "hs_all_found";
        }
    }

    @Override
    protected void onTimeUp() {
        over = true;
        winnerTeam = C.TEAM_RED;
        endReason = "hs_survived";
    }

    @Override
    public boolean botGoal(Player bot, Vector2 out) {
        if (seeker(bot) || spots.size == 0) return false;
        Vector2 s = spots.get((bot.id * 7 + 3) % spots.size);
        out.set(s);
        return true;
    }

    @Override
    public String hud(Player local) {
        int hiders = 0, seekers = 0;
        for (int i = 0; i < w.players.size; i++) {
            if (seeker(w.players.get(i))) seekers++;
            else hiders++;
        }
        return I18n.t("hs_hiders") + ": " + hiders + "   " + I18n.t("hs_seekers") + ": " + seekers;
    }

    @Override
    public String banner(Player local) {
        if (hideTimer > 0) {
            boolean s = local != null && seeker(local);
            return I18n.f(s ? "hs_wait" : "hs_hide", (int) Math.ceil(hideTimer));
        }
        return null;
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        o.writeFloat(hideTimer);
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        hideTimer = in.readFloat();
    }
}
