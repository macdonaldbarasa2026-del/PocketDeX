package com.pocketdex.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;

public interface EcoItem {
    String key();

    String label();

    Drawable icon(Context c);

    void open(Context c);
}
