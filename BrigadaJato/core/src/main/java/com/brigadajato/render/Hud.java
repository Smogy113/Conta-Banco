package com.brigadajato.render;

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
import com.brigadajato.world.modes.TrainingMode;

/** Interface durante a partida (barras, placar, feed de abates, mensagens, minimapa). */
public class Hud implements Disposable {
    private final Assets a;
    private final Settings settings;
    private final GlyphLayout layout = new GlyphLayout();
    private final SoldierPainter painter = new SoldierPainter();
    private final KillBanner killBanner;
    public float w = 1280, h = 720;

    private static class Feed {
        String killer, victim;
        int code;
        int kc, vc;
        float life;
        boolean head, mine;
        float killerW, victimW;
    }

    private static class Msg {
        String text;
        float life, max;
        int color;
    }

    private final Array<Feed> feed = new Array<>();
    private final Array<Msg> center = new Array<>();
    private final Array<Msg> toasts = new Array<>();
    private float hurt;
    private Texture minimap;
    private World world;
    private Draw dr;

    // textos em cache (refeitos 10x por segundo, nao a cada quadro)
    private float refresh;
    private String midText = "", subText = "", banner, deadText = "", rightText = "";
    private final Num.Pair ammo = new Num.Pair(" / ");
    private final Num.Clock clock = new Num.Clock();

    public Hud(Assets a, Settings settings) {
        this.a = a;
        this.settings = settings;
        this.killBanner = new KillBanner(a);
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
                if (t == GameMap.SOLID) c = Color.rgba8888(0.8f, 0.82f, 0.78f, 0.9f);
                else if (t == GameMap.PLATFORM) c = Color.rgba8888(0.95f, 0.72f, 0.35f, 0.85f);
                else if (t == GameMap.SPIKES) c = Color.rgba8888(0.95f, 0.3f, 0.3f, 0.85f);
                else c = Color.rgba8888(0.05f, 0.07f, 0.1f, 0.55f);
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
        if (world.config.mode.teams) return p.team == C.TEAM_RED ? 0xff7b72 : 0x7cc3ff;
        return 0xffffff;
    }

    public static String weaponName(int code) {
        if (code == C.KILL_MELEE) return I18n.t("melee");
        if (code == C.KILL_FRAG) return I18n.t("frag");
        if (code == C.KILL_MINE) return I18n.t("mine");
        if (code == C.KILL_SPIKES) return I18n.t("spikes");
        if (code == C.KILL_BOMB) return I18n.t("bomb");
        if (code == C.KILL_SUICIDE) return I18n.t("suicide");
        if (code == C.KILL_BARREL) return I18n.pt ? "Barril" : "Barrel";
        if (code == C.KILL_BOSS) return I18n.pt ? "Chefão" : "Boss";
        if (code == C.KILL_ZONE) return I18n.pt ? "Zona" : "Zone";
        if (code == C.KILL_INFECT) return I18n.pt ? "Infecção" : "Infection";
        WeaponType t = WeaponType.of(code);
        return t == null ? "?" : (I18n.pt ? t.pt : t.en);
    }

    /** Impacto do emblema de abates (para tremer/aproximar a camera). */
    public float consumeImpact() {
        return killBanner.consumeImpact();
    }

