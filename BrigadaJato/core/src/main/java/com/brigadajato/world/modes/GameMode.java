package com.brigadajato.world.modes;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.Gun;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.World;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Regras de um modo de jogo. */
public abstract class GameMode {
    public final World w;
    public final ModeType type;
    public float timeLeft;
    public final int[] teamScore = new int[3];
    public boolean over;
    /** time vencedor (modos em equipe) ou -1 */
    public int winnerTeam = -1;
    /** id do jogador vencedor (mata-mata) ou -1 */
    public int winnerId = -1;
    /** mensagem de fim de partida (chave i18n) */
    public String endReason = "";

    protected GameMode(World w, ModeType type) {
        this.w = w;
        this.type = type;
        this.timeLeft = w.config.timeLimit;
    }

    public static GameMode create(World w, ModeType t) {
        switch (t) {
            case TEAM_DEATHMATCH:
                return new TeamDeathmatchMode(w);
            case CTF:
                return new CtfMode(w);
            case BOMB:
                return new BombMode(w);
            case SURVIVAL:
                return new SurvivalMode(w);
            case TRAINING:
                return new TrainingMode(w);
            case GUN_GAME:
                return new GunGameMode(w);
            case KING_HILL:
                return new KingHillMode(w);
            case INFECTION:
                return new InfectionMode(w);
            case BATTLE_ROYALE:
                return new BattleRoyaleMode(w);
            case DUEL:
                return new DuelMode(w);
            case HIDE_SEEK:
                return new HideSeekMode(w);
            default:
                return new DeathmatchMode(w);
        }
    }

    public void init() {
    }

    /** Alguem entrou no meio da partida. */
    public void onJoin(Player p) {
    }

    public boolean usesMapItems() {
        return true;
    }

    public void preUpdate(float dt) {
    }

    public void update(float dt) {
        if (over) return;
        if (w.config.timeLimit > 0) {
            timeLeft -= dt;
            if (timeLeft <= 0) {
                timeLeft = 0;
                onTimeUp();
            }
        }
    }

    protected void onTimeUp() {
        finishByScore("end_time");
    }

    protected void finishByScore(String reason) {
        over = true;
        endReason = reason;
        if (type.teams) {
            winnerTeam = teamScore[1] > teamScore[2] ? C.TEAM_RED : (teamScore[2] > teamScore[1] ? C.TEAM_BLUE : 0);
        } else {
            Array<Player> r = w.ranking();
            winnerId = r.size > 0 ? r.first().id : -1;
        }
    }

    public boolean canRespawn(Player p) {
        return !over;
    }

    public float respawnDelay(Player p) {
        return 3f;
    }

    protected Array<Vector2> spawnList(Player p) {
        if (!type.teams || p.team == C.TEAM_NONE) return w.map.spawnsFree;
        return p.team == C.TEAM_RED ? w.map.spawnsRed : w.map.spawnsBlue;
    }

    /** Spawn longe dos inimigos (com um pouco de sorte). */
    public Vector2 chooseSpawn(Player p) {
        Array<Vector2> list = spawnList(p);
        Vector2 best = list.first();
        float bestScore = -1;
        for (int i30_ = 0; i30_ < list.size; i30_++) {
            Vector2 s = list.get(i30_);
            float md = 4000;
            for (int i31_ = 0; i31_ < w.players.size; i31_++) {
                Player q = w.players.get(i31_);
                if (!q.alive || q == p || !w.enemies(p, q)) continue;
                float dx = q.x - s.x, dy = q.y - s.y;
                md = Math.min(md, (float) Math.sqrt(dx * dx + dy * dy));
            }
            float sc = md + w.rng.nextFloat() * 300;
            if (sc > bestScore) {
                bestScore = sc;
                best = s;
            }
        }
        return best;
    }

    public void giveLoadout(Player p) {
        p.giveDefaultLoadout(w.rng);
    }

    public boolean isFrozen(Player p) {
        return over;
    }

    public float speedMul(Player p) {
        return 1f;
    }

    public boolean infiniteAmmo(Player p) {
        return false;
    }

    public void buy(Player p, int code) {
    }

    public float damageMul(Player victim, Player attacker) {
        return 1f;
    }

    public void onKill(Player victim, Player attacker) {
    }

    /** Versao com o codigo da morte (arma/causa); por padrao chama a de dois argumentos. */
    public void onKill(Player victim, Player attacker, int code) {
        onKill(victim, attacker);
    }

    /** Alguem atirou (usado no pique-esconde). */
    public void onShotFired(Player p) {
    }

    /** Multiplicador do consumo de combustivel do jetpack. */
    public float fuelMul(Player p) {
        return 1f;
    }

    public boolean allowGrenades(Player p) {
        return true;
    }

    /** Multiplicador do dano causado por p. */
    public float damageDealtMul(Player p) {
        return 1f;
    }

    /** Armas caem no chao quando alguem morre? */
    public boolean dropsWeapons() {
        return true;
    }

    public boolean canPickup(Player p) {
        return true;
    }

    /** Pode trocar de arma / pegar armas do chao? (Gun Game nao deixa) */
    public boolean allowWeaponChange(Player p) {
        return true;
    }

    /** Objetivo do modo para um bot (posicao no mundo) ou false. */
    public boolean botGoal(Player bot, Vector2 out) {
        return false;
    }

    /** O bot deve segurar "usar" agora? */
    public boolean botUse(Player bot) {
        return false;
    }

    /** Linha principal do HUD (topo da tela). */
    public abstract String hud(Player local);

    /** Texto grande no centro (fases, avisos) ou null. */
    public String banner(Player local) {
        return null;
    }

    protected static String clock(float s) {
        int t = (int) Math.ceil(Math.max(0, s));
        return (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60);
    }

    protected String teamScoreLine() {
        return I18n.t("team_red") + " " + teamScore[1] + "  x  " + teamScore[2] + " " + I18n.t("team_blue");
    }

    // ---- rede
    public void write(DataOutput o) throws IOException {
        o.writeFloat(timeLeft);
        o.writeShort(teamScore[1]);
        o.writeShort(teamScore[2]);
        o.writeBoolean(over);
        o.writeByte(winnerTeam);
        o.writeShort(winnerId);
        o.writeUTF(endReason);
        writeExtra(o);
    }

    public void read(DataInput in) throws IOException {
        timeLeft = in.readFloat();
        teamScore[1] = in.readShort();
        teamScore[2] = in.readShort();
        over = in.readBoolean();
        winnerTeam = in.readByte();
        winnerId = in.readShort();
        endReason = in.readUTF();
        readExtra(in);
    }

    protected void writeExtra(DataOutput o) throws IOException {
    }

    protected void readExtra(DataInput in) throws IOException {
    }

    protected static void giveGun(Player p, int slot, Gun g) {
        p.guns[slot] = g;
    }
}
