package com.brigadajato.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.Platform;

/** Versao para PC (Windows/Linux/Mac). Use WASD + mouse. */
public class DesktopLauncher {
    public static void main(String[] args) {
        // --chave=valor vira propriedade brigadajato.chave (ex.: --start=game:BOMB:fabrica:7)
        for (String a : args) {
            if (!a.startsWith("--")) continue;
            int eq = a.indexOf('=');
            String key = eq > 0 ? a.substring(2, eq) : a.substring(2);
            String val = eq > 0 ? a.substring(eq + 1) : "true";
            System.setProperty("brigadajato." + key, val);
        }
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("Brigada Jato");
        int w = Integer.getInteger("brigadajato.width", 1280), h = Integer.getInteger("brigadajato.height", 720);
        c.setWindowedMode(w, h);
        c.useVsync(true);
        c.setForegroundFPS(60);
        c.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        c.setWindowIcon("icon.png");
        new Lwjgl3Application(new BrigadaJatoGame(Platform.DESKTOP), c);
    }
}
