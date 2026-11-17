package com.brigadajato.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.brigadajato.Assets;
import com.brigadajato.I18n;
import com.brigadajato.Settings;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Pickup;
import com.brigadajato.world.Player;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;
import com.brigadajato.world.modes.BombMode;
import com.brigadajato.world.modes.CtfMode;
import com.brigadajato.world.modes.SurvivalMode;

/** Interface durante a partida (barras, placar, feed de abates, mensagens, minimapa). */
public class Hud implements Disposable {
    private final Assets a;
    private final Settings settings;
    private final GlyphLayout layout = new GlyphLayout();
    public float w = 1280, h = 720;

    private static class Feed {
        String killer, victim, weapon;
        int kc, vc;
        float life;
        boolean head;
    }

    private static class Msg {
        String text;
        float life, max;
        int color;
        boolean big;
    }

    private final Array<Feed> feed = new Array<>();
    private final Array<Msg> center = new Array<>();
    private final Array<Msg> toasts = new Array<>();
    private float hurt;
    private float hitMarker;
    private Texture minimap;
    private World world;
    private int lastStep = -1;

    public Hud(Assets a, Settings settings) {
        this.a = a;
        this.settings = settings;
    }

    public void setWorld(World w) {
        this.world = w;
        feed.clear();
        center.clear();
        toasts.clear();
        buildMinimap(w.map);
    }

    private void buildMinimap(GameMap m) {
        if (minimap != null) minimap.dispose();
        Pixmap pm = new Pixmap(m.w, m.h, Pixmap.Format.RGBA8888);
        for (int y = 0; y < m.h; y++)
            for (int x = 0; x < m.w; x++) {
                int t = m.get(x, m.h - 1 - y);
                int c;
                if (t == GameMap.SOLID) c = Color.rgba8888(0.85f, 0.85f, 0.9f, 0.85f);
                else if (t == GameMap.PLATFORM) c = Color.rgba8888(0.9f, 0.7f, 0.4f, 0.8f);
                else if (t == GameMap.SPIKES) c = Color.rgba8888(0.9f, 0.3f, 0.3f, 0.8f);
                else c = Color.rgba8888(0, 0, 0, 0.35f);
                pm.drawPixel(x, y, c);
            }
        minimap = new Texture(pm);
        pm.dispose();
    }

    public void resize(float w, float h) {
        this.w = w;
        this.h = h;
    }

    // ------------------------------------------------------------------ eventos

    private String name(Player p) {
        return p == null ? "?" : p.name;
    }

    private int teamColor(Player p) {
        if (p == null) return 0xffffff;
        if (world.config.mode.teams) return p.team == C.TEAM_RED ? 0xff6b6b : 0x6bb6ff;
        return 0xffffff;
    }

    public static String weaponName(int code) {
        if (code == C.KILL_MELEE) return I18n.t("melee");
        if (code == C.KILL_FRAG) return I18n.t("frag");
        if (code == C.KILL_MINE) return I18n.t("mine");
        if (code == C.KILL_SPIKES) return I18n.t("spikes");
        if (code == C.KILL_BOMB) return I18n.t("bomb");
        if (code == C.KILL_SUICIDE) return I18n.t("suicide");
        WeaponType t = WeaponType.of(code);
        return t == null ? "?" : (I18n.pt ? t.pt : t.en);
    }

    public void big(String text, int color, float time) {
        Msg m = new Msg();
        m.text = text;
        m.life = m.max = time;
        m.color = color;
        m.big = true;
        center.add(m);
        if (center.size > 3) center.removeIndex(0);
    }

    public void toast(String text, int color) {
        Msg m = new Msg();
        m.text = text;
        m.life = m.max = 2.2f;
        m.color = color;
        toasts.add(m);
        if (toasts.size > 4) toasts.removeIndex(0);
    }

