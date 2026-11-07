package com.pocketdex.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;

public final class Ui {
    private Ui() {
    }

    public static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics()));
    }

    public static float sp(Context c, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, value, c.getResources().getDisplayMetrics());
    }

    public static int alpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255f * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int mix(int colorA, int colorB, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = Math.round(Color.alpha(colorA) + (Color.alpha(colorB) - Color.alpha(colorA)) * t);
        int r = Math.round(Color.red(colorA) + (Color.red(colorB) - Color.red(colorA)) * t);
        int g = Math.round(Color.green(colorA) + (Color.green(colorB) - Color.green(colorA)) * t);
        int b = Math.round(Color.blue(colorA) + (Color.blue(colorB) - Color.blue(colorA)) * t);
        return Color.argb(a, r, g, b);
    }

    public static Drawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(c, radiusDp));
        return drawable;
    }

    public static Drawable roundedStroke(Context c, int color, float radiusDp,
                                         int strokeColor, float strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(c, radiusDp));
        drawable.setStroke(dp(c, strokeWidthDp), strokeColor);
        return drawable;
    }

    public static Drawable ripple(Context c, Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    public static Drawable selectable(Context c) {
        TypedValue out = new TypedValue();
        if (c.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, out, true)) {
            return c.getDrawable(out.resourceId);
        }
        return null;
    }

    public static GradientDrawable desktopGradient() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B2233, 0xFF0B0D12, 0xFF121828});
        return g;
    }
}
