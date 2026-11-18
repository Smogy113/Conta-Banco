package com.brigadajato;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;

/** Preferencias salvas no aparelho. */
public class Settings {
    private final Preferences prefs;

    public String name = "Soldado";
    public final Look look = new Look();
    public boolean portuguese = true;
    /** true = joystick direito atira ao empurrar ate a borda; false = botao de tiro separado */
    public boolean aimToFire = true;
    public float sfxVolume = 0.8f, musicVolume = 0.45f;
    public boolean vibration = true, showFps = false, leftHanded = false, highQuality = true, damageNumbers = true, aimAssist = true;
    public float controlsScale = 1f, controlsOpacity = 0.55f;

    // ---- visual
    /** 0 baixo, 1 medio, 2 alto, 3 ultra */
    public int quality = 2;
    public float particles = 1f;
    /** 0 vermelho, 1 tinta verde, 2 sem sangue */
    public int blood = 0;
    public float shake = 1f;
    /** 0.8 = camera perto, 1.3 = longe */
    public float cameraZoom = 1f;
    public float hudScale = 1f;
    public int crosshairStyle = 0, crosshairColor = 0;
    public boolean minimap = true, showNames = true, ambient = true, killcam = true, highlights = true, killBanner = true;
    /** 0 = 30 fps, 1 = 60 fps, 2 = sem limite */
    public int fpsLimit = 1;
    /** 0 normal, 1 vibrante, 2 retro, 3 noir, 4 sepia, 5 noturno, 6 quente */
    public int colorFilter = 0;
    /** espessura do contorno dos personagens (0..3) */
    public int outline = 2;
    /** 0 sempre dia, 1 ciclo dia/noite, 2 sempre noite */
    public int dayNight = 1;
    /** luzes dinamicas (explosoes, lampadas, tiros iluminam o escuro) */
    public boolean dynamicLights = true;
    /** musica diferente em cada mapa */
    public boolean mapMusic = true;
    /** posicoes personalizadas dos botoes (x,y normalizados por botao; vazio = padrao) */
    public String controlLayout = "";
    public static final int[] CROSSHAIR_COLORS = {0xffffff, 0x76ff03, 0xff1744, 0x00e5ff, 0xffea00, 0xff4081};
    public final MatchConfig lastMatch = new MatchConfig();

    // estatisticas
    public int matches, totalKills, totalDeaths, wins, bestWave, xp;

    public Settings() {
        prefs = Gdx.app.getPreferences("brigadajato");
        load();
    }

