package com.brigadajato.world.modes;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Look;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Names;
import com.brigadajato.world.Player;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Ondas de inimigos cada vez mais fortes. Humanos (time vermelho) tem vidas limitadas. */
public class SurvivalMode extends GameMode {
    public static final int BREAK = 0, FIGHT = 1;
    public int wave = 0;
    public int state = BREAK;
    public float timer = 4f;
    public int toSpawn;
    public int enemiesAlive;
    private float spawnTimer;
    private final Array<Player> deadEnemies = new Array<>();
    private final Array<Float> deadTimes = new Array<>();
    private int enemySeq;

    public SurvivalMode(World w) {
        super(w, ModeType.SURVIVAL);
    }

    @Override
    public void init() {
        for (int i32_ = 0; i32_ < w.players.size; i32_++) {
            Player p = w.players.get(i32_);
            p.team = C.TEAM_RED;
            p.lives = 3;
        }
    }

    private boolean isEnemy(Player p) {
        return p.team == C.TEAM_BLUE;
    }

    @Override
    public boolean canRespawn(Player p) {
        if (over) return false;
        if (isEnemy(p)) return false;
        return p.lives > 0;
    }

    @Override
    public float respawnDelay(Player p) {
        return 5f;
    }

    @Override
    public void onKill(Player victim, Player attacker) {
        if (isEnemy(victim)) {
            deadEnemies.add(victim);
            deadTimes.add(w.time);
            if (attacker != null) attacker.score += 1 + wave / 3;
        } else {
            victim.lives--;
            if (victim.lives > 0) w.message("surv_lives", victim.id, victim.lives, 1);
        }
    }

    @Override
    protected Array<Vector2> spawnList(Player p) {
        return w.map.spawnsFree;
    }

    @Override
    public Vector2 chooseSpawn(Player p) {
        if (!isEnemy(p)) {
            // humanos nascem perto dos aliados vivos
            Vector2 best = w.map.spawnsFree.first();
            float bd = 1e9f;
            for (int i33_ = 0; i33_ < w.map.spawnsFree.size; i33_++) {
                Vector2 s = w.map.spawnsFree.get(i33_);
                float md = 1e9f;
                for (int i34_ = 0; i34_ < w.players.size; i34_++) {
                    Player q = w.players.get(i34_);
                    if (!q.alive || q == p) continue;
                    float dx = q.x - s.x, dy = q.y - s.y;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (isEnemy(q)) md = Math.min(md, 1e6f - d); // longe de inimigos
                    else md = Math.min(md, d);
                }
                if (md < bd) {
                    bd = md;
                    best = s;
                }
            }
            return best;
        }
        // inimigos nascem o mais longe possivel dos humanos
        Array<Vector2> all = new Array<>();
        all.addAll(w.map.spawnsFree);
        all.addAll(w.map.spawnsRed);
        all.addAll(w.map.spawnsBlue);
        Vector2 best = all.first();
        float bs = -1;
        for (int i35_ = 0; i35_ < all.size; i35_++) {
            Vector2 s = all.get(i35_);
            float md = 5000;
            for (int i36_ = 0; i36_ < w.players.size; i36_++) {
                Player q = w.players.get(i36_);
                if (!q.alive || isEnemy(q)) continue;
                float dx = q.x - s.x, dy = q.y - s.y;
                md = Math.min(md, (float) Math.sqrt(dx * dx + dy * dy));
            }
            float sc = md + w.rng.nextFloat() * 400;
            if (sc > bs) {
                bs = sc;
                best = s;
            }
        }
        return best;
    }

    @Override
    public void giveLoadout(Player p) {
        if (!isEnemy(p)) {
            p.giveDefaultLoadout(w.rng);
            return;
        }
        p.guns[1] = new Gun(WeaponType.PISTOL);
        int maxTier = Math.min(3, wave / 3);
        WeaponType t;
        int guard = 0;
        do {
            t = WeaponType.random(w.rng);
            guard++;
        } while (t.tier > maxTier && guard < 30);
        if (t.tier > maxTier) t = WeaponType.SMG;
        p.guns[0] = new Gun(t);
        p.slot = 0;
        p.frags = wave >= 4 ? 1 : 0;
        p.mines = 0;
        p.armor = Math.min(100, Math.max(0, (wave - 3) * 15));
        p.spawnShield = 0.5f;
    }

    @Override
    public float damageMul(Player victim, Player attacker) {
        if (isEnemy(victim)) return 1f / (1f + 0.05f * Math.max(0, wave - 1));
        return 1f;
    }

