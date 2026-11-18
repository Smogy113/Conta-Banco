package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.brigadajato.BrigadaJatoGame;

/** Base das telas de menu (Scene2D + partida de fundo). */
public abstract class BaseScreen extends ScreenAdapter {
    protected final BrigadaJatoGame game;
    protected final Stage stage;
    protected final Skin skin;
    protected final Table root;
    protected boolean background = true;

    protected BaseScreen(BrigadaJatoGame game) {
        this.game = game;
        this.skin = game.skin;
        stage = new Stage(new ExtendViewport(1280, 720), game.batch);
        root = new Table();
        root.setFillParent(true);
        stage.addActor(root);
    }

    @Override
    public void show() {
        InputMultiplexer mux = new InputMultiplexer(stage, new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    onBack();
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(mux);
        // entrada da tela: sobe um pouco e aparece
        root.getColor().a = 0;
        root.setY(-26);
        root.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.22f, com.badlogic.gdx.math.Interpolation.pow2Out),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.moveTo(0, 0, 0.32f, com.badlogic.gdx.math.Interpolation.swingOut)));
    }

    protected void onBack() {
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.06f, 0.1f, 1);
        if (background) game.menuBg.render(delta);
        stage.getViewport().apply();
        stage.act(Math.min(delta, 0.1f));
        drawBehind(Math.min(delta, 0.1f));
        stage.draw();
        drawFront(Math.min(delta, 0.1f));
    }

    /** Efeitos entre o fundo e a interface (o batch esta fechado). */
    protected void drawBehind(float delta) {
    }

    /** Efeitos por cima da interface (o batch esta fechado). */
    protected void drawFront(float delta) {
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        game.menuBg.resize(width, height);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    protected TextButton button(String text, String style, final Runnable action) {
        TextButton b = new TextButton(text, skin, style);
        juice(b);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.audio.ui("ui_click");
                action.run();
            }
        });
        return b;
    }

    /** Botao que afunda ao tocar, volta com mola e cresce um pouco com o mouse em cima. */
    public static void juice(final Actor b) {
        if (b instanceof com.badlogic.gdx.scenes.scene2d.Group) ((com.badlogic.gdx.scenes.scene2d.Group) b).setTransform(true);
        b.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            private boolean over;

            private void to(float s, float t, com.badlogic.gdx.math.Interpolation in) {
                b.setOrigin(com.badlogic.gdx.utils.Align.center);
                b.clearActions();
                b.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(s, s, t, in));
            }

            @Override
            public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int p, int btn) {
                to(0.92f, 0.06f, com.badlogic.gdx.math.Interpolation.pow2Out);
                return true;
            }

            @Override
            public void touchUp(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int p, int btn) {
                to(over ? 1.04f : 1f, 0.28f, com.badlogic.gdx.math.Interpolation.swingOut);
            }

            @Override
            public void enter(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int p, Actor from) {
                if (p != -1) return;
                over = true;
                to(1.04f, 0.12f, com.badlogic.gdx.math.Interpolation.pow2Out);
            }

            @Override
            public void exit(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int p, Actor to) {
                if (p != -1) return;
                over = false;
                to(1f, 0.15f, com.badlogic.gdx.math.Interpolation.pow2Out);
            }
        });
    }

    protected Label label(String text, String style) {
        return new Label(text, skin, style);
    }

    protected void go(com.badlogic.gdx.Screen next) {
        com.badlogic.gdx.Screen old = game.getScreen();
        game.setScreen(next);
        if (old != null) old.dispose();
    }
}
