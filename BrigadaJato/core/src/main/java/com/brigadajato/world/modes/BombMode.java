package com.brigadajato.world.modes;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.Gun;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Plantar a bomba (estilo tatico): rodadas sem renascer, fase de compra, um time ataca
 * (planta a bomba num dos pontos A/B) e o outro defende (desarma). Troca de lado no meio.
 */
public class BombMode extends GameMode {
    public static final int BUY = 0, LIVE = 1, PLANTED = 2, ROUND_END = 3;
    public static final int B_CARRIED = 0, B_DROPPED = 1, B_PLANTED = 2, B_DEFUSED = 3, B_EXPLODED = 4;
    public static final int EV_PLANTED = 0, EV_DEFUSED = 1, EV_EXPLODED = 2, EV_DROPPED = 3, EV_PICKED = 4, EV_BEEP = 5;

    public static final float BUY_TIME = 8f, ROUND_TIME = 100f, BOMB_TIME = 35f, END_TIME = 4.5f;
    public static final float PLANT_TIME = 3f, DEFUSE_TIME = 5f, SITE_RADIUS = 120f;
    public static final int ITEM_ARMOR = 100, ITEM_FRAG = 101, ITEM_MINE = 102, ITEM_SMOKE = 103, ITEM_FLASH = 104, ITEM_STICKY = 105;
    public static final int PRICE_ARMOR = 650, PRICE_FRAG = 300, PRICE_MINE = 400, PRICE_SMOKE = 300, PRICE_FLASH = 200, PRICE_STICKY = 400;

    public int phase = BUY;
    public float phaseTimer = BUY_TIME;
    public int round = 1;
    public int attackTeam = C.TEAM_RED;
    public int bombState = B_CARRIED;
    public int bombCarrier = -1;
    public float bombX, bombY, bombTimer;
    public int site = -1;
    public int lastRoundWinner = 0;
    public String lastRoundReason = "";
    private float beepTimer;
    private final IntArray diedThisRound = new IntArray();
    /** ponto escolhido pelos bots atacantes nesta rodada (0 = A, 1 = B) */
    private int botSite;

    public BombMode(World w) {
        super(w, ModeType.BOMB);
    }

    public int defendTeam() {
        return attackTeam == C.TEAM_RED ? C.TEAM_BLUE : C.TEAM_RED;
    }

    @Override
    public boolean usesMapItems() {
        return false;
    }

    private Vector2 siteA() {
        return w.map.hasSites ? w.map.siteA : new Vector2(w.map.worldW() / 2, w.map.worldH() * 0.7f);
    }

    private Vector2 siteB() {
        return w.map.hasSites ? w.map.siteB : new Vector2(w.map.worldW() / 2, w.map.worldH() * 0.25f);
    }

    public Vector2 sitePos(int i) {
        return i == 0 ? siteA() : siteB();
    }

    @Override
    public void init() {
        for (int i17_ = 0; i17_ < w.players.size; i17_++) {
            Player p = w.players.get(i17_); p.money = 800;
        }
        startRound(true);
    }

    @Override
    protected Array<Vector2> spawnList(Player p) {
        // como no CS: atacantes sempre na base de ataque (r), defensores perto dos bombsites (b)
        return p.team == attackTeam ? w.map.spawnsRed : w.map.spawnsBlue;
    }

    private void startRound(boolean resetGuns) {
        phase = BUY;
        phaseTimer = BUY_TIME;
        bombState = B_CARRIED;
        bombTimer = 0;
        site = -1;
        botSite = w.rng.nextInt(2);
        for (int i18_ = 0; i18_ < w.players.size; i18_++) {
            Player p = w.players.get(i18_);
            if (resetGuns || diedThisRound.contains(p.id) || !p.alive) {
                p.guns[0] = null;
                p.guns[1] = null;
            }
        }
        diedThisRound.clear();
        for (int i19_ = 0; i19_ < w.players.size; i19_++) {
            Player p = w.players.get(i19_); w.respawn(p);
        }
        // entrega a bomba para um atacante
        Array<Player> att = new Array<>();
        for (int i20_ = 0; i20_ < w.players.size; i20_++) {
            Player p = w.players.get(i20_); if (p.team == attackTeam) att.add(p);
        }
        bombCarrier = -1;
        if (att.size > 0) {
            Player c = att.get(w.rng.nextInt(att.size));
            // prefere humanos para carregar a bomba
            for (int i21_ = 0; i21_ < att.size; i21_++) {
                Player p = att.get(i21_); if (!p.isBot) c = p;
            }
            c.hasBomb = true;
            bombCarrier = c.id;
            bombX = c.x;
            bombY = c.y;
        }
        WorldEvent e = w.emit(WorldEvent.Type.ROUND);
        e.a = -1;
        e.b = round;
    }

