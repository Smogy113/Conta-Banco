package com.brigadajato.replay;

import com.badlogic.gdx.utils.Array;
import com.brigadajato.world.WorldEvent;

/** Estado visual do mundo num instante (sem objetos: so numeros). */
public class ReplayFrame {
    public static final int PF = 12, PI = 4, RF = 9, RI = 4, KF = 4, KI = 4, MF = 8;

    public float time;
    public int np, nr, nk;
    public int[] pid;
    public float[] pf;
    public int[] pi;
    public float[] rf;
    public int[] ri;
    public float[] kf;
    public int[] ki;
    public final float[] mode = new float[MF];
    public final Array<WorldEvent> events = new Array<>(false, 8);

    public ReplayFrame(int maxP, int maxR, int maxK) {
        pid = new int[maxP];
        pf = new float[maxP * PF];
        pi = new int[maxP * PI];
        rf = new float[maxR * RF];
        ri = new int[maxR * RI];
        kf = new float[maxK * KF];
        ki = new int[maxK * KI];
    }

    public int maxP() {
        return pid.length;
    }

    public int maxR() {
        return rf.length / RF;
    }

    public int maxK() {
        return kf.length / KF;
    }

    /** Copia enxuta (arrays do tamanho exato usado). */
    public ReplayFrame compactCopy() {
        ReplayFrame c = new ReplayFrame(Math.max(1, np), Math.max(1, nr), Math.max(1, nk));
        c.time = time;
        c.np = np;
        c.nr = nr;
        c.nk = nk;
        System.arraycopy(pid, 0, c.pid, 0, np);
        System.arraycopy(pf, 0, c.pf, 0, np * PF);
        System.arraycopy(pi, 0, c.pi, 0, np * PI);
        System.arraycopy(rf, 0, c.rf, 0, nr * RF);
        System.arraycopy(ri, 0, c.ri, 0, nr * RI);
        System.arraycopy(kf, 0, c.kf, 0, nk * KF);
        System.arraycopy(ki, 0, c.ki, 0, nk * KI);
        System.arraycopy(mode, 0, c.mode, 0, MF);
        c.events.addAll(events);
        return c;
    }

    public int indexOf(int id) {
        for (int i = 0; i < np; i++) if (pid[i] == id) return i;
        return -1;
    }
}
