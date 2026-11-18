package com.badlogic.gdx.graphics.g2d;

/** So para medicao de desempenho (--perf=true no PC): conta quantos sprites foram desenhados. */
public class BJCountingBatch extends SpriteBatch {
    public long sprites;

    public BJCountingBatch(int size) {
        super(size);
    }

    @Override
    public void flush() {
        sprites += idx / 20;
        super.flush();
    }
}