    @Override
    public void giveLoadout(Player p) {
        if (p.guns[1] == null) p.guns[1] = new Gun(WeaponType.PISTOL);
        else {
            Gun g = p.guns[1];
            g.mag = g.type.mag;
            g.reserve = Math.max(g.reserve, g.type.reserve / 2);
        }
        if (p.guns[0] != null) {
            Gun g = p.guns[0];
            g.mag = g.type.mag;
            g.reserve = Math.max(g.reserve, g.type.reserve / 2);
        }
        p.slot = p.guns[0] != null ? 0 : 1;
        p.frags = Math.max(p.frags, 1);
        p.mines = 0;
        p.smokes = 0;
        p.flashes = 0;
        p.stickies = 0;
        p.spawnShield = 0;
        if (p.isBot) botBuy(p);
    }

    private void botBuy(Player p) {
        WeaponType[] wish = {WeaponType.LMG, WeaponType.SNIPER, WeaponType.RIFLE, WeaponType.SHOTGUN, WeaponType.SMG, WeaponType.MAGNUM};
        if (p.guns[0] == null) {
            int start = w.rng.nextInt(3);
            for (int i = start; i < wish.length; i++) {
                if (wish[i].price <= p.money) {
                    buy(p, wish[i].ordinal());
                    break;
                }
            }
        }
        if (p.armor < 50 && p.money >= PRICE_ARMOR) buy(p, ITEM_ARMOR);
        if (p.frags < 2 && p.money >= PRICE_FRAG) buy(p, ITEM_FRAG);
    }

    @Override
    public void buy(Player p, int code) {
        if (phase != BUY && phase != ROUND_END) return;
        if (code == ITEM_ARMOR) {
            if (p.money < PRICE_ARMOR || p.armor >= C.ARMOR_MAX) return;
            p.money -= PRICE_ARMOR;
            p.armor = C.ARMOR_MAX;
        } else if (code == ITEM_SMOKE || code == ITEM_FLASH || code == ITEM_STICKY) {
            int type = code == ITEM_SMOKE ? Player.NADE_SMOKE : (code == ITEM_FLASH ? Player.NADE_FLASH : Player.NADE_STICKY);
            int price = code == ITEM_SMOKE ? PRICE_SMOKE : (code == ITEM_FLASH ? PRICE_FLASH : PRICE_STICKY);
            if (p.money < price || p.nades(type) >= 2) return;
            p.money -= price;
            p.addNades(type, 1);
        } else if (code == ITEM_FRAG) {
            if (p.money < PRICE_FRAG || p.frags >= C.MAX_FRAGS) return;
            p.money -= PRICE_FRAG;
            p.frags++;
        } else if (code == ITEM_MINE) {
            if (p.money < PRICE_MINE || p.mines >= C.MAX_MINES) return;
            p.money -= PRICE_MINE;
            p.mines++;
        } else {
            WeaponType t = WeaponType.of(code);
            if (t == null || t == WeaponType.PISTOL || p.money < t.price || p.has(t)) return;
            p.money -= t.price;
            p.guns[0] = new Gun(t);
            p.slot = 0;
        }
        WorldEvent e = w.emit(WorldEvent.Type.PICKUP);
        e.a = p.id;
        e.b = -1;
        e.x = p.x;
        e.y = p.y;
    }

    @Override
    public boolean canRespawn(Player p) {
        return false;
    }

    @Override
    public float respawnDelay(Player p) {
        return 999f;
    }

    @Override
    public boolean isFrozen(Player p) {
        return over || phase == BUY;
    }

    @Override
    public float speedMul(Player p) {
        return p.actionProgress > 0 ? 0f : 1f;
    }

    @Override
    public void onKill(Player victim, Player attacker) {
        diedThisRound.add(victim.id);
        if (victim.hasBomb) {
            victim.hasBomb = false;
            bombState = B_DROPPED;
            bombCarrier = -1;
            bombX = victim.x;
            bombY = victim.y;
            event(EV_DROPPED, victim.id);
        }
        victim.guns[0] = null;
        victim.guns[1] = null;
    }

    private void event(int action, int player) {
        WorldEvent e = w.emit(WorldEvent.Type.BOMB);
        e.b = action;
        e.c = player;
        e.x = bombX;
        e.y = bombY;
    }

    private int alive(int team) {
        int n = 0;
        for (int i22_ = 0; i22_ < w.players.size; i22_++) {
            Player p = w.players.get(i22_); if (p.team == team && p.alive) n++;
        }
        return n;
    }

