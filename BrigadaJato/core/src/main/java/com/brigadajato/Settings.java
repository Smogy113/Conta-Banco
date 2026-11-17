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
        portuguese = prefs.getBoolean("pt", true);
        aimToFire = prefs.getBoolean("aimToFire", true);
        sfxVolume = prefs.getFloat("sfx", 0.8f);
        musicVolume = prefs.getFloat("music", 0.45f);
        vibration = prefs.getBoolean("vibration", true);
        showFps = prefs.getBoolean("fps", false);
        leftHanded = prefs.getBoolean("leftHanded", false);
        highQuality = prefs.getBoolean("hq", true);
        damageNumbers = prefs.getBoolean("dmgNumbers", true);
        aimAssist = prefs.getBoolean("aimAssist", true);
        controlsScale = prefs.getFloat("ctrlScale", 1f);
        controlsOpacity = prefs.getFloat("ctrlOpacity", 0.55f);
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
        prefs.putBoolean("pt", portuguese);
        prefs.putBoolean("aimToFire", aimToFire);
        prefs.putFloat("sfx", sfxVolume);
        prefs.putFloat("music", musicVolume);
        prefs.putBoolean("vibration", vibration);
        prefs.putBoolean("fps", showFps);
        prefs.putBoolean("leftHanded", leftHanded);
        prefs.putBoolean("hq", highQuality);
        prefs.putBoolean("dmgNumbers", damageNumbers);
        prefs.putBoolean("aimAssist", aimAssist);
        prefs.putFloat("ctrlScale", controlsScale);
        prefs.putFloat("ctrlOpacity", controlsOpacity);
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

    public int level() {
        return 1 + (int) Math.sqrt(xp / 100f);
    }
}
