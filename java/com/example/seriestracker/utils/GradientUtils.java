package com.example.seriestracker.utils;

import android.graphics.drawable.GradientDrawable;

import java.util.List;

public class GradientUtils {

    public static GradientDrawable createGradientDrawable(List<String> colors) {
        if (colors == null || colors.isEmpty()) {
            return null;
        }

        // Если только один цвет, создаем одноцветный градиент
        if (colors.size() == 1) {
            try {
                int colorInt = android.graphics.Color.parseColor(colors.get(0));
                GradientDrawable gradientDrawable = new GradientDrawable();
                gradientDrawable.setColor(colorInt);
                gradientDrawable.setShape(GradientDrawable.OVAL);
                return gradientDrawable;
            } catch (Exception e) {
                return null;
            }
        }

        // Если несколько цветов, создаем градиент
        int[] colorInts = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            try {
                colorInts[i] = android.graphics.Color.parseColor(colors.get(i));
            } catch (Exception e) {
                colorInts[i] = 0xFF2196F3; // Синий по умолчанию
            }
        }

        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setOrientation(GradientDrawable.Orientation.TL_BR); // 45 градусов
        gradientDrawable.setColors(colorInts);
        gradientDrawable.setShape(GradientDrawable.OVAL);

        return gradientDrawable;
    }

    public static GradientDrawable createRectGradient(List<String> colors) {
        if (colors == null || colors.isEmpty()) {
            return null;
        }

        // Если только один цвет, создаем одноцветный фон
        if (colors.size() == 1) {
            try {
                int colorInt = android.graphics.Color.parseColor(colors.get(0));
                GradientDrawable gradientDrawable = new GradientDrawable();
                gradientDrawable.setColor(colorInt);
                gradientDrawable.setCornerRadius(8);
                return gradientDrawable;
            } catch (Exception e) {
                return null;
            }
        }

        // Если несколько цветов, создаем градиент
        int[] colorInts = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            try {
                colorInts[i] = android.graphics.Color.parseColor(colors.get(i));
            } catch (Exception e) {
                colorInts[i] = 0xFF2196F3; // Синий по умолчанию
            }
        }

        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setOrientation(GradientDrawable.Orientation.TL_BR); // 45 градусов
        gradientDrawable.setColors(colorInts);
        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
        gradientDrawable.setCornerRadius(8);

        return gradientDrawable;
    }
}