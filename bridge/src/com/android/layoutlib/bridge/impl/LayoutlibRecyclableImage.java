/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.layoutlib.bridge.impl;

import android.annotation.NonNull;
import com.android.ide.common.rendering.api.RecyclableImage;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.layoutlib.bridge.Bridge;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * An implementation of {@link RecyclableImage} for Layoutlib.
 */
public final class LayoutlibRecyclableImage implements RecyclableImage {
    private static final Cleaner sCleaner = Cleaner.create();

    private final int mLogicalWidth;
    private final int mLogicalHeight;
    private final BufferedImage mLogicalSubImage;
    private final Cleaner.Cleanable mCleanable;
    private final CleanUpTask mCleanUpTask;
    private final AtomicBoolean mIsClosed = new AtomicBoolean(false);

    /**
     * A static inner class representing the resource-releasing action.
     * It does NOT reference {@code LayoutlibRecyclableImage} to avoid leaks.
     */
    private static final class CleanUpTask implements Runnable {
        private final BufferedImage mPhysicalBuffer;
        private final SessionBufferPool mBufferPool;
        private final AtomicBoolean mIsReleased = new AtomicBoolean(false);
        private volatile boolean mWarnOnRelease = true;

        private CleanUpTask(@NonNull BufferedImage physicalBuffer,
                @NonNull SessionBufferPool bufferPool) {
            mPhysicalBuffer = physicalBuffer;
            mBufferPool = bufferPool;
        }

        private void disableWarning() {
            mWarnOnRelease = false;
        }

        @Override
        public void run() {
            // Ensure resource is released exactly once, either by direct close() or by the Cleaner thread.
            if (mIsReleased.compareAndSet(false, true)) {
                if (mWarnOnRelease) {
                    Bridge.getLog().warning(
                            ILayoutLog.TAG_BROKEN,
                            "LayoutlibRecyclableImage was not closed before garbage collection. Backing buffer leaked.",
                            null,
                            null
                    );
                }
                mBufferPool.release(mPhysicalBuffer);
            }
        }
    }

    public LayoutlibRecyclableImage(
            int logicalWidth,
            int logicalHeight,
            @NonNull BufferedImage physicalBuffer,
            @NonNull SessionBufferPool bufferPool) {
        mLogicalWidth = logicalWidth;
        mLogicalHeight = logicalHeight;

        // Expose zero-copy sub-image to discard physical padding/oversizing
        mLogicalSubImage = physicalBuffer.getSubimage(0, 0, logicalWidth, logicalHeight);

        mCleanUpTask = new CleanUpTask(physicalBuffer, bufferPool);
        mCleanable = sCleaner.register(this, mCleanUpTask);
    }

    @Override
    public int getWidth() {
        return mLogicalWidth;
    }

    @Override
    public int getHeight() {
        return mLogicalHeight;
    }

    @NonNull
    @Override
    @Deprecated
    public BufferedImage getImage() {
        return mLogicalSubImage;
    }

    @Override
    public boolean isValid() {
        return !mIsClosed.get();
    }

    private void assertIsValid() {
        if (!isValid()) {
            throw new IllegalStateException("This image has already been closed");
        }
    }

    @Override
    public void drawImageTo(Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1,
            int sx2, int sy2) {
        assertIsValid();
        g.drawImage(mLogicalSubImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
    }

    @Override
    public void paint(Consumer<Graphics2D> command) {
        assertIsValid();
        Graphics2D g = mLogicalSubImage.createGraphics();
        try {
            command.accept(g);
        } finally {
            g.dispose();
        }
    }

    @Override
    public BufferedImage getCopy(int x, int y, int w, int h) {
        assertIsValid();
        BufferedImage toCopy;
        if (x == 0 && y == 0 && w == getWidth() && h == getHeight()) {
            toCopy = mLogicalSubImage;
        } else {
            toCopy = mLogicalSubImage.getSubimage(x, y, w, h);
        }
        WritableRaster raster =
                toCopy.copyData(toCopy.getRaster().createCompatibleWritableRaster(0, 0, w, h));
        return new BufferedImage(toCopy.getColorModel(), raster, toCopy.isAlphaPremultiplied(),
                null);
    }

    @Override
    public void close() {
        if (mIsClosed.compareAndSet(false, true)) {
            mCleanUpTask.disableWarning();
            mCleanable.clean();
        }
    }
}
