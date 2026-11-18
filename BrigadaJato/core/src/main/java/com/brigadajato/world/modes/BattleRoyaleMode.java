package com.brigadajato.world.modes;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Gun;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Pickup;
import com.brigadajato.world.Player;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Battle Royale: uma vida so, comeca com pistola, armas espalhadas pelo mapa e uma zona que
 * fecha em etapas. Fora da zona voce perde vida. O ultimo de pe vence.
 */
public class BattleRoyaleMode extends GameMode {
    public static final float[] STAGE_WAIT = {30, 25, 20, 15, 12, 10};
    public static final float[] STAGE_SHRINK = {18, 15, 12, 10, 8, 8};
    public static final float[] STAGE_DPS = {4, 6, 9, 13, 18, 25};

    public float zoneX, zoneY, zoneR;
    /** proxima zona (alvo) */
    public float nextX, nextY, nextR;
    private float fromX, fromY, fromR;
    public int stage;
    public boolean shrinking;
    public float timer = STAGE_WAIT[0];
    public int aliveCount;
    private float dmgTick;
    private final boolean[] spawnedOnce = new boolean[512];

    public BattleRoyaleMode(World w) {
        super(w, ModeType.BATTLE_ROYALE);
    }

    @Override
    public void init() {
        float ww = w.map.worldW(), wh = w.map.worldH();
        zoneX = ww / 2;
        zoneY = wh / 2;
        zoneR = (float) Math.sqrt(ww * ww + wh * wh) * 0.55f;
        pickNext();
        // armas extras espalhadas pelo chao
        int extra = 8 + w.players.size;
        for (int k = 0; k < extra * 6 && extra > 0; k++) {
            int tx = 1 + w.rng.nextInt(Math.max(1, w.map.w - 2)), ty = 1 + w.rng.nextInt(Math.max(1, w.map.h - 3));
            if (w.map.solid(tx, ty) || w.map.solid(tx, ty + 1) || !w.map.solid(tx, ty - 1)) continue;
            if (w.map.get(tx, ty - 1) == GameMap.SPIKES) continue;
            Pickup pk = w.newPickup(w.rng.nextFloat() < 0.75f ? Pickup.Type.WEAPON : Pickup.Type.HEALTH, tx * C.TILE + C.TILE / 2f, ty * C.TILE);
            if (pk.type == Pickup.Type.WEAPON) pk.weapon = WeaponType.random(w.rng);
            pk.respawnTime = 9999;
            extra--;
        }
    }

    private void pickNext() {
        fromX = zoneX;
        fromY = zoneY;
        fromR = zoneR;
        nextR = Math.max(140, zoneR * (stage == 0 ? 0.55f : 0.6f));
        if (stage >= STAGE_WAIT.length - 1) nextR = 0;
        // novo centro dentro da zona atual
        float maxOff = Math.max(0, zoneR - nextR) * 0.7f;
        for (int k = 0; k < 20; k++) {
            float a = w.rng.nextFloat() * MathUtils.PI2, d = w.rng.nextFloat() * maxOff;
            float nx = zoneX + MathUtils.cos(a) * d, ny = zoneY + MathUtils.sin(a) * d;
            if (nx > 100 && nx < w.map.worldW() - 100 && ny > 100 && ny < w.map.worldH() - 100) {
                nextX = nx;
                nextY = ny;
                return;
            }
        }
        nextX = zoneX;
        nextY = zoneY;
    }

    @Override
    public boolean canRespawn(Player p) {
        if (over) return false;
        return p.id < spawnedOnce.length && !spawnedOnce[p.id];
    }

    @Override
    public void onJoin(Player p) {
        if (p.id < spawnedOnce.length) spawnedOnce[p.id] = true; // entra como espectador
    }

