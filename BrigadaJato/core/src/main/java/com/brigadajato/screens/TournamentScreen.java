package com.brigadajato.screens;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.render.Draw;
import com.brigadajato.render.ModeIcons;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.modes.DuelMode;

/** Chave do torneio offline: quartas, semi e final em duelos contra bots cada vez mais fortes. */
public class TournamentScreen extends BaseScreen {
    private final Tournament tour;
    private final Bracket bracket;

    public static Tournament current;

    public TournamentScreen(BrigadaJatoGame game, Tournament t) {
        super(game);
        this.tour = t;
        current = t;
        Table header = new Table();
        header.add(button(I18n.t("back"), "gray-small", this::onBack)).width(170).height(52).left();
        header.add(label(I18n.t("tournament").toUpperCase(), "big")).expandX().center();
        String status;
        if (t.playerChampion()) status = I18n.t("t_champion");
        else if (t.playerOut) status = I18n.t("t_out");
        else status = I18n.t(t.roundKey(t.round));
        header.add(label(status, t.playerChampion() ? "yellow" : "default")).width(240).right();
        root.top().pad(18, 28, 14, 28);
        root.add(header).growX().padBottom(6).row();
        bracket = new Bracket();
        root.add(bracket).size(1220, 500).padTop(26).row();
        Table buttons = new Table();
        if (t.finished || t.playerOut) {
            buttons.add(button(I18n.t("t_new"), "green", () -> go(new TournamentScreen(game, new Tournament(game.settings.name, game.settings.look.copy())))))
                    .width(320).height(62).padRight(16);
            buttons.add(button(I18n.t("menu"), "gray", () -> go(new MenuScreen(game)))).width(220).height(62);
        } else {
            buttons.add(button(I18n.t("t_play") + "  >", "green", this::play)).width(340).height(64);
        }
        root.add(buttons).padTop(8);
        if (t.playerChampion() && t.justDecided == Tournament.ROUNDS - 1) {
            game.achievements.add("champion", 1);
            game.settings.xp += 500;
            game.settings.save();
            game.audio.ui("win");
        }
    }

    private void play() {
        final MatchConfig cfg = new MatchConfig();
        cfg.mode = ModeType.DUEL;
        cfg.applyModeDefaults();
        cfg.mapId = tour.mapFor(tour.round);
        cfg.difficulty = tour.difficulty();
        cfg.bots = 1;
        cfg.scoreLimit = 3;
        cfg.timeLimit = 0;
        final int opp = tour.opponent();
        final String[] names = {tour.names[opp]};
        final com.brigadajato.world.Look[] looks = {tour.looks[opp]};
        String sub = I18n.t(tour.roundKey(tour.round)) + "  ·  " + tour.names[tour.player()] + " " + I18n.t("t_vs") + " " + tour.names[opp];
        go(new LoadingScreen(game, cfg, sub, () -> {
            GameScreen gs = GameScreen.offlineVs(game, cfg, names, looks);
            gs.hookButton = I18n.t("t_continue");
            gs.matchHook = (win, screen) -> {
                int mine = 0, theirs = 0;
                if (screen.world().mode instanceof DuelMode) {
                    DuelMode dm = (DuelMode) screen.world().mode;
                    for (com.brigadajato.world.Player p : screen.world().players) {
                        if (p.isBot) theirs = dm.wins(p);
                        else mine = dm.wins(p);
                    }
                }
                tour.playerResult(win, mine, theirs);
                screen.goTo(new TournamentScreen(game, tour));
            };
            return gs;
        }));
    }

    @Override
    protected void onBack() {
        go(new ModeSelectScreen(game, SetupScreen.Kind.OFFLINE));
    }

    /** Desenho da chave com as linhas avancando e o trofeu no fim. */
    private class Bracket extends Actor {
        private float time;
        private ModeIcons icons;
        private final SoldierPainter painter = new SoldierPainter();
        private final SoldierPainter.Pose pose = new SoldierPainter.Pose();

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
        }

        private float slotY(int r, int i) {
            int n = Tournament.SIZE >> r;
            float gap = getHeight() / n;
            return getY() + getHeight() - gap * (i + 0.5f);
        }