    private int count(int team) {
        int n = 0;
        for (int i23_ = 0; i23_ < w.players.size; i23_++) {
            Player p = w.players.get(i23_); if (p.team == team) n++;
        }
        return n;
    }

    @Override
    public void update(float dt) {
        if (over) return;
        phaseTimer -= dt;
        switch (phase) {
            case BUY:
                if (phaseTimer <= 0) {
                    phase = LIVE;
                    phaseTimer = ROUND_TIME;
                    w.message("bomb_go", -1, -1, 0);
                }
                break;
            case LIVE:
                updateCarrier(dt);
                if (count(defendTeam()) > 0 && alive(defendTeam()) == 0) endRound(attackTeam, "bomb_r_elim");
                else if (count(attackTeam) > 0 && alive(attackTeam) == 0) endRound(defendTeam(), "bomb_r_elim");
                else if (phaseTimer <= 0) endRound(defendTeam(), "bomb_r_time");
                break;
            case PLANTED:
                bombTimer -= dt;
                beepTimer -= dt;
                if (beepTimer <= 0) {
                    beepTimer = bombTimer > 10 ? 1f : (bombTimer > 4 ? 0.5f : 0.2f);
                    event(EV_BEEP, -1);
                }
                updateDefuse(dt);
                if (bombState == B_DEFUSED) {
                    endRound(defendTeam(), "bomb_r_defused");
                } else if (bombTimer <= 0) {
                    bombState = B_EXPLODED;
                    event(EV_EXPLODED, -1);
                    w.explosion(bombX, bombY + 10, 420, 320, -1, 0, C.KILL_BOMB);
                    endRound(attackTeam, "bomb_r_exploded");
                } else if (count(defendTeam()) > 0 && alive(defendTeam()) == 0) {
                    endRound(attackTeam, "bomb_r_elim");
                }
                break;
            case ROUND_END:
                if (phaseTimer <= 0) nextRound();
                break;
        }
    }

    private void updateCarrier(float dt) {
        if (bombState == B_DROPPED) {
            if (!w.map.solidAt(bombX, bombY - 2)) bombY = Math.max(0, bombY - 400 * dt);
            for (int i24_ = 0; i24_ < w.players.size; i24_++) {
                Player p = w.players.get(i24_);
                if (!p.alive || p.team != attackTeam) continue;
                float dx = p.x - bombX, dy = p.y - bombY;
                if (dx * dx + dy * dy < 36 * 36) {
                    p.hasBomb = true;
                    bombState = B_CARRIED;
                    bombCarrier = p.id;
                    event(EV_PICKED, p.id);
                    break;
                }
            }
            return;
        }
        Player c = w.player(bombCarrier);
        if (c == null || !c.alive) return;
        bombX = c.x;
        bombY = c.y;
        int s = siteAt(c.x, c.y);
        if (s >= 0 && c.input.use && c.onGround) {
            c.actionProgress += dt / PLANT_TIME;
            if (c.actionProgress >= 1f) {
                c.actionProgress = 0;
                c.hasBomb = false;
                bombState = B_PLANTED;
                bombCarrier = -1;
                site = s;
                phase = PLANTED;
                bombTimer = BOMB_TIME;
                beepTimer = 0;
                c.money += 300;
                c.score += 2;
                event(EV_PLANTED, c.id);
            }
        } else c.actionProgress = 0;
    }

    private void updateDefuse(float dt) {
        Player best = null;
        for (int i25_ = 0; i25_ < w.players.size; i25_++) {
            Player p = w.players.get(i25_);
            if (!p.alive || p.team != defendTeam()) {
                if (p.team == attackTeam) p.actionProgress = 0;
                continue;
            }
            float dx = p.x - bombX, dy = p.y - bombY;
            if (dx * dx + dy * dy < 50 * 50 && p.input.use && p.onGround) {
                if (best == null || p.actionProgress > best.actionProgress) best = p;
            } else p.actionProgress = 0;
        }
        if (best != null) {
            best.actionProgress += dt / DEFUSE_TIME;
            if (best.actionProgress >= 1f) {
                best.actionProgress = 0;
                bombState = B_DEFUSED;
                best.money += 300;
                best.score += 2;
                event(EV_DEFUSED, best.id);
            }
        }
    }

    public int siteAt(float x, float y) {
        for (int i = 0; i < 2; i++) {
            Vector2 s = sitePos(i);
            float dx = x - s.x, dy = y - s.y;
            if (dx * dx + dy * dy < SITE_RADIUS * SITE_RADIUS) return i;
        }
        return -1;
    }

