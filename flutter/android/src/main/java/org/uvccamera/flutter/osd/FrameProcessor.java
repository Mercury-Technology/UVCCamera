package org.uvccamera.flutter.osd;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.ImageFormat;

import java.io.ByteArrayOutputStream;

/**
 * Клас для обробки кадрів UVC камери: накладання дати/часу та конвертації NV21 ↔ Bitmap
 */
public class FrameProcessor {
    private static final String TAG = "FrameProcessor";

    private final int width, height;

    private TimespampStyle style;

    public FrameProcessor(int width, int height) {
        this.width = width;
        this.height = height;

        this.style = new TimespampStyle(width, height);
    }

    public byte[] addDateTimeToNV21(byte[] nv21) {
        try {
            // NV21 → JPEG → Bitmap
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null);
            ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, this.width, this.height), 100, jpegStream);
            byte[] jpegData = jpegStream.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);

            // Adding date/time
            Bitmap bitmapWithDate = drawDateTimeOnBitmap(bitmap);

            // Bitmap → NV21
            byte[] nv21WithDate = convertBitmapToNV21(bitmapWithDate);

            bitmap.recycle();
            bitmapWithDate.recycle();

            return nv21WithDate;
        } catch (Exception e) {
            e.printStackTrace();
            // In case of error return original
            return nv21;
        }
    }

    private Bitmap drawDateTimeOnBitmap(Bitmap srcBitmap) {
        Bitmap resultBitmap = srcBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(resultBitmap);

        // Text
        Paint paintText = new Paint();
        paintText.setColor(this.style.fontColor);
        paintText.setAntiAlias(true);
        paintText.setTextSize(this.style.fontSize);
        paintText.setTypeface(this.style.typeface);

        String content = this.style.getOSDContent();

        // Calculate font size
        Rect textBounds = new Rect();
        paintText.getTextBounds(content, 0, content.length(), textBounds);

        // Box coordinates (bottom right corner)
        float right = resultBitmap.getWidth() - this.style.boxPaddingHorizontal;
        float bottom = resultBitmap.getHeight() - this.style.boxPaddingVertical;
        float left = right - textBounds.width() - this.style.boxPaddingHorizontal * 2;
        float top = bottom - textBounds.height() - this.style.boxPaddingVertical * 2;

        // Draw box
        Paint paintBg = new Paint();
        paintBg.setColor(this.style.boxColor);
        canvas.drawRect(left, top, right, bottom, paintBg);

        // Draw text
        float textX = left + this.style.boxPaddingHorizontal;
        float textY = bottom - this.style.boxPaddingVertical; // baseline
        canvas.drawText(content, textX, textY, paintText);

        return resultBitmap;
    }

    private byte[] convertBitmapToNV21(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);

        byte[] yuv = new byte[width * height * 3 / 2];
        encodeYUV420SP(yuv, argb, width, height);
        return yuv;
    }

    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;
        int yIndex = 0;
        int uvIndex = frameSize;

        int R, G, B, Y, U, V;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int rgb = argb[j * width + i];
                R = (rgb >> 16) & 0xFF;
                G = (rgb >> 8) & 0xFF;
                B = rgb & 0xFF;

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                yuv420sp[yIndex++] = (byte) Math.max(0, Math.min(255, Y));

                if (j % 2 == 0 && i % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) Math.max(0, Math.min(255, V));
                    yuv420sp[uvIndex++] = (byte) Math.max(0, Math.min(255, U));
                }
            }
        }
    }
}