        private float colX(int r) {
            return getX() + r * 255;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Draw d = game.draw;
            if (icons == null) icons = new ModeIcons(d, game.assets, painter);
            float sw = 225, sh = 44;
            int me = tour.player();
            // painel escuro atras da chave
            d.rgb(0x000000, 0.45f * parentAlpha);
            ModeSelectScreen.box(d, game.assets, getX() + getWidth() / 2 + 4, getY() + getHeight() / 2 + 4, getWidth() + 30, getHeight() + 62);
            d.rgb(0x0e1218, 0.82f * parentAlpha);
            ModeSelectScreen.box(d, game.assets, getX() + getWidth() / 2, getY() + getHeight() / 2 + 8, getWidth() + 30, getHeight() + 62);
            // linhas e caixas por rodada
            for (int r = 0; r <= Tournament.ROUNDS; r++) {
                int n = Tournament.SIZE >> r;
                float appearR = MathUtils.clamp((time - r * 0.12f) * 3, 0, 1);
                for (int i = 0; i < n; i++) {
                    int who = r == 0 ? i : tour.winners[r - 1][i];
                    float x = colX(r), y = slotY(r, i);
                    boolean decidedNow = r > 0 && tour.justDecided == r - 1;
                    float reveal = decidedNow ? MathUtils.clamp((time - 0.5f - i * 0.15f) / 0.6f, 0, 1) : 1;
                    // linha ligando ao proximo
                    if (r < Tournament.ROUNDS) {
                        float nx = colX(r + 1), ny = slotY(r + 1, i / 2);
                        int win = tour.winners[r][i / 2];
                        boolean adv = win >= 0 && win == who;
                        boolean animate = tour.justDecided == r;
                        float lk = animate ? MathUtils.clamp((time - 0.3f - (i / 2) * 0.15f) / 0.5f, 0, 1) : 1;
                        int lc = adv ? (who == me ? 0xffc61a : 0x90a4ae) : 0x37474f;
                        float midX = x + sw + (nx - x - sw) / 2;
                        d.rgb(lc, appearR * (adv ? 1 : 0.6f));
                        if (adv) {
                            float l1 = Math.min(1, lk * 3), l2 = MathUtils.clamp(lk * 3 - 1, 0, 1), l3 = MathUtils.clamp(lk * 3 - 2, 0, 1);
                            d.rect(x + sw, y - 2, (midX - x - sw) * l1, 4);
                            if (l2 > 0) d.rect(midX - 2, Math.min(y, ny), 4, Math.abs(ny - y) * l2 + 2);
                            if (l3 > 0) d.rect(midX, ny - 2, (nx - midX) * l3, 4);
                        } else {
                            d.rect(x + sw, y - 1, midX - x - sw, 2);
                            d.rect(midX - 1, Math.min(y, ny), 2, Math.abs(ny - y));
                        }
                    }
                    if (r == Tournament.ROUNDS) continue; // o campeao e desenhado a parte
                    if (who < 0) {
                        d.rgb(0x263238, 0.8f * appearR);
                        ModeSelectScreen.box(d, game.assets, x + sw / 2, y, sw, sh);
                        BitmapFont sm = game.assets.small;
                        sm.setColor(0.5f, 0.55f, 0.6f, appearR);
                        sm.draw(batch, "?", x + sw / 2, y + 8, 0, Align.center, false);
                        continue;
                    }
                    boolean lost = r < Tournament.ROUNDS && tour.winners[r][i / 2] >= 0 && tour.winners[r][i / 2] != who;
                    boolean current = r == tour.round && !tour.finished && !tour.playerOut && i / 2 == tour.playerMatch();
                    float a = appearR * reveal;
                    if (a <= 0) continue;
                    if (current) {
                        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 5);
                        d.additive(true);
                        d.rgb(0xffc61a, 0.35f * pulse);
                        ModeSelectScreen.box(d, game.assets, x + sw / 2, y, sw + 16, sh + 16);
                        d.additive(false);
                    }
                    d.rgb(who == me ? 0xffc61a : SoldierPainter.OUTLINE, a);
                    ModeSelectScreen.box(d, game.assets, x + sw / 2, y, sw + 4, sh + 4);
                    d.rgb(lost ? 0x1c2128 : (who == me ? 0x3a2f0f : 0x222a33), a);
                    ModeSelectScreen.box(d, game.assets, x + sw / 2, y, sw, sh);
                    // cabeca do soldado
                    drawHead(d, tour.looks[who], x + 24, y - 2, lost ? 0.5f * a : a);
                    BitmapFont nm = game.assets.small;
                    nm.setColor(lost ? 0.5f : 1, lost ? 0.5f : (who == me ? 0.85f : 1), lost ? 0.5f : (who == me ? 0.35f : 1), a);
                    nm.draw(batch, tour.names[who], x + 48, y + 9);
                    // placar da partida
                    if (r < Tournament.ROUNDS && tour.winners[r][i / 2] >= 0) {
                        int sc = tour.winners[r][i / 2] == who ? tour.scoreW[r][i / 2] : tour.scoreL[r][i / 2];
                        nm.setColor(1, 1, 1, a * (lost ? 0.5f : 1));
                        nm.draw(batch, "" + sc, x + sw - 14, y + 9, 0, Align.right, false);
                    }
                    if (lost) {
                        d.rgb(0xe53935, 0.7f * a);
                        d.rect(x + 44, y - 1, Math.min(150, sw - 70), 2);
                    }
                }
            }
            // nome das rodadas
            BitmapFont sm = game.assets.small;
            String[] keys = {"t_quarter", "t_semi", "t_final"};
            for (int r = 0; r < 3; r++) {
                sm.setColor(r == tour.round && !tour.finished ? 1 : 0.6f, r == tour.round && !tour.finished ? 0.85f : 0.65f, r == tour.round && !tour.finished ? 0.3f : 0.7f, 1);
                sm.draw(batch, I18n.t(keys[r]), colX(r) + sw / 2, getY() + getHeight() + 16, 0, Align.center, false);
            }
            // trofeu e campeao
            float tx = colX(Tournament.ROUNDS) + 110, ty = slotY(Tournament.ROUNDS, 0) + 40;
            float ta = MathUtils.clamp((time - 0.4f) * 2, 0, 1);
            icons.trophy(tx, ty, 170 * (0.9f + 0.1f * Interpolation.swingOut.apply(ta)), time, ta);
            int champ = tour.champion();
            BitmapFont big = game.assets.big;
            if (champ >= 0) {
                float ca = MathUtils.clamp((time - 1.4f) * 2, 0, 1);
                big.setColor(1, 0.82f, 0.25f, ca);
                big.draw(batch, tour.names[champ], tx, ty - 110, 0, Align.center, false);
                sm.setColor(1, 1, 1, ca);
                sm.draw(batch, I18n.t("t_champion"), tx, ty - 150, 0, Align.center, false);
                if (champ == me) confetti(d, tx, ty, ca);
            } else {
                sm.setColor(0.7f, 0.75f, 0.8f, ta);
                sm.draw(batch, I18n.t("desc_tournament"), tx - 110, ty - 120, 220, Align.center, true);
            }
            sm.setColor(1, 1, 1, 1);
            big.setColor(1, 1, 1, 1);
            batch.setColor(1, 1, 1, 1);
        }

        private void confetti(Draw d, float cx, float cy, float a) {
            int[] pal = {0xffc107, 0xe53935, 0x42a5f5, 0x66bb6a, 0xffffff};
            for (int i = 0; i < 60; i++) {
                float hu = (i * 0.618f) % 1f;
                float u = (time * (0.2f + hu * 0.25f) + hu) % 1f;
                float x = cx - 200 + hu * 400 + MathUtils.sin(time * 2 + i) * 14;
                float y = cy + 260 - u * 520;
                d.rgb(pal[i % pal.length], a * 0.9f);
                d.rect(x, y, 8, 5, 4, 2.5f, time * 200 + i * 37);
            }
        }

        private void drawHead(Draw d, com.brigadajato.world.Look look, float cx, float cy, float a) {
            d.rgb(SoldierPainter.OUTLINE, a);
            d.circle(cx, cy, 14);
            d.rgb(look.skin, a);
            d.circle(cx, cy, 12);
            d.rgb(look.body, a);
            d.region(game.assets.half, cx, cy + 5, 26, 13, 0);
            d.rgb(0x15151c, a);
            d.circle(cx + 4, cy - 1, 1.8f);
            d.circle(cx - 3, cy - 1, 1.8f);
        }
    }
}
