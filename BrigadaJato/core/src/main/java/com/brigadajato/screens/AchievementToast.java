package com.brigadajato.screens;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.brigadajato.Achievements;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.render.Draw;
import com.brigadajato.render.MedalArt;

/** Faixa "CONQUISTA DESBLOQUEADA" que desce do topo da tela (em qualquer tela). */
public final class AchievementToast {
    private AchievementToast() {
    }

    private static Achievements.Def showing;
    private static float t;
    private static final float DURATION = 3.6f;

    public static boolean active() {
        return showing != null;
    }

    /** Desenha com o batch ja aberto, em coordenadas de tela W x H. */
    public static void draw(BrigadaJatoGame game, float dt, float W, float H) {
        Achievements ach = game.achievements;
        if (showing == null) {
            if (ach.pending.size == 0) return;
            showing = ach.pending.removeIndex(0);
            t = 0;
            game.audio.ui("levelup");
            ach.save();
        }
        t += dt;
        if (t > DURATION) {
            showing = null;
            return;
        }
        float in = Interpolation.swingOut.apply(MathUtils.clamp(t / 0.45f, 0, 1));
        float out = MathUtils.clamp((DURATION - t) / 0.35f, 0, 1);
        float k = Math.min(in, out);
        Draw d = game.draw;
        float w = 520, h = 92, x = W / 2 - w / 2, y = H - 12 - h * k;
        d.rgb(0x000000, 0.5f * out);
        d.rect(x + 5, y - 5, w, h);
        d.rgb(0xffc107, out);
        d.rect(x - 3, y - 3, w + 6, h + 6);
        d.rgb(0x15120a, out);
        d.rect(x, y, w, h);
        // brilho passando
        float sh = (t * 0.9f) % 1.5f;
        if (sh < 1) {
            d.additive(true);
            d.color(1, 0.85f, 0.4f, 0.25f * out);
            d.rect(x + sh * (w - 60), y, 60, h);
            d.additive(false);
        }
        MedalArt.draw(d, game.assets, showing.sym, showing.color, x + 52, y + h / 2 + 4, 76, out, false, t);
        BitmapFont sm = game.assets.small, nm = game.assets.normal;
        sm.setColor(1, 0.8f, 0.25f, out);
        sm.draw(d.batch, I18n.t("ach_unlocked"), x + 100, y + h - 14);
        nm.setColor(1, 1, 1, out);
        nm.draw(d.batch, showing.name(), x + 100, y + h - 40);
        sm.setColor(0.75f, 0.8f, 0.85f, out);
        float ss = sm.getScaleX();
        sm.getData().setScale(ss * 0.8f);
        sm.draw(d.batch, showing.desc() + "   +" + showing.xp + " XP", x + 100, y + 22);
        sm.getData().setScale(ss);
        sm.setColor(1, 1, 1, 1);
        nm.setColor(1, 1, 1, 1);
        d.batch.setColor(1, 1, 1, 1);
    }
}
