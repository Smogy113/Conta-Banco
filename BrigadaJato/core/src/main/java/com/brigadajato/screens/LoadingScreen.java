package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.render.Draw;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.MatchConfig;

/**
 * Tela de carregamento: o mapa ao vivo ao fundo, modo e nome do mapa entrando, uma dica e as
 * regras ativas, enquanto a partida e montada por tras. Toque (ou espere) para comecar.
 */
public class LoadingScreen extends BaseScreen {
    public interface Factory {
        Screen make();
    }

    private final MatchConfig cfg;
    private final Factory factory;
    private final String subtitle;
    private final MapPreview preview;
    private final String mapName;
    private final String tip;
    private final Array<String> rules = new Array<>();
    private Screen next;
    private float t, progress, leaving = -1;
    private static final float MIN_TIME = 1.7f, AUTO_START = 3.6f;

    public LoadingScreen(BrigadaJatoGame game, MatchConfig cfg, String subtitle, Factory factory) {
        super(game);
        this.cfg = cfg;
        this.factory = factory;
        this.subtitle = subtitle;
        background = false;
        GameMap m = BrigadaJatoGame.loadMap(cfg.mapId);
        com.brigadajato.render.ArtCache.prefetchMap(m);
        mapName = I18n.pt ? m.name : m.nameEn;
        preview = new MapPreview(game, 960, 540);
        preview.bots = 6;
        preview.viewH = 640;
        preview.setMap(cfg.mapId);
        String[][] tips = MenuScreen.TIPS;
        tip = tips[MathUtils.random(tips.length - 1)][I18n.pt ? 0 : 1];
        if (cfg.gravityMul != 1f) rules.add(I18n.t("r_gravity") + " " + Math.round(cfg.gravityMul * 100) + "%");
        if (cfg.healthMul != 1f) rules.add(I18n.t("r_health") + " " + Math.round(cfg.healthMul * 100) + "%");
        if (cfg.speedMul != 1f) rules.add(I18n.t("r_speed") + " " + Math.round(cfg.speedMul * 100) + "%");
        if (cfg.weaponSet != MatchConfig.SET_NORMAL) rules.add(I18n.t("set_" + cfg.weaponSet));
        if (cfg.infiniteAmmo) rules.add(I18n.t("r_inf_ammo"));
        if (cfg.infiniteJet) rules.add(I18n.t("r_inf_jet"));
        if (cfg.instagib) rules.add(I18n.t("r_instagib"));
        if (cfg.noGrenades) rules.add(I18n.t("r_no_nades"));
        if (cfg.spectate) rules.add(I18n.t("spectate"));
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.1f);
        t += dt;
        if (next == null && t > 0.12f) {
            next = factory.make(); // monta a partida (o primeiro quadro ja apareceu)
        }
        float target = next == null ? 0.35f : 1f;
        progress = Math.min(target, progress + dt * (next == null ? 0.6f : 0.9f));
        boolean ready = next != null && progress >= 1 && t >= MIN_TIME;
        if (ready && leaving < 0 && (t >= AUTO_START || Gdx.input.justTouched() || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ANY_KEY))) {
            leaving = 0;
            game.audio.ui("ui_click");
        }
        if (leaving >= 0) {
            leaving += dt;
            if (leaving > 0.3f) {
                Screen n = next;
                next = null;
                go(n);
                return;
            }
        }
        preview.update(dt);
        ScreenUtils.clear(0, 0, 0, 1);
        preview.render();
        stage.getViewport().apply();
        game.batch.setProjectionMatrix(stage.getCamera().combined);
        game.batch.begin();
        draw(ready);
        game.batch.end();
        game.batch.setColor(1, 1, 1, 1);
    }

    private void draw(boolean ready) {
        Draw d = game.draw;
        float W = stage.getViewport().getWorldWidth(), H = stage.getViewport().getWorldHeight();
        float x0 = stage.getCamera().position.x - W / 2, y0 = stage.getCamera().position.y - H / 2;
        TextureRegion tex = preview.texture();
        if (tex != null) {
            game.batch.setColor(1, 1, 1, 1);
            game.batch.draw(tex, x0, y0, W, H);
        }
        // escurece embaixo e em cima para o texto aparecer
        int steps = 32;
        for (int i = 0; i < steps; i++) {
            float k = i / (float) steps;
            d.color(0, 0, 0, 0.75f * (1 - k) * (1 - k));
            d.rect(x0, y0 + k * H * 0.55f, W, H * 0.55f / steps);
            d.color(0, 0, 0, 0.5f * (1 - k) * (1 - k));
            d.rect(x0, y0 + H - (k + 1f / steps) * H * 0.3f, W, H * 0.3f / steps);
        }
        float e = Interpolation.pow3Out.apply(MathUtils.clamp(t / 0.6f, 0, 1));
        int col = ModeSelectScreen.colorOf(cfg.mode);
        // modo (icone + nome) no topo
        float bx = x0 + 40 - (1 - e) * 200, by = y0 + H - 70;
        d.rgb(SoldierPainter.OUTLINE, e);
        ModeSelectScreen.box(d, game.assets, bx + 36, by, 76, 76);
        d.rgb(SoldierPainter.mix(col, 0x0b0e14, 0.5f), e);
        ModeSelectScreen.box(d, game.assets, bx + 36, by, 72, 72);
        ModeSelectScreen.drawModeIcon(d, game.assets, null, cfg.mode, bx + 36, by, 46, t, e);
        BitmapFont big = game.assets.big, sm = game.assets.small, nm = game.assets.normal, huge = game.assets.huge;
        nm.setColor(1, 0.85f, 0.3f, e);
        nm.draw(game.batch, I18n.t("mode_" + ModeSelectScreen.key(cfg.mode)).toUpperCase(), bx + 88, by + 22);
        if (subtitle != null) {
            sm.setColor(1, 1, 1, e * 0.9f);
            sm.draw(game.batch, subtitle, bx + 88, by - 8);
        }
        // nome do mapa grande
        float e2 = Interpolation.pow3Out.apply(MathUtils.clamp((t - 0.15f) / 0.6f, 0, 1));
        float mx = x0 + 40 - (1 - e2) * 300;
        d.rgb(col, e2);
        d.rect(x0 + 40, y0 + 168, 8, 70);
        huge.setColor(0, 0, 0, 0.6f * e2);
        huge.draw(game.batch, mapName, mx + 24, y0 + 236 - 4);
        huge.setColor(1, 1, 1, e2);
        huge.draw(game.batch, mapName, mx + 20, y0 + 236);
        // regras ativas
        float rx = x0 + 60;
        for (int i = 0; i < rules.size; i++) {
            String r = rules.get(i);
            float a = MathUtils.clamp((t - 0.4f - i * 0.08f) * 4, 0, 1);
            float sc = sm.getScaleX();
            sm.getData().setScale(sc * 0.8f);
            com.badlogic.gdx.graphics.g2d.GlyphLayout gl = new com.badlogic.gdx.graphics.g2d.GlyphLayout(sm, r);
            d.rgb(0x7b1fa2, a * 0.9f);
            ModeSelectScreen.box(d, game.assets, rx + gl.width / 2 + 10, y0 + 150, gl.width + 24, 30);
            sm.setColor(1, 1, 1, a);
            sm.draw(game.batch, r, rx + 10, y0 + 158);
            sm.getData().setScale(sc);
            rx += gl.width + 34;
        }
        // dica
        float ta = MathUtils.clamp((t - 0.5f) * 3, 0, 1);
        d.rgb(0x000000, 0.45f * ta);
        ModeSelectScreen.box(d, game.assets, x0 + W / 2, y0 + 92, Math.min(W - 80, 1000), 52);
        d.rgb(0xffd54f, ta);
        d.circle(x0 + W / 2 - Math.min(W - 80, 1000) / 2 + 30, y0 + 92, 9);
        sm.setColor(0.95f, 0.95f, 0.95f, ta);
        sm.draw(game.batch, (I18n.pt ? "Dica: " : "Tip: ") + tip, x0 + W / 2 - Math.min(W - 80, 1000) / 2 + 52, y0 + 102, Math.min(W - 80, 1000) - 70, Align.left, true);
        // barra de progresso
        float pw = W - 80, px = x0 + 40, py = y0 + 34;
        d.rgb(0x000000, 0.6f);
        d.rect(px, py, pw, 10);
        d.rgb(col, 1);
        d.rect(px, py, pw * progress, 10);
        d.rgb(0xffffff, 0.35f);
        d.rect(px, py + 6, pw * progress, 4);
        float sheen = (t * 0.8f) % 1.4f;
        if (sheen < 1) {
            d.rgb(0xffffff, 0.5f);
            d.rect(px + pw * progress * sheen, py, 30, 10);
        }
        sm.setColor(1, 1, 1, 0.9f);
        String status = ready ? (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop ? (I18n.pt ? "Clique ou aperte uma tecla para começar" : "Click or press a key to start") : I18n.t("tap_start"))
                : I18n.t("loading") + "  " + Math.round(progress * 100) + "%";
        if (ready) sm.setColor(1, 0.85f, 0.3f, 0.6f + 0.4f * MathUtils.sin(t * 6));
        sm.draw(game.batch, status, x0 + W - 40, py + 36, 0, Align.right, false);
        sm.setColor(1, 1, 1, 1);
        nm.setColor(1, 1, 1, 1);
        huge.setColor(1, 1, 1, 1);
        big.setColor(1, 1, 1, 1);
        // escurece na saida
        if (leaving >= 0) {
            d.color(0, 0, 0, Math.min(1, leaving / 0.3f));
            d.rect(x0, y0, W, H);
        }
        game.batch.setColor(1, 1, 1, 1);
    }

    @Override
    public void dispose() {
        if (next != null) next.dispose();
        preview.dispose();
        super.dispose();
    }
}
