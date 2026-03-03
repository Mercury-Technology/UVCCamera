package org.uvccamera.flutter.osd;

import android.opengl.GLES20;
import android.opengl.GLES11Ext;
import android.opengl.Matrix;
import android.util.Log;

public class GLRenderer {
    private static final String TAG = "GLRenderer";

    // simple vertex shader (texture coords)
    public static final String VERTEX =
            "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTexCoord;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform mat4 uMVP;\n" +
                    "void main() {\n" +
                    "  gl_Position = uMVP * aPosition;\n" +
                    "  vTexCoord = aTexCoord.xy;\n" +
                    "}\n";

    // fragment shader for external OES texture (camera)
    public static final String FRAGMENT_OES =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, vTexCoord);\n" +
                    "}\n";

    // fragment shader for simple 2D texture (text bitmap)
    public static final String FRAGMENT_2D =
            "precision mediump float;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "  vec4 c = texture2D(sTexture, vTexCoord);\n" +
                    "  gl_FragColor = c;\n" +
                    "}\n";

    // simple full-screen quad coords / texcoords
    public static final float[] QUAD_COORDS = {
            -1f, -1f, 0f,   // bottom-left
            1f, -1f, 0f,   // bottom-right
            -1f,  1f, 0f,   // top-left
            1f,  1f, 0f    // top-right
    };

    public static final float[] QUAD_TEX = {
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
    };

    // utility: compile shader & link program
    public static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        final int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    public static int createProgram(String vtx, String frag) {
        int v = loadShader(GLES20.GL_VERTEX_SHADER, vtx);
        int f = loadShader(GLES20.GL_FRAGMENT_SHADER, frag);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, v);
        GLES20.glAttachShader(prog, f);
        GLES20.glLinkProgram(prog);
        final int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(prog));
            GLES20.glDeleteProgram(prog);
            prog = 0;
        }
        return prog;
    }

    // create OES texture
    public static int createOESTexture() {
        final int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return tex[0];
    }

    // create 2D texture (for bitmap)
    public static int create2DTexture() {
        final int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return tex[0];
    }

    // convenience multiply identity mat
    public static float[] identityM() {
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        return m;
    }
}
