package com.brigadajato.screens;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.Achievements;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.render.Draw;
import com.brigadajato.render.MedalArt;
import com.brigadajato.render.SoldierPainter;

/** Lista de conquistas com medalha, descricao, barra de progresso e recompensa. */
public class AchievementsScreen extends BaseScreen {
    public AchievementsScreen(BrigadaJatoGame game) {
        super(game);
        Achievements ach = game.achievements;
        Table header = new Table();
        header.add(button(I18n.t("back"), "gray-small", this::onBack)).width(170).height(52).left();
        header.add(label(I18n.t("achievements").toUpperCase(), "big")).expandX().center();
        header.add(label(ach.unlockedCount() + " / " + Achievements.DEFS.length, "yellow")).width(170).right();
        root.top().pad(18, 28, 14, 28);
        root.add(header).growX().padBottom(12).row();
        Table grid = new Table();
        int i = 0;
        for (Achievements.Def d : Achievements.DEFS) {
            grid.add(new Card(d, i)).size(296, 118).pad(6);
            if (++i % 4 == 0) grid.row();
        }
        ScrollPane sp = new ScrollPane(grid);
        sp.setScrollingDisabled(true, false);
        sp.setFadeScrollBars(false);
        root.add(sp).grow();
    }

    @Override
    protected void onBack() {
        go(new MenuScreen(game));
    }

    private class Card extends Actor {
        final Achievements.Def def;
        final int idx;
        float time;

        Card(Achievements.Def def, int idx) {
            this.def = def;
            this.idx = idx;
            time = -idx * 0.03f;
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float k = Interpolation.pow2Out.apply(MathUtils.clamp(time / 0.35f, 0, 1));
            if (k <= 0) return;
            Draw d = game.draw;
            Achievements ach = game.achievements;
            boolean on = ach.unlocked(def);
            int prog = Math.min(def.target, ach.progress(def));
            float a = parentAlpha * k;
            float x = getX(), y = getY() - (1 - k) * 30, w = getWidth(), h = getHeight();
            d.rgb(0x000000, 0.45f * a);
            d.rect(x + 4, y - 4, w, h);
            d.rgb(on ? def.color : SoldierPainter.OUTLINE, a);
            d.rect(x - 2, y - 2, w + 4, h + 4);
            d.rgb(on ? SoldierPainter.mix(def.color, 0x0d1117, 0.8f) : 0x161b22, 0.96f * a);
            d.rect(x, y, w, h);
            MedalArt.draw(d, game.assets, def.sym, def.color, x + 48, y + h / 2 + 6, 76, a, !on, time + idx);
            BitmapFont nm = game.assets.normal, sm = game.assets.small;
            float ns = nm.getScaleX(), ss = sm.getScaleX();
            nm.getData().setScale(ns * 0.72f);
            nm.setColor(on ? 1 : 0.75f, on ? 1 : 0.78f, on ? 1 : 0.8f, a);
            nm.draw(batch, def.name(), x + 94, y + h - 14);
            nm.getData().setScale(ns);
            sm.getData().setScale(ss * 0.68f);
            sm.setColor(0.7f, 0.75f, 0.8f, a);
            sm.draw(batch, def.desc(), x + 94, y + h - 44, w - 102, Align.left, true);
            // barra
            float bx = x + 94, by = y + 14, bw = w - 110;
            d.rgb(0x000000, 0.6f * a);
            d.rect(bx, by, bw, 10);
            d.rgb(on ? 0x66bb6a : def.color, a);
            d.rect(bx, by, bw * prog / (float) def.target, 10);
            sm.setColor(1, on ? 0.85f : 1, on ? 0.3f : 1, a);
            sm.draw(batch, on ? "+" + def.xp + " XP" : prog + "/" + def.target, bx + bw, by + 30, 0, Align.right, false);
            sm.getData().setScale(ss);
            sm.setColor(1, 1, 1, 1);
            nm.setColor(1, 1, 1, 1);
            batch.setColor(1, 1, 1, 1);
        }
    }
}
