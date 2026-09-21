/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.graphics.Rect;
import com.android.internal.lang.System_Delegate;

/**
 * Accessor to allow layoutlib to call {@link ViewRootImpl} methods directly.
 */
public class ViewRootImpl_Accessor {
    public static void dispatchApplyInsets(ViewRootImpl viewRoot, View host) {
        viewRoot.dispatchApplyInsets(host);
    }

    public static void detachFromWindow(ViewRootImpl viewRoot) {
        viewRoot.mAccessibilityInteractionConnectionManager.ensureNoConnection();
        viewRoot.mAccessibilityInteractionConnectionManager.ensureNoDirectConnection();
    }

    public static void performTraversals(ViewRootImpl viewRoot) {
        viewRoot.mTraversalScheduled = true;
        viewRoot.postTraversalBarrier();
        try {
            viewRoot.doTraversal(System_Delegate.nanoTime());
        } finally {
            viewRoot.mIsInTraversal = false;
        }
    }

    public static void resetTraversalState(ViewRootImpl viewRoot) {
        setInTraversal(viewRoot, false);
    }

    public static void setInTraversal(ViewRootImpl viewRoot, boolean inTraversal) {
        if (viewRoot != null) {
            viewRoot.mIsInTraversal = inTraversal;
        }
    }

    public static void updateFrame(ViewRootImpl viewRoot, int width, int height) {
        viewRoot.mWinFrame.set(0, 0, width, height);
        viewRoot.mTmpFrames.frame.set(0, 0, width, height);
        viewRoot.mTmpFrames.displayFrame.set(0, 0, width, height);
    }

    public static Rect getWindowFrame(ViewRootImpl viewRoot) {
        return viewRoot.mWinFrame;
    }
}