    public void big(String text, int color, float time) {
        Msg m = new Msg();
        m.text = text;
        m.life = m.max = time;
        m.color = color;
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
                f.code = e.c;
                f.kc = teamColor(k);
                f.vc = teamColor(v);
                f.head = e.f > 0;
                f.mine = e.a == lid || e.b == lid;
                f.life = 6;
                layout.setText(a.small, f.killer);
                f.killerW = layout.width;
                layout.setText(a.small, f.victim);
                f.victimW = layout.width;
                feed.add(f);
                if (feed.size > 4) feed.removeIndex(0);
                if (e.a == lid && e.b != lid) {
                    toast(I18n.f("you_killed", name(v)), 0xffd54f);
                    if (settings.killBanner) killBanner.trigger(e.d > 0 ? e.d : 1, e.c, e.f > 0);
                    else if (e.f > 0) big(I18n.t("headshot"), 0xff5252, 1.5f);
                }
                if (e.b == lid && k != null && k != v) big(I18n.f("killed_by", name(k)), 0xff8a80, 2.5f);
                break;
            }
            case HIT:
                if (e.a == lid) hurt = 0.5f;
                break;
            case STREAK: {
                Player p = world.player(e.a);
                String key = "streak" + e.b;
                String t = I18n.t(key);
                if (t.equals(key)) t = I18n.f("streak", e.b);
                if (e.a == lid) {
                    if (!settings.killBanner) big(t, 0xffd740, 2f);
                } else toast(name(p) + ": " + t, 0xffd740);
                break;
            }
            case PICKUP: {
                if (e.a != lid || e.b < 0) break;
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
                else if ("surv_cleared".equals(e.text) || "surv_boss".equals(e.text)) text = I18n.f(e.text, e.a);
                else if ("player_back".equals(e.text) || "player_joined_game".equals(e.text) || "gg_knife".equals(e.text)
                        || "inf_infected".equals(e.text)) text = I18n.f(e.text, name(p));
                else if ("gg_demoted".equals(e.text)) text = I18n.f(e.text, name(p), e.b);
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
        refresh -= dt;
        killBanner.update(dt);
    }

    private void text(BitmapFont f, String s, float x, float y, int color, float alpha, int align) {
        f.setColor(((color >> 16) & 0xff) / 255f, ((color >> 8) & 0xff) / 255f, (color & 0xff) / 255f, alpha);
        f.draw(dr.batch, s, x, y, 0, align, false);
    }

    private void chip(float x, float y, float w, float h, float alpha) {
        dr.color(1, 1, 1, alpha);
        Draw.patch(dr, a.uiChip, x, y, w, h, 14);
    }

    /** Refaz os textos dinamicos (no maximo 10x por segundo). */
    private void rebuildTexts(World w, Player local) {
        refresh = 0.1f;
        banner = w.mode.banner(local);
        if (local != null && !local.alive) {
            deadText = w.mode.canRespawn(local) && local.respawnTimer < 100
                    ? Num.of((int) Math.ceil(Math.max(0, local.respawnTimer)))
                    : (w.mode instanceof SurvivalMode ? I18n.t("lives") + ": 0" : I18n.t("spectating"));
        }
        if (w.mode instanceof BombMode) {
            BombMode bm = (BombMode) w.mode;
            midText = bm.phase == BombMode.PLANTED ? clock.of(bm.bombTimer) : clock.of(bm.phaseTimer);
            subText = I18n.t("round") + " " + bm.round + (local == null ? "" : "  ·  " + (local.team == bm.attackTeam ? I18n.t("role_attack") : I18n.t("role_defend")));
        } else if (w.mode instanceof SurvivalMode) {
            SurvivalMode sm = (SurvivalMode) w.mode;
            midText = I18n.t("wave") + " " + Math.max(1, sm.wave);
            subText = I18n.t("enemies") + ": " + (sm.enemiesAlive + sm.toSpawn);
        } else if (w.mode instanceof TrainingMode) {
            midText = I18n.t("mode_training");
            subText = (((TrainingMode) w.mode).step + 1) + " / " + TrainingMode.STEPS.length;
        } else {
            midText = w.config.timeLimit > 0 ? clock.of(w.mode.timeLeft) : "--:--";
            boolean custom = w.mode instanceof com.brigadajato.world.modes.GunGameMode || w.mode instanceof com.brigadajato.world.modes.KingHillMode
                    || w.mode instanceof com.brigadajato.world.modes.InfectionMode || w.mode instanceof com.brigadajato.world.modes.BattleRoyaleMode
                    || w.mode instanceof com.brigadajato.world.modes.DuelMode;
            if (custom) {
                subText = w.mode.hud(local);
                if (w.mode instanceof com.brigadajato.world.modes.KingHillMode) subText = I18n.t("mode_koth");
                if (w.mode instanceof com.brigadajato.world.modes.BattleRoyaleMode) {
                    com.brigadajato.world.modes.BattleRoyaleMode br = (com.brigadajato.world.modes.BattleRoyaleMode) w.mode;
                    midText = br.shrinking ? "!!" : clock.of(br.timer);
                    subText = I18n.t("br_alive") + ": " + br.aliveCount + (br.shrinking ? "  ·  " + I18n.t("br_shrinking") : "");
                }
                if (w.mode instanceof com.brigadajato.world.modes.DuelMode) midText = I18n.t("round") + " " + ((com.brigadajato.world.modes.DuelMode) w.mode).round;
                if (w.mode instanceof com.brigadajato.world.modes.InfectionMode && !((com.brigadajato.world.modes.InfectionMode) w.mode).started)
                    midText = clock.of(((com.brigadajato.world.modes.InfectionMode) w.mode).countdown);
            } else if (w.config.mode.teams) subText = w.mode instanceof CtfMode ? I18n.t("mode_ctf") : I18n.t("mode_team");
            else {
                Array<Player> r = w.ranking();
                Player lead = r.size > 0 ? r.first() : null;
                subText = lead == null ? "" : I18n.t("kills") + ": " + (local != null ? local.kills : 0)
                        + (w.config.scoreLimit > 0 ? "/" + w.config.scoreLimit : "") + "   ·   1º " + lead.name + " " + lead.kills;
            }
        }
        if (local != null && w.mode instanceof BombMode) rightText = "$" + local.money;
        else rightText = "";
    }

