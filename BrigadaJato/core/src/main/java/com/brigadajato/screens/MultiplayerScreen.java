package com.brigadajato.screens;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.net.Discovery;
import com.brigadajato.net.Net;

import java.util.List;

/** Criar ou entrar numa sala no Wi-Fi local. */
public class MultiplayerScreen extends BaseScreen {
    private final Discovery.Finder finder = new Discovery.Finder();
    private final Table roomList = new Table();
    private final Label status;
    private final TextField ipField, passField;
    private float refresh;
    private String lastSig = null;

    public MultiplayerScreen(BrigadaJatoGame game) {
        this(game, null);
    }

    public MultiplayerScreen(BrigadaJatoGame game, String notice) {
        super(game);
        finder.start();
        Table panel = new Table(skin);
        panel.setBackground("panel");
        panel.pad(24);
        panel.defaults().pad(6);
        panel.add(label(I18n.t("multiplayer"), "big")).colspan(2).row();
        panel.add(label(I18n.t("mp_hint"), "gray")).colspan(2).padBottom(10).row();

        Table left = new Table();
        left.add(button(I18n.t("create_room"), "green", () -> go(new SetupScreen(game, SetupScreen.Kind.HOST)))).width(340).height(70).row();
        List<String> ips = Net.localIps();
        left.add(label(I18n.f("local_ip", ips.isEmpty() ? "?" : ips.get(0)), "gray")).width(340).padTop(10).row();
        if (!Net.onLocalNetwork()) {
            Label warn = label(I18n.t("no_wifi"), "yellow-small");
            warn.setWrap(true);
            left.add(warn).width(340).padTop(6).row();
        }
        Label tip = label(I18n.pt ? "Dica: ative o roteador do celular (hotspot) e\npeça para os amigos conectarem nele." : "Tip: turn on your phone hotspot\nand ask friends to join it.", "small");
        left.add(tip).padTop(10).row();
        if (game.platform.bluetoothSupported()) {
            left.add(button(I18n.t("bt_join"), "purple-small", () -> {
                finder.stop();
                go(new BluetoothScreen(game, pass()));
            })).width(340).height(56).padTop(12).row();
            Label bh = label(I18n.t("bt_hint"), "small");
            bh.setWrap(true);
            left.add(bh).width(340).row();
        }

        Table right = new Table();
        right.add(label(I18n.t("join_room"), "yellow")).left().row();
        roomList.top();
        ScrollPane sp = new ScrollPane(roomList, skin);
        sp.setFadeScrollBars(false);
        right.add(sp).width(520).height(220).row();
        Table manual = new Table();
        ipField = new TextField("", skin);
        ipField.setMessageText("192.168.0.10");
        manual.add(label(I18n.t("ip_address"), "small")).padRight(8);
        manual.add(ipField).width(250).height(54).padRight(8);
        manual.add(button(I18n.t("connect"), "blue", () -> connect(ipField.getText().trim()))).width(170).height(54);
        right.add(manual).padTop(8).row();
        Table pw = new Table();
        passField = new TextField("", skin);
        passField.setMessageText(I18n.pt ? "(se a sala tiver)" : "(if the room has one)");
        pw.add(label(I18n.t("password"), "small")).padRight(8);
        pw.add(passField).width(250).height(50);
        right.add(pw).padTop(4).row();

        panel.add(left).top().padRight(30);
        panel.add(right).top().row();
        status = label(notice != null ? notice : I18n.t("searching"), "small");
        panel.add(status).colspan(2).padTop(8).row();
        panel.add(button(I18n.t("back"), "gray", this::onBack)).colspan(2).width(240).height(58).padTop(6);
        root.add(panel);
        rebuildRooms(finder.rooms());
    }

    private String pass() {
        return passField != null ? passField.getText().trim() : "";
    }

    private void connect(String ip) {
        if (ip.isEmpty()) return;
        finder.stop();
        go(LobbyScreen.client(game, ip, passField.getText().trim()));
    }

    private void rebuildRooms(List<Discovery.Room> rooms) {
        StringBuilder sig = new StringBuilder();
        for (Discovery.Room r : rooms) sig.append(r.ip).append(r.players).append(r.mode).append(r.map).append(r.locked).append(r.inGame).append(';');
        if (sig.toString().equals(lastSig)) return;
        lastSig = sig.toString();
        roomList.clearChildren();
        if (rooms.isEmpty()) {
            Label l = label(I18n.t("no_rooms"), "gray");
            l.setWrap(true);
            roomList.add(l).width(500).pad(10);
            return;
        }
        for (final Discovery.Room r : rooms) {
            String text = I18n.f("room_of", r.name) + "  ·  " + r.mode + "  ·  " + r.players + "/" + r.max
                    + (r.locked ? "  ·  " + I18n.t("locked") : "") + (r.inGame ? "  ·  " + I18n.t("in_game") : "");
            roomList.add(button(text, "blue-small", () -> connect(r.ip))).width(500).height(52).pad(4).row();
        }
    }

    @Override
    public void render(float delta) {
        refresh -= delta;
        if (refresh <= 0) {
            refresh = 0.5f;
            rebuildRooms(finder.rooms());
        }
        super.render(delta);
    }

    @Override
    protected void onBack() {
        go(new MenuScreen(game));
    }

    @Override
    public void dispose() {
        finder.stop();
        super.dispose();
    }
}
