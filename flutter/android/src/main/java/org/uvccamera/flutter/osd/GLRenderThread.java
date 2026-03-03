package org.uvccamera.flutter.osd;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES11Ext;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.view.Surface;
import android.graphics.SurfaceTexture;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GLRenderThread extends Thread {
    private static final String TAG = "GLRenderThread";

    private final Surface outSurface;
    private final int width, height;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

    private SurfaceTexture inputSurfaceTexture;
    private Surface inputSurface;

    private int oesTextureId = -1;
    private int programOES = -1;
    private int program2D = -1;
    private int textTextureId = -1;

    private int aPosOES, aTexOES, uMVPOES, sTexOES;
    private int aPos2D, aTex2D, uMVP2D, sTex2D;

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer texBuffer;

    private final CountDownLatch initLatch = new CountDownLatch(1);
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final AtomicReference<Runnable> onFirstFrame = new AtomicReference<>(null);

    private TimespampStyle style;

    private Paint textPaint;
    private Bitmap textBitmap;
    private Canvas textCanvas;

    private float[] textMatrix = new float[16];
    private long lastTextUpdate = 0;

    private volatile boolean frameAvailable = false;

    public GLRenderThread(final Surface outSurface, int width, int height) {
        this.outSurface = outSurface;
        this.width = width;
        this.height = height;

        this.style = new TimespampStyle(width, height);

        vertexBuffer = ByteBuffer.allocateDirect(4 * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(GLRenderer.QUAD_COORDS).position(0);

        texBuffer = ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texBuffer.put(GLRenderer.QUAD_TEX).position(0);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(style.fontColor);
        textPaint.setTextSize(style.fontSize);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(style.typeface);

        // Create initial small bitmap
        textBitmap = Bitmap.createBitmap(200, 50, Bitmap.Config.ARGB_8888);
        textCanvas = new Canvas(textBitmap);
        drawInitialText("INITIAL");
    }

    private void drawInitialText(String text) {
        textCanvas.drawColor(style.boxColor);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float x = 4f;
        float y = 4f - fm.ascent;
        textCanvas.drawText(text, x, y, textPaint);
    }

    public void setOnFirstFrameRendered(Runnable r) { onFirstFrame.set(r); }
    public Surface getInputSurface() { return inputSurface; }
    public SurfaceTexture getInputSurfaceTexture() { return inputSurfaceTexture; }
    public void shutdown() { running.set(false); interrupt(); }
    public void waitForInit() throws InterruptedException { initLatch.await(); }

    @Override
    public void run() {
        try {
            initEGL();
            initGL();
            initLatch.countDown();

            boolean firstFrame = true;

            while (running.get() && !Thread.interrupted()) {
                if (inputSurfaceTexture == null) break;

                if (!frameAvailable) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                frameAvailable = false;

                inputSurfaceTexture.updateTexImage();

                long now = System.currentTimeMillis();
                if (now - lastTextUpdate >= 300) {
                    String content = style.getOSDContent();
                    updateTextBitmapTexture(content);
                    lastTextUpdate = now;
                }

                drawFrame();

                if (firstFrame) {
                    Runnable cb = onFirstFrame.getAndSet(null);
                    if (cb != null) cb.run();
                    firstFrame = false;
                }

                EGL14.eglSwapBuffers(eglDisplay, eglSurface);
                try {
                    Thread.sleep(33);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "GL thread error", e);
        } finally {
            release();
        }
    }

    private void initEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw new RuntimeException("eglGetDisplay failed");

        final int[] ver = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) throw new RuntimeException("eglInitialize failed");

        final int[] attribList = {
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE,8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        };
        final EGLConfig[] configs = new EGLConfig[1];
        final int[] numConfig = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfig, 0))
            throw new RuntimeException("eglChooseConfig failed");

        final int[] ctxAttrib = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0);
        if (eglContext == null) throw new RuntimeException("eglCreateContext failed");

        final int[] surfAttrib = { EGL14.EGL_NONE };
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], outSurface, surfAttrib, 0);
        if (eglSurface == null) throw new RuntimeException("eglCreateWindowSurface failed");

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
            throw new RuntimeException("eglMakeCurrent failed");
    }

    private void initGL() {
        GLES20.glViewport(0, 0, width, height);

        programOES = GLRenderer.createProgram(GLRenderer.VERTEX, GLRenderer.FRAGMENT_OES);
        program2D = GLRenderer.createProgram(GLRenderer.VERTEX, GLRenderer.FRAGMENT_2D);

        aPosOES = GLES20.glGetAttribLocation(programOES, "aPosition");
        aTexOES = GLES20.glGetAttribLocation(programOES, "aTexCoord");
        uMVPOES = GLES20.glGetUniformLocation(programOES, "uMVP");
        sTexOES = GLES20.glGetUniformLocation(programOES, "sTexture");

        aPos2D = GLES20.glGetAttribLocation(program2D, "aPosition");
        aTex2D = GLES20.glGetAttribLocation(program2D, "aTexCoord");
        uMVP2D = GLES20.glGetUniformLocation(program2D, "uMVP");
        sTex2D = GLES20.glGetUniformLocation(program2D, "sTexture");

        oesTextureId = GLRenderer.createOESTexture();
        inputSurfaceTexture = new SurfaceTexture(oesTextureId);
        inputSurfaceTexture.setDefaultBufferSize(width, height);
        inputSurface = new Surface(inputSurfaceTexture);

        // Wait for a first frame
        inputSurfaceTexture.setOnFrameAvailableListener(st -> frameAvailable = true);

        // text texture init
        textTextureId = GLRenderer.create2DTexture();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textBitmap, 0);
    }

    private void drawFrame() {
        GLES20.glClearColor(0f,0f,0f,1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // OES texture
        GLES20.glUseProgram(programOES);
        GLES20.glEnableVertexAttribArray(aPosOES);
        GLES20.glVertexAttribPointer(aPosOES,3,GLES20.GL_FLOAT,false,3*4,vertexBuffer);
        GLES20.glEnableVertexAttribArray(aTexOES);
        GLES20.glVertexAttribPointer(aTexOES,2,GLES20.GL_FLOAT,false,2*4,texBuffer);

        float[] m = GLRenderer.identityM();
        GLES20.glUniformMatrix4fv(uMVPOES, 1, false, m, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);
        GLES20.glUniform1i(sTexOES, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // 2D text overlay
        GLES20.glUseProgram(program2D);
        GLES20.glEnableVertexAttribArray(aPos2D);
        GLES20.glVertexAttribPointer(aPos2D, 3, GLES20.GL_FLOAT, false, 3 * 4, vertexBuffer);
        GLES20.glEnableVertexAttribArray(aTex2D);
        GLES20.glVertexAttribPointer(aTex2D, 2, GLES20.GL_FLOAT, false, 2*4, texBuffer);

        // Small sized texture, scale = text/video
        float scaleX = (float) textBitmap.getWidth() / width;
        float scaleY = (float) textBitmap.getHeight() / height;
        Matrix.setIdentityM(textMatrix,0);
        Matrix.translateM(textMatrix, 0, 1f - scaleX - 0.01f, -1f + scaleY + 0.01f, 0f);
        Matrix.scaleM(textMatrix, 0, scaleX, scaleY, 1f);

        GLES20.glUniformMatrix4fv(uMVP2D, 1, false, textMatrix, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId);
        GLES20.glUniform1i(sTex2D, 1);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(aPosOES);
        GLES20.glDisableVertexAttribArray(aTexOES);
        GLES20.glDisableVertexAttribArray(aPos2D);
        GLES20.glDisableVertexAttribArray(aTex2D);
    }

    public void updateTextBitmapTexture(String text) {
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textWidth = textPaint.measureText(text);
        float textHeight = fm.descent - fm.ascent;

        int bitmapWidth = (int)(textWidth + style.boxPaddingHorizontal * 2);
        int bitmapHeight = (int)(textHeight + style.boxPaddingVertical * 2);

        if (textBitmap != null) {
            textBitmap.recycle();
        }
        textBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        textCanvas = new Canvas(textBitmap);

        textCanvas.drawColor(style.boxColor);
        textCanvas.drawText(text, style.boxPaddingHorizontal, style.boxPaddingVertical - fm.ascent, textPaint);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,textBitmap,0);
    }

    private void release() {
        try {
            if (inputSurface != null) {
                inputSurface.release();
                inputSurface = null;
            }
            if (inputSurfaceTexture != null) {
                inputSurfaceTexture.release();
                inputSurfaceTexture = null;
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(eglDisplay, eglSurface);
                EGL14.eglDestroyContext(eglDisplay, eglContext);
                EGL14.eglTerminate(eglDisplay);
            }
            if (textBitmap != null) {
                textBitmap.recycle();
                textBitmap = null;
            }
        } catch (Exception e) {
            Log.w(TAG,"release error", e);
        } finally {
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            eglContext = EGL14.EGL_NO_CONTEXT;
            eglSurface = EGL14.EGL_NO_SURFACE;
        }
    }
}