    public void draw(Draw d, World w, Player local, float fps, boolean scoreboard, boolean touchUi) {
        dr = d;
        float W = this.w, H = this.h;
        if (refresh <= 0) rebuildTexts(w, local);
        // vinheta de dano
        if (hurt > 0) {
            d.color(0.85f, 0, 0, hurt * 0.55f);
            d.rect(0, 0, W, 16);
            d.rect(0, H - 16, W, 16);
            d.rect(0, 0, 16, H);
            d.rect(W - 16, 0, 16, H);
            d.color(0.8f, 0, 0, hurt * 0.12f);
            d.rect(0, 0, W, H);
        }
        if (local != null && local.health < 30 && local.alive) {
            float pulse = 0.12f + 0.08f * MathUtils.sin(w.time * 6);
            d.color(0.7f, 0, 0, pulse);
            d.rect(0, 0, W, 10);
            d.rect(0, H - 10, W, 10);
        }
        killBanner.drawEdgeGlow(d, W, H);
        if (local != null) drawStatus(d, w, local);
        if (settings.minimap && minimap != null) drawMinimap(d, w, local, 16, H - 132);
        drawTop(d, w, local, W, H);

        // banner do modo
        if (banner != null) {
            int nl = banner.indexOf('\n');
            String l1 = nl >= 0 ? banner.substring(0, nl) : banner;
            String l2 = nl >= 0 ? banner.substring(nl + 1) : null;
            layout.setText(a.normal, l1);
            float bw = Math.min(W - 40, layout.width + 50);
            float by = H - 122 - (l2 != null ? 28 : 0);
            chip(W / 2 - bw / 2, by, bw, 44 + (l2 != null ? 28 : 0), 1);
            text(a.normal, l1, W / 2, by + 34 + (l2 != null ? 28 : 0), 0xffe082, 1, Align.center);
            if (l2 != null) text(a.small, l2, W / 2, by + 30, 0xffffff, 1, Align.center);
        }

        drawFeed(d, W, H);

        // mensagens centrais
        float cy = H * 0.64f;
        for (int i = center.size - 1; i >= 0; i--) {
            Msg m = center.get(i);
            float t = m.life / m.max;
            float alpha = Math.min(1, m.life * 2);
            float scale = 0.85f + Math.max(0, (t - 0.85f)) * 3f;
            a.big.getData().setScale(scale / a.fontScale);
            text(a.big, m.text, W / 2, cy, m.color, alpha, Align.center);
            a.big.getData().setScale(1 / a.fontScale);
            cy -= 50;
        }
        killBanner.draw(d, W / 2, H * 0.3f);
        float ty = 112;
        for (int i = toasts.size - 1; i >= 0; i--) {
            Msg m = toasts.get(i);
            text(a.small, m.text, W / 2, ty, m.color, Math.min(1, m.life * 2), Align.center);
            ty += 26;
        }

        if (local != null) {
            if (!local.alive && !w.mode.over) {
                d.color(0, 0, 0, 0.4f);
                d.rect(0, H * 0.4f - 46, W, 92);
                d.color(1, 1, 1, 1);
                Draw.tiled(d, a.uiStripes, 0, H * 0.4f + 40, W, 6);
                Draw.tiled(d, a.uiStripes, 0, H * 0.4f - 46, W, 6);
                boolean counting = w.mode.canRespawn(local) && local.respawnTimer < 100;
                if (counting) {
                    text(a.small, I18n.t("respawning"), W / 2, H * 0.4f + 30, 0xffffff, 1, Align.center);
                    text(a.big, deadText, W / 2, H * 0.4f, 0xffd54f, 1, Align.center);
                } else text(a.big, deadText, W / 2, H * 0.4f + 16, 0xffffff, 1, Align.center);
            }
            if (local.alive && local.reloading()) {
                Gun g = local.gun();
                float frac = g != null ? 1 - local.reloadTimer / g.type.reload : 0;
                Draw.segBar(d, W / 2 - 70, H * 0.35f, 140, 10, frac, 0xffffff, 10);
                text(a.small, I18n.t("reloading"), W / 2, H * 0.35f + 32, 0xffffff, 0.95f, Align.center);
            } else if (local.alive && local.gun() != null && local.gun().empty()) {
                text(a.normal, I18n.t("no_ammo"), W / 2, H * 0.35f + 30, 0xff5252, 0.6f + 0.4f * MathUtils.sin(w.time * 8), Align.center);
            }
            if (local.actionProgress > 0) {
                boolean planting = local.hasBomb;
                Draw.segBar(d, W / 2 - 160, H * 0.29f, 320, 18, local.actionProgress, planting ? 0xff5252 : 0x42a5f5, 16);
                text(a.normal, I18n.t(planting ? "planting" : "defusing"), W / 2, H * 0.29f + 50, 0xffffff, 1, Align.center);
            }
            if (!touchUi && local.alive && local.gun() != null) drawAmmoPc(d, local, W);
        }

        if (scoreboard) drawScoreboard(d, w, local);
        if (settings.showFps) text(a.small, "FPS " + Num.of((int) fps), W / 2, 22, 0xb2ff59, 1, Align.center);
        a.small.setColor(Color.WHITE);
        a.normal.setColor(Color.WHITE);
        a.big.setColor(Color.WHITE);
    }

