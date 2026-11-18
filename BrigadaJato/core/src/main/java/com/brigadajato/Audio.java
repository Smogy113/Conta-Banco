package com.brigadajato;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ObjectFloatMap;

/** Toca sons com volume/pan pela distancia ate a camera. */
public class Audio {
    private final Assets assets;
    private final Settings settings;
    private final ObjectFloatMap<String> lastPlay = new ObjectFloatMap<>();
    private float time;
    public float listenerX, listenerY;
    private long jetId = -1;
    private Sound jetSound;
    private boolean musicOn;
    /** sem som (usado na partida de fundo do menu) */
    public boolean muted;

    public Audio(Assets assets, Settings settings) {
        this.assets = assets;
        this.settings = settings;
    }

    public void update(float dt) {
        time += dt;
    }

    /** Som de interface (sem posicao). */
    public void ui(String name) {
        play(name, Float.NaN, 0, 1f, 1f);
    }

    public void at(String name, float x, float y) {
        play(name, x, y, 1f, 1f);
    }

    /** volume extra (replays tocam mais baixo) */
    public float volumeScale = 1f;
    /** tom (camera lenta deixa o som mais grave) */
    public float pitchScale = 1f;
    private final float[] recent = new float[10];
    private int recentIdx;

    public void play(String name, float x, float y, float vol, float pitch) {
        if (muted || settings.sfxVolume <= 0) return;
        Sound s = assets.sound(name);
        if (s == null) return;
        float last = lastPlay.get(name, -1f);
        if (time - last < 0.035f) return;
        // limite global: muitos sons no mesmo instante engasgam o Android
        int busy = 0;
        for (float r : recent) if (time - r < 0.1f) busy++;
        if (busy >= recent.length && !Float.isNaN(x)) {
            float dx = x - listenerX, dy = y - listenerY;
            if (dx * dx + dy * dy > 300 * 300) return;
        }
        recent[recentIdx] = time;
        recentIdx = (recentIdx + 1) % recent.length;
        lastPlay.put(name, time);
        vol *= volumeScale;
        float v = settings.sfxVolume * vol, pan = 0;
        if (!Float.isNaN(x)) {
            float dx = x - listenerX, dy = y - listenerY;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float att = MathUtils.clamp(1f - d / 1500f, 0f, 1f);
            if (att <= 0.02f) return;
            v *= 0.25f + 0.75f * att * att;
            pan = MathUtils.clamp(dx / 800f, -0.8f, 0.8f);
        }
        s.play(MathUtils.clamp(v, 0, 1), MathUtils.clamp(pitch * pitchScale * MathUtils.random(0.94f, 1.06f), 0.5f, 2f), pan);
    }

    /** Loop do jetpack do jogador local. */
    public void jetpack(boolean on) {
        if (jetSound == null) jetSound = assets.sound("jetpack");
        if (jetSound == null) return;
        if (on && settings.sfxVolume > 0) {
            if (jetId == -1) jetId = jetSound.loop(settings.sfxVolume * 0.45f, 1f, 0);
        } else if (jetId != -1) {
            jetSound.stop(jetId);
            jetId = -1;
        }
    }

    private com.badlogic.gdx.audio.Music mapMusic;
    private String mapTrack;

    /** Musica do mapa (null = volta para a musica do menu). */
    public void mapMusic(String track) {
        if (track != null && !settings.mapMusic) track = null;
        if (track != null && track.equals(mapTrack) && mapMusic != null) {
            mapMusic.setVolume(settings.musicVolume * 0.8f);
            if (!mapMusic.isPlaying() && settings.musicVolume > 0.01f) mapMusic.play();
            return;
        }
        if (mapMusic != null) {
            mapMusic.stop();
            mapMusic.dispose();
            mapMusic = null;
            mapTrack = null;
        }
        if (track == null) return;
        if (assets.music != null && assets.music.isPlaying()) assets.music.pause();
        try {
            mapMusic = Gdx.audio.newMusic(Gdx.files.internal("music/" + track + ".ogg"));
            mapMusic.setLooping(true);
            mapMusic.setVolume(settings.musicVolume * 0.8f);
            if (settings.musicVolume > 0.01f && !muted) mapMusic.play();
            mapTrack = track;
        } catch (Exception e) {
            Gdx.app.error("Audio", "musica do mapa nao carregou: " + track, e);
        }
    }

    public static String trackFor(com.brigadajato.world.Theme t) {
        switch (t) {
            case DESERT: return "desert";
            case FACTORY: return "factory";
            case ICE: return "ice";
            case JUNGLE: return "jungle";
            case SPACE: return "space";
            case SHIP: case HOLD: return "ship";
            case CITY: case SEWER: return "city";
            case VOLCANO: case MAGMA: return "volcano";
            case UNDERSEA: case REACTOR: return "undersea";
            case SCHOOL: case GYM: return "school";
            case MARKET: case CELLAR: return "market";
            default: return "sky";
        }
    }

    public void music(boolean on) {
        if (on && mapMusic != null) mapMusic(null);
        if (assets.music == null) return;
        assets.music.setVolume(settings.musicVolume);
        if (on && settings.musicVolume > 0.01f) {
            if (!assets.music.isPlaying()) assets.music.play();
        } else if (assets.music.isPlaying()) assets.music.pause();
        musicOn = on;
    }

    public void refreshMusic() {
        if (mapMusic != null) {
            mapMusic.setVolume(settings.musicVolume * 0.8f);
            return;
        }
        music(musicOn);
    }

    public void vibrate(int ms) {
        vibrate(ms, 0.6f);
    }

    /** Vibra com forca de 0 a 1 (multiplicada pela intensidade escolhida nas configuracoes). */
    public void vibrate(int ms, float strength) {
        int lvl = settings.vibeLevel;
        if (lvl <= 0 || muted || volumeScale < 1f) return;
        float mul = lvl == 1 ? 0.45f : (lvl == 2 ? 0.75f : 1f);
        int amp = Math.max(1, Math.min(255, Math.round(255 * Math.min(1f, strength) * mul)));
        int dur = Math.max(8, Math.round(ms * (lvl == 1 ? 0.7f : (lvl == 3 ? 1.3f : 1f))));
        try {
            Gdx.input.vibrate(dur, amp, true);
        } catch (Throwable e) {
            try {
                Gdx.input.vibrate(dur);
            } catch (Throwable ignored) {
            }
        }
    }
}