    public void load() {
        name = prefs.getString("name", "Soldado");
        look.body = prefs.getInteger("look.body", look.body);
        look.helmet = prefs.getInteger("look.helmet", look.helmet);
        look.visor = prefs.getInteger("look.visor", look.visor);
        look.skin = prefs.getInteger("look.skin", look.skin);
        look.hat = prefs.getInteger("look.hat", look.hat);
        look.hair = prefs.getInteger("look.hair", look.hair);
        look.hairColor = prefs.getInteger("look.hairColor", look.hairColor);
        look.eyes = prefs.getInteger("look.eyes", look.eyes);
        look.mouth = prefs.getInteger("look.mouth", look.mouth);
        look.beard = prefs.getInteger("look.beard", look.beard);
        look.eyewear = prefs.getInteger("look.eyewear", look.eyewear);
        look.pattern = prefs.getInteger("look.pattern", look.pattern);
        look.patternColor = prefs.getInteger("look.patternColor", look.patternColor);
        look.pants = prefs.getInteger("look.pants", look.pants);
        look.jetpack = prefs.getInteger("look.jetpack", look.jetpack);
        look.jetColor = prefs.getInteger("look.jetColor", look.jetColor);
        look.flame = prefs.getInteger("look.flame", look.flame);
        look.gunSkin = prefs.getInteger("look.gunSkin", look.gunSkin);
        look.accessory = prefs.getInteger("look.accessory", look.accessory);
        look.accessoryColor = prefs.getInteger("look.accessoryColor", look.accessoryColor);
        portuguese = prefs.getBoolean("pt", true);
        aimToFire = prefs.getBoolean("aimToFire", true);
        sfxVolume = prefs.getFloat("sfx", 0.8f);
        musicVolume = prefs.getFloat("music", 0.45f);
        dayNight = prefs.getInteger("v.daynight", 1);
        dynamicLights = prefs.getBoolean("v.lights", true);
        mapMusic = prefs.getBoolean("mapmusic", true);
        controlLayout = prefs.getString("ctl.layout", "");
        vibration = prefs.getBoolean("vibration", true);
        showFps = prefs.getBoolean("fps", false);
        leftHanded = prefs.getBoolean("leftHanded", false);
        highQuality = prefs.getBoolean("hq", true);
        damageNumbers = prefs.getBoolean("dmgNumbers", true);
        aimAssist = prefs.getBoolean("aimAssist", true);
        controlsScale = prefs.getFloat("ctrlScale", 1f);
        controlsOpacity = prefs.getFloat("ctrlOpacity", 0.55f);
        quality = prefs.getInteger("v.quality", 2);
        particles = prefs.getFloat("v.particles", 1f);
        blood = prefs.getInteger("v.blood", 0);
        shake = prefs.getFloat("v.shake", 1f);
        cameraZoom = prefs.getFloat("v.zoom", 1f);
        hudScale = prefs.getFloat("v.hud", 1f);
        crosshairStyle = prefs.getInteger("v.cross", 0);
        crosshairColor = prefs.getInteger("v.crossColor", 0);
        minimap = prefs.getBoolean("v.minimap", true);
        showNames = prefs.getBoolean("v.names", true);
        ambient = prefs.getBoolean("v.ambient", true);
        killcam = prefs.getBoolean("v.killcam", true);
        highlights = prefs.getBoolean("v.highlights", true);
        killBanner = prefs.getBoolean("v.killBanner", true);
        fpsLimit = prefs.getInteger("v.fps", 1);
        colorFilter = prefs.getInteger("v.filter", 0);
        outline = prefs.getInteger("v.outline", 2);
        highQuality = quality >= 2;
        lastMatch.mode = ModeType.of(prefs.getInteger("m.mode", 0));
        lastMatch.mapId = prefs.getString("m.map", "fortaleza");
        lastMatch.bots = prefs.getInteger("m.bots", 5);
        lastMatch.difficulty = prefs.getInteger("m.diff", 1);
        lastMatch.timeLimit = prefs.getInteger("m.time", 300);
        lastMatch.scoreLimit = prefs.getInteger("m.score", 20);
        lastMatch.friendlyFire = prefs.getBoolean("m.ff", false);
        lastMatch.playerTeam = prefs.getInteger("m.team", 0);
        matches = prefs.getInteger("s.matches", 0);
        totalKills = prefs.getInteger("s.kills", 0);
        totalDeaths = prefs.getInteger("s.deaths", 0);
        wins = prefs.getInteger("s.wins", 0);
        bestWave = prefs.getInteger("s.wave", 0);
        xp = prefs.getInteger("s.xp", 0);
        I18n.pt = portuguese;
    }

