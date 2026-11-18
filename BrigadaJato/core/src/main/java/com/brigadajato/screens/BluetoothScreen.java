package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.Platform;
import com.brigadajato.net.Link;

/** Escolhe o celular (pareado) que criou a sala por Bluetooth. */
public class BluetoothScreen extends BaseScreen {
    private final Table list = new Table();
    private final Label status;
    private final String password;

    public BluetoothScreen(BrigadaJatoGame game, String password) {
        super(game);
        this.password = password;
        Table panel = new Table(skin);
        panel.setBackground("panel");
        panel.pad(24);
        panel.defaults().pad(6);
        panel.add(label(I18n.t("bt_join"), "big")).row();
        Label hint = label(I18n.t("bt_hint"), "gray");
        hint.setWrap(true);
        panel.add(hint).width(600).row();
        list.top();
        panel.add(list).width(600).height(300).row();
        status = label(I18n.t("bt_scanning"), "small");
        panel.add(status).row();
        Table b = new Table();
        b.add(button(I18n.pt ? "Procurar de novo" : "Scan again", "blue-small", this::scan)).height(54).padRight(10);
        b.add(button(I18n.t("back"), "gray", this::onBack)).width(200).height(56);
        panel.add(b);
        root.add(panel);
        scan();
    }

    private void scan() {
        status.setText(I18n.t("bt_scanning"));
        game.platform.bluetoothScan(new Platform.BtListener() {
            @Override
            public void status(final String text) {
                Gdx.app.postRunnable(() -> status.setText(text));
            }

            @Override
            public void devices(final String[] names, final String[] addresses) {
                Gdx.app.postRunnable(() -> fill(names, addresses));
            }
        });
    }

    private void fill(String[] names, String[] addresses) {
        list.clearChildren();
        for (int i = 0; i < names.length; i++) {
            final String addr = addresses[i], name = names[i];
            list.add(button(name + "  ·  " + addr, "blue-small", () -> {
                Link.Factory f = game.platform.bluetoothLink(addr, name);
                if (f != null) go(LobbyScreen.client(game, f, password));
            })).width(580).height(52).pad(3).row();
        }
        if (names.length == 0) list.add(label(I18n.pt ? "Nenhum aparelho pareado." : "No paired devices.", "gray"));
    }

    @Override
    protected void onBack() {
        go(new MultiplayerScreen(game));
    }
}
