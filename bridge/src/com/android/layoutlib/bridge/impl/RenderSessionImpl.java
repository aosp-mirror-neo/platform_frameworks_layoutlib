/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.RecyclableImage;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.RenderSizeProvider;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SizeAction;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.rendering.api.ViewType;
import com.android.internal.policy.PhoneWindow;
import com.android.internal.view.menu.ActionMenuItemView;
import com.android.internal.view.menu.BridgeMenuItemImpl;
import com.android.internal.view.menu.IconMenuItemView;
import com.android.internal.view.menu.ListMenuItemView;
import com.android.internal.view.menu.MenuItemImpl;
import com.android.internal.view.menu.MenuView;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.android.BridgeXmlBlockParser;
import com.android.layoutlib.bridge.android.RenderParamsFlags;
import com.android.layoutlib.bridge.android.graphics.NopCanvas;
import com.android.layoutlib.bridge.android.support.DesignLibUtil;
import com.android.layoutlib.bridge.android.support.FragmentTabHostUtil;
import com.android.layoutlib.bridge.android.support.SupportPreferencesUtil;
import com.android.layoutlib.bridge.util.KeyEventHandling;
import com.android.tools.idea.validator.LayoutValidator;
import com.android.tools.idea.validator.ValidatorHierarchy;
import com.android.tools.idea.validator.hierarchy.CustomHierarchyHelper;
import com.android.tools.layoutlib.annotations.NotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable_VectorDrawableAnimatorUI_Delegate;
import android.preference.Preference_Delegate;
import android.util.Pair;
import android.util.TimeUtils;
import android.view.AttachInfo_Accessor;
import android.view.BridgeInflater;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutlibRenderer;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewParent;
import android.view.ViewRootImpl;
import android.view.ViewRootImpl_Accessor;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ActionMenuView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static android.os._Original_Build.VERSION.SDK_INT;
import static com.android.ide.common.rendering.api.Result.Status.ERROR_INFLATION;
import static com.android.ide.common.rendering.api.Result.Status.ERROR_NOT_INFLATED;
import static com.android.ide.common.rendering.api.Result.Status.ERROR_UNKNOWN;
import static com.android.ide.common.rendering.api.Result.Status.SUCCESS;
import static com.android.layoutlib.common.util.ReflectionUtils.isInstanceOf;

/**
 * Class implementing the render session.
 * <p/>
 * A session is a stateful representation of a layout file. It is initialized with data coming
 * through the {@link Bridge} API to inflate the layout. Further actions and rendering can then
 * be done on the layout.
 */
public class RenderSessionImpl extends RenderAction<SessionParams> {

    private static final Canvas NOP_CANVAS = new NopCanvas();
    private static final String SIMULATED_SDK_TOO_HIGH = String.format(Locale.ENGLISH,
            "The current rendering only supports APIs up to %d. You may encounter crashes if " +
                    "using with higher APIs. To avoid, you can set a lower API for your previews.",
            SDK_INT);

    // scene state
    private RenderSession mScene;
    private BridgeXmlBlockParser mBlockParser;
    private BridgeInflater mInflater;
    private ViewGroup mViewRoot;
    private FrameLayout mContentRoot;
    private int mMeasuredScreenWidth = -1;
    private int mMeasuredScreenHeight = -1;
    /** If >= 0, a frame will be executed */
    private long mElapsedFrameTimeNanos = -1;
    /** True if one frame has been already executed to start the animations */
    private boolean mFirstFrameExecuted = false;

    // information being returned through the API
    private BufferedImage mImage;
    private int mImageWidth = -1;
    private int mImageHeight = -1;
    private List<ViewInfo> mViewInfoList;
    private List<ViewInfo> mSystemViewInfoList;
    private Layout.Builder mLayoutBuilder;
    private boolean mNewRenderSize;
    private LayoutlibRenderer mRenderer;
    private final SessionBufferPool mBufferPool = new SessionBufferPool();

    // Passed in MotionEvent initialization when dispatching a touch event.
    private final MotionEvent.PointerProperties[] mPointerProperties =
            MotionEvent.PointerProperties.createArray(1);
    private final MotionEvent.PointerCoords[] mPointerCoords =
            MotionEvent.PointerCoords.createArray(1);

    private long mLastActionDownTimeNanos = -1;
    @Nullable private ValidatorHierarchy mValidatorHierarchy = null;
    private Consumer<List<View>> mWindowChangeListener;

    private static final class PostInflateException extends Exception {
        private static final long serialVersionUID = 1L;

        private PostInflateException(String message) {
            super(message);
        }
    }

    /**
     * Creates a layout scene with all the information coming from the layout bridge API.
     * <p>
     * This <b>must</b> be followed by a call to {@link RenderSessionImpl#init(long)},
     * which act as a
     * call to {@link RenderSessionImpl#acquire(long)}
     *
     * @see Bridge#createSession(SessionParams)
     */
    public RenderSessionImpl(SessionParams params) {
        super(new SessionParams(params));
    }

    /**
     * Initializes and acquires the scene, creating various Android objects such as context,
     * inflater, and parser.
     *
     * @param timeout the time to wait if another rendering is happening.
     *
     * @return whether the scene was prepared
     *
     * @see #acquire(long)
     * @see #release()
     */
    @Override
    public Result init(long timeout) {
        Result result = super.init(timeout);
        if (!result.isSuccess()) {
            return result;
        }

        SessionParams params = getParams();
        BridgeContext context = getContext();

        mLayoutBuilder = new Layout.Builder(params, context);

        // build the inflater and parser.
        mInflater = new BridgeInflater(context, params.getLayoutlibCallback());
        context.setBridgeInflater(mInflater);

        ILayoutPullParser layoutParser = params.getLayoutDescription();
        mBlockParser = new BridgeXmlBlockParser(layoutParser, context, layoutParser.getLayoutNamespace());

        Bitmap.setDefaultDensity(params.getHardwareConfig().getDensity().getDpiValue());

        return SUCCESS.createResult();
    }

