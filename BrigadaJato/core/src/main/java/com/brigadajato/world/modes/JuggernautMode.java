package com.brigadajato.world.modes;

import com.badlogic.gdx.math.Vector2;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.Gun;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Juggernaut: quem fizer o primeiro abate vira um tanque gigante (muita vida, metralhadora com
 * municao infinita, mais lento). Todos cacam ele; quem abate o Juggernaut vira o proximo.
 * So pontua quem esta de Juggernaut (por abate) ou quem derruba o Juggernaut.
 */
public class JuggernautMode extends GameMode {
    public int juggId = -1;
    /** vezes que cada jogador virou Juggernaut (estatistica) */
    public static final float ARMOR = 0.2f; // dano recebido pelo Juggernaut

    public JuggernautMode(World w) {
        super(w, ModeType.JUGGERNAUT);
    }

    public Player jugg() {
        return juggId >= 0 ? w.player(juggId) : null;
    }

    private void setJugg(Player p) {
        Player old = jugg();
        if (old != null) old.juggernaut = false;
        juggId = p != null ? p.id : -1;
        if (p == null) return;
        p.juggernaut = true;
        p.health = C.HEALTH_MAX;
        p.armor = 100;
        p.guns[0] = new Gun(WeaponType.LMG);
        p.guns[1] = null;
        p.slot = 0;
        p.reloadTimer = 0;
        p.timesJugg++;
        w.message("jug_new", p.id, -1, 0);
    }

    @Override
    public void onKill(Player victim, Player attacker, int code) {
        boolean wasJugg = victim.juggernaut;
        boolean validKiller = attacker != null && attacker != victim && attacker.alive;
        if (attacker != null && attacker != victim && !attacker.juggernaut && !wasJugg) attacker.score -= 1; // abate comum nao pontua
        if (wasJugg) {
            victim.juggernaut = false;
            if (validKiller) {
                if (attacker != null) attacker.juggKills++;
                setJugg(attacker);
            } else juggId = -1;
        } else if (juggId < 0 && validKiller) setJugg(attacker); // primeiro sangue
        if (attacker != null && w.config.scoreLimit > 0 && attacker.score >= w.config.scoreLimit) finishByScore("end_score");
    }

    @Override
    public void onKill(Player victim, Player attacker) {
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        Player j = jugg();
        if (j != null && (!j.alive || !w.players.contains(j, true))) {
            if (j.alive) j.juggernaut = false;
            juggId = -1;
        }
    }

    @Override
    public float damageMul(Player victim, Player attacker) {
        return victim.juggernaut ? ARMOR : 1f;
    }

    @Override
    public float speedMul(Player p) {
        return p.juggernaut ? 0.8f : 1f;
    }

    @Override
    public float fuelMul(Player p) {
        return p.juggernaut ? 1.7f : 1f;
    }

    @Override
    public boolean infiniteAmmo(Player p) {
        return p.juggernaut;
    }

    @Override
    public boolean canPickup(Player p) {
        return !p.juggernaut;
    }

    @Override
    public boolean allowWeaponChange(Player p) {
        return !p.juggernaut;
    }

    @Override
    public boolean allowGrenades(Player p) {
        return !p.juggernaut;
    }

    @Override
    public boolean botGoal(Player bot, Vector2 out) {
        Player j = jugg();
        if (j == null || j == bot || !j.alive) return false;
        out.set(j.x, j.y);
        return true;
    }

    @Override
    public String hud(Player local) {
        StringBuilder sb = new StringBuilder();
        Player j = jugg();
        if (j == null) sb.append(I18n.t("jug_first"));
        else if (j == local) sb.append(I18n.t("jug_you"));
        else sb.append("JUGGERNAUT: ").append(j.name);
        if (local != null) {
            sb.append("  ·  ").append(I18n.t("score")).append(": ").append(local.score);
            if (w.config.scoreLimit > 0) sb.append("/").append(w.config.scoreLimit);
        }
        return sb.toString();
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        o.writeShort(juggId);
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        juggId = in.readShort();
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            p.juggernaut = p.id == juggId;
        }
    }
}
