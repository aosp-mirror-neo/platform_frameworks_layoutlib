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

package com.android.layoutlib.bridge.android;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser;
import com.android.layoutlib.bridge.intensive.setup.LayoutlibBridgeClientCallback;

import org.junit.Test;

import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewRootImpl_Accessor;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerGlobal_Delegate;
import com.android.layoutlib.bridge.impl.RenderSessionImpl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link RenderSession#dispose()} and memory leak prevention in
 * {@link WindowManagerGlobal}.
 */
public class RenderSessionDisposeTest extends RenderTestBase {

    @Test
    public void testSessionDisposeCleansWindowManagerGlobalViews() {
        String layoutStr =
                "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "              android:layout_width=\"match_parent\"\n" +
                        "              android:layout_height=\"match_parent\"/>";

        LayoutPullParser parser = LayoutPullParser.createFromString(layoutStr);
        LayoutlibBridgeClientCallback callback =
                new LayoutlibBridgeClientCallback(getLogger(), mDefaultClassLoader,
                        "com.android.layoutlib.test.myapplication");
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(callback)
                .build();

        RenderSession session = sBridge.createSession(params);
        try {
            Result renderResult = session.render(50000);
            assertTrue("Render failed: " + renderResult.getErrorMessage(),
                    renderResult.isSuccess());

            ArrayList<View> views = WindowManagerGlobal.getInstance().getWindowViews();
            assertFalse("WindowManagerGlobal should contain views while session is active",
                    views.isEmpty());
        } finally {
            session.dispose();
        }

        ArrayList<View> viewsAfterDispose = WindowManagerGlobal.getInstance().getWindowViews();
        assertTrue("WindowManagerGlobal should have cleared views after session dispose",
                viewsAfterDispose.isEmpty());
    }

    @Test
    public void testSessionDisposeWithStuckTraversalCleansWindowManagerGlobalViews() {
        String layoutStr =
                "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "              android:layout_width=\"match_parent\"\n" +
                        "              android:layout_height=\"match_parent\"/>";

        LayoutPullParser parser = LayoutPullParser.createFromString(layoutStr);
        LayoutlibBridgeClientCallback callback =
                new LayoutlibBridgeClientCallback(getLogger(), mDefaultClassLoader,
                        "com.android.layoutlib.test.myapplication");
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(callback)
                .build();

        RenderSession session = sBridge.createSession(params);
        try {
            Result renderResult = session.render(50000);
            assertTrue("Render failed: " + renderResult.getErrorMessage(),
                    renderResult.isSuccess());

            ArrayList<View> views = WindowManagerGlobal.getInstance().getWindowViews();
            assertFalse("WindowManagerGlobal should contain views while session is active",
                    views.isEmpty());

            View view = views.getFirst();
            ViewRootImpl root = view.getViewRootImpl();
            assertNotNull("ViewRootImpl should not be null", root);
            // Simulate mIsInTraversal remaining true (e.g. unhandled exception thrown during
            // performTraversals)
            ViewRootImpl_Accessor.setInTraversal(root, true);
        } finally {
            session.dispose();
        }

        ArrayList<View> viewsAfterDispose = WindowManagerGlobal.getInstance().getWindowViews();
        assertTrue("WindowManagerGlobal should have cleared views even if traversal had issues",
                viewsAfterDispose.isEmpty());
    }

    @Test
    public void testSessionDisposeRemovesWindowViewsListener() {
        String layoutStr =
                "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "              android:layout_width=\"match_parent\"\n" +
                        "              android:layout_height=\"match_parent\"/>";

        LayoutPullParser parser = LayoutPullParser.createFromString(layoutStr);
        LayoutlibBridgeClientCallback callback =
                new LayoutlibBridgeClientCallback(getLogger(), mDefaultClassLoader,
                        "com.android.layoutlib.test.myapplication");
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(callback)
                .build();

        RenderSession session = sBridge.createSession(params);
        try {
            Result renderResult = session.render(50000);
            assertTrue("Render failed: " + renderResult.getErrorMessage(),
                    renderResult.isSuccess());

            assertTrue("WindowManagerGlobal should have active window views listener during session",
                    hasWindowViewsListeners());
        } finally {
            session.dispose();
        }

        assertFalse("WindowManagerGlobal should have removed window views listener after session dispose",
                hasWindowViewsListeners());
    }

    @Test
    public void testConcurrentWindowViewsListenerAddAndRemove() throws InterruptedException {
        int threadCount = 8;
        int iterations = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < iterations; j++) {
                        Consumer<List<View>> listener = views -> {};
                        WindowManagerGlobal_Delegate.addWindowViewsListener(listener);
                        WindowManagerGlobal_Delegate.removeWindowViewsListener(listener);
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue("Threads did not finish in time", doneLatch.await(10, TimeUnit.SECONDS));
        assertNull("Concurrent add/remove threw an exception: " + error.get(), error.get());
    }

    @Test
    public void testConcurrentClearWindowViewsAndDispose() throws Exception {
        String layoutStr =
                "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "              android:layout_width=\"match_parent\"\n" +
                        "              android:layout_height=\"match_parent\"/>";

        LayoutPullParser parser = LayoutPullParser.createFromString(layoutStr);
        LayoutlibBridgeClientCallback callback =
                new LayoutlibBridgeClientCallback(getLogger(), mDefaultClassLoader,
                        "com.android.layoutlib.test.myapplication");
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(callback)
                .build();

        RenderSession session = sBridge.createSession(params);
        Result renderResult = session.render(50000);
        assertTrue("Render failed: " + renderResult.getErrorMessage(), renderResult.isSuccess());

        Field sessionField = session.getClass().getDeclaredField("mSession");
        sessionField.setAccessible(true);
        RenderSessionImpl sessionImpl = (RenderSessionImpl) sessionField.get(session);

        int threadCount = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount + 1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    sessionImpl.clearWindowViews();
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        new Thread(() -> {
            try {
                startLatch.await();
                session.dispose();
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            } finally {
                doneLatch.countDown();
            }
        }).start();

        startLatch.countDown();
        assertTrue("Threads did not finish in time", doneLatch.await(10, TimeUnit.SECONDS));
        assertNull("Concurrent clearWindowViews/dispose threw an exception: " + error.get(),
                error.get());
        assertFalse("WindowManagerGlobal should have removed window views listener",
                hasWindowViewsListeners());
    }

    /**
     * Checks if {@link WindowManagerGlobal} has any registered window views listeners.
     */
    private static boolean hasWindowViewsListeners() {
        WindowManagerGlobal global = WindowManagerGlobal.getInstance();
        synchronized (global.mLock) {
            return global.mWindowViewsListenerGroup != null
                    && global.mWindowViewsListenerGroup.mListeners != null
                    && !global.mWindowViewsListenerGroup.mListeners.isEmpty();
        }
    }
}