    /**
     * Measures the current layout if needed (see {@link #invalidateRenderingSize}).
     */
    private void measureLayout(@NonNull SessionParams params) {
        // only do the screen measure when needed.
        int previousWidth = mMeasuredScreenWidth;
        int previousHeight = mMeasuredScreenHeight;
        HardwareConfig hardwareConfig = params.getHardwareConfig();
        if (mMeasuredScreenWidth == -1) {
            mMeasuredScreenWidth = hardwareConfig.getScreenWidth();
            mMeasuredScreenHeight = hardwareConfig.getScreenHeight();
            mContentRoot.forceLayout();
        }

        RenderingMode renderingMode = params.getRenderingMode();

        if (renderingMode != RenderingMode.NORMAL) {
            int widthMeasureSpecMode = renderingMode.getHorizAction() == SizeAction.EXPAND ?
                    MeasureSpec.UNSPECIFIED // this lets us know the actual needed size
                    : MeasureSpec.EXACTLY;
            int heightMeasureSpecMode = renderingMode.getVertAction() == SizeAction.EXPAND ?
                    MeasureSpec.UNSPECIFIED // this lets us know the actual needed size
                    : MeasureSpec.EXACTLY;

            // We used to compare the measured size of the content to the screen size but
            // this does not work anymore due to the 2 following issues:
            // - If the content is in a decor (system bar, title/action bar), the root view
            //   will not resize even with the UNSPECIFIED because of the embedded layout.
            // - If there is no decor, but a dialog frame, then the dialog padding prevents
            //   comparing the size of the content to the screen frame (as it would not
            //   take into account the dialog padding).

            // The solution is to first get the content size in a normal rendering, inside
            // the decor or the dialog padding.
            // Then measure only the content with UNSPECIFIED to see the size difference
            // and apply this to the screen size.

            View measuredView = mContentRoot.getChildAt(0);
            if (measuredView == null) {
                return;
            }

            int maxWidth = hardwareConfig.getScreenWidth();
            int maxHeight = hardwareConfig.getScreenHeight();
            // first measure the full layout, with EXACTLY to get the size of the
            // content as it is inside the decor/dialog
            Pair<Integer, Integer> exactMeasure = measureView(
                    mViewRoot, measuredView,
                    maxWidth, MeasureSpec.EXACTLY,
                    maxHeight, MeasureSpec.EXACTLY);

            // now measure the content only using UNSPECIFIED (where applicable, based on
            // the rendering mode). This will give us the size the content needs.
            Pair<Integer, Integer> neededMeasure = measureView(
                    mContentRoot, measuredView,
                    maxWidth, widthMeasureSpecMode,
                    maxHeight, heightMeasureSpecMode);

            // If measuredView is not null, exactMeasure nor result will be null.
            assert (exactMeasure != null && neededMeasure != null);

            // now look at the difference and add what is needed.
            mMeasuredScreenWidth = calcSize(mMeasuredScreenWidth, neededMeasure.first,
                    exactMeasure.first, renderingMode.getHorizAction());
            mMeasuredScreenHeight = calcSize(mMeasuredScreenHeight, neededMeasure.second,
                    exactMeasure.second, renderingMode.getVertAction());
        }
        mNewRenderSize =
                mMeasuredScreenWidth != previousWidth || mMeasuredScreenHeight != previousHeight;
    }

    /**
     * Calculate the required vertical (height) or horizontal (width) size of the canvas for the
     * view, given current size requirements.
     * @param currentSize current size of the canvas
     * @param neededSize the size the content actually needs
     * @param measuredSize the measured size of the content (restricted by the current size)
     * @param action the {@link SizeAction} of the view
     * @return the size the canvas should be
     */
    private static int calcSize(int currentSize, int neededSize, int measuredSize,
            SizeAction action) {
        if (action == SizeAction.EXPAND) {
            if (neededSize > measuredSize) {
                currentSize += neededSize - measuredSize;
            }
            if (currentSize < measuredSize) {
                // If the screen size is less than the exact measured size, expand to match.
                currentSize = measuredSize;
            }
        } else if (action == SizeAction.SHRINK) {
            currentSize = neededSize;
        }
        return currentSize;
    }

    /**
     * Inflates the layout.
     * <p>
     * {@link #acquire(long)} must have been called before this.
     *
     * @throws IllegalStateException if the current context is different than the one owned by
     *      the scene, or if {@link #init(long)} was not called.
     */
    public Result inflate() {
        checkLock();

        try {
            BridgeContext context = getContext();
            Window window = new PhoneWindow(context);
            window.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            window.requestFeature(Window.FEATURE_NO_TITLE);

            mViewRoot = (ViewGroup) window.getDecorView();
            Layout layout = new Layout(mLayoutBuilder);
            mLayoutBuilder = null;  // Done with the builder.
            mContentRoot = layout.getContentRoot();
            window.setContentView(layout);
            mViewRoot.setBackground(layout.getBackground());
            SessionParams params = getParams();

            int simulatedVersion = params.getSimulatedPlatformVersion();
            sSimulatedSdk = simulatedVersion > 0 ? simulatedVersion : SDK_INT;
            if (sSimulatedSdk > SDK_INT) {
                Bridge.getLog().fidelityWarning(ILayoutLog.TAG_UNSUPPORTED, SIMULATED_SDK_TOO_HIGH,
                        null, null, null);
            }

            if (Bridge.isLocaleRtl(params.getLocale())) {
                if (!params.isRtlSupported()) {
                    Bridge.getLog().warning(ILayoutLog.TAG_RTL_NOT_ENABLED,
                            "You are using a right-to-left " +
                                    "(RTL) locale but RTL is not enabled", null, null);
                } else if (params.getSimulatedPlatformVersion() !=0 &&
                        params.getSimulatedPlatformVersion() < 17) {
                    // This will render ok because we are using the latest layoutlib but at least
                    // warn the user that this might fail in a real device.
                    Bridge.getLog().warning(ILayoutLog.TAG_RTL_NOT_SUPPORTED, "You are using a " +
                            "right-to-left " +
                            "(RTL) locale but RTL is not supported for API level < 17", null, null);
                }
            }

            String rootTag = params.getFlag(RenderParamsFlags.FLAG_KEY_ROOT_TAG);
            boolean isPreference = "PreferenceScreen".equals(rootTag) ||
                    SupportPreferencesUtil.isSupportRootTag(rootTag);
            View view;
            if (isPreference) {
                // First try to use the support library inflater. If something fails, fallback
                // to the system preference inflater.
                view = SupportPreferencesUtil.inflatePreference(context, mBlockParser,
                        mContentRoot);
                if (view == null) {
                    view = Preference_Delegate.inflatePreference(context, mBlockParser,
                            mContentRoot);
                }
            } else {
                view = mInflater.inflate(mBlockParser, mContentRoot);
            }

            // done with the parser, pop it.
            context.popParser();

            // set the AttachInfo on the root view.
            mRenderer = AttachInfo_Accessor.setAttachInfo(mViewRoot, layout);

            // Invalidate rendering when window is added or removed
            if (mWindowChangeListener != null) {
                WindowManagerGlobal.getInstance().removeWindowViewsListener(mWindowChangeListener);
            }
            mWindowChangeListener = views -> {
                if (mRenderer != null) {
                    mRenderer.invalidateRoot();
                }
            };
            WindowManagerGlobal.getInstance()
                    .addWindowViewsListener(Runnable::run, mWindowChangeListener);

            // post-inflate process. For now this supports TabHost/TabWidget
            postInflateProcess(view, params.getLayoutlibCallback(), isPreference ? view : null);
            mInflater.onDoneInflation();

            setActiveToolbar(view, context, params);

            measureLayout(params);
            ViewRootImpl_Accessor.updateFrame(mViewRoot.getViewRootImpl(), mMeasuredScreenWidth,
                    mMeasuredScreenHeight);
            ViewRootImpl_Accessor.performTraversals(mViewRoot.getViewRootImpl());

            List<ViewGroup> viewRoots =
                    getWindowViews().stream().filter(ViewGroup.class::isInstance).map(ViewGroup.class::cast).toList();
            mSystemViewInfoList =
                    visitAllChildren(viewRoots, 0, 0, params, false);

            return SUCCESS.createResult();
        } catch (PostInflateException e) {
            return ERROR_INFLATION.createResult(e.getMessage(), e);
        } catch (Throwable e) {
            // get the real cause of the exception.
            Throwable t = e;
            while (t.getCause() != null) {
                t = t.getCause();
            }

            return ERROR_INFLATION.createResult(t.getMessage(), t);
        }
    }

