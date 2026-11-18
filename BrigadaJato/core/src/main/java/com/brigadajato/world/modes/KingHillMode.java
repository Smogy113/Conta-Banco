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

/** Rei do Morro: o time que ficar sozinho na zona marca pontos. A zona muda de lugar. */
public class KingHillMode extends GameMode {
    public static final float RADIUS = 110;
    public static final float MOVE_EVERY = 55f;
    public final Array<Vector2> hills = new Array<>();
    public int active;
    /** time dono da zona agora (0 = ninguem, -1 = disputada) */
    public int owner;
    public float moveTimer = MOVE_EVERY;
    private float tick;
    private final float[] partial = new float[3];

    public KingHillMode(World w) {
        super(w, ModeType.KING_HILL);
        if (w.map.hills.size > 0) hills.addAll(w.map.hills);
        else {
            // mapas antigos: usa os pontos de arma como morros
            for (int i = 0; i < w.map.weaponSpawns.size; i++) hills.add(new Vector2(w.map.weaponSpawns.get(i)));
            if (hills.size == 0) hills.add(new Vector2(w.map.worldW() / 2, w.map.worldH() / 2));
        }
    }

    @Override
    public void init() {
        active = w.rng.nextInt(hills.size);
    }

    public Vector2 hill() {
        return hills.get(Math.min(active, hills.size - 1));
    }

    public boolean inside(Player p) {
        Vector2 h = hill();
        float dx = p.x - h.x, dy = p.centerY() - (h.y + 30);
        return dx * dx + dy * dy < RADIUS * RADIUS;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        if (over) return;
        int red = 0, blue = 0;
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            if (!p.alive || !inside(p)) continue;
            if (p.team == C.TEAM_RED) red++;
            else if (p.team == C.TEAM_BLUE) blue++;
        }
        owner = red > 0 && blue > 0 ? -1 : (red > 0 ? C.TEAM_RED : (blue > 0 ? C.TEAM_BLUE : 0));
        if (owner > 0) {
            partial[owner] += dt;
            while (partial[owner] >= 1f) {
                partial[owner] -= 1f;
                teamScore[owner]++;
                for (int i = 0; i < w.players.size; i++) {
                    Player p = w.players.get(i);
                    if (p.alive && p.team == owner && inside(p)) p.score++;
                }
            }
            if (w.config.scoreLimit > 0 && teamScore[owner] >= w.config.scoreLimit) {
                finishByScore("end_score");
                return;
            }
        }
        moveTimer -= dt;
        if (moveTimer <= 0 && hills.size > 1) {
            moveTimer = MOVE_EVERY;
            int n = active;
            while (n == active) n = w.rng.nextInt(hills.size);
            active = n;
            WorldEvent e = w.emit(WorldEvent.Type.MESSAGE);
            e.text = "koth_moved";
            e.a = -1;
            e.b = -1;
            e.c = 2;
        }
        tick += dt;
    }

    @Override
    public boolean botGoal(Player bot, Vector2 out) {
        Vector2 h = hill();
        out.set(h.x + ((bot.id * 37) % 5 - 2) * 18, h.y);
        return true;
    }

    @Override
    public String hud(Player local) {
        String s = teamScoreLine();
        if (w.config.timeLimit > 0) s = clock(timeLeft) + "   " + s;
        return s;
    }

    @Override
    public String banner(Player local) {
        if (owner == -1) return I18n.t("koth_contested");
        return null;
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        o.writeByte(active);
        o.writeByte(owner);
        o.writeFloat(moveTimer);
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        active = in.readByte();
        owner = in.readByte();
        moveTimer = in.readFloat();
    }
}