    public void handle(WorldEvent e, Player local) {
        int lid = local != null ? local.id : -999;
        switch (e.type) {
            case KILL: {
                Player k = world.player(e.a), v = world.player(e.b);
                Feed f = new Feed();
                f.killer = k != null && k != v ? name(k) : "";
                f.victim = name(v);
                f.weapon = weaponName(e.c);
                f.kc = teamColor(k);
                f.vc = teamColor(v);
                f.head = e.f > 0;
                f.life = 6;
                feed.add(f);
                if (feed.size > 4) feed.removeIndex(0);
                if (e.a == lid && e.b != lid) {
                    toast(I18n.f("you_killed", name(v)), 0xffd54f);
                    if (e.f > 0) big(I18n.t("headshot"), 0xff5252, 1.5f);
                }
                if (e.b == lid) {
                    if (k != null && k != v) big(I18n.f("killed_by", name(k)), 0xff8a80, 2.5f);
                }
                break;
            }
            case HIT:
                if (e.a == lid) hurt = 0.5f;
                if (e.b == lid) hitMarker = 0.2f;
                break;
            case STREAK: {
                Player p = world.player(e.a);
                String key = "streak" + e.b;
                String t = I18n.t(key);
                if (t.equals(key)) t = I18n.f("streak", e.b);
                if (e.a == lid) big(t, 0xffd740, 2f);
                else toast(name(p) + ": " + t, 0xffd740);
                break;
            }
            case PICKUP: {
                if (e.a != lid) break;
                if (e.b < 0) break;
                Pickup.Type t = Pickup.Type.values()[e.b];
                switch (t) {
                    case WEAPON: {
                        WeaponType wt = WeaponType.of(e.c);
                        if (wt != null) toast("+ " + (I18n.pt ? wt.pt : wt.en), 0xffffff);
                        break;
                    }
                    case HEALTH: toast(I18n.t("pickup_health"), 0x81c784); break;
                    case GRENADES: toast(I18n.t("pickup_grenades"), 0xaed581); break;
                    case ARMOR: toast(I18n.t("pickup_armor"), 0x64b5f6); break;
                    case DAMAGE: big(I18n.t("pickup_damage"), 0xff5252, 1.6f); break;
                    case JETPACK: big(I18n.t("pickup_jet"), 0x40c4ff, 1.6f); break;
                    case SPEED: big(I18n.t("pickup_speed"), 0xffff00, 1.6f); break;
                }
                break;
            }
            case MESSAGE: {
                String text;
                Player p = world.player(e.a);
                if ("surv_lives".equals(e.text)) text = I18n.f(e.text, name(p), e.b);
                else if ("player_left".equals(e.text) || "player_joined".equals(e.text)) text = I18n.f(e.text, name(p));
                else if ("surv_cleared".equals(e.text)) text = I18n.f(e.text, e.a);
                else text = I18n.t(e.text);
                if (e.c == 0 || e.c == 2) big(text, e.c == 2 ? 0x81c784 : 0xffffff, 2.2f);
                else toast(text, 0xffcc80);
                break;
            }
            case FLAG: {
                String team = e.a == C.TEAM_RED ? I18n.t("team_red") : I18n.t("team_blue");
                Player p = world.player(e.c);
                String t;
                switch (e.b) {
                    case CtfMode.EV_TAKEN: t = I18n.f("flag_taken", name(p), team); break;
                    case CtfMode.EV_DROPPED: t = I18n.f("flag_dropped", team); break;
                    case CtfMode.EV_RETURNED: t = I18n.f("flag_returned", team); break;
                    default: t = I18n.f("flag_captured", name(p), team); break;
                }
                big(t, e.a == C.TEAM_RED ? 0xff8a80 : 0x82b1ff, 2.5f);
                break;
            }
            case BOMB: {
                Player p = world.player(e.c);
                switch (e.b) {
                    case BombMode.EV_PLANTED: big(I18n.f("bomb_planted_by", name(p)), 0xff5252, 2.5f); break;
                    case BombMode.EV_DEFUSED: big(I18n.f("bomb_defused_by", name(p)), 0x82b1ff, 2.5f); break;
                    case BombMode.EV_DROPPED: toast(I18n.t("bomb_dropped"), 0xffcc80); break;
                    case BombMode.EV_PICKED: toast(I18n.f("bomb_picked", name(p)), 0xffcc80); break;
                    default: break;
                }
                break;
            }
            case WAVE:
                big(I18n.f("surv_wave", e.a), 0xff7043, 2.5f);
                break;
            case ROUND:
                if (e.a <= 0) big(I18n.t("round") + " " + e.b, 0xffffff, 1.8f);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ desenho

    public void update(float dt) {
        for (int i = feed.size - 1; i >= 0; i--) {
            feed.get(i).life -= dt;
            if (feed.get(i).life <= 0) feed.removeIndex(i);
        }
        for (int i = center.size - 1; i >= 0; i--) {
            center.get(i).life -= dt;
            if (center.get(i).life <= 0) center.removeIndex(i);
        }
        for (int i = toasts.size - 1; i >= 0; i--) {
            toasts.get(i).life -= dt;
            if (toasts.get(i).life <= 0) toasts.removeIndex(i);
        }
        hurt = Math.max(0, hurt - dt);
        hitMarker = Math.max(0, hitMarker - dt);
    }

    private void text(BitmapFont f, String s, float x, float y, int color, float alpha, int align) {
        f.setColor(((color >> 16) & 0xff) / 255f, ((color >> 8) & 0xff) / 255f, (color & 0xff) / 255f, alpha);
        f.draw(batchRef.batch, s, x, y, 0, align, false);
    }

    private Draw batchRef;

    public void draw(Draw d, World w, Player local, float fps, boolean scoreboard, boolean touchUi) {
        batchRef = d;
        float W = this.w, H = this.h;
        // vinheta de dano
        if (hurt > 0) {
            d.color(0.8f, 0, 0, hurt * 0.5f);
            d.rect(0, 0, W, 18);
            d.rect(0, H - 18, W, 18);
            d.rect(0, 0, 18, H);
            d.rect(W - 18, 0, 18, H);
            d.color(0.8f, 0, 0, hurt * 0.15f);
            d.rect(0, 0, W, H);
        }
        if (local != null && local.health < 30 && local.alive) {
            float pulse = 0.12f + 0.08f * MathUtils.sin(w.time * 6);
            d.color(0.7f, 0, 0, pulse);
            d.rect(0, 0, W, 10);
            d.rect(0, H - 10, W, 10);
        }

        // ---- barras (canto superior esquerdo)
        float x = 18, y = H - 34;
        if (local != null) {
            d.rgb(0x000000, 0.4f);
            d.rrect(x - 8, y - 66, 300, 84);
            // coracao
            d.rgb(0xe53935, 1);
            d.circle(x + 7, y + 6, 6);
            d.circle(x + 17, y + 6, 6);
            d.region(a.tri, x + 12, y - 2, 22, 14, 180);
            float hp = Math.max(0, local.health) / C.HEALTH_MAX;
            d.bar(x + 32, y - 2, 230, 15, hp, hp > 0.5f ? 0x66bb6a : (hp > 0.25f ? 0xffca28 : 0xef5350), 0x3a1010);
            if (local.armor > 0) {
                d.rgb(0x42a5f5, 1);
                d.rect(x + 32, y + 9, 230 * local.armor / C.ARMOR_MAX, 4);
            }
            text(a.small, "" + (int) Math.ceil(Math.max(0, local.health)), x + 32 + 115, y + 13, 0xffffff, 1, Align.center);
            // jetpack
            y -= 28;
            d.rgb(local.jetBoost > 0 ? 0x40c4ff : 0xffab00, 1);
            d.region(a.tri, x + 12, y + 4, 14, 18, 180);
            float fuel = local.fuel / C.FUEL_MAX;
            d.bar(x + 32, y - 3, 230, 12, local.jetBoost > 0 ? 1 : fuel, local.fuelLocked ? 0x9e9e9e : (local.jetBoost > 0 ? 0x40c4ff : 0x4fc3f7), 0x10203a);
            // granadas e dinheiro
            y -= 26;
            d.rgb(0x33691e, 1);
            d.circle(x + 10, y + 2, 7);
            text(a.small, "x" + local.frags, x + 22, y + 10, 0xffffff, 1, Align.left);
            d.rgb(0x455a64, 1);
            d.batch.draw(a.half, x + 58, y - 4, 18, 10);
            text(a.small, "x" + local.mines, x + 80, y + 10, 0xffffff, 1, Align.left);
            if (w.mode instanceof BombMode) text(a.small, I18n.f("money", local.money), x + 130, y + 10, 0x9ccc65, 1, Align.left);
            float px = x + 200;
            if (local.dmgBoost > 0) {
                text(a.small, "x2 " + (int) Math.ceil(local.dmgBoost), px, y + 10, 0xff5252, 1, Align.left);
                px += 60;
            }
            if (local.jetBoost > 0) {
                text(a.small, "∞ " + (int) Math.ceil(local.jetBoost), px, y + 10, 0x40c4ff, 1, Align.left);
                px += 55;
            }
            if (local.speedBoost > 0) text(a.small, "» " + (int) Math.ceil(local.speedBoost), px, y + 10, 0xffff00, 1, Align.left);
        }

        // ---- minimapa
        if (minimap != null) {
            float mw = 170, mh = mw * w.map.h / w.map.w;
            float mx = 18, my = H - 120 - mh;
            d.color(1, 1, 1, 0.9f);
            d.batch.draw(minimap, mx, my, mw, mh);
            float sx = mw / w.map.worldW(), sy = mh / w.map.worldH();
            for (Player p : w.players) {
                if (!p.alive || p.dummy) continue;
                boolean me = p == local;
                boolean ally = local != null && w.config.mode.teams && p.team == local.team;
                if (!me && !ally && !p.recentlyFired && !(p.carryingFlag != 0) && !p.hasBomb) continue;
                int c = me ? 0xffffff : (ally ? 0x76ff03 : 0xff1744);
                d.rgb(0x000000, 1);
                d.circle(mx + p.x * sx, my + (p.y + 20) * sy, me ? 4.5f : 3.5f);
                d.rgb(c, 1);
                d.circle(mx + p.x * sx, my + (p.y + 20) * sy, me ? 3.2f : 2.4f);
            }
            if (w.mode instanceof CtfMode) {
                CtfMode m = (CtfMode) w.mode;
                for (int t = 1; t <= 2; t++) {
                    d.rgb(t == 1 ? 0xff1744 : 0x2979ff, 1);
                    d.rect(mx + m.fx[t] * sx - 3, my + (m.fy[t] + 20) * sy - 3, 6, 6);
                }
            }
            if (w.mode instanceof BombMode) {
                BombMode bm = (BombMode) w.mode;
                for (int i = 0; i < 2; i++) text(a.small, i == 0 ? "A" : "B", mx + bm.sitePos(i).x * sx, my + bm.sitePos(i).y * sy + 14, 0xff8a65, 1, Align.center);
                if (bm.bombState == BombMode.B_PLANTED || bm.bombState == BombMode.B_DROPPED) {
                    d.rgb(0xff1744, (w.time * 4) % 1 < 0.5f ? 1 : 0.3f);
                    d.circle(mx + bm.bombX * sx, my + (bm.bombY + 10) * sy, 4);
                }
            }
        }

        // ---- topo: status do modo
        String status = w.mode.hud(local);
        layout.setText(a.normal, status);
        float sw = layout.width + 40;
        d.rgb(0x000000, 0.45f);
        d.rrect(W / 2 - sw / 2, H - 50, sw, 42);
        text(a.normal, status, W / 2, H - 20, 0xffffff, 1, Align.center);

        // ---- banner do modo
        String banner = w.mode.banner(local);
        if (banner != null) {
            int nl = banner.indexOf('\n');
            String l1 = nl >= 0 ? banner.substring(0, nl) : banner;
            String l2 = nl >= 0 ? banner.substring(nl + 1) : null;
            layout.setText(a.normal, l1);
            float bw = Math.min(W - 40, layout.width + 40);
            d.rgb(0x000000, 0.4f);
            d.rrect(W / 2 - bw / 2, H - 100 - (l2 != null ? 30 : 0), bw, 40 + (l2 != null ? 30 : 0));
            text(a.normal, l1, W / 2, H - 70, 0xffe082, 1, Align.center);
            if (l2 != null) text(a.small, l2, W / 2, H - 102, 0xffffff, 1, Align.center);
            if (w.mode instanceof com.brigadajato.world.modes.TrainingMode) {
                int step = ((com.brigadajato.world.modes.TrainingMode) w.mode).step;
                if (step != lastStep) lastStep = step;
            }
        }

        // ---- feed de abates (direita)
        float fy = H - 100;
        for (int i = feed.size - 1; i >= 0; i--) {
            Feed f = feed.get(i);
            float alpha = Math.min(1, f.life);
            String line = (f.killer.isEmpty() ? "" : f.killer + "  ") + "[" + f.weapon + "]" + (f.head ? " ☠" : "") + "  " + f.victim;
            layout.setText(a.small, line);
            d.rgb(0x000000, 0.4f * alpha);
            d.rrect(W - 24 - layout.width - 12, fy - 22, layout.width + 24, 28);
            float cx = W - 24 - layout.width;
            if (!f.killer.isEmpty()) {
                text(a.small, f.killer, cx, fy, f.kc, alpha, Align.left);
                layout.setText(a.small, f.killer + "  ");
                cx += layout.width;
            }
            String mid = "[" + f.weapon + "]" + (f.head ? " ☠" : "") + "  ";
            text(a.small, mid, cx, fy, 0xbdbdbd, alpha, Align.left);
            layout.setText(a.small, mid);
            cx += layout.width;
            text(a.small, f.victim, cx, fy, f.vc, alpha, Align.left);
            fy -= 32;
        }

        // ---- mensagens centrais
        float cy = H * 0.66f;
        for (int i = center.size - 1; i >= 0; i--) {
            Msg m = center.get(i);
            float t = m.life / m.max;
            float alpha = Math.min(1, m.life * 2);
            float scale = 1 + Math.max(0, (t - 0.85f)) * 2;
            a.big.getData().setScale(scale / a.fontScale);
            text(a.big, m.text, W / 2, cy, m.color, alpha, Align.center);
            a.big.getData().setScale(1 / a.fontScale);
            cy -= 52;
        }
        float ty = 210;
        for (int i = toasts.size - 1; i >= 0; i--) {
            Msg m = toasts.get(i);
            text(a.small, m.text, W / 2, ty, m.color, Math.min(1, m.life * 2), Align.center);
            ty += 26;
        }

        if (local != null) {
            // renascendo
            if (!local.alive) {
                d.rgb(0x000000, 0.35f);
                d.rect(0, H * 0.42f - 40, W, 70);
                String s = w.mode.canRespawn(local) && local.respawnTimer < 100
                        ? I18n.f("respawn_in", (int) Math.ceil(Math.max(0, local.respawnTimer)))
                        : (w.mode instanceof SurvivalMode ? I18n.t("lives") + ": 0" : I18n.t("spectating"));
                text(a.big, s, W / 2, H * 0.42f + 14, 0xffffff, 1, Align.center);
            }
            // recarregando
            if (local.alive && local.reloading()) {
                Gun g = local.gun();
                float frac = g != null ? 1 - local.reloadTimer / g.type.reload : 0;
                d.bar(W / 2 - 60, H * 0.36f, 120, 8, frac, 0xffffff, 0x333333);
                text(a.small, I18n.t("reloading"), W / 2, H * 0.36f + 30, 0xffffff, 0.9f, Align.center);
            } else if (local.alive && local.gun() != null && local.gun().empty()) {
                text(a.normal, I18n.t("no_ammo"), W / 2, H * 0.36f + 30, 0xff5252, 0.6f + 0.4f * MathUtils.sin(w.time * 8), Align.center);
            }
            // plantar / desarmar
            if (local.actionProgress > 0) {
                boolean planting = local.hasBomb;
                d.bar(W / 2 - 150, H * 0.3f, 300, 18, local.actionProgress, planting ? 0xff5252 : 0x42a5f5, 0x222222);
                text(a.normal, I18n.t(planting ? "planting" : "defusing"), W / 2, H * 0.3f + 48, 0xffffff, 1, Align.center);
            }
            // arma e municao (PC; no celular fica no painel de toque)
            if (!touchUi && local.alive && local.gun() != null) {
                Gun g = local.gun();
                String s = (I18n.pt ? g.type.pt : g.type.en) + "   " + g.mag + " / " + g.reserve;
                text(a.normal, s, W - 30, 50, g.mag == 0 ? 0xff5252 : 0xffffff, 1, Align.right);
                Gun o = local.otherGun();
                if (o != null) text(a.small, "[Q] " + (I18n.pt ? o.type.pt : o.type.en) + "  " + o.mag + "/" + o.reserve, W - 30, 22, 0xbdbdbd, 1, Align.right);
                text(a.small, (local.grenadeType == 0 ? I18n.t("frag") : I18n.t("mine")) + " [G]  [T]", W - 30, 82, 0xaed581, 1, Align.right);
            }
            if (hitMarker > 0) {
                d.color(1, 1, 1, hitMarker * 4);
            }
        }

        if (scoreboard) drawScoreboard(d, w, local);
        if (settings.showFps) text(a.small, I18n.f("fps", (int) fps), W / 2, 22, 0xb2ff59, 1, Align.center);
        a.small.setColor(Color.WHITE);
        a.normal.setColor(Color.WHITE);
        a.big.setColor(Color.WHITE);
    }

    public void drawScoreboard(Draw d, World w, Player local) {
        float W = this.w, H = this.h;
        Array<Player> ranking = w.ranking();
        int rows = 0;
        for (Player p : ranking) if (!p.dummy) rows++;
        float pw = 760, ph = 90 + rows * 34;
        float x = W / 2 - pw / 2, y = H / 2 - ph / 2;
        d.rgb(0x000000, 0.7f);
        d.rrect(x, y, pw, ph);
        text(a.big, I18n.t("scoreboard"), W / 2, y + ph - 14, 0xffffff, 1, Align.center);
        float cy = y + ph - 66;
        text(a.small, I18n.t("players"), x + 30, cy, 0xbdbdbd, 1, Align.left);
        text(a.small, I18n.t("kills"), x + 470, cy, 0xbdbdbd, 1, Align.center);
        text(a.small, I18n.t("deaths"), x + 570, cy, 0xbdbdbd, 1, Align.center);
        text(a.small, I18n.t("score"), x + 670, cy, 0xbdbdbd, 1, Align.center);
        cy -= 32;
        for (Player p : ranking) {
            if (p.dummy) continue;
            if (p == local) {
                d.rgb(0xffffff, 0.12f);
                d.rect(x + 14, cy - 24, pw - 28, 30);
            }
            int c = w.config.mode.teams ? (p.team == C.TEAM_RED ? 0xff8a80 : 0x82b1ff) : 0xffffff;
            text(a.small, p.name + (p.isBot ? "  (bot)" : "") + (p.alive ? "" : "  ✝"), x + 30, cy, c, 1, Align.left);
            text(a.small, "" + p.kills, x + 470, cy, 0xffffff, 1, Align.center);
            text(a.small, "" + p.deaths, x + 570, cy, 0xffffff, 1, Align.center);
            text(a.small, "" + p.score, x + 670, cy, 0xffd54f, 1, Align.center);
            cy -= 34;
        }
    }

    @Override
    public void dispose() {
        if (minimap != null) minimap.dispose();
    }
}