    /**
     * Sets the time for which the next frame will be selected. The time is the elapsed time from
     * the current system nanos time. You
     */
    public void setElapsedFrameTimeNanos(long nanos) {
        mElapsedFrameTimeNanos = nanos;
    }

    /**
     * Renders the scene.
     * <p>
     * {@link #acquire(long)} must have been called before this.
     *
     * @param freshRender whether the render is a new one and should erase the existing bitmap (in
     *      the case where bitmaps are reused). This is typically needed when not playing
     *      animations.)
     *
     * @throws IllegalStateException if the current context is different than the one owned by
     *      the scene, or if {@link #acquire(long)} was not called.
     *
     * @see SessionParams#getRenderingMode()
     * @see RenderSession#render(long)
     */
    public Result render(boolean freshRender) {
        return renderAndBuildResult(freshRender, false);
    }

    /**
     * Measures the layout
     * <p>
     * {@link #acquire(long)} must have been called before this.
     *
     * @throws IllegalStateException if the current context is different than the one owned by
     *      the scene, or if {@link #acquire(long)} was not called.
     *
     * @see SessionParams#getRenderingMode()
     * @see RenderSession#render(long)
     */
    public Result measure() {
        return renderAndBuildResult(false, true);
    }

    /**
     * Renders the scene.
     * <p>
     * {@link #acquire(long)} must have been called before this.
     *
     * @param freshRender whether the render is a new one and should erase the existing bitmap (in
     *      the case where bitmaps are reused). This is typically needed when not playing
     *      animations.)
     *
     * @throws IllegalStateException if the current context is different than the one owned by
     *      the scene, or if {@link #acquire(long)} was not called.
     *
     * @see SessionParams#getRenderingMode()
     * @see RenderSession#render(long)
     */
    private Result renderAndBuildResult(boolean freshRender, boolean onlyMeasure) {
        checkLock();

        SessionParams params = getParams();

        int simulatedVersion = params.getSimulatedPlatformVersion();
        sSimulatedSdk = simulatedVersion > 0 ? simulatedVersion : SDK_INT;
        if (sSimulatedSdk > SDK_INT) {
            Bridge.getLog().fidelityWarning(ILayoutLog.TAG_UNSUPPORTED, SIMULATED_SDK_TOO_HIGH,
                    null, null, null);
        }

        try {
            if (mViewRoot == null || mRenderer == null) {
                return ERROR_NOT_INFLATED.createResult();
            }

            if (mConfigurationUpdated) {
                mViewRoot.dispatchConfigurationChanged(getContext().getConfiguration());
                mConfigurationUpdated = false;
            }

            measureLayout(params);

            float[] scale = new float[]{1.0f, 1.0f};
            if (onlyMeasure) {
                // delete the canvas and image to reset them on the next full rendering
                releaseRender();
                ViewRootImpl_Accessor.updateFrame(mViewRoot.getViewRootImpl(), mMeasuredScreenWidth,
                        mMeasuredScreenHeight);
                ViewRootImpl_Accessor.performTraversals(mViewRoot.getViewRootImpl());
                handleScrolling(getContext(), mViewRoot);
            } else {
                // When disableBitmapCaching is true, we do not reuse mImage and
                // we create a new one in every render.
                // This is useful when mImage is just a wrapper of Graphics2D so
                // it doesn't get cached.
                boolean disableBitmapCaching = Boolean.TRUE.equals(params.getFlag(
                    RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING));

                if (mNewRenderSize || mImage == null || disableBitmapCaching) {
                    prepareRenderBuffers(params, scale);
                }

                List<View> views = getWindowViews();
                updateAndTraverseViews(views);
                handleAnimations();
                drawAndCopyImage(params, views);
            }

            List<ViewGroup> viewRoots =
                    getWindowViews().stream().filter(ViewGroup.class::isInstance).map(ViewGroup.class::cast).toList();
            mSystemViewInfoList =
                    visitAllChildren(viewRoots, 0, 0, params, false);

            Consumer<BufferedImage> imageTransformation = getParams().getImageTransformation();
            if (imageTransformation != null) {
                imageTransformation.accept(mImage);
            }

            runLayoutValidation(params, scale);

            // success!
            return SUCCESS.createResult();
        } catch (Throwable e) {
            // get the real cause of the exception.
            Throwable t = e;
            while (t.getCause() != null) {
                t = t.getCause();
            }

            return ERROR_UNKNOWN.createResult(t.getMessage(), t);
        }
    }

