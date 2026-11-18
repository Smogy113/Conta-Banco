package com.brigadajato.screens;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.render.Draw;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.GameMap;

/**
 * Carrossel de mapas: previa AO VIVO no meio (bots jogando no mapa de verdade), setas e arrastar
 * para os lados, e uma fileira de miniaturas embaixo que desliza ate o mapa escolhido.
 */
public class MapCarousel extends Actor implements Disposable {
    public interface Listener {
        void select(String mapId);
    }

    private final BrigadaJatoGame game;
    private final MapPreview preview;
    private final String[] ids;
    private final ObjectMap<String, Texture> thumbs = new ObjectMap<>();
    private final ObjectMap<String, String> names = new ObjectMap<>();
    private final Listener listener;
    private int index;
    private float strip, time, nameIn, dragX, dragStart;
    private boolean dragging;
    private int dir = 1;
    private static final float STRIP_H = 46;

    public MapCarousel(BrigadaJatoGame game, String[] ids, String current, Listener listener) {
        this.game = game;
        this.ids = ids;
        this.listener = listener;
        preview = new MapPreview(game, 512, 288);
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(current)) index = i;
        strip = index;
        for (String id : ids) {
            GameMap m = BrigadaJatoGame.loadMap(id);
            thumbs.put(id, MapCard.buildThumb(m));
            names.put(id, I18n.pt ? m.name : m.nameEn);
        }
        preview.setMap(ids[index]);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                dragging = true;
                dragStart = x;
                dragX = 0;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                dragX = x - dragStart;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                dragging = false;
                if (Math.abs(dragX) > 50) {
                    move(dragX < 0 ? 1 : -1);
                } else if (y < STRIP_H) {
                    // toque na fileira de miniaturas
                    float slotW = 64, cx = getWidth() / 2;
                    int k = Math.round((x - cx) / slotW + strip);
                    if (k >= 0 && k < ids.length && k != index) jump(k);
                } else if (x < getWidth() * 0.18f) move(-1);
                else if (x > getWidth() * 0.82f) move(1);
                dragX = 0;
            }
        });
    }

    public void move(int d) {
        jump((index + d + ids.length) % ids.length);
        dir = d;
    }

    private void jump(int k) {
        dir = k > index ? 1 : -1;
        index = k;
        nameIn = 0;
        preview.setMap(ids[index]);
        game.audio.ui("ui_click");
        if (listener != null) listener.select(ids[index]);
    }

    /** Muda o mapa por fora (sem avisar o ouvinte). */
    public void show(String id) {
        for (int i = 0; i < ids.length; i++)
            if (ids[i].equals(id) && i != index) {
                index = i;
                nameIn = 0;
                preview.setMap(id);
            }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
        nameIn = Math.min(1, nameIn + delta * 2.5f);
        strip += (index - strip) * Math.min(1, delta * 8);
        preview.update(delta);
    }

    /** Chamar antes do stage desenhar (fora do batch). */
    public void renderPreview() {
        if (getStage() != null) preview.render();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Draw d = game.draw;
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float px = x, py = y + STRIP_H + 8, pw = w, ph = h - STRIP_H - 8;
        float shift = dragging ? MathUtils.clamp(dragX, -60, 60) * 0.4f : 0;
        // moldura
        d.rgb(0x000000, 0.5f * parentAlpha);
        d.rect(px + 5, py - 6, pw, ph);
        d.rgb(SoldierPainter.OUTLINE, parentAlpha);
        d.rect(px - 4, py - 4, pw + 8, ph + 8);
        TextureRegion tex = preview.texture();
        if (tex != null) {
            batch.setColor(1, 1, 1, parentAlpha);
            batch.draw(tex, px + shift, py, pw, ph);
        } else {
            Texture t = thumbs.get(ids[index]);
            batch.setColor(1, 1, 1, parentAlpha);
            batch.draw(t, px, py, pw, ph);
        }
        // transicao: chiado de TV e clarao
        float sc = preview.sinceChange;
        if (sc < 0.35f) {
            float k = 1 - sc / 0.35f;
            for (int i = 0; i < 60; i++) {
                float rx = px + MathUtils.random(pw - 20), ry = py + MathUtils.random(ph - 4);
                float g = MathUtils.random(0.3f, 1f);
                d.color(g, g, g, k * 0.8f);
                d.rect(rx, ry, MathUtils.random(10f, 60f), MathUtils.random(2f, 5f));
            }
            d.color(1, 1, 1, k * k * 0.6f);
            d.rect(px, py, pw, ph);
        }
        // linhas de tela e cantos escurecidos
        d.color(0, 0, 0, 0.12f * parentAlpha);
        for (float ly = py; ly < py + ph; ly += 4) d.rect(px, ly, pw, 1.5f);
        d.color(0, 0, 0, 0.45f * parentAlpha);
        d.soft(px, py, 110);
        d.soft(px + pw, py, 110);
        d.soft(px, py + ph, 110);
        d.soft(px + pw, py + ph, 110);
        // selo AO VIVO
        boolean blink = ((int) (time * 2)) % 2 == 0;
        d.rgb(0x000000, 0.6f * parentAlpha);
        d.rect(px + 10, py + ph - 34, 96, 24);
        d.rgb(0xff1744, (blink ? 1 : 0.35f) * parentAlpha);
        d.circle(px + 24, py + ph - 22, 6);
        BitmapFont sm = game.assets.small;
        float ss = sm.getScaleX();
        sm.getData().setScale(ss * 0.75f);
        sm.setColor(1, 1, 1, parentAlpha);
        sm.draw(batch, I18n.pt ? "AO VIVO" : "LIVE", px + 36, py + ph - 15);
        sm.getData().setScale(ss);
        // nome do mapa entrando
        float e = Interpolation.pow3Out.apply(nameIn);
        String name = names.get(ids[index]);
        BitmapFont big = game.assets.big;
        float nx = px + 16 - (1 - e) * 120 * dir;
        d.rgb(0x000000, 0.6f * e * parentAlpha);
        d.rect(px, py + 8, pw * 0.75f * e, 46);
        d.rgb(0xffc61a, e * parentAlpha);
        d.rect(px, py + 8, 6, 46);
        big.setColor(1, 1, 1, e * parentAlpha);
        float bs = big.getScaleX();
        big.getData().setScale(bs * 0.8f);
        big.draw(batch, name, nx, py + 44);
        big.getData().setScale(bs);
        big.setColor(1, 1, 1, 1);
        sm.setColor(0.8f, 0.8f, 0.8f, parentAlpha);
        sm.getData().setScale(ss * 0.7f);
        sm.draw(batch, (index + 1) + " / " + ids.length, px + pw - 14, py + 26, 0, Align.right, false);
        sm.getData().setScale(ss);
        sm.setColor(1, 1, 1, 1);
        // setas
        for (int s = -1; s <= 1; s += 2) {
            float ax = s < 0 ? px + 26 : px + pw - 26, ay = py + ph / 2;
            float bob = MathUtils.sin(time * 4) * 3 * s;
            d.rgb(0x000000, 0.55f * parentAlpha);
            d.circle(ax + bob, ay, 22);
            d.rgb(0xffffff, parentAlpha);
            d.region(game.assets.tri, ax + bob, ay, 18, 18, s < 0 ? 90 : -90);
        }
        // fileira de miniaturas
        float slotW = 64, cx = x + w / 2;
        for (int i = 0; i < ids.length; i++) {
            float off = (i - strip) * slotW;
            if (Math.abs(off) > w / 2 - 20) continue;
            float edge = 1 - Math.min(1, Math.abs(off) / (w / 2));
            boolean sel = i == index;
            float tw = sel ? 60 : 50, th = sel ? 38 : 30;
            float tx = cx + off - tw / 2, ty = y + (STRIP_H - th) / 2;
            if (sel) {
                d.rgb(0xffc61a, parentAlpha);
                d.rect(tx - 4, ty - 4, tw + 8, th + 8);
            }
            d.rgb(SoldierPainter.OUTLINE, parentAlpha);
            d.rect(tx - 2, ty - 2, tw + 4, th + 4);
            batch.setColor(1, 1, 1, (0.35f + 0.65f * edge) * parentAlpha);
            batch.draw(thumbs.get(ids[i]), tx, ty, tw, th);
        }
        batch.setColor(1, 1, 1, 1);
    }

    @Override
    public void dispose() {
        preview.dispose();
        for (Texture t : thumbs.values()) t.dispose();
    }
}
