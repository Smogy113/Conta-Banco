package com.brigadajato.render;

import com.badlogic.gdx.math.MathUtils;

/** Sistema de particulas simples com pool fixo. */
public class Particles {
    public static final int CIRCLE = 0, SOFT = 1, RECT = 2, LINE = 3, RING = 4;

    public static class P {
        public float x, y, vx, vy, life, max, size, size2, r, g, b, a, a2, grav, drag, rot, vrot;
        public int shape;
        public boolean additive;
        public boolean active;
    }

    private final P[] pool;
    private int cursor;
    public float quality = 1f;

    public Particles(int max) {
        pool = new P[max];
        for (int i = 0; i < max; i++) pool[i] = new P();
    }

    public P spawn(int shape, float x, float y, float vx, float vy, float life, float size, float size2, int rgb, float alpha) {
        P p = pool[cursor];
        cursor = (cursor + 1) % pool.length;
        p.active = true;
        p.shape = shape;
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.life = p.max = life;
        p.size = size;
        p.size2 = size2;
        p.r = ((rgb >> 16) & 0xff) / 255f;
        p.g = ((rgb >> 8) & 0xff) / 255f;
        p.b = (rgb & 0xff) / 255f;
        p.a = alpha;
        p.a2 = 0;
        p.grav = 0;
        p.drag = 0;
        p.rot = MathUtils.random(360f);
        p.vrot = 0;
        p.additive = false;
        return p;
    }

    public boolean skip() {
        return quality < 1f && MathUtils.random() > quality;
    }

    public void update(float dt) {
        for (P p : pool) {
            if (!p.active) continue;
            p.life -= dt;
            if (p.life <= 0) {
                p.active = false;
                continue;
            }
            p.vy -= p.grav * dt;
            if (p.drag > 0) {
                float k = Math.max(0, 1 - p.drag * dt);
                p.vx *= k;
                p.vy *= k;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.vrot * dt;
        }
    }

    public void draw(Draw d, boolean additivePass, float minX, float minY, float maxX, float maxY) {
        for (P p : pool) {
            if (!p.active || p.additive != additivePass) continue;
            if (p.x < minX - 100 || p.x > maxX + 100 || p.y < minY - 100 || p.y > maxY + 100) continue;
            float t = 1 - p.life / p.max;
            float s = p.size + (p.size2 - p.size) * t;
            float alpha = p.a + (p.a2 - p.a) * t;
            d.color(p.r, p.g, p.b, alpha);
            switch (p.shape) {
                case CIRCLE:
                    d.circle(p.x, p.y, s);
                    break;
                case SOFT:
                    d.soft(p.x, p.y, s);
                    break;
                case RECT:
                    d.rect(p.x - s / 2, p.y - s / 4, s, s / 2, s / 2, s / 4, p.rot);
                    break;
                case LINE: {
                    float len = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy) + 1;
                    float l = s * 3;
                    d.line(p.x - p.vx / len * l, p.y - p.vy / len * l, p.x, p.y, Math.max(1.2f, s * 0.4f));
                    break;
                }
                case RING:
                    d.ring(p.x, p.y, s);
                    break;
                default:
                    break;
            }
        }
    }

    public void clear() {
        for (P p : pool) p.active = false;
    }
}
