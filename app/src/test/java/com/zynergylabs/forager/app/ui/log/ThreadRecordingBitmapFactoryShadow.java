package com.zynergylabs.forager.app.ui.log;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.concurrent.CopyOnWriteArrayList;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Records the thread each {@code BitmapFactory.decodeFile} call runs on, for
 * {@code PhotoDecodeThreadTest}. In Java for the same reason as {@code GatedBitmapFactoryShadow}. *
 * <p>A dimensions-only call ({@code inJustDecodeBounds}) is answered as the platform answers it: the
 * 8x8 size in {@code outWidth}/{@code outHeight}, and {@code null}. It is neither recorded nor held, so
 * only the pixel decode is (RECORD -670: {@code DecodedPhoto} reads the size first, then decodes
 * at the size its cell needs, as the photo viewer's decode does).
 */
@Implements(BitmapFactory.class)
public class ThreadRecordingBitmapFactoryShadow {
    public static final CopyOnWriteArrayList<Thread> DECODE_THREADS = new CopyOnWriteArrayList<>();

    @Implementation
    public static Bitmap decodeFile(String pathName, BitmapFactory.Options opts) {
        if (opts != null && opts.inJustDecodeBounds) {
            opts.outWidth = 8;
            opts.outHeight = 8;
            return null;
        }
        DECODE_THREADS.add(Thread.currentThread());
        return Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
    }
}
