package com.example.seriestracker.utils;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Цвета градиента карточки коллекции (светлая / тёмная тема). */
public final class CollectionCardColors {

    private CollectionCardColors() {
    }

    public static int[] lightGradient(int mainColor) {
        int top = brighten(mainColor);
        int mid = Color.rgb(
                (Color.red(top) * 2 + Color.red(mainColor)) / 3,
                (Color.green(top) * 2 + Color.green(mainColor)) / 3,
                (Color.blue(top) * 2 + Color.blue(mainColor)) / 3);
        int bottom = deepen(mainColor);
        return new int[]{top, mid, bottom};
    }

    public static int[] darkGradient(int mainColor) {
        int softTop = soften(mainColor);
        int mid = Color.argb(255,
                (Color.red(softTop) + 10) / 3,
                (Color.green(softTop) + 12) / 3,
                (Color.blue(softTop) + 14) / 3);
        return new int[]{softTop, mid, 0xFF000000};
    }

    public static GradientDrawable verticalGradient(int[] colors, float cornerRadius) {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, colors);
        bg.setCornerRadius(cornerRadius);
        return bg;
    }

    /** Самый яркий цвет из стопов градиента (по HSV Value). */
    public static int brightestFromGradient(int[] colors) {
        if (colors == null || colors.length == 0) return 0xFF8AB0C0;
        int best = colors[0];
        float bestScore = -1f;
        for (int c : colors) {
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            float score = hsv[2] * 2f + hsv[1];
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** Акцентный цвет текста/полоски в тон карточке. */
    public static int accentFromGradient(int[] colors) {
        return brightestFromGradient(colors);
    }

    public static int soften(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(hsv[1] * 0.45f, 0.42f);
        hsv[2] = Math.min(0.62f, Math.max(0.38f, hsv[2] * 0.55f + 0.22f));
        return Color.HSVToColor(hsv);
    }

    public static int brighten(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(0.32f, hsv[1] * 0.38f + 0.06f);
        hsv[2] = Math.min(0.92f, Math.max(0.78f, hsv[2] * 0.25f + 0.7f));
        return Color.HSVToColor(hsv);
    }

    public static int deepen(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(0.42f, hsv[1] * 0.48f + 0.12f);
        hsv[2] = Math.min(0.68f, Math.max(0.52f, hsv[2] * 0.4f + 0.28f));
        return Color.HSVToColor(hsv);
    }
}
