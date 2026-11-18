package com.brigadajato.world.modes;

import com.badlogic.gdx.math.Vector2;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Look;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.Player;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Campo de treino com tutorial passo a passo e bonecos-alvo. */
public class TrainingMode extends GameMode {
    public static final String[] STEPS = {
            "tut_move", "tut_jet", "tut_shoot", "tut_kill", "tut_grenade", "tut_pickup", "tut_swap",
            "tut_reload", "tut_melee", "tut_zoom", "tut_done"};
    public int step = 0;
    private float stepTimer;
    private int killsAtStep;
    private int base;

    public TrainingMode(World w) {
        super(w, ModeType.TRAINING);
    }

    @Override
    public void init() {
        int i = 0;
        for (int i41_ = 0; i41_ < w.map.dummySpawns.size; i41_++) {
            Vector2 v = w.map.dummySpawns.get(i41_);
            Player d = w.addPlayer(I18n.t("dummy") + " " + (i + 1), C.TEAM_BLUE,
                    new Look(0xd7ccc8, 0xa1887f, 0xff5252, 0xffe0b2, 6), true);
            d.dummy = true;
            d.spawnIndex = i;
            i++;
        }
        for (int i42_ = 0; i42_ < w.players.size; i42_++) {
            Player p = w.players.get(i42_); if (!p.dummy) p.team = C.TEAM_RED;
        }
        base = 0;
    }

    @Override
    public Vector2 chooseSpawn(Player p) {
        if (p.dummy && p.spawnIndex >= 0 && p.spawnIndex < w.map.dummySpawns.size) return w.map.dummySpawns.get(p.spawnIndex);
        return w.map.spawnsFree.first();
    }

    @Override
    public void giveLoadout(Player p) {
        if (p.dummy) {
            p.guns[0] = null;
            p.guns[1] = new Gun(WeaponType.PISTOL, 0, 0);
            p.slot = 1;
            return;
        }
        p.guns[0] = new Gun(WeaponType.RIFLE);
        p.guns[1] = null;
        p.slot = 0;
        p.frags = C.MAX_FRAGS;
        p.mines = C.MAX_MINES;
    }

    @Override
    public float respawnDelay(Player p) {
        return p.dummy ? 1.5f : 1f;
    }

    @Override
    public boolean infiniteAmmo(Player p) {
        return !p.isBot;
    }

    @Override
    public void update(float dt) {
        if (step >= STEPS.length - 1) return;
        stepTimer += dt;
        boolean done = false;
        switch (step) {
            case 0: done = w.statWalk > 250; break;
            case 1: done = w.statJetTime > 1.2f; break;
            case 2: done = w.statShots > 8; break;
            case 3: done = humanKills() > killsAtStep; break;
            case 4: done = w.statGrenades > 0; break;
            case 5: done = w.statPickups > 0; break;
            case 6: done = w.statSwaps > 0; break;
            case 7: done = w.statReloads > 0; break;
            case 8: done = w.statMeleeHits > 0; break;
            case 9: done = w.statZooms > 0; break;
            default: break;
        }
        if (done && stepTimer > 1.0f) {
            step++;
            stepTimer = 0;
            killsAtStep = humanKills();
            WorldEvent e = w.emit(WorldEvent.Type.SOUND);
            e.text = "powerup";
            e.x = -1;
        }
    }

    private int humanKills() {
        int k = 0;
        for (int i43_ = 0; i43_ < w.players.size; i43_++) {
            Player p = w.players.get(i43_); if (!p.isBot) k += p.kills;
        }
        return k;
    }

    @Override
    public String hud(Player local) {
        return I18n.t("mode_training") + "  " + Math.min(step + 1, STEPS.length) + "/" + STEPS.length;
    }

    @Override
    public String banner(Player local) {
        return I18n.t(STEPS[Math.min(step, STEPS.length - 1)]);
    }

    @Override
    protected void writeExtra(DataOutput o) throws IOException {
        o.writeByte(step);
    }

    @Override
    protected void readExtra(DataInput in) throws IOException {
        step = in.readByte();
    }
}
