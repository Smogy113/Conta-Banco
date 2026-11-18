package com.brigadajato.world.modes;

import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.Gun;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

/**
 * Gun Game: cada abate troca a sua arma pela proxima da escada. Quem fizer um abate com a
 * ultima (a faca/soco) vence. Golpe corpo a corpo rebaixa a vitima um nivel.
 */
public class GunGameMode extends GameMode {
    public static final WeaponType[] LADDER = {
            WeaponType.SMG, WeaponType.RIFLE, WeaponType.SHOTGUN, WeaponType.DUAL_SMG, WeaponType.LMG,
            WeaponType.FLAMETHROWER, WeaponType.TESLA, WeaponType.SNIPER, WeaponType.BOW, WeaponType.HARPOON,
            WeaponType.ROCKET, WeaponType.BOOMERANG, WeaponType.DUAL_PISTOLS, WeaponType.MAGNUM, WeaponType.PISTOL};

    /** ultimo nivel: so soco / faca */
    public static final int FINAL = LADDER.length;

    public GunGameMode(World w) {
        super(w, ModeType.GUN_GAME);
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
    public boolean allowGrenades(Player p) {
        return false;
    }

    @Override
    public boolean allowWeaponChange(Player p) {
        return false;
    }

    @Override
    public boolean dropsWeapons() {
        return false;
    }

    @Override
    public float respawnDelay(Player p) {
        return 2f;
    }

    @Override
    public void giveLoadout(Player p) {
        p.guns[0] = p.gunLevel < FINAL ? new Gun(LADDER[p.gunLevel]) : null;
        if (p.guns[0] != null) p.guns[0].reserve = p.guns[0].type.reserve * 3 + p.guns[0].type.mag * 2;
        p.guns[1] = null;
        p.slot = 0;
        p.frags = 0;
        p.mines = 0;
    }

    @Override
    public boolean infiniteAmmo(Player p) {
        return false;
    }

    @Override
    public void onKill(Player victim, Player attacker, int code) {
        if (code == C.KILL_MELEE && victim.gunLevel > 0) {
            victim.gunLevel--;
            w.message("gg_demoted", victim.id, victim.gunLevel + 1, 1);
        }
        if (attacker == null || attacker == victim) return;
        if (attacker.gunLevel >= FINAL) {
            if (code == C.KILL_MELEE) {
                over = true;
                winnerId = attacker.id;
                endReason = "end_gungame";
            }
            return;
        }
        attacker.gunLevel++;
        WorldEvent e = w.emit(WorldEvent.Type.LEVEL_UP);
        e.a = attacker.id;
        e.b = attacker.gunLevel;
        e.x = attacker.x;
        e.y = attacker.y;
        if (attacker.alive) {
            int keep = attacker.guns[0] != null ? attacker.guns[0].type.ordinal() : -1;
            giveLoadout(attacker);
            attacker.reloadTimer = 0;
            attacker.fireCooldown = 0.2f;
            if (keep >= 0 && attacker.gunLevel >= FINAL) w.message("gg_knife", attacker.id, -1, 2);
        }
    }

    @Override
    protected void onTimeUp() {
        over = true;
        endReason = "end_time";
        Player best = null;
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            if (best == null || p.gunLevel > best.gunLevel || (p.gunLevel == best.gunLevel && p.kills > best.kills)) best = p;
        }
        winnerId = best != null ? best.id : -1;
    }

    public static String levelName(int level) {
        if (level >= FINAL) return I18n.t("gg_final");
        WeaponType t = LADDER[Math.max(0, level)];
        return I18n.pt ? t.pt : t.en;
    }

    @Override
    public String hud(Player local) {
        StringBuilder sb = new StringBuilder();
        if (w.config.timeLimit > 0) sb.append(clock(timeLeft)).append("   ");
        if (local != null) {
            sb.append(I18n.t("level")).append(' ').append(Math.min(FINAL, local.gunLevel) + 1).append('/').append(FINAL + 1)
                    .append("  ").append(levelName(local.gunLevel));
        }
        return sb.toString();
    }
}
