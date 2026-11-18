package com.brigadajato;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.ObjectMap;

/** Medicao de tempo por etapa (so com --perf=true no PC): termina o trabalho da GPU a cada marca. */
public final class Perf {
    private Perf() {
    }

    public static final boolean ON = "true".equals(System.getProperty("brigadajato.perfgpu"));
    private static final ObjectMap<String, long[]> T = new ObjectMap<>();
    private static long last;

    public static void start(Batch b) {
        if (!ON) return;
        if (b instanceof com.badlogic.gdx.graphics.g2d.BJCountingBatch) ((com.badlogic.gdx.graphics.g2d.BJCountingBatch) b).sprites = 0;
        Gdx.gl.glFinish();
        last = System.nanoTime();
    }

    public static void mark(Batch b, String name) {
        if (!ON) return;
        if (b != null && b.isDrawing()) b.flush();
        if (b instanceof com.badlogic.gdx.graphics.g2d.BJCountingBatch) {
            com.badlogic.gdx.graphics.g2d.BJCountingBatch cb = (com.badlogic.gdx.graphics.g2d.BJCountingBatch) b;
            long[] sv = T.get(name + "#");
            if (sv == null) T.put(name + "#", sv = new long[2]);
            sv[0] += cb.sprites * 1000000L;
            cb.sprites = 0;
        }
        Gdx.gl.glFinish();
        long now = System.nanoTime();
        long[] v = T.get(name);
        if (v == null) T.put(name, v = new long[2]);
        v[0] += now - last;
        v[1]++;
        last = now;
    }

    public static String report(long frames) {
        StringBuilder sb = new StringBuilder();
        for (ObjectMap.Entry<String, long[]> e : T) sb.append(e.key).append('=').append(String.format("%.2f", e.value[0] / 1e6 / Math.max(1, frames))).append(' ');
        T.clear();
        return sb.toString();
    }
}