    @Override
    public void giveLoadout(Player p) {
        if (p.id < spawnedOnce.length) spawnedOnce[p.id] = true;
        p.guns[0] = new Gun(WeaponType.PISTOL);
        p.guns[1] = null;
        p.slot = 0;
        p.frags = 1;
        p.mines = 0;
    }

    @Override
    public boolean dropsWeapons() {
        return true;
    }

    public boolean outside(float x, float y) {
        float dx = x - zoneX, dy = y - zoneY;
        return dx * dx + dy * dy > zoneR * zoneR;
    }

    @Override
    public void update(float dt) {
        if (over) return;
        timer -= dt;
        int st = Math.min(stage, STAGE_WAIT.length - 1);
        if (!shrinking && timer <= 0) {
            shrinking = true;
            timer = STAGE_SHRINK[st];
            WorldEvent e = w.emit(WorldEvent.Type.MESSAGE);
            e.text = "br_closing";
            e.a = -1;
            e.b = -1;
            e.c = 1;
        } else if (shrinking) {
            float k = 1 - Math.max(0, timer) / STAGE_SHRINK[st];
            zoneX = fromX + (nextX - fromX) * k;
            zoneY = fromY + (nextY - fromY) * k;
            zoneR = fromR + (nextR - fromR) * k;
            if (timer <= 0) {
                shrinking = false;
                stage++;
                timer = STAGE_WAIT[Math.min(stage, STAGE_WAIT.length - 1)];
                pickNext();
            }
        }
        dmgTick += dt;
        boolean hit = dmgTick >= 0.5f;
        if (hit) dmgTick = 0;
        aliveCount = 0;
        Player last = null;
        int waiting = 0;
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            if (p.alive) {
                aliveCount++;
                last = p;
                if (hit && outside(p.x, p.centerY())) w.damage(p, STAGE_DPS[st] * 0.5f, null, C.KILL_ZONE, false, 0, 0);
            } else if (canRespawn(p)) waiting++;
        }
        if (waiting == 0 && aliveCount <= 1 && w.players.size > 1 && w.time > 2) {
            over = true;
            winnerId = last != null ? last.id : -1;
            endReason = "br_win";
            if (last != null) last.score += 5;
        }
    }

    @Override
    public void onKill(Player victim, Player attacker, int code) {
        if (attacker != null && attacker != victim) attacker.score += 1;
        // posicao final conta pontos: quanto mais tarde morre, mais pontos
        victim.score += Math.max(0, w.players.size - aliveCount - 1) / 2;
    }

    @Override
    public boolean botGoal(Player bot, Vector2 out) {
        float dx = bot.x - zoneX, dy = bot.centerY() - zoneY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float target = shrinking ? nextR : zoneR;
        if (d > target * 0.75f) {
            out.set(shrinking ? nextX : zoneX, shrinking ? nextY : zoneY);
            return true;
        }
        return false;
    }

    @Override
    public String hud(Player local) {
        return I18n.t("br_alive") + ": " + aliveCount + "    " + (shrinking ? I18n.t("br_shrinking") : I18n.f("br_next", (int) Math.ceil(Math.max(0, timer))));
    }

    @Override
    public String banner(Player local) {
        if (local != null && local.alive && outside(local.x, local.centerY())) return I18n.t("br_outside");
        return null;
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        o.writeFloat(zoneX);
        o.writeFloat(zoneY);
        o.writeFloat(zoneR);
        o.writeFloat(nextX);
        o.writeFloat(nextY);
        o.writeFloat(nextR);
        o.writeByte(stage);
        o.writeBoolean(shrinking);
        o.writeFloat(timer);
        o.writeShort(aliveCount);
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        zoneX = in.readFloat();
        zoneY = in.readFloat();
        zoneR = in.readFloat();
        nextX = in.readFloat();
        nextY = in.readFloat();
        nextR = in.readFloat();
        stage = in.readByte();
        shrinking = in.readBoolean();
        timer = in.readFloat();
        aliveCount = in.readShort();
    }
}
