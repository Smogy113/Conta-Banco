package com.brigadajato.screens;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.net.ClientSession;
import com.brigadajato.net.HostSession;
import com.brigadajato.net.Net;
import com.brigadajato.net.NetClient;
import com.brigadajato.world.C;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;

import java.util.List;

/** Sala de espera (anfitriao ou cliente). */
public class LobbyScreen extends BaseScreen {
    private final HostSession host;
    private final ClientSession client;
    private final Table players = new Table();
    private final Label info, status;
    private String lastSig = "";

    public static LobbyScreen host(BrigadaJatoGame game, MatchConfig cfg) {
        HostSession h = new HostSession(cfg, game.settings);
        if (!h.start()) {
            h.close();
            return new LobbyScreen(game, null, null, I18n.t("conn_failed") + " " + h.net.error);
        }
        game.platform.setMulticastLock(true);
        return new LobbyScreen(game, h, null, null);
    }

    public static LobbyScreen client(BrigadaJatoGame game, String ip) {
        ClientSession c = new ClientSession(ip, game.settings.name, game.settings.look, BrigadaJatoGame::loadMap);
        return new LobbyScreen(game, null, c, null);
    }

    /** Volta para a sala de espera depois de uma partida. */
    public static LobbyScreen resume(BrigadaJatoGame game, HostSession h, ClientSession c) {
        return new LobbyScreen(game, h, c, null);
    }

    private LobbyScreen(BrigadaJatoGame game, HostSession host, ClientSession client, String error) {
        super(game);
        this.host = host;
        this.client = client;
        Table panel = new Table(skin);
        panel.setBackground("panel");
        panel.pad(24);
        panel.defaults().pad(6);
        panel.add(label(I18n.t("lobby"), "big")).row();
        info = label("", "yellow");
        info.setAlignment(Align.center);
        panel.add(info).row();
        if (host != null) {
            List<String> ips = Net.localIps();
            panel.add(label(I18n.f("local_ip", ips.isEmpty() ? "?" : joinIps(ips)), "gray")).row();
        }
        players.top();
        panel.add(players).width(640).height(300).row();
        status = label(error != null ? error : (host != null ? "" : I18n.t("connecting")), "small");
        panel.add(status).row();
        Table buttons = new Table();
        buttons.defaults().pad(6);
        if (error == null) {
            buttons.add(button(I18n.t("change_team"), "purple-small", this::switchTeam)).width(200).height(56);
            if (host != null) {
                buttons.add(button(I18n.t("settings"), "gray-small", () -> {
                    final LobbyScreen self = this;
                    game.setScreen(new SetupScreen(game, SetupScreen.Kind.LOBBY_EDIT, host.cfg, () -> {
                        game.setScreen(self);
                        host.broadcastLobby();
                    }));
                })).width(200).height(56);
                buttons.add(button(I18n.t("start_match"), "green", this::startMatch)).width(260).height(60);
            }
        }
        buttons.add(button(I18n.t("back"), "red-small", this::onBack)).width(160).height(56);
        panel.add(buttons).padTop(6);
        root.add(panel);
        if (host != null) host.broadcastLobby();
    }

    private static String joinIps(List<String> ips) {
        StringBuilder sb = new StringBuilder();
        for (String ip : ips) {
            if (sb.length() > 0) sb.append("  /  ");
            sb.append(ip);
        }
        return sb.toString();
    }

    private void switchTeam() {
        if (host != null) {
            host.hostTeam = host.hostTeam == C.TEAM_RED ? C.TEAM_BLUE : C.TEAM_RED;
            host.broadcastLobby();
        } else if (client != null) {
            int mine = C.TEAM_RED;
            for (HostSession.Entry e : client.lobby) if (e.id == client.myId) mine = e.team;
            client.net.requestTeam(mine == C.TEAM_RED ? C.TEAM_BLUE : C.TEAM_RED);
        }
    }

    private void startMatch() {
        if (host == null) return;
        go(GameScreen.host(game, host));
    }

    private void refreshList(List<HostSession.Entry> list, MatchConfig cfg) {
        StringBuilder sig = new StringBuilder(cfg.mode.name() + cfg.mapId + cfg.bots);
        for (HostSession.Entry e : list) sig.append(e.id).append(e.team).append(e.name);
        if (sig.toString().equals(lastSig)) return;
        lastSig = sig.toString();
        String map = BrigadaJatoGame.loadMap(cfg.mapId).name;
        if (!I18n.pt) map = BrigadaJatoGame.loadMap(cfg.mapId).nameEn;
        info.setText((I18n.pt ? cfg.mode.pt : cfg.mode.en) + "  •  " + map + "  •  " + I18n.t("bots") + ": " + cfg.bots + "  •  " + I18n.t("diff" + cfg.difficulty));
        players.clearChildren();
        boolean teams = cfg.mode.teams && cfg.mode != ModeType.SURVIVAL && cfg.mode != ModeType.TRAINING;
        for (HostSession.Entry e : list) {
            Table row = new Table(skin);
            row.setBackground("panel");
            row.add(new SoldierPreview(game, e.look)).size(48, 58).padRight(10);
            Label n = label(e.name + (e.host ? "  (" + I18n.t("host") + ")" : ""), "default");
            row.add(n).expandX().left();
            if (teams) row.add(label(e.team == C.TEAM_RED ? I18n.t("team_red") : I18n.t("team_blue"), e.team == C.TEAM_RED ? "yellow" : "default")).padRight(14);
            players.add(row).width(620).height(66).pad(3).row();
        }
    }

    @Override
    public void render(float delta) {
        if (host != null) {
            host.updateLobby();
            while (host.notices.size > 0) status.setText(host.notices.removeIndex(0));
            refreshList(host.entries(), host.cfg);
            // teste automatico no PC: --autostart=N inicia quando N clientes entrarem
            String auto = System.getProperty("brigadajato.autostart");
            if (auto != null && host.entries().size() > Integer.parseInt(auto)) {
                startMatch();
                return;
            }
        } else if (client != null) {
            client.updateLobby();
            if (client.net.state == NetClient.State.CONNECTED) {
                if (client.myId >= 0) status.setText(I18n.t("waiting_host"));
                refreshList(client.lobby, client.cfg);
            } else if (client.net.state == NetClient.State.FAILED) {
                client.close();
                go(new MultiplayerScreen(game, I18n.t("conn_failed") + " (" + client.net.host + ")"));
                return;
            } else if (client.net.state == NetClient.State.CLOSED) {
                go(new MultiplayerScreen(game, I18n.t("disconnected")));
                return;
            }
            if (client.kicked != null) {
                client.close();
                go(new MultiplayerScreen(game, I18n.t("conn_failed") + " (" + client.kicked + ")"));
                return;
            }
            if (client.startedWorld != null) {
                go(GameScreen.client(game, client));
                return;
            }
        }
        super.render(delta);
    }

    @Override
    protected void onBack() {
        if (host != null) {
            host.close();
            game.platform.setMulticastLock(false);
        }
        if (client != null) client.close();
        go(new MultiplayerScreen(game));
    }
}