    @Override
    public void update(float dt) {
        if (over) return;
        // remove corpos de inimigos
        for (int i = deadEnemies.size - 1; i >= 0; i--) {
            if (w.time - deadTimes.get(i) > 2.5f) {
                w.removePlayer(deadEnemies.get(i).id);
                deadEnemies.removeIndex(i);
                deadTimes.removeIndex(i);
            }
        }
        enemiesAlive = 0;
        for (int i37_ = 0; i37_ < w.players.size; i37_++) {
            Player p = w.players.get(i37_); if (isEnemy(p) && p.alive) enemiesAlive++;
        }
        boolean humansLeft = false;
        for (int i38_ = 0; i38_ < w.players.size; i38_++) {
            Player p = w.players.get(i38_); if (!isEnemy(p) && (p.alive || p.lives > 0)) humansLeft = true;
        }
        if (!humansLeft) {
            over = true;
            winnerTeam = C.TEAM_BLUE;
            endReason = "surv_over";
            return;
        }
        timer -= dt;
        if (state == BREAK) {
            if (timer <= 0) startWave();
        } else {
            spawnTimer -= dt;
            int maxAlive = Math.min(12, 3 + wave);
            if (toSpawn > 0 && spawnTimer <= 0 && enemiesAlive < maxAlive) {
                spawnEnemy();
                spawnTimer = Math.max(0.4f, 1.6f - wave * 0.1f);
            }
            if (toSpawn == 0 && enemiesAlive == 0 && w.bosses.size == 0) {
                state = BREAK;
                timer = 7f;
                for (int i39_ = 0; i39_ < w.players.size; i39_++) {
                    Player p = w.players.get(i39_);
                    if (isEnemy(p)) continue;
                    if (p.alive) {
                        p.health = C.HEALTH_MAX;
                        for (Gun g : p.guns) if (g != null) g.reserve = Math.max(g.reserve, g.type.reserve);
                        p.frags = Math.max(p.frags, 2);
                    } else if (p.lives <= 0) {
                        p.lives = 1; // quem morreu volta na proxima onda
                        p.respawnTimer = 1;
                    }
                }
                w.message("surv_cleared", wave, -1, 2);
            }
        }
    }

    private void startWave() {
        wave++;
        state = FIGHT;
        toSpawn = 3 + wave * 2;
        spawnTimer = 0.5f;
        WorldEvent e = w.emit(WorldEvent.Type.WAVE);
        e.a = wave;
        if (wave % 5 == 0) {
            // onda de chefao: helicoptero, robo gigante, depois os dois juntos
            int bossWave = wave / 5;
            float hp = 1400 + wave * 90 + 250 * Math.max(0, humans() - 1);
            if (bossWave % 3 == 1) w.spawnBoss(com.brigadajato.world.Boss.HELI, hp);
            else if (bossWave % 3 == 2) w.spawnBoss(com.brigadajato.world.Boss.ROBOT, hp * 1.2f);
            else {
                w.spawnBoss(com.brigadajato.world.Boss.HELI, hp * 0.8f);
                w.spawnBoss(com.brigadajato.world.Boss.ROBOT, hp);
            }
            toSpawn = 2 + wave / 2;
            w.message("surv_boss", wave, -1, 1);
        }
    }

    private int humans() {
        int n = 0;
        for (int i = 0; i < w.players.size; i++) if (!isEnemy(w.players.get(i))) n++;
        return n;
    }

    private void spawnEnemy() {
        toSpawn--;
        int diff = wave <= 2 ? 0 : (wave <= 5 ? 1 : 2);
        String name = Names.BOTS[(enemySeq++) % Names.BOTS.length];
        Look look = new Look(0x7b1fa2, 0x311b92, 0xff1744, Look.SKINS[w.rng.nextInt(Look.SKINS.length)], w.rng.nextInt(Look.HAT_COUNT));
        if (wave >= 5 && w.rng.nextFloat() < 0.3f) look = new Look(0x212121, 0x000000, 0xffea00, look.skin, 0);
        Player p = w.addPlayer(name, C.TEAM_BLUE, look, true);
        p.bot.setDifficulty(diff);
        w.respawn(p);
    }

    @Override
    public boolean botGoal(Player bot, Vector2 out) {
        if (!isEnemy(bot)) return false;
        // inimigos cacam o humano mais proximo
        Player best = null;
        float bd = 1e9f;
        for (int i40_ = 0; i40_ < w.players.size; i40_++) {
            Player q = w.players.get(i40_);
            if (!q.alive || isEnemy(q)) continue;
            float d = bot.distTo(q);
            if (d < bd) {
                bd = d;
                best = q;
            }
        }
        if (best == null) return false;
        out.set(best.x, best.y);
        return true;
    }

    @Override
    public String hud(Player local) {
        String s = I18n.t("wave") + " " + Math.max(1, wave) + "    " + I18n.t("enemies") + ": " + (enemiesAlive + toSpawn + w.bosses.size);
        if (local != null) s += "    " + I18n.t("lives") + ": " + Math.max(0, local.lives);
        return s;
    }

    @Override
    public String banner(Player local) {
        if (state == BREAK && timer > 0) {
            return I18n.f("surv_next", wave + 1, (int) Math.ceil(timer));
        }
        return null;
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        o.writeShort(wave);
        o.writeByte(state);
        o.writeFloat(timer);
        o.writeShort(toSpawn);
        o.writeShort(enemiesAlive);
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        wave = in.readShort();
        state = in.readByte();
        timer = in.readFloat();
        toSpawn = in.readShort();
        enemiesAlive = in.readShort();
    }
}
