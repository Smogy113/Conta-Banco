package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.input.TouchControls;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.Look;
import com.brigadajato.world.Player;

/** Editor de layout: arraste os botoes e os joysticks para onde quiser e salve. */
public class ControlsEditorScreen extends BaseScreen {
    private final TouchControls controls;
    private final OrthographicCamera hud = new OrthographicCamera();
    private final Player dummy;
    private final SoldierPainter painter = new SoldierPainter();
    /** o que esta sendo arrastado: indice do botao, -2 joystick esquerdo, -3 direito */
    private int dragging = -1;
    private float grabDx, grabDy;

    public ControlsEditorScreen(BrigadaJatoGame game) {
        super(game);
        controls = new TouchControls(game.settings, true);
        controls.editMode = true;
        dummy = new Player(0, "", 0, new Look(), false);
        dummy.giveDefaultLoadout(new java.util.Random(1));
        dummy.alive = true;
        Table bar = new Table(skin);
        bar.setBackground("panel");
        bar.pad(8);
        bar.add(label(I18n.t("layout_hint"), "small")).padRight(14);
        bar.add(button(I18n.t("reset"), "gray-small", () -> {
            game.settings.controlLayout = "";
            controls.layout();
        })).height(44).padRight(6);
        bar.add(button(I18n.t("save"), "green-small", () -> {
            game.settings.controlLayout = controls.layoutString();
            game.settings.save();
            onBack();
        })).height(44).padRight(6);
        bar.add(button(I18n.t("back"), "red-small", this::onBack)).height(44);
        root.top().add(bar).padTop(6);
    }

    @Override
    public void show() {
        super.show();
        InputMultiplexer mux = (InputMultiplexer) Gdx.input.getInputProcessor();
        mux.addProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                float x = hx(sx), y = hy(sy);
                dragging = -1;
                float best = 90 * 90;
                for (int i = 0; i < TouchControls.B_COUNT; i++) {
                    float dx = x - controls.bx[i], dy = y - controls.by[i];
                    float d = dx * dx + dy * dy;
                    if (d < Math.max(best, controls.br[i] * controls.br[i] * 1.6f) && d < best + 1) {
                        best = d;
                        dragging = i;
                    }
                }
                float dl = (x - controls.leftStickX) * (x - controls.leftStickX) + (y - controls.leftStickY) * (y - controls.leftStickY);
                float dr = (x - controls.rightStickX) * (x - controls.rightStickX) + (y - controls.rightStickY) * (y - controls.rightStickY);
                if (dl < best && dl < 110 * 110) {
                    best = dl;
                    dragging = -2;
                }
                if (dr < best && dr < 110 * 110) dragging = -3;
                if (dragging == -1) return false;
                float ox = dragging == -2 ? controls.leftStickX : (dragging == -3 ? controls.rightStickX : controls.bx[dragging]);
                float oy = dragging == -2 ? controls.leftStickY : (dragging == -3 ? controls.rightStickY : controls.by[dragging]);
                grabDx = ox - x;
                grabDy = oy - y;
                return true;
            }

            @Override
            public boolean touchDragged(int sx, int sy, int pointer) {
                if (dragging == -1) return false;
                float x = Math.max(20, Math.min(controls.hudW - 20, hx(sx) + grabDx));
                float y = Math.max(20, Math.min(controls.hudH - 20, hy(sy) + grabDy));
                if (dragging == -2) {
                    controls.leftStickX = x;
                    controls.leftStickY = y;
                } else if (dragging == -3) {
                    controls.rightStickX = x;
                    controls.rightStickY = y;
                } else {
                    controls.bx[dragging] = x;
                    controls.by[dragging] = y;
                }
                return true;
            }

            @Override
            public boolean touchUp(int sx, int sy, int pointer, int button) {
                dragging = -1;
                return false;
            }
        });
    }

    private float hx(int sx) {
        return sx / (float) Gdx.graphics.getWidth() * controls.hudW;
    }

    private float hy(int sy) {
        return (1f - sy / (float) Gdx.graphics.getHeight()) * controls.hudH;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        float aspect = width / (float) Math.max(1, height);
        hud.viewportHeight = 720;
        hud.viewportWidth = 720 * aspect;
        hud.position.set(hud.viewportWidth / 2, 360, 0);
        hud.update();
        controls.resize(hud.viewportWidth, 720);
    }

    @Override
    public void render(float delta) {
        super.render(delta);
        game.batch.setProjectionMatrix(hud.combined);
        game.batch.begin();
        game.draw.color(0, 0, 0, 0.35f);
        game.draw.rect(0, 0, hud.viewportWidth, 720);
        controls.updateVisibility(dummy, true, true);
        controls.draw(game.draw, game.assets.small, game.assets.normal, dummy, painter);
        if (dragging != -1) {
            game.assets.small.setColor(1, 0.85f, 0.3f, 1);
            game.assets.small.draw(game.batch, "+", hud.viewportWidth / 2, 40, 0, Align.center, false);
            game.assets.small.setColor(1, 1, 1, 1);
        }
        game.batch.end();
        game.batch.setColor(1, 1, 1, 1);
        // os botoes do topo por cima
        stage.draw();
    }

    @Override
    protected void onBack() {
        go(new SettingsScreen(game, 2));
    }
}
