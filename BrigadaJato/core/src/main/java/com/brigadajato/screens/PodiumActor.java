package com.brigadajato.screens;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.Assets;
import com.brigadajato.render.Draw;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.C;
import com.brigadajato.world.Player;

/** Podio do fim de partida: os 3 melhores dancando em cima dos blocos 1, 2 e 3. */
public class PodiumActor extends Actor {
    private final Draw d;
    private final Assets a;
    private final Array<Player> top = new Array<>();
    private final boolean teams;
    private final SoldierPainter painter = new SoldierPainter();
    private final SoldierPainter.Pose pose = new SoldierPainter.Pose();
    private float time;

    public PodiumActor(Draw d, Assets a, Array<Player> ranking, boolean teams) {
        this.d = d;
        this.a = a;
        this.teams = teams;
        for (int i = 0; i < ranking.size && top.size < 3; i++) if (!ranking.get(i).dummy) top.add(ranking.get(i));
        setSize(560, 230);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x0 = getX(), y0 = getY(), w = getWidth();
        // ordem visual: 2o, 1o, 3o
        int[] order = {1, 0, 2};
        float[] heights = {90, 62, 40};
        int[] colors = {0xffc107, 0xb0bec5, 0xcd7f32};
        float bw = w / 3.3f;
        for (int k = 0; k < 3; k++) {
            int rank = order[k];
            float cx = x0 + w / 2 + (k - 1) * (bw + 10);
            float h = heights[rank];
            float appear = MathUtils.clamp((time - 0.15f * (2 - rank)) * 3, 0, 1);
            float bh = h * appear;
            d.rgb(SoldierPainter.OUTLINE, parentAlpha);
            d.rect(cx - bw / 2 - 2, y0 - 2, bw + 4, bh + 4);
            d.rgb(colors[rank], parentAlpha);
            d.rect(cx - bw / 2, y0, bw, bh);
            d.rgb(0xffffff, 0.25f * parentAlpha);
            d.rect(cx - bw / 2, y0 + bh - 6, bw, 6);
            a.big.setColor(0.1f, 0.1f, 0.12f, parentAlpha * appear);
            a.big.draw(batch, "" + (rank + 1), cx, y0 + bh * 0.5f + 16, 0, Align.center, false);
            if (rank >= top.size || appear < 1) continue;
            Player p = top.get(rank);
            pose.x = cx;
            pose.y = y0 + bh + 2;
            pose.look = p.look;
            pose.team = teams ? p.team : 0;
            pose.weapon = null;
            pose.facing = k == 2 ? -1 : 1;
            pose.aim = pose.facing > 0 ? 0 : MathUtils.PI;
            pose.onGround = true;
            pose.crouch = false;
            pose.jetting = false;
            pose.time = time + rank * 0.37f;
            pose.anim = 0;
            pose.alpha = parentAlpha;
            pose.hurtFlash = 0;
            pose.meleeAnim = 0;
            pose.carryingFlag = false;
            pose.hasBomb = false;
            pose.shield = false;
            pose.vx = 0;
            pose.zombie = p.zombie;
            pose.action = rank == 0 ? 1 + (((int) (time / 2.5f)) % 5) : 2 + ((rank * 2 + (int) (time / 3f)) % 4);
            if (pose.action == 1) pose.action = 2;
            float s = rank == 0 ? 1.6f : 1.35f;
            batch.getTransformMatrix().idt().translate(cx, y0 + bh, 0).scale(s, s, 1).translate(-cx, -(y0 + bh), 0);
            batch.setTransformMatrix(batch.getTransformMatrix());
            painter.draw(d, pose);
            batch.setTransformMatrix(batch.getTransformMatrix().idt());
            a.small.setColor(1, rank == 0 ? 0.85f : 1, rank == 0 ? 0.3f : 1, parentAlpha);
            a.small.draw(batch, p.name, cx, y0 + bh + 92 * s * 0.75f + 18, 0, Align.center, false);
            if (rank == 0) { // coroa piscando
                float cy = y0 + bh + 72 * s + MathUtils.sin(time * 4) * 3;
                d.rgb(0xffd740, parentAlpha);
                for (int i = -1; i <= 1; i++) d.region(a.tri, cx + i * 9, cy, 9, 12, 0);
                d.rect(cx - 14, cy - 8, 28, 6);
            }
        }
        a.big.setColor(1, 1, 1, 1);
        a.small.setColor(1, 1, 1, 1);
        batch.setColor(1, 1, 1, 1);
    }
}