    private void drawStatus(Draw d, World w, Player local) {
        float x = 16, top = h - 14;
        chip(x - 6, top - 112, 314, 118, 1);
        // vida
        float y = top - 24;
        Icons.heart(d, x + 16, y + 2, 24, 0xe53935);
        float hp = Math.max(0, local.health) / C.HEALTH_MAX;
        int hpCol = hp > 0.5f ? 0x5dd35d : (hp > 0.25f ? 0xffc93c : 0xf2453d);
        Draw.segBar(d, x + 36, y - 8, 214, 17, hp, hpCol, 10);
        if (local.armor > 0) Draw.segBar(d, x + 36, y + 12, 214 * local.armor / C.ARMOR_MAX, 5, 1, 0x4aa8ff, 0);
        text(a.small, Num.of((int) Math.ceil(Math.max(0, local.health))), x + 258, y + 10, 0xffffff, 1, Align.left);
        // jetpack
        y -= 34;
        int flameCol = local.jetBoost > 0 ? 0x40c4ff : local.look.flameColor(w.time);
        Icons.flame(d, x + 16, y + 2, 26, local.fuelLocked ? 0x9e9e9e : flameCol);
        float fuel = local.jetBoost > 0 ? 1 : local.fuel / C.FUEL_MAX;
        Draw.segBar(d, x + 36, y - 6, 214, 13, fuel, local.fuelLocked ? 0x8a8a8a : 0x4fc3f7, 10);
        // granadas, minas, dinheiro, power-ups
        y -= 34;
        Icons.grenade(d, x + 16, y + 2, 24);
        text(a.small, Num.of(local.frags), x + 32, y + 12, local.grenadeType == 0 ? 0xffffff : 0x9e9e9e, 1, Align.left);
        Icons.mine(d, x + 72, y + 2, 26, local.grenadeType == 1);
        text(a.small, Num.of(local.mines), x + 90, y + 12, local.grenadeType == 1 ? 0xffffff : 0x9e9e9e, 1, Align.left);
        if (local.grenadeType == 0) {
            d.rgb(0xffd54f, 0.9f);
            d.rect(x + 4, y - 12, 26, 2.5f);
        } else {
            d.rgb(0xffd54f, 0.9f);
            d.rect(x + 60, y - 12, 26, 2.5f);
        }
        float px = x + 128;
        if (!rightText.isEmpty()) {
            text(a.small, rightText, px, y + 12, 0x9ccc65, 1, Align.left);
            px += 70;
        }
        if (local.dmgBoost > 0) {
            Icons.star(d, px + 8, y + 2, 20, 0xff1744);
            text(a.small, Num.of((int) Math.ceil(local.dmgBoost)), px + 20, y + 12, 0xff8a80, 1, Align.left);
            px += 52;
        }
        if (local.jetBoost > 0) {
            Icons.flame(d, px + 8, y + 2, 20, 0x40c4ff);
            text(a.small, Num.of((int) Math.ceil(local.jetBoost)), px + 20, y + 12, 0x80d8ff, 1, Align.left);
            px += 52;
        }
        if (local.speedBoost > 0) {
            Icons.bolt(d, px + 8, y + 2, 20, 0xffea00);
            text(a.small, Num.of((int) Math.ceil(local.speedBoost)), px + 20, y + 12, 0xffff8d, 1, Align.left);
        }
    }

