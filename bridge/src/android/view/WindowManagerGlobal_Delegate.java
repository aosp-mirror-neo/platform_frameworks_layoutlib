/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.view;

import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.content.Context;

/**
 * Delegate used to provide new implementation of a select few methods of
 * {@link WindowManagerGlobal}
 *
 * Through the layoutlib_create tool, the original  methods of WindowManagerGlobal have been
 * replaced by calls to methods of the same name in this delegate class.
 *
 */
public class WindowManagerGlobal_Delegate {

    private static IWindowManager sService;

    @LayoutlibDelegate
    public static IWindowManager getWindowManagerService() {
        return sService;
    }

    // ---- internal implementation stuff ----

    public static void setWindowManagerService(IWindowManager service) {
        sService = service;
    }

    /**
     * Purges all views, roots, and params associated with the given context from
     * {@link WindowManagerGlobal}.
     */
    public static void clearViewsForContext(Context context) {
        WindowManagerGlobal global = WindowManagerGlobal.getInstance();
        synchronized (global.mLock) {
            boolean removedAny = false;
            for (int i = global.mRoots.size() - 1; i >= 0; i--) {
                ViewRootImpl root = global.mRoots.get(i);
                View view = (i < global.mViews.size()) ? global.mViews.get(i) : null;
                Context viewContext = (view != null) ? view.getContext()
                        : (root != null ? root.mContext : null);
                if (viewContext != null && BridgeContext.getBaseContext(viewContext) == context) {
                    global.mRoots.remove(i);
                    if (i < global.mParams.size()) {
                        global.mParams.remove(i);
                    }
                    if (i < global.mViews.size()) {
                        View v = global.mViews.remove(i);
                        if (global.mDyingViews != null && v != null) {
                            global.mDyingViews.remove(v);
                        }
                        if (v != null) {
                            v.assignParent(null);
                        }
                    }
                    removedAny = true;
                }
            }
            if (removedAny && global.mWindowViewsListenerGroup != null) {
                global.mWindowViewsListenerGroup.accept(global.getWindowViews());
            }
        }
    }
}