    private void endRound(int winner, String reason) {
        phase = ROUND_END;
        phaseTimer = END_TIME;
        teamScore[winner]++;
        lastRoundWinner = winner;
        lastRoundReason = reason;
        for (int i26_ = 0; i26_ < w.players.size; i26_++) {
            Player p = w.players.get(i26_);
            p.money = Math.min(9000, p.money + (p.team == winner ? 2500 : 1400));
            p.actionProgress = 0;
        }
        WorldEvent e = w.emit(WorldEvent.Type.ROUND);
        e.a = winner;
        e.b = round;
        e.text = reason;
        if (w.config.scoreLimit > 0 && teamScore[winner] >= w.config.scoreLimit) {
            over = true;
            winnerTeam = winner;
            endReason = "end_score";
        }
    }

    private void nextRound() {
        int half = Math.max(1, w.config.scoreLimit - 1);
        boolean swap = round == half;
        round++;
        if (swap) {
            attackTeam = defendTeam();
            for (int i27_ = 0; i27_ < w.players.size; i27_++) {
                Player p = w.players.get(i27_); p.money = 800;
            }
            w.message("bomb_swap", -1, -1, 0);
        }
        startRound(swap);
    }

    // ---- bots
    @Override
    public boolean botGoal(Player bot, Vector2 out) {
        if (phase == BUY || phase == ROUND_END) return false;
        if (bot.team == attackTeam) {
            if (bot.hasBomb) {
                out.set(sitePos(botSite));
                return true;
            }
            if (bombState == B_DROPPED) {
                out.set(bombX, bombY);
                return true;
            }
            if (bombState == B_PLANTED) {
                out.set(bombX + (bot.id % 2 == 0 ? 120 : -120), bombY + 40);
                return true;
            }
            Player c = w.player(bombCarrier);
            if (c != null && bot.id % 2 == 0) {
                out.set(c.x, c.y);
                return true;
            }
            out.set(sitePos(botSite));
            return true;
        }
        if (bombState == B_PLANTED) {
            out.set(bombX, bombY);
            return true;
        }
        out.set(sitePos(bot.id % 2));
        return true;
    }

    @Override
    public boolean botUse(Player bot) {
        if (bot.team == attackTeam && bot.hasBomb && siteAt(bot.x, bot.y) >= 0) return true;
        if (bot.team == defendTeam() && bombState == B_PLANTED) {
            float dx = bot.x - bombX, dy = bot.y - bombY;
            return dx * dx + dy * dy < 45 * 45;
        }
        return false;
    }

    // ---- HUD
    @Override
    public String hud(Player local) {
        String clock;
        if (phase == PLANTED) clock = I18n.t("bomb") + " " + clock(bombTimer);
        else clock = clock(Math.max(0, phaseTimer));
        String role = local == null ? "" : (local.team == attackTeam ? I18n.t("role_attack") : I18n.t("role_defend")) + "    ";
        return role + I18n.t("round") + " " + round + "    " + teamScoreLine() + "    " + clock;
    }

    @Override
    public String banner(Player local) {
        if (phase == BUY) return I18n.f("bomb_buy", (int) Math.ceil(phaseTimer));
        if (phase == ROUND_END) {
            String who = lastRoundWinner == C.TEAM_RED ? I18n.t("team_red") : I18n.t("team_blue");
            return I18n.f("bomb_round_win", who) + "\n" + I18n.t(lastRoundReason);
        }
        if (phase == PLANTED) {
            if (local != null && local.team == defendTeam()) return I18n.t("bomb_defuse_hint");
            return I18n.t("bomb_planted");
        }
        if (local != null && local.hasBomb) return I18n.t(siteAt(local.x, local.y) >= 0 ? "bomb_plant_hint" : "bomb_carry");
        return null;
    }

    public boolean localRole(Player local) {
        return local != null && local.team == attackTeam;
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        o.writeByte(phase);
        o.writeFloat(phaseTimer);
        o.writeByte(round);
        o.writeByte(attackTeam);
        o.writeByte(bombState);
        o.writeShort(bombCarrier);
        o.writeFloat(bombX);
        o.writeFloat(bombY);
        o.writeFloat(bombTimer);
        o.writeByte(site);
        o.writeByte(lastRoundWinner);
        o.writeUTF(lastRoundReason);
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        phase = in.readByte();
        phaseTimer = in.readFloat();
        round = in.readByte();
        attackTeam = in.readByte();
        bombState = in.readByte();
        bombCarrier = in.readShort();
        bombX = in.readFloat();
        bombY = in.readFloat();
        bombTimer = in.readFloat();
        site = in.readByte();
        lastRoundWinner = in.readByte();
        lastRoundReason = in.readUTF();
    }
}
