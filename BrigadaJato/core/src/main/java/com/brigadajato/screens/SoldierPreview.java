package com.brigadajato.screens;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.render.Draw;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.Look;
import com.brigadajato.world.Theme;
import com.brigadajato.world.WeaponType;

/**
 * Mostra o soldado animado: varias poses, virar, zoom, fundos de mapa e vitrine com todas as armas.
 */
public class SoldierPreview extends Actor {
    public static final int POSE_IDLE = 0, POSE_WALK = 1, POSE_FLY = 2, POSE_SHOOT = 3, POSE_CROUCH = 4, POSE_CELEBRATE = 5, POSE_COUNT = 6;

    private final BrigadaJatoGame game;
    private final SoldierPainter painter = new SoldierPainter();
    private final SoldierPainter.Pose pose = new SoldierPainter.Pose();
    private final Matrix4 saved = new Matrix4(), tmp = new Matrix4();
    public Look look;
    public WeaponType weapon = WeaponType.RIFLE;
    public int poseType = POSE_IDLE;
    public int facing = 1;
    public float zoom = 1f;
    /** -1 = sem fundo, senao indice de Theme */
    public int background = -1;
    /** mostra uma fileira com todas as armas */
    public boolean showcase;
    public boolean autoPose;
    private float time;
    private float shotFlash;

    public SoldierPreview(BrigadaJatoGame game, Look look) {
        this.game = game;
        this.look = look;
        setSize(220, 260);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
        if (poseType == POSE_SHOOT) {
            shotFlash -= delta;
            if (shotFlash < -0.12f) shotFlash = 0.06f;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Draw d = game.draw;
        if (background >= 0) drawBackground(d, parentAlpha);
        if (showcase) {
            drawShowcase(batch, parentAlpha);
            return;
        }
        int p = poseType;
        if (autoPose) p = ((int) (time / 3.5f)) % 2 == 0 ? POSE_IDLE : POSE_CELEBRATE;
        float scale = getHeight() / (background >= 0 ? 88f : 70f) * zoom;
        float baseY = getY() + (background >= 0 ? getHeight() * 0.16f : 8);
        drawSoldier(batch, getX() + getWidth() / 2, baseY, scale, p, weapon, parentAlpha, time);
    }

    private void drawSoldier(Batch batch, float cx, float by, float scale, int p, WeaponType w, float alpha, float t) {
        Draw d = game.draw;
        saved.set(batch.getTransformMatrix());
        tmp.set(saved).translate(cx, by, 0).scale(scale, scale, 1);
        batch.setTransformMatrix(tmp);
        d.color(0, 0, 0, 0.3f);
        d.ellipse(0, 1, 16, 3.5f, 0);
        float fly = p == POSE_FLY ? 10 + MathUtils.sin(t * 3) * 3 : 0;
        pose.x = 0;
        pose.y = 2 + fly;
        pose.look = look;
        pose.team = 0;
        pose.facing = facing;
        pose.onGround = p != POSE_FLY;
        pose.crouch = p == POSE_CROUCH;
        pose.jetting = p == POSE_FLY;
        pose.action = p == POSE_CELEBRATE ? 1 : 0;
        float baseAim = facing > 0 ? 0 : MathUtils.PI;
        float wobble = p == POSE_SHOOT ? MathUtils.sin(t * 2) * 0.25f : MathUtils.sin(t * 1.3f) * 0.3f;
        pose.aim = baseAim + wobble * facing;
        pose.anim = p == POSE_WALK ? t * 6 : 0;
        pose.vx = p == POSE_WALK ? 200 * facing : 0;
        pose.weapon = w;
        pose.time = t;
        pose.alpha = alpha;
        pose.meleeAnim = 0;
        pose.hurtFlash = 0;
        if (p == POSE_FLY) {
            int fc = look.flameColor(t);
            d.additive(true);
            int jets = look.jetpack == 1 ? 2 : 1;
            for (int j = 0; j < jets; j++) {
                float off = jets == 2 ? (j == 0 ? -2.7f : 2.7f) : 0;
                for (int k = 0; k < 5; k++) {
                    float fy = pose.y + 8 - k * 4 - (t * 40 % 4);
                    d.rgb(fc, 0.8f - k * 0.14f);
                    d.soft(-facing * 10 + off + MathUtils.sin(t * 30 + k) * 0.8f, fy, 5 - k * 0.6f);
                }
            }
            d.additive(false);
        }
        painter.draw(d, pose);
        if (p == POSE_SHOOT && shotFlash > 0) {
            float len = w.gunLen + 10;
            float sy = pose.y + 44 * 0.66f;
            d.additive(true);
            d.rgb(0xffe082, 0.9f);
            d.soft(MathUtils.cos(pose.aim) * len, sy + MathUtils.sin(pose.aim) * len, 9);
            d.additive(false);
        }
        batch.setTransformMatrix(saved);
        batch.setColor(1, 1, 1, 1);
    }

    private void drawShowcase(Batch batch, float alpha) {
        WeaponType[] all = WeaponType.values();
        int cols = 6;
        int rows = (all.length + cols - 1) / cols;
        float cw = getWidth() / cols, ch = getHeight() / rows;
        float scale = Math.min(cw / 34f, ch / 70f);
        for (int i = 0; i < all.length; i++) {
            int c = i % cols, r = i / cols;
            float cx = getX() + cw * (c + 0.5f), by = getY() + getHeight() - ch * (r + 1) + ch * 0.12f;
            drawSoldier(batch, cx, by, scale, POSE_IDLE, all[i], alpha, time + i * 0.7f);
            game.assets.small.setColor(1, 1, 1, alpha);
            game.assets.small.draw(batch, game.settings.portuguese ? all[i].pt : all[i].en, cx - cw / 2, by - 4, cw, com.badlogic.gdx.utils.Align.center, false);
        }
    }

    private void drawBackground(Draw d, float alpha) {
        Theme t = Theme.values()[Math.min(background, Theme.values().length - 1)];
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int bands = 12;
        for (int i = 0; i < bands; i++) {
            float k = i / (float) (bands - 1);
            d.rgb(SoldierPainter.mix(t.skyBottom, t.skyTop, k), alpha);
            d.rect(x, y + h * 0.16f + (h * 0.84f) * i / bands, w, h * 0.84f / bands + 1);
        }
        d.rgb(t.far, alpha * 0.9f);
        d.batch.draw(d.a.tri, x + w * 0.05f, y + h * 0.16f, w * 0.55f, h * 0.45f);
        d.batch.draw(d.a.tri, x + w * 0.45f, y + h * 0.16f, w * 0.6f, h * 0.36f);
        d.rgb(t.mid, alpha);
        d.ellipse(x + w * 0.25f, y + h * 0.16f, w * 0.24f, h * 0.1f, 0);
        d.ellipse(x + w * 0.76f, y + h * 0.16f, w * 0.23f, h * 0.08f, 0);
        d.rgb(t.tile, alpha);
        d.rect(x, y, w, h * 0.16f);
        d.rgb(t.tileTop, alpha);
        d.rect(x, y + h * 0.13f, w, h * 0.035f);
        d.rgb(SoldierPainter.OUTLINE, alpha);
        d.rect(x, y + h * 0.16f - 1.5f, w, 3);
    }
}
