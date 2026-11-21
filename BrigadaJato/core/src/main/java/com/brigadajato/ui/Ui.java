package com.brigadajato.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.brigadajato.Assets;

/** Skin da interface criada por codigo (botoes cartoon coloridos). */
public final class Ui {
    private Ui() {
    }

    public static Color c(int rgb) {
        return new Color(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, 1f);
    }

    public static Skin build(Assets a) {
        Skin s = new Skin();
        NinePatch panel = new NinePatch(a.uiPanel, 15, 15, 15, 15);
        NinePatch btn = new NinePatch(a.uiButton, 9, 9, 9, 15);
        NinePatch btnDown = new NinePatch(a.uiButtonDown, 9, 9, 12, 12);
        s.add("chip", new NinePatchDrawable(new NinePatch(a.uiChip, 9, 9, 9, 9)), Drawable.class);
        s.add("plate", new NinePatchDrawable(new NinePatch(a.uiPlate, 9, 9, 9, 9)), Drawable.class);
        s.add("stripes", new TextureRegionDrawable(a.uiStripes), Drawable.class);
        NinePatch field = new NinePatch(a.uiField, 9, 9, 9, 9);
        s.add("panel", new NinePatchDrawable(panel), Drawable.class);
        s.add("field", new NinePatchDrawable(field), Drawable.class);
        s.add("white", new TextureRegionDrawable(a.white), Drawable.class);

        s.add("small", a.small);
        s.add("normal", a.normal);
        s.add("big", a.big);
        s.add("huge", a.huge);

        String[] names = {"default", "green", "blue", "red", "gray", "purple"};
        int[] cols = {0xeaa233, 0x529c45, 0x3a7cc6, 0xc8483c, 0x5c6a76, 0x7d56aa};
        for (int i = 0; i < names.length; i++) {
            TextButton.TextButtonStyle st = new TextButton.TextButtonStyle();
            st.up = new NinePatchDrawable(btn).tint(c(cols[i]));
            st.down = new NinePatchDrawable(btnDown).tint(c(cols[i]).mul(0.9f, 0.9f, 0.9f, 1));
            st.checked = new NinePatchDrawable(btnDown).tint(c(cols[i]).mul(0.8f, 0.8f, 0.8f, 1));
            st.disabled = new NinePatchDrawable(btn).tint(c(0x616161));
            st.font = a.normal;
            st.fontColor = Color.WHITE;
            st.downFontColor = new Color(1, 1, 0.85f, 1);
            st.disabledFontColor = Color.LIGHT_GRAY;
            st.pressedOffsetY = -2;
            s.add(names[i], st);
            TextButton.TextButtonStyle sm = new TextButton.TextButtonStyle(st);
            sm.font = a.small;
            s.add(names[i] + "-small", sm);
        }
        // botao de opcao selecionavel
        TextButton.TextButtonStyle toggle = new TextButton.TextButtonStyle();
        toggle.up = new NinePatchDrawable(btn).tint(c(0x546e7a));
        toggle.down = new NinePatchDrawable(btnDown).tint(c(0x455a64));
        toggle.checked = new NinePatchDrawable(btn).tint(c(0xffb300));
        toggle.font = a.small;
        toggle.fontColor = Color.WHITE;
        s.add("toggle", toggle);

        Label.LabelStyle def = new Label.LabelStyle(a.normal, Color.WHITE);
        s.add("default", def);
        s.add("small", new Label.LabelStyle(a.small, Color.WHITE));
        s.add("big", new Label.LabelStyle(a.big, Color.WHITE));
        s.add("title", new Label.LabelStyle(a.huge, c(0xffc61a)));
        s.add("heading", new Label.LabelStyle(a.big, c(0xffc61a)));
        s.add("yellow", new Label.LabelStyle(a.normal, c(0xffd54f)));
        s.add("yellow-small", new Label.LabelStyle(a.small, c(0xffd54f)));
        s.add("gray", new Label.LabelStyle(a.small, c(0xb0bec5)));

        TextField.TextFieldStyle tf = new TextField.TextFieldStyle();
        tf.font = a.normal;
        tf.fontColor = c(0x212121);
        tf.background = new NinePatchDrawable(field);
        tf.cursor = new TextureRegionDrawable(a.white).tint(c(0x212121));
        tf.selection = new TextureRegionDrawable(a.white).tint(new Color(0.3f, 0.6f, 1f, 0.5f));
        tf.messageFont = a.normal;
        tf.messageFontColor = c(0x9e9e9e);
        s.add("default", tf);

        Slider.SliderStyle sl = new Slider.SliderStyle();
        TextureRegionDrawable bar = new TextureRegionDrawable(a.rrect);
        bar.setMinHeight(14);
        bar.setMinWidth(40);
        sl.background = bar.tint(c(0x37474f));
        TextureRegionDrawable before = new TextureRegionDrawable(a.rrect);
        before.setMinHeight(14);
        sl.knobBefore = before.tint(c(0xffb300));
        TextureRegionDrawable knob = new TextureRegionDrawable(a.circle);
        knob.setMinWidth(34);
        knob.setMinHeight(34);
        sl.knob = knob.tint(Color.WHITE);
        s.add("default-horizontal", sl);

        CheckBox.CheckBoxStyle cb = new CheckBox.CheckBoxStyle();
        TextureRegionDrawable on = new TextureRegionDrawable(a.rrect);
        on.setMinWidth(36);
        on.setMinHeight(36);
        TextureRegionDrawable off = new TextureRegionDrawable(a.rrect);
        off.setMinWidth(36);
        off.setMinHeight(36);
        cb.checkboxOn = on.tint(c(0x66bb6a));
        cb.checkboxOff = off.tint(c(0x546e7a));
        cb.font = a.normal;
        cb.fontColor = Color.WHITE;
        s.add("default", cb);

        s.add("default", new ScrollPane.ScrollPaneStyle());
        return s;
    }
}