    private void drawMinimap(Draw d, World w, Player local, float mx, float topY) {
        float mw = 176, mh = mw * w.map.h / w.map.w;
        float my = topY - mh - 6;
        chip(mx - 6, my - 6, mw + 12, mh + 12, 0.9f);
        d.color(1, 1, 1, 0.92f);
        d.batch.draw(minimap, mx, my, mw, mh);
        float sx = mw / w.map.worldW(), sy = mh / w.map.worldH();
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            if (!p.alive || p.dummy) continue;
            boolean me = p == local;
            boolean ally = local != null && w.config.mode.teams && p.team == local.team;
            if (!me && !ally && !p.recentlyFired && p.carryingFlag == 0 && !p.hasBomb) continue;
            int c = me ? 0xffffff : (ally ? 0x76ff03 : 0xff1744);
            d.rgb(0x000000, 1);
            d.circle(mx + p.x * sx, my + (p.y + 20) * sy, me ? 4.5f : 3.5f);
            d.rgb(c, 1);
            d.circle(mx + p.x * sx, my + (p.y + 20) * sy, me ? 3.2f : 2.4f);
        }
        if (w.mode instanceof CtfMode) {
            CtfMode m = (CtfMode) w.mode;
            for (int t = 1; t <= 2; t++) Icons.flag(d, mx + m.fx[t] * sx, my + (m.fy[t] + 20) * sy, 14, t == 1 ? 0xff1744 : 0x2979ff, w.time);
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

    private void drawTop(Draw d, World w, Player local, float W, float H) {
        float cx = W / 2, y = H - 58;
        if (w.config.mode.teams && !(w.mode instanceof SurvivalMode) && !(w.mode instanceof TrainingMode)
                && !(w.mode instanceof com.brigadajato.world.modes.InfectionMode)) {
            // placar em caixas por time
            float box = 74, mid = 118;
            boolean myRed = local != null && local.team == C.TEAM_RED;
            Draw.box(d, cx - mid / 2 - box, y, box, 46, SoldierPainter.RED, myRed);
            Draw.box(d, cx + mid / 2, y, box, 46, SoldierPainter.BLUE, local != null && !myRed);
            chip(cx - mid / 2, y, mid, 46, 1);
            text(a.big, Num.of(w.mode.teamScore[1]), cx - mid / 2 - box / 2, y + 40, 0xffffff, 1, Align.center);
            text(a.big, Num.of(w.mode.teamScore[2]), cx + mid / 2 + box / 2, y + 40, 0xffffff, 1, Align.center);
            boolean urgent = w.mode instanceof BombMode && ((BombMode) w.mode).phase == BombMode.PLANTED;
            text(a.normal, midText, cx, y + 34, urgent ? 0xff5252 : 0xffffff, 1, Align.center);
            if (urgent) Icons.bomb(d, cx - 46, y + 22, 18, (w.time * 4) % 1 < 0.5f);
        } else {
            layout.setText(a.normal, midText);
            float mw = Math.max(130, layout.width + 40);
            chip(cx - mw / 2, y, mw, 46, 1);
            text(a.normal, midText, cx, y + 34, 0xffffff, 1, Align.center);
            if (w.mode instanceof SurvivalMode && local != null) {
                for (int i = 0; i < Math.max(0, local.lives); i++) Icons.heart(d, cx + mw / 2 + 16 + i * 22, y + 24, 18, 0xe53935);
            }
        }
        if (!subText.isEmpty()) {
            layout.setText(a.small, subText);
            chip(cx - layout.width / 2 - 14, y - 30, layout.width + 28, 28, 0.85f);
            text(a.small, subText, cx, y - 9, 0xe0e0e0, 1, Align.center);
        }
    }

    private void drawFeed(Draw d, float W, float H) {
        float fy = H - 104;
        for (int i = feed.size - 1; i >= 0; i--) {
            Feed f = feed.get(i);
            float alpha = Math.min(1, f.life);
            float iconW = 46, gap = 10;
            float total = (f.killer.isEmpty() ? 0 : f.killerW + gap) + iconW + (f.head ? 22 : 0) + gap + f.victimW;
            float x = W - 22 - total;
            chip(x - 12, fy - 24, total + 24, 32, 0.85f * alpha);
            if (f.mine) {
                d.rgb(0xffd54f, 0.85f * alpha);
                d.rect(x - 12, fy - 24, 4, 32);
            }
            if (!f.killer.isEmpty()) {
                text(a.small, f.killer, x, fy, f.kc, alpha, Align.left);
                x += f.killerW + gap;
            }
            drawKillIcon(d, f.code, x + iconW / 2, fy - 8, alpha);
            x += iconW;
            if (f.head) {
                Icons.skull(d, x + 10, fy - 8, 18, 0xffffff);
                x += 22;
            }
            x += gap;
            text(a.small, f.victim, x, fy, f.vc, alpha, Align.left);
            fy -= 36;
        }
    }

    private void drawKillIcon(Draw d, int code, float x, float y, float alpha) {
        WeaponType t = WeaponType.of(code);
        if (t != null) {
            painter.drawGun(d, t, x, y, 0, 26f / Math.max(26f, t.gunLen) * 1.25f, alpha);
            return;
        }
        switch (code) {
            case C.KILL_MELEE: Icons.fist(d, x, y, 22, 0xf1c27d); break;
            case C.KILL_FRAG: Icons.grenade(d, x, y, 22); break;
            case C.KILL_MINE: Icons.mine(d, x, y, 24, true); break;
            case C.KILL_SPIKES: Icons.spikes(d, x, y, 24); break;
            case C.KILL_BOMB: Icons.bomb(d, x, y, 24, true); break;
            default: Icons.skull(d, x, y, 20, 0xbdbdbd); break;
        }
    }

    private void drawAmmoPc(Draw d, Player local, float W) {
        Gun g = local.gun();
        Gun o0 = local.otherGun();
        chip(W - 300, 12, 286, o0 != null ? 112 : 92, 1);
        painter.drawGun(d, g.type, local.look.gunSkin, W - 236, 66, 0, 1.4f, 1);
        text(a.big, ammo.of(g.mag, g.reserve), W - 30, 78, g.mag == 0 ? 0xff5252 : 0xffffff, 1, Align.right);
        text(a.small, I18n.pt ? g.type.pt : g.type.en, W - 30, 38, 0xbdbdbd, 1, Align.right);
        Gun o = local.otherGun();
        if (o != null) text(a.small, "Q: " + (I18n.pt ? o.type.pt : o.type.en), W - 290, 116, 0x9e9e9e, 1, Align.left);
    }

    public void drawScoreboard(Draw d, World w, Player local) {
        dr = d;
        float W = this.w, H = this.h;
        Array<Player> ranking = w.ranking();
        int rows = 0;
        for (int i = 0; i < ranking.size; i++) if (!ranking.get(i).dummy) rows++;
        rows = Math.min(rows, 12);
        float pw = 780, ph = 100 + rows * 36;
        float x = W / 2 - pw / 2, y = H / 2 - ph / 2;
        d.color(1, 1, 1, 1);
        Draw.patch(d, a.uiPanel, x, y, pw, ph, 16);
        Draw.tiled(d, a.uiStripes, x + 16, y + ph - 22, pw - 32, 8);
        text(a.big, I18n.t("scoreboard"), W / 2, y + ph - 30, 0xffc61a, 1, Align.center);
        float cy = y + ph - 80;
        text(a.small, I18n.t("players"), x + 34, cy, 0x9e9e9e, 1, Align.left);
        text(a.small, I18n.t("kills"), x + 480, cy, 0x9e9e9e, 1, Align.center);
        text(a.small, I18n.t("deaths"), x + 580, cy, 0x9e9e9e, 1, Align.center);
        text(a.small, I18n.t("score"), x + 690, cy, 0x9e9e9e, 1, Align.center);
        cy -= 34;
        int shown = 0;
        for (int i = 0; i < ranking.size && shown < rows; i++) {
            Player p = ranking.get(i);
            if (p.dummy) continue;
            shown++;
            int c = w.config.mode.teams ? (p.team == C.TEAM_RED ? 0xff8a80 : 0x82b1ff) : 0xffffff;
            if (p == local) {
                d.rgb(0xffd54f, 0.16f);
                d.rect(x + 16, cy - 26, pw - 32, 32);
            }
            d.rgb(c, 0.9f);
            d.rect(x + 18, cy - 24, 5, 28);
            text(a.small, p.name, x + 34, cy, p.alive ? c : 0x9e9e9e, 1, Align.left);
            if (p.isBot) text(a.small, "BOT", x + 300, cy, 0x757575, 1, Align.left);
            text(a.small, Num.of(p.kills), x + 480, cy, 0xffffff, 1, Align.center);
            text(a.small, Num.of(p.deaths), x + 580, cy, 0xffffff, 1, Align.center);
            text(a.small, Num.of(p.score), x + 690, cy, 0xffd54f, 1, Align.center);
            cy -= 36;
        }
    }

    @Override
    public void dispose() {
        if (minimap != null) minimap.dispose();
    }
}
