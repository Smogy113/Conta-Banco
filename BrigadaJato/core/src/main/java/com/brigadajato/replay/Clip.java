package com.brigadajato.replay;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntMap;
import com.brigadajato.world.Look;

/** Um trecho gravado (killcam ou melhor jogada). */
public class Clip {
    public static class Who {
        public String name;
        public Look look;
        public int team;
        public boolean dummy;
    }

    public final Array<ReplayFrame> frames = new Array<>();
    public final IntMap<Who> roster = new IntMap<>();
    /** jogador que a camera segue */
    public int focusId;
    /** momentos importantes (abates) para camera lenta */
    public final FloatArray keyTimes = new FloatArray();
    public float score;
    public String title = "", subtitle = "";
    public int kills;
    public boolean localPlay;

    public float start() {
        return frames.size > 0 ? frames.first().time : 0;
    }

    public float end() {
        return frames.size > 0 ? frames.peek().time : 0;
    }
}