    public void save() {
        prefs.putString("name", name);
        prefs.putInteger("look.body", look.body);
        prefs.putInteger("look.helmet", look.helmet);
        prefs.putInteger("look.visor", look.visor);
        prefs.putInteger("look.skin", look.skin);
        prefs.putInteger("look.hat", look.hat);
        prefs.putInteger("look.hair", look.hair);
        prefs.putInteger("look.hairColor", look.hairColor);
        prefs.putInteger("look.eyes", look.eyes);
        prefs.putInteger("look.mouth", look.mouth);
        prefs.putInteger("look.beard", look.beard);
        prefs.putInteger("look.eyewear", look.eyewear);
        prefs.putInteger("look.pattern", look.pattern);
        prefs.putInteger("look.patternColor", look.patternColor);
        prefs.putInteger("look.pants", look.pants);
        prefs.putInteger("look.jetpack", look.jetpack);
        prefs.putInteger("look.jetColor", look.jetColor);
        prefs.putInteger("look.flame", look.flame);
        prefs.putInteger("look.gunSkin", look.gunSkin);
        prefs.putInteger("look.accessory", look.accessory);
        prefs.putInteger("look.accessoryColor", look.accessoryColor);
        prefs.putBoolean("pt", portuguese);
        prefs.putBoolean("aimToFire", aimToFire);
        prefs.putFloat("sfx", sfxVolume);
        prefs.putFloat("music", musicVolume);
        prefs.putInteger("v.daynight", dayNight);
        prefs.putBoolean("v.lights", dynamicLights);
        prefs.putBoolean("mapmusic", mapMusic);
        prefs.putString("ctl.layout", controlLayout);
        prefs.putBoolean("vibration", vibration);
        prefs.putBoolean("fps", showFps);
        prefs.putBoolean("leftHanded", leftHanded);
        prefs.putBoolean("hq", highQuality);
        prefs.putBoolean("dmgNumbers", damageNumbers);
        prefs.putBoolean("aimAssist", aimAssist);
        prefs.putFloat("ctrlScale", controlsScale);
        prefs.putFloat("ctrlOpacity", controlsOpacity);
        prefs.putInteger("v.quality", quality);
        prefs.putFloat("v.particles", particles);
        prefs.putInteger("v.blood", blood);
        prefs.putFloat("v.shake", shake);
        prefs.putFloat("v.zoom", cameraZoom);
        prefs.putFloat("v.hud", hudScale);
        prefs.putInteger("v.cross", crosshairStyle);
        prefs.putInteger("v.crossColor", crosshairColor);
        prefs.putBoolean("v.minimap", minimap);
        prefs.putBoolean("v.names", showNames);
        prefs.putBoolean("v.ambient", ambient);
        prefs.putBoolean("v.killcam", killcam);
        prefs.putBoolean("v.highlights", highlights);
        prefs.putBoolean("v.killBanner", killBanner);
        prefs.putInteger("v.fps", fpsLimit);
        prefs.putInteger("v.filter", colorFilter);
        prefs.putInteger("v.outline", outline);
        highQuality = quality >= 2;
        prefs.putInteger("m.mode", lastMatch.mode.ordinal());
        prefs.putString("m.map", lastMatch.mapId);
        prefs.putInteger("m.bots", lastMatch.bots);
        prefs.putInteger("m.diff", lastMatch.difficulty);
        prefs.putInteger("m.time", lastMatch.timeLimit);
        prefs.putInteger("m.score", lastMatch.scoreLimit);
        prefs.putBoolean("m.ff", lastMatch.friendlyFire);
        prefs.putInteger("m.team", lastMatch.playerTeam);
        prefs.putInteger("s.matches", matches);
        prefs.putInteger("s.kills", totalKills);
        prefs.putInteger("s.deaths", totalDeaths);
        prefs.putInteger("s.wins", wins);
        prefs.putInteger("s.wave", bestWave);
        prefs.putInteger("s.xp", xp);
        prefs.flush();
        I18n.pt = portuguese;
    }

    /** Aplica o limite de FPS escolhido. */
    public void applyFps() {
        try {
            Gdx.graphics.setForegroundFPS(fpsLimit == 0 ? 30 : (fpsLimit == 1 ? 60 : 0));
        } catch (Throwable ignored) {
        }
    }

    public int crosshairRgb() {
        return CROSSHAIR_COLORS[Math.max(0, Math.min(CROSSHAIR_COLORS.length - 1, crosshairColor))];
    }

    public int level() {
        return 1 + (int) Math.sqrt(xp / 100f);
    }
}
