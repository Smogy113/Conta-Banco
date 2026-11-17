package com.brigadajato.screens;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.Look;
import com.brigadajato.world.WeaponType;

/** Mostra o soldado animado numa tela de menu. */
public class SoldierPreview extends Actor {
    private final BrigadaJatoGame game;
    private final SoldierPainter painter = new SoldierPainter();
    private final SoldierPainter.Pose pose = new SoldierPainter.Pose();
    private final Matrix4 saved = new Matrix4(), tmp = new Matrix4();
    public Look look;
    public WeaponType weapon = WeaponType.RIFLE;
    private float time;

    public SoldierPreview(BrigadaJatoGame game, Look look) {
        this.game = game;
        this.look = look;
        setSize(220, 260);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float scale = getHeight() / 62f;
        saved.set(batch.getTransformMatrix());
        tmp.set(saved).translate(getX() + getWidth() / 2, getY() + 8, 0).scale(scale, scale, 1);
        batch.setTransformMatrix(tmp);
        // sombra
        game.draw.color(0, 0, 0, 0.3f);
        game.draw.ellipse(0, 1, 16, 3.5f, 0);
        pose.x = 0;
        pose.y = 2;
        pose.look = look;
        pose.team = 0;
        pose.facing = 1;
        pose.aim = (float) Math.sin(time * 1.3f) * 0.35f;
        pose.anim = time * 2.5f;
        pose.onGround = true;
        pose.weapon = weapon;
        pose.time = time;
        pose.alpha = parentAlpha;
        painter.draw(game.draw, pose);
        batch.setTransformMatrix(saved);
        batch.setColor(1, 1, 1, 1);
    }
}
