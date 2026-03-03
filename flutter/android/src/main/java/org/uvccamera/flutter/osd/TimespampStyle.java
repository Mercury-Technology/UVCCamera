package org.uvccamera.flutter.osd;

import android.graphics.Color;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;

public class TimespampStyle {
    private static final String TAG = "TimespampStyle";

    private final int width, height;

    public final int fontColor = Color.WHITE;

    public int fontSize = 0;

    public final Typeface typeface = Typeface.DEFAULT_BOLD;

    public final int boxColor = Color.BLACK;

    public int boxPaddingHorizontal = 0;

    public int boxPaddingVertical = 0;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());

    public TimespampStyle(int width, int height) {
        this.width = width;
        this.height = height;

        this.fontSize = this.getFontSize();
        this.boxPaddingHorizontal = this.fontSize / 3;
        this.boxPaddingVertical = this.fontSize / 4;
    }

    public String getOSDContent() {
        return dateFormat.format(new Date());
    }

    private int FONT_SIZE_MIN = 8;
    private int FONT_SIZE_MAX = 32;

    private int getFontSize() {
        float fontSize = 8f + (this.height - 384f) * (24f - 8f) / (720f - 384f);
        if (fontSize < FONT_SIZE_MIN) fontSize = FONT_SIZE_MIN;
        if (fontSize > FONT_SIZE_MAX) fontSize = FONT_SIZE_MAX;
        return Math.round(fontSize);
    }
}
