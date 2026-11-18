package com.brigadajato.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Filtros de cor aplicados no mundo (shader do SpriteBatch). */
public class ColorFilter implements Disposable {
    public static final String[] NAMES_PT = {"Normal", "Vibrante", "Retrô", "Noir", "Sépia", "Noturno", "Quente"};
    public static final String[] NAMES_EN = {"Normal", "Vibrant", "Retro", "Noir", "Sepia", "Night", "Warm"};

    private static final String VERT = "attribute vec4 a_position;\n" +
            "attribute vec4 a_color;\n" +
            "attribute vec2 a_texCoord0;\n" +
            "uniform mat4 u_projTrans;\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_texCoords;\n" +
            "void main() {\n" +
            "  v_color = a_color;\n" +
            "  v_color.a = v_color.a * (255.0/254.0);\n" +
            "  v_texCoords = a_texCoord0;\n" +
            "  gl_Position = u_projTrans * a_position;\n" +
            "}\n";

    private static final String FRAG = "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_texCoords;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform float u_sat;\n" +
            "uniform float u_contrast;\n" +
            "uniform float u_bright;\n" +
            "uniform float u_sepia;\n" +
            "uniform vec3 u_tint;\n" +
            "uniform float u_poster;\n" +
            "void main() {\n" +
            "  vec4 c = v_color * texture2D(u_texture, v_texCoords);\n" +
            "  float l = dot(c.rgb, vec3(0.299, 0.587, 0.114));\n" +
            "  vec3 col = mix(vec3(l), c.rgb, u_sat);\n" +
            "  col = (col - 0.5) * u_contrast + 0.5 + u_bright;\n" +
            "  col = mix(col, vec3(l * 1.07, l * 0.74, l * 0.43), u_sepia);\n" +
            "  col = col * u_tint;\n" +
            "  if (u_poster > 0.5) col = floor(col * 5.0 + 0.5) / 5.0;\n" +
            "  gl_FragColor = vec4(clamp(col, 0.0, 1.0), c.a);\n" +
            "}\n";

    private ShaderProgram shader;
    private boolean failed;

    public boolean begin(SpriteBatch batch, int mode) {
        if (mode <= 0 || failed) return false;
        if (shader == null) {
            ShaderProgram.pedantic = false;
            shader = new ShaderProgram(VERT, FRAG);
            if (!shader.isCompiled()) {
                Gdx.app.error("ColorFilter", shader.getLog());
                failed = true;
                shader.dispose();
                shader = null;
                return false;
            }
        }
        batch.setShader(shader);
        return true;
    }

    /** Chamar depois de batch.begin() (shader ja ligado). */
    public void uniforms(int mode) {
        if (shader == null) return;
        float sat = 1, contrast = 1, bright = 0, sepia = 0, r = 1, g = 1, b = 1, poster = 0;
        switch (mode) {
            case 1: sat = 1.45f; contrast = 1.1f; break;
            case 2: sat = 0.8f; contrast = 1.15f; poster = 1; r = 1.05f; b = 0.92f; break;
            case 3: sat = 0f; contrast = 1.35f; bright = -0.03f; break;
            case 4: sepia = 0.85f; contrast = 1.05f; break;
            case 5: sat = 0.65f; bright = -0.12f; r = 0.75f; g = 0.85f; b = 1.25f; break;
            case 6: sat = 1.15f; r = 1.12f; g = 1.0f; b = 0.82f; break;
            default: break;
        }
        shader.setUniformf("u_sat", sat);
        shader.setUniformf("u_contrast", contrast);
        shader.setUniformf("u_bright", bright);
        shader.setUniformf("u_sepia", sepia);
        shader.setUniformf("u_tint", r, g, b);
        shader.setUniformf("u_poster", poster);
    }

    public void end(SpriteBatch batch) {
        batch.setShader(null);
    }

    @Override
    public void dispose() {
        if (shader != null) shader.dispose();
    }
}
