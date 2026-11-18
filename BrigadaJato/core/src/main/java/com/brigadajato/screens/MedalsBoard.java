package com.brigadajato.screens;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.Assets;
import com.brigadajato.Audio;
import com.brigadajato.I18n;
import com.brigadajato.render.Draw;
import com.brigadajato.render.MedalArt;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.Medals;
import com.brigadajato.world.Player;

/** Fim de partida: cartao do MVP com o soldado comemorando e as medalhas virando uma a uma. */
public class MedalsBoard extends Actor {
    private final Draw d;
    private final Assets a;
    private final Audio audio;
    private final Array<Medals.Award> awards;
    private final Player mvp;
    private final int localId;
    private final boolean teams;
    private final SoldierPainter painter = new SoldierPainter();
    private final SoldierPainter.Pose pose = new SoldierPainter.Pose();
    private float time;
    private int revealed;

    public MedalsBoard(Draw d, Assets a, Audio audio, Array<Medals.Award> awards, int localId, boolean teams) {
        this.d = d;
        this.a = a;
        this.audio = audio;
        this.localId = localId;
        this.teams = teams;
        this.awards = new Array<>();
        Player m = null;
        for (Medals.Award aw : awards) {
            if (aw.medal == Medals.MVP) m = aw.p;
            else if (this.awards.size < 9) this.awards.add(aw);
        }
        mvp = m;
        setSize(1120, 440);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
        int should = (int) ((time - 0.7f) / 0.22f) + 1;
        while (revealed < Math.min(should, awards.size)) {
            revealed++;
            audio.ui(awards.get(revealed - 1).p.id == localId ? "streak" : "pickup");
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX(), y = getY(), h = getHeight();
        // ---- MVP
        float mw = 340;
        float appear = Interpolation.swingOut.apply(MathUtils.clamp(time / 0.5f, 0, 1));
        float mcx = x + mw / 2, mcy = y + h / 2;
        d.rgb(0x000000, 0.5f * parentAlpha);
        d.rect(x + 6, y - 6, mw, h);
        d.rgb(0xffc107, parentAlpha);
        d.rect(x - 3, y - 3, mw + 6, h + 6);
        d.rgb(0x1a1508, parentAlpha);
        d.rect(x, y, mw, h);
        // raios dourados atras do soldado
        d.additive(true);
        for (int i = 0; i < 10; i++) {
            float ang = time * 14 + i * 36;
            float c = MathUtils.cosDeg(ang), s = MathUtils.sinDeg(ang);
            d.color(1f, 0.78f, 0.2f, 0.09f);
            d.region(a.tri, mcx + c * 140, mcy + 40 + s * 140, 60, 280, ang + 90);
        }
        d.color(1f, 0.8f, 0.3f, 0.35f);
        d.soft(mcx, mcy + 30, 200);
        d.additive(false);
        // confete
        for (int i = 0; i < 24; i++) {
            float hu = (i * 0.618f) % 1f;
            float u = (time * (0.25f + hu * 0.2f) + hu) % 1f;
            float cx = x + 10 + hu * (mw - 20) + MathUtils.sin(time * 2 + i) * 8;
            float cy = y + h - u * h;
            int[] pal = {0xffc107, 0xe53935, 0x42a5f5, 0x66bb6a, 0xffffff};
            d.rgb(pal[i % pal.length], 0.85f * parentAlpha);
            d.rect(cx, cy, 7, 4, 3.5f, 2, time * 220 + i * 40);
        }
        BitmapFont big = a.big, sm = a.small, nm = a.normal;
        big.setColor(1, 0.82f, 0.25f, parentAlpha);
        float bks = big.getScaleX();
        big.getData().setScale(bks * 0.78f);
        big.draw(batch, I18n.t("mvp"), mcx, y + h - 24, 0, Align.center, false);
        big.getData().setScale(bks);
        if (mvp != null) {
            // soldado comemorando
            float sc = 2.3f * appear;
            float fy = y + 150;
            pose.x = mcx;
            pose.y = fy;
            pose.look = mvp.look;
            pose.team = teams ? mvp.team : 0;
            pose.weapon = null;
            pose.facing = 1;
            pose.aim = 0;
            pose.onGround = true;
            pose.crouch = pose.jetting = pose.carryingFlag = pose.hasBomb = pose.shield = false;
            pose.time = time;
            pose.anim = 0;
            pose.alpha = parentAlpha;
            pose.hurtFlash = pose.meleeAnim = 0;
            pose.vx = 0;
            pose.zombie = mvp.zombie;
            pose.action = ((int) (time / 3f)) % 2 == 0 ? 1 : 2 + ((int) (time / 6f)) % 4;
            d.rgb(0x000000, 0.4f * parentAlpha);
            d.ellipse(mcx, fy, 60, 10, 0);
            batch.getTransformMatrix().idt().translate(mcx, fy, 0).scale(sc, sc, 1).translate(-mcx, -fy, 0);
            batch.setTransformMatrix(batch.getTransformMatrix());
            painter.draw(d, pose);
            batch.setTransformMatrix(batch.getTransformMatrix().idt());
            nm.setColor(mvp.id == localId ? 1 : 1, mvp.id == localId ? 0.85f : 1, mvp.id == localId ? 0.3f : 1, parentAlpha);
            nm.draw(batch, mvp.name, mcx, fy - 18, 0, Align.center, false);
            String l1 = I18n.t("kills") + " " + mvp.kills + "   " + I18n.t("deaths") + " " + mvp.deaths;
            String l2 = I18n.t("damage") + " " + Math.round(mvp.damageDealt) + "   " + I18n.t("accuracy") + " " + Math.round(Medals.accuracy(mvp) * 100) + "%";
            sm.setColor(0.9f, 0.9f, 0.9f, parentAlpha);
            sm.draw(batch, l1, mcx, fy - 58, 0, Align.center, false);
            sm.draw(batch, l2, mcx, fy - 86, 0, Align.center, false);
        }
        // ---- medalhas
        big.setColor(1, 1, 1, parentAlpha);
        float gx = x + mw + 30, gw = getWidth() - mw - 30;
        big.draw(batch, I18n.t("medals"), gx, y + h - 22);
        int cols = 3;
        float cw = (gw - (cols - 1) * 12) / cols, ch = 104;
        for (int i = 0; i < awards.size; i++) {
            Medals.Award aw = awards.get(i);
            int c = i % cols, r = i / cols;
            float cx = gx + c * (cw + 12), cy = y + h - 76 - (r + 1) * (ch + 10);
            float k = i < revealed ? MathUtils.clamp((time - 0.7f - i * 0.22f) / 0.3f, 0, 1) : 0;
            float flip = Interpolation.swingOut.apply(k);
            float vw = cw * Math.max(0.02f, Math.abs(MathUtils.cos((1 - k) * MathUtils.PI / 2))) * Math.min(1, flip);
            float ox = cx + (cw - vw) / 2;
            boolean mine = aw.p.id == localId;
            int col = Medals.COLORS[aw.medal];
            if (k <= 0) {
                d.rgb(0x263238, 0.8f * parentAlpha);
                d.rect(cx, cy, cw, ch);
                continue;
            }
            d.rgb(mine ? 0xffd54f : SoldierPainter.OUTLINE, parentAlpha);
            d.rect(ox - 3, cy - 3, vw + 6, ch + 6);
            d.rgb(SoldierPainter.mix(col, 0x0d1117, 0.78f), parentAlpha);
            d.rect(ox, cy, vw, ch);
            d.rgb(col, 0.35f * parentAlpha);
            d.rect(ox, cy + ch - 6, vw, 6);
            if (k < 0.95f) continue;
            MedalArt.draw(d, a, aw.medal, col, cx + 46, cy + ch / 2 + 4, 74, parentAlpha, false, time + i);
            nm.setColor(1, 1, 1, parentAlpha);
            float ns = nm.getScaleX();
            nm.getData().setScale(ns * 0.8f);
            nm.draw(batch, Medals.name(aw.medal), cx + 92, cy + ch - 18);
            nm.getData().setScale(ns);
            sm.setColor(mine ? 1 : 0.8f, mine ? 0.85f : 0.85f, mine ? 0.3f : 0.85f, parentAlpha);
            float ss = sm.getScaleX();
            sm.getData().setScale(ss * 0.8f);
            sm.draw(batch, aw.p.name, cx + 92, cy + ch - 50);
            sm.setColor(0.7f, 0.75f, 0.8f, parentAlpha);
            sm.draw(batch, Medals.detail(aw.medal, aw.value), cx + 92, cy + 26);
            sm.getData().setScale(ss);
        }
        if (awards.size == 0) {
            sm.setColor(0.7f, 0.7f, 0.7f, parentAlpha);
            sm.draw(batch, I18n.pt ? "Nenhuma medalha nesta partida" : "No medals this match", gx, y + h / 2);
        }
        big.setColor(1, 1, 1, 1);
        nm.setColor(1, 1, 1, 1);
        sm.setColor(1, 1, 1, 1);
        batch.setColor(1, 1, 1, 1);
    }
}
