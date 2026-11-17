package com.brigadajato.world.modes;

import com.brigadajato.world.C;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;

/** Mata-mata em equipes. */
public class TeamDeathmatchMode extends GameMode {
    public TeamDeathmatchMode(World w) {
        super(w, ModeType.TEAM_DEATHMATCH);
    }

    @Override
    public void onKill(Player victim, Player attacker) {
        if (attacker == null || attacker == victim || attacker.team == victim.team) {
            // suicidio ou fogo amigo da ponto ao outro time
            int other = victim.team == C.TEAM_RED ? C.TEAM_BLUE : C.TEAM_RED;
            teamScore[other]++;
        } else {
            teamScore[attacker.team]++;
        }
        int lim = w.config.scoreLimit;
        if (lim > 0 && (teamScore[1] >= lim || teamScore[2] >= lim)) finishByScore("end_score");
    }

    @Override
    public String hud(Player local) {
        String s = teamScoreLine();
        if (w.config.timeLimit > 0) s = clock(timeLeft) + "     " + s;
        return s;
    }
}