    /**
     * Prepares the image buffer and renderer for rendering.
     */
    private void prepareRenderBuffers(SessionParams params, float[] scale) {
        int imageWidth = mMeasuredScreenWidth;
        int imageHeight = mMeasuredScreenHeight;
        RenderSizeProvider sizeProvider = params.getSizeProvider();
        if (sizeProvider != null) {
            Dimension size = sizeProvider.getTargetSize(imageWidth, imageHeight);
            imageWidth = size.width;
            imageHeight = size.height;
        }

        mImage = mBufferPool.acquire(imageWidth, imageHeight);
        mImageWidth = imageWidth;
        mImageHeight = imageHeight;

        assert mImage.getType() == BufferedImage.TYPE_INT_ARGB_PRE;

        // Compute scaling using the negotiated/active render dimensions, not the oversized physical buffer size
        boolean enableImageResizing =
                imageWidth != mMeasuredScreenWidth && imageHeight != mMeasuredScreenHeight &&
                        Boolean.TRUE.equals(
                                params.getFlag(RenderParamsFlags.FLAG_KEY_RESULT_IMAGE_AUTO_SCALE));

        if (enableImageResizing) {
            scale[0] = imageWidth * 1.0f / mMeasuredScreenWidth;
            scale[1] = imageHeight * 1.0f / mMeasuredScreenHeight;
            mRenderer.setScale(scale[0], scale[1]);
        } else {
            mRenderer.setScale(1.0f, 1.0f);
        }

        // Setup native renderer with the negotiated target dimensions instead of oversized physical buffer dimensions
        mRenderer.setup(imageWidth, imageHeight, mViewRoot);
        mNewRenderSize = false;
    }

    /**
     * Updates bounds of root views, triggers layout passes, and updates scrolling for all views.
     */
    private void updateAndTraverseViews(List<View> views) {
        for (View view : views) {
            if (view instanceof ViewGroup) {
                if (view == mViewRoot) {
                    ViewRootImpl_Accessor.updateFrame((ViewRootImpl) view.getParent(),
                            mMeasuredScreenWidth, mMeasuredScreenHeight);
                }
                ViewRootImpl_Accessor.performTraversals((ViewRootImpl) view.getParent());
                handleScrolling(getContext(), view);
            }
        }
    }

    /**
     * Initializes and advances animations if an elapsed frame time is set.
     */
    private void handleAnimations() {
        if (mElapsedFrameTimeNanos >= 0) {
            if (!mFirstFrameExecuted) {
                // We need to run an initial draw call to initialize the animations
                mViewRoot.draw(NOP_CANVAS);

                // The first frame will initialize the animations
                mFirstFrameExecuted = true;
            }
            // Second frame will move the animations
            AnimatedVectorDrawable_VectorDrawableAnimatorUI_Delegate.sFrameTime =
                    mElapsedFrameTimeNanos / 1000000;
        }
    }

    /**
     * Instructs the layout renderer to draw the views and copies the result to the image buffer.
     */
    private void drawAndCopyImage(SessionParams params, List<View> views) {
        mRenderer.draw(views);

        int[] imageData = ((DataBufferInt) mImage.getRaster().getDataBuffer()).getData();
        ByteBuffer buffer = mRenderer.getBuffer();
        try {
            if (buffer == null) {
                return;
            }

            int physicalWidth = mImage.getWidth();
            int stride = mRenderer.getRowStride();

            // Optimize copy by using native byte order to avoid per-word swapping
            buffer.order(ByteOrder.nativeOrder());
            IntBuffer intBuffer = buffer.asIntBuffer();

            if (stride == mImageWidth * 4 && physicalWidth == mImageWidth) {
                // Bulk copy for contiguous buffers matching the physical image size (the fastest path)
                intBuffer.get(imageData, 0, mImageWidth * mImageHeight);
            } else {
                // Stride-aware row-by-row sub-region copy for non-contiguous/oversized buffers
                int intStride = stride / 4;
                for (int y = 0; y < mImageHeight; y++) {
                    intBuffer.position(y * intStride);
                    intBuffer.get(imageData, y * physicalWidth, mImageWidth);
                }
            }
        } finally {
            mRenderer.releaseBuffer();
        }
    }

    /**
     * Validates the currently rendered layout using layout validator.
     */
    private void runLayoutValidation(SessionParams params, float[] scale) {
        try {
            if (params.isLayoutValidationEnabled() && !getViewInfos().isEmpty()) {
                CustomHierarchyHelper.sLayoutlibCallback =
                        getContext().getLayoutlibCallback();

                ValidatorHierarchy hierarchy = LayoutValidator.buildHierarchy(
                        ((View) getViewInfos().getFirst().getViewObject()),
                        mImage.getSubimage(0, 0, mImageWidth, mImageHeight),
                        scale[0],
                        scale[1]);
                setValidatorHierarchy(hierarchy);
            }
        } catch (Throwable e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            ValidatorHierarchy hierarchy = new ValidatorHierarchy();
            hierarchy.mErrorMessage = sw.toString();
            setValidatorHierarchy(hierarchy);
        } finally {
            CustomHierarchyHelper.sLayoutlibCallback = null;
        }
    }

    /**
     * Returns the list of all window root views relevant to this session,
     * sorted by their window type.
     * <p>
     * This is used to determine which window should receive events or be rendered on top.
     */
    @NonNull
    private List<View> getWindowViews() {
        return WindowManagerGlobal.getInstance().getWindowViews().stream()
                .filter(v -> BridgeContext.getBaseContext(v.getContext()) == getContext())
                .sorted((v1, v2) -> {
                    WindowManager.LayoutParams p1 =
                            (WindowManager.LayoutParams) v1.getLayoutParams();
                    WindowManager.LayoutParams p2 =
                            (WindowManager.LayoutParams) v2.getLayoutParams();
                    return Integer.compare(p1.type, p2.type);
                })
                .toList();
    }

