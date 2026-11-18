package com.brigadajato;

import java.util.HashMap;
import java.util.Locale;

/** Textos do jogo em portugues e ingles. */
public final class I18n {
    private I18n() {
    }

    /** true = portugues, false = ingles */
    public static boolean pt = true;
    private static final HashMap<String, String[]> T = new HashMap<>();

    private static void d(String key, String ptText, String enText) {
        T.put(key, new String[]{ptText, enText});
    }

    public static String t(String key) {
        if (key == null) return "";
        String[] v = T.get(key);
        if (v == null) return key;
        return pt ? v[0] : v[1];
    }

    public static String f(String key, Object... args) {
        try {
            return String.format(Locale.ROOT, t(key), args);
        } catch (Exception e) {
            return t(key);
        }
    }

    static {
        // ---- menu
        d("title", "BRIGADA JATO", "BRIGADA JATO");
        d("subtitle", "tiroteio com jetpack", "jetpack shootout");
        d("play", "Jogar contra Bots", "Play vs Bots");
        d("multiplayer", "Multiplayer Wi-Fi", "Wi-Fi Multiplayer");
        d("training", "Treino", "Training");
        d("soldier", "Meu Soldado", "My Soldier");
        d("settings", "Configurações", "Settings");
        d("quit", "Sair", "Quit");
        d("back", "Voltar", "Back");
        d("start", "Começar", "Start");
        d("mode", "Modo", "Mode");
        d("map", "Mapa", "Map");
        d("bots", "Bots", "Bots");
        d("allies", "Aliados (bots)", "Allies (bots)");
        d("difficulty", "Dificuldade", "Difficulty");
        d("diff0", "Fácil", "Easy");
        d("diff1", "Normal", "Normal");
        d("diff2", "Difícil", "Hard");
        d("time_limit", "Tempo", "Time");
        d("score_limit", "Placar para vencer", "Score to win");
        d("rounds_limit", "Rodadas para vencer", "Rounds to win");
        d("none", "Sem limite", "No limit");
        d("your_team", "Seu time", "Your team");
        d("auto", "Automático", "Auto");
        d("friendly_fire", "Fogo amigo", "Friendly fire");
        d("min", "min", "min");
        d("match_setup", "Configurar Partida", "Match Setup");

        // ---- multiplayer
        d("create_room", "Criar Sala", "Create Room");
        d("join_room", "Entrar numa Sala", "Join Room");
        d("searching", "Procurando salas na rede Wi-Fi...", "Searching rooms on Wi-Fi...");
        d("no_rooms", "Nenhuma sala encontrada. Confira se estão no mesmo Wi-Fi/hotspot.", "No rooms found. Make sure you are on the same Wi-Fi/hotspot.");
        d("ip_address", "IP do anfitrião", "Host IP");
        d("connect", "Conectar", "Connect");
        d("connecting", "Conectando...", "Connecting...");
        d("conn_failed", "Não foi possível conectar.", "Could not connect.");
        d("disconnected", "Desconectado do anfitrião.", "Disconnected from host.");
        d("lobby", "Sala de Espera", "Lobby");
        d("players", "Jogadores", "Players");
        d("host", "Anfitrião", "Host");
        d("waiting_host", "Aguardando o anfitrião começar...", "Waiting for host to start...");
        d("start_match", "Iniciar Partida", "Start Match");
        d("local_ip", "Seu IP: %s  (outros jogadores podem digitar este IP)", "Your IP: %s  (others can type this IP)");
        d("mp_hint", "Todos precisam estar no mesmo Wi-Fi ou no hotspot do anfitrião.", "Everyone must be on the same Wi-Fi or on the host's hotspot.");
        d("player_joined", "%s entrou", "%s joined");
        d("player_left", "%s saiu", "%s left");
        d("room_of", "Sala de %s", "%s's room");
        d("refresh", "Atualizar", "Refresh");
        d("room_ip", "IP DA SALA:", "ROOM IP:");
        d("other_ips", "Outros IPs:", "Other IPs:");
        d("no_wifi", "Você não está num Wi-Fi. Ligue o roteador (hotspot) do celular ou conecte no mesmo Wi-Fi.", "You are not on Wi-Fi. Turn on your phone hotspot or join the same Wi-Fi.");
        d("room_in_game", "(a partida já começou, espere acabar)", "(match already started, wait for it to end)");
        d("room_version", "(versões diferentes do jogo, atualizem os dois)", "(different game versions, update both)");
        d("change_team", "Trocar time", "Switch team");
        d("add_bot", "+ Bot", "+ Bot");
        d("remove_bot", "- Bot", "- Bot");

        // ---- soldado
        d("name", "Nome", "Name");
        d("uniform", "Uniforme", "Uniform");
        d("helmet", "Chapéu", "Hat");
        d("visor", "Óculos", "Goggles");
        d("skin", "Pele", "Skin");
        d("hat", "Estilo", "Style");
        d("hat0", "Capacete", "Helmet");
        d("hat1", "Boné", "Cap");
        d("hat2", "Bandana", "Bandana");
        d("hat3", "Boina", "Beret");
        d("hat4", "Cartola", "Top hat");
        d("hat5", "Moicano", "Mohawk");
        d("hat6", "Careca", "Bald");
        d("random", "Aleatório", "Random");
        d("stats", "Estatísticas", "Stats");
        d("stat_matches", "Partidas: %d", "Matches: %d");
        d("stat_kills", "Abates: %d", "Kills: %d");
        d("stat_deaths", "Mortes: %d", "Deaths: %d");
        d("stat_wins", "Vitórias: %d", "Wins: %d");
        d("stat_wave", "Melhor onda (sobrevivência): %d", "Best wave (survival): %d");
        d("level", "Nível %d", "Level %d");

        // ---- configuracoes
        d("language", "Idioma", "Language");
        d("lang_pt", "Português", "Português");
        d("lang_en", "English", "English");
        d("fire_mode", "Tiro no celular", "Mobile shooting");
        d("fire_auto", "Mirar = atirar", "Aim = shoot");
        d("fire_button", "Botão de tiro", "Fire button");
        d("volume_sfx", "Efeitos", "Sound FX");
        d("volume_music", "Música", "Music");
        d("vibration", "Vibrar", "Vibration");
        d("show_fps", "Mostrar FPS", "Show FPS");
        d("controls_size", "Tamanho dos controles", "Controls size");
        d("controls_opacity", "Transparência dos controles", "Controls opacity");
        d("left_handed", "Canhoto (inverter)", "Left-handed (swap)");
        d("quality", "Efeitos visuais", "Visual effects");
        d("q_low", "Leve", "Low");
        d("q_high", "Bonito", "High");
        d("damage_numbers", "Números de dano", "Damage numbers");
        d("auto_aim_help", "Mira assistida", "Aim assist");
        d("on", "Ligado", "On");
        d("off", "Desligado", "Off");

        // ---- jogo
        d("resume", "Continuar", "Resume");
        d("exit_match", "Sair da Partida", "Leave Match");
        d("paused", "Pausado", "Paused");
        d("scoreboard", "Placar", "Scoreboard");
        d("kills", "Abates", "Kills");
        d("deaths", "Mortes", "Deaths");
        d("score", "Pontos", "Score");
        d("victory", "VITÓRIA!", "VICTORY!");
        d("defeat", "DERROTA", "DEFEAT");
        d("draw", "EMPATE", "DRAW");
        d("winner", "%s venceu!", "%s wins!");
        d("team_wins", "Time %s venceu!", "%s team wins!");
        d("play_again", "Jogar de Novo", "Play Again");
        d("menu", "Menu", "Menu");
        d("respawn_in", "Renascendo em %d...", "Respawning in %d...");
        d("respawning", "RENASCENDO EM", "RESPAWNING IN");
        d("spectating", "Aguardando a próxima rodada...", "Waiting for next round...");
        d("killed_by", "Abatido por %s", "Killed by %s");
        d("you_killed", "Você abateu %s", "You killed %s");
        d("headshot", "TIRO NA CABEÇA!", "HEADSHOT!");
        d("streak", "SEQUÊNCIA DE %d!", "%d KILL STREAK!");
        d("streak3", "TRIPLO!", "TRIPLE!");
        d("streak5", "IMPARÁVEL!", "UNSTOPPABLE!");
        d("streak8", "LENDÁRIO!", "LEGENDARY!");
        d("streak12", "DEUS DA GUERRA!", "GOD OF WAR!");
        d("suicide", "se explodiu", "blew up");
        d("spikes", "caiu nos espinhos", "hit the spikes");
        d("reload", "Recarregar", "Reload");
        d("reloading", "Recarregando...", "Reloading...");
        d("grenade", "Granada", "Grenade");
        d("frag", "Granada", "Grenade");
        d("mine", "Mina", "Mine");
        d("swap", "Trocar", "Swap");
        d("melee", "Soco", "Melee");
        d("pickup", "Pegar", "Pick up");
        d("use", "Usar", "Use");
        d("zoom", "Zoom", "Zoom");
        d("buy", "Comprar", "Buy");
        d("buy_menu", "Loja (só na fase de compra)", "Shop (buy phase only)");
        d("money", "$%d", "$%d");
        d("armor", "Colete", "Armor");
        d("no_ammo", "SEM MUNIÇÃO", "NO AMMO");
        d("team_red", "VERMELHO", "RED");
        d("team_blue", "AZUL", "BLUE");
        d("time_left", "Tempo", "Time");
        d("end_time", "O tempo acabou!", "Time is up!");
        d("end_score", "Placar atingido!", "Score limit reached!");
        d("pickup_health", "+50 Vida", "+50 Health");
        d("pickup_grenades", "+Granadas", "+Grenades");
        d("pickup_armor", "+Colete", "+Armor");
        d("pickup_damage", "DANO EM DOBRO!", "DOUBLE DAMAGE!");
        d("pickup_jet", "JETPACK INFINITO!", "INFINITE JETPACK!");
        d("pickup_speed", "SUPER VELOCIDADE!", "SUPER SPEED!");
        d("you", "Você", "You");
        d("fps", "FPS %d", "FPS %d");
        d("ping", "rede", "net");

        // ---- modos
        d("mode_deathmatch", "Mata-mata", "Deathmatch");
        d("mode_team", "Times", "Team Deathmatch");
        d("mode_ctf", "Capture a Bandeira", "Capture the Flag");
        d("mode_bomb", "Plantar a Bomba", "Bomb Defusal");
        d("mode_survival", "Sobrevivência", "Survival");
        d("mode_training", "Treino", "Training");
        d("desc_deathmatch", "Todos contra todos. Quem abater mais vence.", "Free for all. Most kills wins.");
        d("desc_team", "Vermelho contra azul. O time com mais abates vence.", "Red vs blue. Team with most kills wins.");
        d("desc_ctf", "Roube a bandeira inimiga e leve até a sua base.", "Steal the enemy flag and bring it to your base.");
        d("desc_bomb", "Rodadas sem renascer. Ataque planta a bomba em A ou B, defesa desarma. Compre armas no começo da rodada.", "Rounds without respawn. Attack plants the bomb at A or B, defense defuses. Buy weapons at round start.");
        d("desc_survival", "Sobreviva a ondas de inimigos cada vez mais fortes. 3 vidas.", "Survive waves of ever stronger enemies. 3 lives.");
        d("desc_training", "Aprenda os controles e teste todas as armas.", "Learn the controls and try every weapon.");

        // ---- bandeira
        d("ctf_carry", "Você está com a bandeira! Leve até a sua base!", "You have the flag! Bring it to your base!");
        d("flag_taken", "%s pegou a bandeira %s!", "%s took the %s flag!");
        d("flag_dropped", "A bandeira %s caiu!", "The %s flag was dropped!");
        d("flag_returned", "A bandeira %s voltou para a base", "The %s flag was returned");
        d("flag_captured", "%s CAPTUROU a bandeira %s!", "%s CAPTURED the %s flag!");

        // ---- bomba
        d("bomb", "BOMBA", "BOMB");
        d("round", "Rodada", "Round");
        d("role_attack", "ATAQUE", "ATTACK");
        d("role_defend", "DEFESA", "DEFENSE");
        d("bomb_buy", "FASE DE COMPRA  %d", "BUY PHASE  %d");
        d("bomb_go", "VALENDO!", "GO GO GO!");
        d("bomb_round_win", "%s venceu a rodada!", "%s wins the round!");
        d("bomb_r_elim", "Time inimigo eliminado", "Enemy team eliminated");
        d("bomb_r_time", "O tempo acabou", "Time ran out");
        d("bomb_r_defused", "A bomba foi desarmada", "The bomb was defused");
        d("bomb_r_exploded", "A bomba explodiu", "The bomb exploded");
        d("bomb_planted", "BOMBA PLANTADA!", "BOMB PLANTED!");
        d("bomb_defuse_hint", "BOMBA PLANTADA! Segure USAR perto dela para desarmar", "BOMB PLANTED! Hold USE near it to defuse");
        d("bomb_plant_hint", "Segure USAR para plantar a bomba!", "Hold USE to plant the bomb!");
        d("bomb_carry", "Você está com a bomba! Leve até o ponto A ou B", "You have the bomb! Take it to site A or B");
        d("bomb_swap", "TROCA DE LADO!", "SWITCHING SIDES!");
        d("bomb_picked", "%s pegou a bomba", "%s picked up the bomb");
        d("bomb_dropped", "A bomba caiu!", "The bomb was dropped!");
        d("bomb_defused_by", "%s desarmou a bomba!", "%s defused the bomb!");
        d("bomb_planted_by", "%s plantou a bomba!", "%s planted the bomb!");
        d("planting", "Plantando...", "Planting...");
        d("defusing", "Desarmando...", "Defusing...");

        // ---- sobrevivencia
        d("wave", "Onda", "Wave");
        d("enemies", "Inimigos", "Enemies");
        d("lives", "Vidas", "Lives");
        d("surv_next", "Onda %d em %d...", "Wave %d in %d...");
        d("surv_wave", "ONDA %d!", "WAVE %d!");
        d("surv_cleared", "Onda %d concluída! Vida restaurada", "Wave %d cleared! Health restored");
        d("surv_lives", "%s perdeu uma vida (%d restantes)", "%s lost a life (%d left)");
        d("surv_over", "Fim de jogo! Você chegou à onda %d", "Game over! You reached wave %d");
        d("new_record", "NOVO RECORDE!", "NEW RECORD!");

        // ---- abates em sequencia
        d("kb_2", "DUPLO", "DOUBLE");
        d("kb_3", "TRIPLO", "TRIPLE");
        d("kb_4", "QUÁDRUPLO", "QUADRA");
        d("kb_5", "IMPARÁVEL", "UNSTOPPABLE");
        d("kb_6", "LENDÁRIO", "LEGENDARY");

        // ---- replays
        d("killcam", "KILLCAM", "KILLCAM");
        d("replay", "REPLAY", "REPLAY");
        d("slowmo", "CÂMERA LENTA", "SLOW MOTION");
        d("killcam_by", "%s te pegou", "%s got you");
        d("hp_left", "%d de vida", "%d HP left");
        d("tap_skip", "Toque para pular", "Tap to skip");
        d("best_plays", "MELHORES JOGADAS", "BEST PLAYS");
        d("hl_flag", "BANDEIRA CAPTURADA", "FLAG CAPTURED");
        d("hl_plant", "BOMBA PLANTADA", "BOMB PLANTED");
        d("hl_defuse", "DESARME NA HORA H", "CLUTCH DEFUSE");
        d("hl_massacre", "MASSACRE", "RAMPAGE");
        d("hl_triple", "ABATE TRIPLO", "TRIPLE KILL");
        d("hl_double", "ABATE DUPLO", "DOUBLE KILL");
        d("hl_double_boom", "EXPLOSÃO DUPLA", "DOUBLE BOOM");
        d("hl_melee", "NA PORRADA", "BEATDOWN");
        d("hl_snipe", "TIRO DE LONGE", "LONG SHOT");
        d("hl_air", "ABATE NO AR", "AIR KILL");
        d("hl_boom", "KABUM", "KABOOM");
        d("hl_kill", "BELO ABATE", "NICE KILL");

        // ---- treino
        d("dummy", "Boneco", "Dummy");
        d("tut_move", "Use o JOYSTICK ESQUERDO para andar  (PC: A / D)", "Use the LEFT JOYSTICK to walk  (PC: A / D)");
        d("tut_jet", "Empurre o joystick esquerdo para CIMA para voar com o jetpack  (PC: W / Espaço)", "Push the left joystick UP to fly with the jetpack  (PC: W / Space)");
        d("tut_shoot", "Use o JOYSTICK DIREITO para mirar e atirar  (PC: mouse)", "Use the RIGHT JOYSTICK to aim and shoot  (PC: mouse)");
        d("tut_kill", "Abata um boneco de treino!", "Take down a training dummy!");
        d("tut_grenade", "Toque no botão de GRANADA para lançar  (PC: G ou botão direito)", "Tap the GRENADE button to throw  (PC: G or right click)");
        d("tut_pickup", "Pegue uma arma no chão: chegue perto e toque em PEGAR  (PC: E)", "Pick up a weapon: get close and tap PICK UP  (PC: E)");
        d("tut_swap", "Toque na arma (embaixo à direita) para TROCAR  (PC: Q)", "Tap the weapon (bottom right) to SWAP  (PC: Q)");
        d("tut_reload", "Toque em RECARREGAR  (PC: R)", "Tap RELOAD  (PC: R)");
        d("tut_melee", "Chegue perto de um boneco e dê um SOCO  (PC: F)", "Get close to a dummy and MELEE  (PC: F)");
        d("tut_zoom", "Toque na LUPA para dar zoom  (PC: Z)", "Tap the MAGNIFIER to zoom  (PC: Z)");
        d("tut_done", "Treino concluído! Teste todas as armas à vontade.", "Training complete! Try every weapon freely.");
    }
}
