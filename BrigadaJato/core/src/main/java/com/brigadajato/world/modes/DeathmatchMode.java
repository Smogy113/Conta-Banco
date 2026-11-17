package com.brigadajato.world.modes;

import com.brigadajato.I18n;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;

/** Todos contra todos. */
public class DeathmatchMode extends GameMode {
    public DeathmatchMode(World w) {
        super(w, ModeType.DEATHMATCH);
    }

    @Override
    public void onKill(Player victim, Player attacker) {
        if (attacker != null && attacker != victim && w.config.scoreLimit > 0 && attacker.kills >= w.config.scoreLimit) {
            finishByScore("end_score");
        }
    }

    @Override
    public String hud(Player local) {
        StringBuilder sb = new StringBuilder();
        if (w.config.timeLimit > 0) sb.append(clock(timeLeft)).append("   ");
        if (local != null) sb.append(I18n.t("kills")).append(": ").append(local.kills);
        if (w.config.scoreLimit > 0) sb.append(" / ").append(w.config.scoreLimit);
        return sb.toString();
    }
}