    /**
     * Executes {@link View#measure(int, int)} on a given view with the given parameters (used
     * to create measure specs with {@link MeasureSpec#makeMeasureSpec(int, int)}.
     *
     * if <var>measuredView</var> is non null, the method returns a {@link Pair} of (width, height)
     * for the view (using {@link View#getMeasuredWidth()} and {@link View#getMeasuredHeight()}).
     *
     * @param viewToMeasure the view on which to execute measure().
     * @param measuredView if non null, the view to query for its measured width/height.
     * @param width the width to use in the MeasureSpec.
     * @param widthMode the MeasureSpec mode to use for the width.
     * @param height the height to use in the MeasureSpec.
     * @param heightMode the MeasureSpec mode to use for the height.
     * @return the measured width/height if measuredView is non-null, null otherwise.
     */
    private static Pair<Integer, Integer> measureView(ViewGroup viewToMeasure, View measuredView,
            int width, int widthMode, int height, int heightMode) {
        int w_spec = MeasureSpec.makeMeasureSpec(width, widthMode);
        int h_spec = MeasureSpec.makeMeasureSpec(height, heightMode);
        viewToMeasure.measure(w_spec, h_spec);

        if (measuredView != null) {
            return Pair.create(measuredView.getMeasuredWidth(), measuredView.getMeasuredHeight());
        }

        return null;
    }

    /**
     * Post process on a view hierarchy that was just inflated.
     * <p/>
     * At the moment this only supports TabHost: If {@link TabHost} is detected, look for the
     * {@link TabWidget}, and the corresponding {@link FrameLayout} and make new tabs automatically
     * based on the content of the {@link FrameLayout}.
     * @param view the root view to process.
     * @param layoutlibCallback callback to the project.
     * @param skip the view and it's children are not processed.
     */
    private void postInflateProcess(View view, LayoutlibCallback layoutlibCallback, View skip)
            throws PostInflateException {
        if (view == skip) {
            return;
        }
        if (view instanceof TabHost) {
            setupTabHost((TabHost) view, layoutlibCallback);
        } else if (view instanceof QuickContactBadge badge) {
            badge.setImageToDefault();
        } else if (view instanceof ViewGroup group) {
            mInflater.postInflateProcess(view);
            final int count = group.getChildCount();
            for (int c = 0; c < count; c++) {
                View child = group.getChildAt(c);
                postInflateProcess(child, layoutlibCallback, skip);
            }
        }
    }

    /**
     * If the root layout is a CoordinatorLayout with an AppBar:
     * Set the title of the AppBar to the title of the activity context.
     */
    private void setActiveToolbar(View view, BridgeContext context, SessionParams params) {
        View coordinatorLayout = findChildView(view, DesignLibUtil.CN_COORDINATOR_LAYOUT);
        if (coordinatorLayout == null) {
            return;
        }
        View appBar = findChildView(coordinatorLayout, DesignLibUtil.CN_APPBAR_LAYOUT);
        if (appBar == null) {
            return;
        }
        ViewGroup collapsingToolbar =
                (ViewGroup) findChildView(appBar, DesignLibUtil.CN_COLLAPSING_TOOLBAR_LAYOUT);
        if (collapsingToolbar == null) {
            return;
        }
        if (!hasToolbar(collapsingToolbar)) {
            return;
        }
        String title = params.getAppLabel();
        DesignLibUtil.setTitle(collapsingToolbar, title);
    }

    private View findChildView(View view, String[] className) {
        if (!(view instanceof ViewGroup group)) {
            return null;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            if (isInstanceOf(group.getChildAt(i), className)) {
                return group.getChildAt(i);
            }
        }
        return null;
    }

