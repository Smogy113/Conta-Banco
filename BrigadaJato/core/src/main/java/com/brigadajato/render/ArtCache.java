package com.brigadajato.render;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Theme;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Cache das artes de fundo pintadas (as mais caras de gerar): gera em segundo plano antes de
 * precisar (mapas vizinhos no carrossel, mapa escolhido) e guarda as ultimas usadas para trocar
 * de mapa sem travar. Tudo e chamado da thread do OpenGL, menos a geracao das imagens.
 */
public final class ArtCache {
    private ArtCache() {
    }

    private static final int KEEP_FREE = 3;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "arte-fundo");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final ObjectMap<String, Future<BackdropArt>> PENDING = new ObjectMap<>();
    private static final ObjectMap<String, BackdropArt> READY = new ObjectMap<>();
    private static final ObjectMap<String, Integer> USERS = new ObjectMap<>();
    /** artes sem ninguem usando, da mais antiga para a mais nova */
    private static final Array<String> FREE = new Array<>();

    static <T> Future<T> submit(java.util.concurrent.Callable<T> job) {
        return POOL.submit(job);
    }

    private static String key(Theme t, String seedKey) {
        return t.name() + "|" + seedKey;
    }

    /** Comeca a gerar a arte de um tema em segundo plano (se ainda nao tiver). */
    public static synchronized void prefetch(final Theme t, final String seedKey) {
        final String k = key(t, seedKey);
        if (READY.containsKey(k) || PENDING.containsKey(k)) return;
        PENDING.put(k, POOL.submit(() -> new BackdropArt(t, seedKey, true)));
    }

    /** Gera em segundo plano as artes de um mapa (as duas areas, se tiver). */
    public static void prefetchMap(GameMap m) {
        TileArt.prefetch(m.theme);
        prefetch(m.theme, m.id);
        if (m.theme2 != null) {
            TileArt.prefetch(m.theme2);
            prefetch(m.theme2, m.id + "2");
        }
    }

    /** Pega a arte pronta (ou gera agora) e marca como em uso. */
    public static synchronized BackdropArt acquire(Theme t, String seedKey) {
        String k = key(t, seedKey);
        BackdropArt art = READY.get(k);
        if (art == null) {
            Future<BackdropArt> f = PENDING.remove(k);
            if (f != null) {
                try {
                    art = f.get();
                } catch (Exception e) {
                    art = null;
                }
            }
            if (art == null) art = new BackdropArt(t, seedKey, true);
            art.upload();
            art.cacheKey = k;
            READY.put(k, art);
        }
        FREE.removeValue(k, false);
        USERS.put(k, USERS.get(k, 0) + 1);
        return art;
    }

    /** Ninguem mais usa esta arte agora: fica guardada (as mais antigas sao descartadas). */
    public static synchronized void release(BackdropArt art) {
        String k = art.cacheKey;
        if (k == null || !READY.containsKey(k)) {
            art.dispose();
            return;
        }
        int n = USERS.get(k, 1) - 1;
        if (n > 0) {
            USERS.put(k, n);
            return;
        }
        USERS.remove(k);
        FREE.add(k);
        while (FREE.size > KEEP_FREE) {
            String old = FREE.removeIndex(0);
            BackdropArt a = READY.remove(old);
            if (a != null) a.dispose();
        }
    }
}