    private boolean hasToolbar(View collapsingToolbar) {
        if (!(collapsingToolbar instanceof ViewGroup group)) {
            return false;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            if (isInstanceOf(group.getChildAt(i), DesignLibUtil.CN_TOOLBAR)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the scroll position on all the components with the "scrollX" and "scrollY" attribute. If
     * the component supports nested scrolling attempt that first, then use the unconsumed scroll
     * part to scroll the content in the component.
     */
    private static void handleScrolling(BridgeContext context, View view) {
        int scrollPosX = context.getScrollXPos(view);
        int scrollPosY = context.getScrollYPos(view);
        if (scrollPosX != 0 || scrollPosY != 0) {
            if (view.isNestedScrollingEnabled()) {
                int[] consumed = new int[2];
                int axis = scrollPosX != 0 ? View.SCROLL_AXIS_HORIZONTAL : 0;
                axis |= scrollPosY != 0 ? View.SCROLL_AXIS_VERTICAL : 0;
                if (view.startNestedScroll(axis)) {
                    view.dispatchNestedPreScroll(scrollPosX, scrollPosY, consumed, null);
                    view.dispatchNestedScroll(consumed[0], consumed[1], scrollPosX, scrollPosY,
                            null);
                    view.stopNestedScroll();
                    scrollPosX -= consumed[0];
                    scrollPosY -= consumed[1];
                }
            }
            if (scrollPosX != 0 || scrollPosY != 0) {
                view.scrollTo(scrollPosX, scrollPosY);
            }
        }

        if (!(view instanceof ViewGroup group)) {
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            handleScrolling(context, child);
        }
    }

    /**
     * Sets up a {@link TabHost} object.
     * @param tabHost the TabHost to setup.
     * @param layoutlibCallback The project callback object to access the project R class.
     * @throws PostInflateException if TabHost is missing the required ids for TabHost
     */
    private void setupTabHost(TabHost tabHost, LayoutlibCallback layoutlibCallback)
            throws PostInflateException {
        // look for the TabWidget, and the FrameLayout. They have their own specific names
        View v = tabHost.findViewById(android.R.id.tabs);

        if (v == null) {
            throw new PostInflateException(
                    "TabHost requires a TabWidget with id \"android:id/tabs\".\n");
        }

        if (!(v instanceof TabWidget)) {
            throw new PostInflateException(String.format(
                    "TabHost requires a TabWidget with id \"android:id/tabs\".\n" +
                    "View found with id 'tabs' is '%s'", v.getClass().getCanonicalName()));
        }

        v = tabHost.findViewById(android.R.id.tabcontent);

        if (v == null) {
            // TODO: see if we can fake tabs even without the FrameLayout (same below when the frameLayout is empty)
            //noinspection SpellCheckingInspection
            throw new PostInflateException(
                    "TabHost requires a FrameLayout with id \"android:id/tabcontent\".");
        }

        if (!(v instanceof FrameLayout content)) {
            //noinspection SpellCheckingInspection
            throw new PostInflateException(String.format(
                    "TabHost requires a FrameLayout with id \"android:id/tabcontent\".\n" +
                    "View found with id 'tabcontent' is '%s'", v.getClass().getCanonicalName()));
        }

        // now process the content of the frameLayout and dynamically create tabs for it.
        final int count = content.getChildCount();

        // this must be called before addTab() so that the TabHost searches its TabWidget
        // and FrameLayout.
        if (isInstanceOf(tabHost, FragmentTabHostUtil.CN_FRAGMENT_TAB_HOST)) {
            FragmentTabHostUtil.setup(tabHost, getContext());
        } else {
            tabHost.setup();
        }

        if (count == 0) {
            // Create a placeholder child to get a single tab
            TabSpec spec = tabHost.newTabSpec("tag")
                    .setIndicator("Tab Label", tabHost.getResources()
                            .getDrawable(android.R.drawable.ic_menu_info_details, null))
                    .setContent(tag -> new LinearLayout(getContext()));
            tabHost.addTab(spec);
        } else {
            // for each child of the frameLayout, add a new TabSpec
            for (int i = 0 ; i < count ; i++) {
                View child = content.getChildAt(i);
                String tabSpec = String.format("tab_spec%d", i+1);
                int id = child.getId();
                ResourceReference resource = layoutlibCallback.resolveResourceId(id);
                String name;
                if (resource != null) {
                    name = resource.getName();
                } else {
                    name = String.format("Tab %d", i+1); // default name if id is unresolved.
                }
                tabHost.addTab(tabHost.newTabSpec(tabSpec).setIndicator(name).setContent(id));
            }
        }
    }

    /**
     * Visits a {@link View} and its children and generate a {@link ViewInfo} containing the
     * bounds of all the views.
     *
     * @param view the root View
     * @param hOffset horizontal offset for the view bounds.
     * @param vOffset vertical offset for the view bounds.
     * @param isContentFrame {@code true} if the {@code ViewInfo} to be created is part of the
     *                       content frame.
     *
     * @return {@code ViewInfo} containing the bounds of the view and it children otherwise.
     */
    private ViewInfo visit(View view, int hOffset, int vOffset, SessionParams params,
            boolean isContentFrame) {
        ViewInfo result = createViewInfo(view, hOffset, vOffset, params.getExtendedViewInfoMode(),
                isContentFrame);

        if (view instanceof ViewGroup group) {
            result.setChildren(visitAllChildren(List.of(group), isContentFrame ? 0 : hOffset,
                    isContentFrame ? 0 : vOffset,
                    params, isContentFrame));
        }
        return result;
    }

    /**
     * Visits all the children of a given ViewGroup and generates a list of {@link ViewInfo}
     * containing the bounds of all the views. It also initializes the {@link #mViewInfoList} with
     * the children of the {@code mContentRoot}.
     *
     * @param viewGroupList List of root Views
     * @param hOffset horizontal offset from the top for the content view frame.
     * @param vOffset vertical offset from the top for the content view frame.
     * @param isContentFrame {@code true} if the {@code ViewInfo} to be created is part of the
     *                       content frame. {@code false} if the {@code ViewInfo} to be created is
     *                       part of the system decor.
     */
    private List<ViewInfo> visitAllChildren(List<ViewGroup> viewGroupList, int hOffset, int vOffset,
            SessionParams params, boolean isContentFrame) {
        if (viewGroupList == null) {
            return null;
        }

        List<ViewInfo> childrenWithoutOffset = new ArrayList<>();
        List<ViewInfo> childrenWithOffset = new ArrayList<>();

        for (ViewGroup viewGroup : viewGroupList) {
            int currentVOffset = vOffset;
            int currentHOffset = hOffset;
            if (!isContentFrame) {
                ViewRootImpl rootImpl = viewGroup.getViewRootImpl();
                if (rootImpl != null) {
                    Rect frame = ViewRootImpl_Accessor.getWindowFrame(rootImpl);
                    currentVOffset += frame.top;
                    currentHOffset += frame.left;
                } else {
                    currentVOffset += viewGroup.getTop();
                    currentHOffset += viewGroup.getLeft();
                }
            }

            int childCount = viewGroup.getChildCount();
            if (viewGroup == mContentRoot) {
                for (int i = 0; i < childCount; i++) {
                    ViewInfo[] childViewInfo =
                            visitContentRoot(viewGroup.getChildAt(i), currentHOffset,
                                    currentVOffset, params);
                    childrenWithoutOffset.add(childViewInfo[0]);
                    childrenWithOffset.add(childViewInfo[1]);
                }
                mViewInfoList = childrenWithOffset;
            } else {
                for (int i = 0; i < childCount; i++) {
                    childrenWithoutOffset.add(
                            visit(viewGroup.getChildAt(i), currentHOffset, currentVOffset, params,
                                    isContentFrame));
                }
            }
        }
        return childrenWithoutOffset;
    }

    /**
     * Visits the children of {@link #mContentRoot} and generates {@link ViewInfo} containing the
     * bounds of all the views. It returns two {@code ViewInfo} objects with the same children,
     * one with the {@code offset} and other without the {@code offset}. The offset is needed to
     * get the right bounds if the {@code ViewInfo} hierarchy is accessed from
     * {@code mViewInfoList}. When the hierarchy is accessed via {@code mSystemViewInfoList}, the
     * offset is not needed.
     * If a custom parser was passed inside the {@link SessionParams} argument, this will be used
     * to generate the {@link ViewInfo}s. Otherwise, {@link RenderSessionImpl#visitAllChildren}
     * will be used.
     *
     * @return an array of length two, with ViewInfo at index 0 is without offset and ViewInfo at
     *         index 1 is with the offset.
     */
    @NonNull
    private ViewInfo[] visitContentRoot(View view, int hOffset, int vOffset, SessionParams params) {
        ViewInfo[] result = new ViewInfo[2];
        if (view == null) {
            return result;
        }

        boolean setExtendedInfo = params.getExtendedViewInfoMode();
        result[0] = createViewInfo(view, 0, 0, setExtendedInfo, true);
        result[1] = createViewInfo(view, hOffset, vOffset, setExtendedInfo, true);
        Function<Object, List<ViewInfo>> customParser = params.getCustomContentHierarchyParser();
        List<ViewInfo> children = null;
        if (customParser != null) {
            children = customParser.apply(view);
        } else if (view instanceof ViewGroup) {
            children = visitAllChildren(List.of((ViewGroup) view), 0, 0, params, true);
        }
        result[0].setChildren(children);
        result[1].setChildren(children);
        return result;
    }

    /**
     * Creates a {@link ViewInfo} for the view. The {@code ViewInfo} corresponding to the children
     * of the {@code view} are not created. Consequently, the children of {@code ViewInfo} is not
     * set.
     * @param hOffset horizontal offset for the view bounds. Used only if view is part of the
     * content frame.
     * @param vOffset vertical an offset for the view bounds. Used only if view is part of the
     * content frame.
     */
    private ViewInfo createViewInfo(View view, int hOffset, int vOffset, boolean setExtendedInfo,
            boolean isContentFrame) {
        if (view == null) {
            return null;
        }

        ViewParent parent = view.getParent();
        ViewInfo result;
        if (isContentFrame) {
            // Account for parent scroll values when calculating the bounding box
            int scrollX = parent != null ? ((View)parent).getScrollX() : 0;
            int scrollY = parent != null ? ((View)parent).getScrollY() : 0;

            // The view is part of the layout added by the user. Hence,
            // the ViewCookie may be obtained only through the Context.
            int shiftX = -scrollX + Math.round(view.getTranslationX()) + hOffset;
            int shiftY = -scrollY + Math.round(view.getTranslationY()) + vOffset;
            result = new ViewInfo(view.getClass().getName(),
                    getViewKey(view),
                    shiftX + view.getLeft(),
                    shiftY + view.getTop(),
                    shiftX + view.getRight(),
                    shiftY + view.getBottom(),
                    view, null, view.getLayoutParams());
        } else {
            // We are part of the system decor.
            SystemViewInfo r = new SystemViewInfo(view.getClass().getName(),
                    getViewKey(view),
                    view.getLeft(), view.getTop(), view.getRight(),
                    view.getBottom(), view, null, view.getLayoutParams());
            result = r;
            // We currently mark three kinds of views:
            // 1. Menus in the Action Bar
            // 2. Menus in the Overflow popup.
            // 3. The overflow popup button.
            if (view instanceof ListMenuItemView) {
                // Mark 2.
                // All menus in the popup are of type ListMenuItemView.
                r.setViewType(ViewType.ACTION_BAR_OVERFLOW_MENU);
            } else {
                // Mark 3.
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp instanceof ActionMenuView.LayoutParams &&
                        ((ActionMenuView.LayoutParams) lp).isOverflowButton) {
                    r.setViewType(ViewType.ACTION_BAR_OVERFLOW);
                } else {
                    // Mark 1.
                    // A view is a menu in the Action Bar is it is not the overflow button and of
                    // its parent is of type ActionMenuView. We can also check if the view is
                    // instanceof ActionMenuItemView but that will fail for menus using
                    // actionProviderClass.
                    while (parent != mViewRoot && parent instanceof ViewGroup) {
                        if (parent instanceof ActionMenuView) {
                            r.setViewType(ViewType.ACTION_BAR_MENU);
                            break;
                        }
                        parent = parent.getParent();
                    }
                }
            }
        }

        if (setExtendedInfo) {
            MarginLayoutParams marginParams = null;
            LayoutParams params = view.getLayoutParams();
            if (params instanceof MarginLayoutParams) {
                marginParams = (MarginLayoutParams) params;
            }
            result.setExtendedInfo(view.getBaseline(),
                    marginParams != null ? marginParams.leftMargin : 0,
                    marginParams != null ? marginParams.topMargin : 0,
                    marginParams != null ? marginParams.rightMargin : 0,
                    marginParams != null ? marginParams.bottomMargin : 0);
        }

        return result;
    }

    /* (non-Javadoc)
     * The cookie for menu items are stored in menu item and not in the map from View stored in
     * BridgeContext.
     */
    @Nullable
    private Object getViewKey(View view) {
        BridgeContext context = getContext();
        if ("com.google.android.material.tabs.TabLayout.TabView".equals(
                view.getClass().getCanonicalName())) {
            // TabView from the material library is a LinearLayout, but it is defined in XML
            // as a TabItem. Because of this, TabView doesn't get the correct cookie, but its
            // children do. So this reassigns the cookie from the first child to link the XML
            // TabItem to the actual TabView view.
            ViewGroup tabView = (ViewGroup)view;
            if (tabView.getChildCount() > 0) {
                return context.getViewKey(tabView.getChildAt(0));
            }
        }
        if (!(view instanceof MenuView.ItemView)) {
            return context.getViewKey(view);
        }
        MenuItemImpl menuItem;
        if (view instanceof ActionMenuItemView) {
            menuItem = ((ActionMenuItemView) view).getItemData();
        } else if (view instanceof ListMenuItemView) {
            menuItem = ((ListMenuItemView) view).getItemData();
        } else if (view instanceof IconMenuItemView) {
            menuItem = ((IconMenuItemView) view).getItemData();
        } else {
            menuItem = null;
        }
        if (menuItem instanceof BridgeMenuItemImpl) {
            return ((BridgeMenuItemImpl) menuItem).getViewCookie();
        }

        return null;
    }

    public void invalidateRenderingSize() {
        mMeasuredScreenWidth = mMeasuredScreenHeight = -1;
    }

    @Nullable
    public RecyclableImage getRecyclableImage() {
        if (mImage == null) {
            return null;
        }
        RecyclableImage recyclableImage = new LayoutlibRecyclableImage(
                mImageWidth,
                mImageHeight,
                mImage,
                mBufferPool
        );
        mImage = null;
        return recyclableImage;
    }

    public List<ViewInfo> getViewInfos() {
        return mViewInfoList;
    }

    public List<ViewInfo> getSystemViewInfos() {
        return mSystemViewInfoList;
    }

    public Map<Object, Map<ResourceReference, ResourceValue>> getDefaultNamespacedProperties() {
        return getContext().getDefaultProperties();
    }

    public Map<Object, String> getDefaultStyles() {
        Map<Object, String> defaultStyles = new IdentityHashMap<>();
        Map<Object, ResourceReference> namespacedStyles = getDefaultNamespacedStyles();
        for (Object key : namespacedStyles.keySet()) {
            ResourceReference style = namespacedStyles.get(key);
            defaultStyles.put(key, style.getQualifiedName());
        }
        return defaultStyles;
    }

    public Map<Object, ResourceReference> getDefaultNamespacedStyles() {
        return getContext().getDefaultNamespacedStyles();
    }

    @Nullable
    public ValidatorHierarchy getValidatorHierarchy() {
        return mValidatorHierarchy;
    }

    private void setValidatorHierarchy(@NotNull ValidatorHierarchy validatorHierarchy) {
        mValidatorHierarchy = validatorHierarchy;
    }

    public void setScene(RenderSession session) {
        mScene = session;
    }

    public RenderSession getSession() {
        return mScene;
    }

    public void dispatchTouchEvent(int motionEventType, long currentTimeNanos, float x, float y) {
        // Events should be dispatched to the top window if there are more than one present.
        ViewGroup root = null;
        List<View> views = getWindowViews();

        for (int i = views.size() - 1; i >= 0; i--) {
            View view = views.get(i);
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();

            if ((params.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                continue;
            }

            int left;
            int top;
            ViewRootImpl rootImpl = view.getViewRootImpl();
            if (rootImpl != null) {
                Rect frame = ViewRootImpl_Accessor.getWindowFrame(rootImpl);
                left = frame.left;
                top = frame.top;
            } else {
                left = view.getLeft();
                top = view.getTop();
            }
            int right = left + view.getWidth();
            int bottom = top + view.getHeight();

            boolean isInside = x >= left && x <= right && y >= top && y <= bottom;

            if (isInside || params.isModal()) {
                root = (ViewGroup) view;
                x -= left;
                y -= top;
                break;
            }
        }

        if (root == null) {
            root = mViewRoot;
        }
        if (root == null) {
            return;
        }
        if (motionEventType == MotionEvent.ACTION_DOWN) {
            mLastActionDownTimeNanos = currentTimeNanos;
        }
        // Ignore events not started with MotionEvent.ACTION_DOWN
        if (mLastActionDownTimeNanos == -1) {
            return;
        }

        mPointerProperties[0].id = 0;
        mPointerProperties[0].toolType = MotionEvent.TOOL_TYPE_FINGER;

        mPointerCoords[0].clear();
        mPointerCoords[0].x = x;
        mPointerCoords[0].y = y;
        mPointerCoords[0].pressure = 1.0f;
        mPointerCoords[0].size = 1.0f;

        MotionEvent event = MotionEvent.obtain(
            mLastActionDownTimeNanos / TimeUtils.NANOS_PER_MS,
            currentTimeNanos / TimeUtils.NANOS_PER_MS,
            motionEventType,
            1, mPointerProperties, mPointerCoords,
            0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);

        root.dispatchTouchEvent(event);
    }

    public void dispatchKeyEvent(java.awt.event.KeyEvent event, long currentTimeNanos) {
        List<View> views = getWindowViews();
        ViewGroup root = null;

        for (int i = views.size() - 1; i >= 0; i--) {
            View view = views.get(i);
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
            // In Android, the window manager keeps track of the focused window.
            // For layoutlib, we'll assume the top-most visible window that can have focus is the one.
            if (view.getVisibility() == View.VISIBLE &&
                    (params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
                root = (ViewGroup) view;
                break;
            }
        }

        if (root == null) {
            root = mViewRoot;
        }

        if (root == null) {
            return;
        }

        // Ensure the target window has focus so it can process the key event (e.g. for focus navigation)
        for (View view : views) {
            AttachInfo_Accessor.setHasWindowFocus(view, view == root);
        }

        if (event.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
            mLastActionDownTimeNanos = currentTimeNanos;
        }
        // Ignore events not started with KeyEvent.ACTION_DOWN
        if (mLastActionDownTimeNanos == -1) {
            return;
        }

        KeyEvent androidEvent = KeyEventHandling.javaToAndroidKeyEvent(event,
                mLastActionDownTimeNanos, currentTimeNanos);
        boolean success = root.dispatchKeyEvent(androidEvent);
        if (!success && root != mViewRoot) {
            // If the event was not consumed by a Window, pass it down to the root layout
            mViewRoot.dispatchKeyEvent(androidEvent);
        }
    }

    @Override
    public void release() {
        super.release();
        List<View> views = getWindowViews();
        for (View view : views) {
            ViewRootImpl viewRootImpl = view.getViewRootImpl();
            if (viewRootImpl != null) {
                ViewRootImpl_Accessor.detachFromWindow(viewRootImpl);
            }
        }
    }

    private void disposeImageSurface() {
        if (mRenderer != null) {
            mRenderer.reset();
        }
    }

    public void releaseRender() {
        disposeImageSurface();
        mNewRenderSize = true;
    }
    @Override
    public void dispose() {
        try {
            releaseRender();
            if (mWindowChangeListener != null) {
                WindowManagerGlobal.getInstance().mWindowViewsListenerGroup.mListeners
                        .remove(mWindowChangeListener);
                mWindowChangeListener = null;
            }
            if (mRenderer != null) {
                mRenderer.destroy();
                mRenderer = null;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            List<View> views = getWindowViews();
            for (View view : views) {
                wm.removeViewImmediate(view);
            }
            getContext().getSessionInteractiveData().dispose();
            if (mViewInfoList != null) {
                mViewInfoList.clear();
            }
            if (mSystemViewInfoList != null) {
                mSystemViewInfoList.clear();
            }
            mBufferPool.clear();
            AnimatedVectorDrawable_VectorDrawableAnimatorUI_Delegate.sFrameTime = 0;
            mValidatorHierarchy = null;
            mViewRoot = null;
            mContentRoot = null;
            mBlockParser = null;
        } catch (Throwable t) {
            getContext().error("Error while disposing a RenderSession", t);
        }
        super.dispose();
    }
}
