/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.layoutlib.bridge.intensive;

import com.android.ide.common.rendering.api.RecyclableImage;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.rendering.api.XmlParserFactory;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.internal.R;
import com.android.internal.lang.System_Delegate;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.android.RenderParamsFlags;
import com.android.layoutlib.bridge.android.RenderTestBase;
import com.android.layoutlib.bridge.impl.ParserFactory;
import com.android.layoutlib.bridge.impl.RenderAction;
import com.android.layoutlib.bridge.impl.RenderActionTestUtil;
import com.android.layoutlib.bridge.impl.ResourceHelper;
import com.android.layoutlib.bridge.intensive.setup.ConfigGenerator;
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser;
import com.android.resources.Density;
import com.android.resources.Navigation;
import com.android.resources.ResourceType;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources_Delegate;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.StateSet;
import android.widget.Button;
import android.widget.LinearLayout;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static android.os._Original_Build.VERSION.SDK_INT;
import static com.android.layoutlib.bridge.android.RenderParamsFlags.FLAG_KEY_SHOW_CUTOUT;
import static com.android.layoutlib.bridge.android.RenderParamsFlags.FLAG_KEY_USE_GESTURE_NAV;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Set of render tests
 */
public class RenderTests extends RenderTestBase {

    @After
    public void afterTestCase() {
        com.android.layoutlib.bridge.test.widgets.HookWidget.reset();
    }

    @Test
    public void testActivity() throws ClassNotFoundException, FileNotFoundException {
        renderAndVerify("activity.xml", "activity.png", true);
    }

    @Test
    public void testActivityOnOldTheme() throws ClassNotFoundException {
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                                android:layout_width="match_parent"
                                android:layout_height="match_parent"
                                android:paddingLeft="@dimen/activity_horizontal_margin"
                                android:paddingRight="@dimen/activity_horizontal_margin"
                                android:paddingTop="@dimen/activity_vertical_margin"
                                android:paddingBottom="@dimen/activity_vertical_margin">
                    <TextView
                        android:text="@string/hello_world"
                        android:layout_width="wrap_content"
                        android:layout_height="200dp"
                        android:background="#FF0000"
                        android:id="@+id/text1"/>
                </RelativeLayout>""");
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.NoTitleBar", false)
                .build();

        renderAndVerify(params, "simple_activity-old-theme.png");
    }

    @Test
    public void testTranslucentBars() throws ClassNotFoundException {
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        LayoutPullParser parser = createParserFromPath("four_corners.xml");
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.TranslucentDecor", false)
                .build();
        renderAndVerify(params, "four_corners_translucent.png");

        parser = createParserFromPath("four_corners.xml");
        params = getSessionParamsBuilder()
                .setConfigGenerator(ConfigGenerator.NEXUS_5_LAND)
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.TranslucentDecor", false)
                .build();
        renderAndVerify(params, "four_corners_translucent_land.png");

        parser = createParserFromPath("four_corners.xml");
        params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar", false)
                .build();
        renderAndVerify(params, "four_corners.png");
    }

    @Test
    public void testAllWidgets() throws ClassNotFoundException {
        LayoutPullParser parser = createParserFromPath("allwidgets.xml");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(ConfigGenerator.NEXUS_5)
                .setCallback(layoutLibCallback)
                .build();

        renderAndVerify(params, "allwidgets.png");
    }

    @Test
    public void testArrayCheck() throws ClassNotFoundException, FileNotFoundException {
        renderAndVerify("array_check.xml", "array_check.png", false);
    }

    @Test
    public void testAllWidgetsTablet() throws ClassNotFoundException {
        LayoutPullParser parser = createParserFromPath("allwidgets.xml");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(ConfigGenerator.NEXUS_7_2012)
                .setCallback(layoutLibCallback)
                .build();
        renderAndVerify(params, "allwidgets_tab.png");
    }

    @Test
    public void testActivityActionBar() throws ClassNotFoundException {
        String simpleActivity = """
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                                android:layout_width="match_parent"
                                android:layout_height="match_parent"
                                android:paddingLeft="@dimen/activity_horizontal_margin"
                                android:paddingRight="@dimen/activity_horizontal_margin"
                                android:paddingTop="@dimen/activity_vertical_margin"
                                android:paddingBottom="@dimen/activity_vertical_margin">
                    <TextView
                        android:text="@string/hello_world"
                        android:layout_width="wrap_content"
                        android:layout_height="200dp"
                        android:background="#FF0000"
                        android:id="@+id/text1"/>
                </RelativeLayout>""";

        LayoutPullParser parser = LayoutPullParser.createFromString(simpleActivity);
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "simple_activity_noactionbar.png");

        parser = LayoutPullParser.createFromString(simpleActivity);
        params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "simple_activity.png");

        // This also tests that a theme with "NoActionBar" DOES HAVE an action bar when we are
        // displaying menus.
        parser = LayoutPullParser.createFromString(simpleActivity);
        params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ROOT_TAG, "menu");
        renderAndVerify(params, "simple_activity.png");
    }

    @Test
    public void testOnApplyInsetsCall()
            throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        // We get the widget via reflection to avoid IntelliJ complaining about the class being
        // located in the wrong package. (From the Bridge tests point of view, it is)
        Class<?> insetsWidgetClass = Class.forName(
                "com.android.layoutlib.test.myapplication.widgets.InsetsWidget");
        Field field = insetsWidgetClass.getDeclaredField("sApplyInsetsCalled");
        assertFalse((Boolean) field.get(null));

        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="wrap_content"
                              android:layout_height="wrap_content">

                    <com.android.layoutlib.test.myapplication.widgets.InsetsWidget
                        android:text="Hello world"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:id="@+id/text1"/>
                </LinearLayout>
                """);
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar", false)
                .build();

        render(sBridge, params, -1);

        assertTrue((Boolean) field.get(null));
        field.set(null, false);
    }

    /** Test expand_layout.xml */
    @Test
    public void testExpand() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = createParserFromPath("expand_vert_layout.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        ConfigGenerator customConfigGenerator = new ConfigGenerator()
                .setScreenWidth(300)
                .setScreenHeight(20)
                .setDensity(Density.XHIGH)
                .setNavigation(Navigation.NONAV);

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(customConfigGenerator)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "expand_vert_layout.png");

        customConfigGenerator = new ConfigGenerator()
                .setScreenWidth(20)
                .setScreenHeight(300)
                .setDensity(Density.XHIGH)
                .setNavigation(Navigation.NONAV);
        parser = createParserFromPath("expand_horz_layout.xml");
        params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(customConfigGenerator)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.H_SCROLL)
                .build();

        renderAndVerify(params, "expand_horz_layout.png");
    }

    @Test
    public void testShrink() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = createParserFromPath("expand_vert_layout.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        // Normal mode
        ConfigGenerator customConfigGenerator = new ConfigGenerator()
                .setScreenWidth(600)
                .setScreenHeight(3000)
                .setDensity(Density.XHIGH)
                .setNavigation(Navigation.NONAV);
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(customConfigGenerator)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.NORMAL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "normal_layout.png");

        // Shrink mode
        customConfigGenerator = new ConfigGenerator()
                .setScreenWidth(600)
                .setScreenHeight(3000)
                .setDensity(Density.XHIGH)
                .setNavigation(Navigation.NONAV);
        parser = createParserFromPath("expand_vert_layout.xml");
        params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(customConfigGenerator)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.SHRINK)
                .disableDecoration()
                .build();

        renderAndVerify(params, "shrunk_layout.png");
    }

    /** Test indeterminate_progressbar.xml */
    @Test
    public void testVectorAnimation() throws ClassNotFoundException {
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">

                    <ProgressBar
                             android:layout_height="fill_parent"
                             android:layout_width="fill_parent" />

                </LinearLayout>
                """;

        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "animated_vector.png", TimeUnit.SECONDS.toNanos(2));

        parser = LayoutPullParser.createFromString(layout);
        params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        renderAndVerify(params, "animated_vector_1.png", TimeUnit.SECONDS.toNanos(3));
    }

    /**
     * Test a vector drawable that uses trimStart and trimEnd. It also tests all the primitives
     * for vector drawables (lines, moves and cubic and quadratic curves).
     */
    @Test
    public void testVectorDrawable() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <ImageView
                             android:layout_height="fill_parent"
                             android:layout_width="fill_parent"
                             android:src="@drawable/multi_path" />

                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .disableDecoration()
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "vector_drawable.png", TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Regression test for http://b.android.com/91383 and http://b.android.com/203797
     */
    @Test
    public void testVectorDrawable91383() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="vertical"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <ImageView
                        android:layout_height="wrap_content"
                        android:layout_width="wrap_content"
                        android:src="@drawable/android"/>
                    <ImageView
                        android:layout_height="wrap_content"
                        android:layout_width="wrap_content"
                        android:src="@drawable/headset"/>
                    <ImageView
                        android:layout_height="wrap_content"
                        android:layout_width="wrap_content"
                        android:src="@drawable/clipped_even_odd"/>
                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "vector_drawable_91383.png", TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Test a ImageView which has a vector drawable as its src and tint attribute.
     */
    @Test
    public void testVectorDrawableWithTintInImageView() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <ImageView xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_height="match_parent"
                    android:layout_width="match_parent"
                    android:src="@drawable/vector_drawable_without_tint"
                    android:tint="#FF00FF00" />""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "vector_drawable_with_tint_in_image_view.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Test a vector drawable which has tint attribute.
     */
    @Test
    public void testVectorDrawableWithTintInItself() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <ImageView xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_height="match_parent"
                    android:layout_width="match_parent"
                    android:src="@drawable/vector_drawable_with_tint" />""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "vector_drawable_with_tint_itself.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Test a vector drawable which is transparent.
     */
    @Test
    public void testTransparentDrawable() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <ImageView xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_height="fill_parent"
                    android:layout_width="fill_parent"
                    android:src="@drawable/transparent_drawable" />""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .setTransparentBackground()
                .build();

        renderAndVerify(params, "transparent_drawable.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Test a vector drawable that uses trimStart and trimEnd. It also tests all the primitives
     * for vector drawables (lines, moves and cubic and quadratic curves).
     */
    @Ignore("This test does not make sense in layoutlib anymore, test in Studio")
    @Test
    public void testVectorDrawableHasMultipleLineInPathData() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">
                    <ImageView
                             android:layout_height="match_parent"
                             android:layout_width="match_parent"
                             android:src="@drawable/multi_line_of_path_data" />

                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "vector_drawable_multi_line_of_path_data.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Tests that the gradients are correctly transformed using the viewport of the vector drawable.
     * <p/>
     * If a vector drawable is 50x50 and the gradient has startX=25 and startY=25, the gradient
     * will start in the middle of the box.
     * <p/>
     * http://b/65495452
     */
    @Test
    public void testVectorDrawableGradient() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">
                    <ImageView
                             android:layout_height="match_parent"
                             android:layout_width="match_parent"
                             android:src="@drawable/shadow" />

                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "vector_drawable_gradient.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Tests that the radial gradients are correctly transformed using the viewport of the vector
     * drawable.
     * <p/>
     * http://b/66168608
     */
    @Test
    public void testVectorDrawableRadialGradient() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">
                    <ImageView
                             android:layout_height="match_parent"
                             android:layout_width="match_parent"
                             android:src="@drawable/radial_gradient" />

                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "vector_drawable_radial_gradient.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Tests that the gradients are correctly displayed when using transparent colors
     * and a wide range of offset values.
     * <p/>
     * http://b/112759140
     */
    @Test
    public void testGradientColors() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">
                    <ImageView
                             android:layout_height="match_parent"
                             android:layout_width="match_parent"
                             android:src="@drawable/gradient" />

                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "gradient_colors.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Tests that the gradients are correctly combined with alpha values.
     * <p/>
     * http://b/122260583
     */
    @Test
    public void testGradientAlphaDrawable() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">
                    <ImageView
                             android:layout_height="match_parent"
                             android:layout_width="match_parent"
                             android:src="@drawable/vector_gradient_alpha" />

                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "gradient_alpha_drawable.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    /** Test activity.xml */
    @Test
    public void testScrollingAndMeasure() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = createParserFromPath("scrolled.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();
        params.setExtendedViewInfoMode(true);

        // Do an only-measure pass
        RenderSession session = sBridge.createSession(params);
        session.measure();
        RenderResult result = RenderResult.getFromSession(session);
        assertNotNull(result);
        assertNotNull(result.getResult());
        assertTrue(result.getResult().isSuccess());

        ViewInfo rootLayout = result.getRootViews().get(0);
        // Check the first box in the main LinearLayout
        assertEquals(-90, rootLayout.getChildren().get(0).getTop());
        assertEquals(-30, rootLayout.getChildren().get(0).getLeft());
        assertEquals(90, rootLayout.getChildren().get(0).getBottom());
        assertEquals(150, rootLayout.getChildren().get(0).getRight());

        // Check the first box within the nested LinearLayout
        assertEquals(-450, rootLayout.getChildren().get(5).getChildren().get(0).getTop());
        assertEquals(90, rootLayout.getChildren().get(5).getChildren().get(0).getLeft());
        assertEquals(-270, rootLayout.getChildren().get(5).getChildren().get(0).getBottom());
        assertEquals(690, rootLayout.getChildren().get(5).getChildren().get(0).getRight());
        session.dispose();

        // Do a full render pass
        parser = createParserFromPath("scrolled.xml");

        params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();
        params.setExtendedViewInfoMode(true);

        result = renderAndVerify(params, "scrolled.png");
        assertNotNull(result);
        assertNotNull(result.getResult());
        assertTrue(result.getResult().isSuccess());
    }

    @Test
    public void testGetResourceNameVariants() throws Exception {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(ConfigGenerator.NEXUS_4)
                .setCallback(layoutLibCallback)
                .build();
        AssetManager assetManager = AssetManager.getSystem();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics,
                params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());
        Resources resources = Resources_Delegate.initSystem(context, assetManager, metrics,
                configuration, params.getLayoutlibCallback());
        // Test
        assertEquals("android:style/ButtonBar",
                resources.getResourceName(android.R.style.ButtonBar));
        assertEquals("android", resources.getResourcePackageName(android.R.style.ButtonBar));
        assertEquals("ButtonBar", resources.getResourceEntryName(android.R.style.ButtonBar));
        assertEquals("style", resources.getResourceTypeName(android.R.style.ButtonBar));
        int id = Resources_Delegate.getLayoutlibCallback(resources).getOrGenerateResourceId(
                new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "app_name"));
        assertEquals("com.android.layoutlib.test.myapplication:string/app_name",
                resources.getResourceName(id));
        assertEquals("com.android.layoutlib.test.myapplication",
                resources.getResourcePackageName(id));
        assertEquals("string", resources.getResourceTypeName(id));
        assertEquals("app_name", resources.getResourceEntryName(id));

        context.disposeResources();
    }

    @Test
    public void testStringEscaping() throws Exception {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(RenderTestBase.getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setConfigGenerator(ConfigGenerator.NEXUS_4)
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .build();
        AssetManager assetManager = AssetManager.getSystem();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics,
                params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());
        Resources resources = Resources_Delegate.initSystem(context, assetManager, metrics,
                configuration, params.getLayoutlibCallback());

        int id = Resources_Delegate.getLayoutlibCallback(resources).getOrGenerateResourceId(
                new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ARRAY,
                        "string_array"));
        String[] strings = resources.getStringArray(id);
        assertArrayEquals(
                new String[]{"mystring", "Hello world!", "candidates", "Unknown", "?EC"},
                strings);
        assertTrue(sRenderMessages.isEmpty());

        context.disposeResources();
    }

    @Test
    public void testFonts() throws ClassNotFoundException, FileNotFoundException {
        // TODO: styles seem to be broken in TextView
        renderAndVerify("fonts_test.xml", "font_test.png", false);
    }

    @Test
    public void testVariableAndCjkFonts() throws ClassNotFoundException, FileNotFoundException {
        renderAndVerify("variable_cjk_font_test.xml", "variable_cjk_font_test.png", false);
    }

    @Test
    public void testAdaptiveIcon() throws ClassNotFoundException {
        // Create the layout pull parser.
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <ImageView
                             android:layout_height="wrap_content"
                             android:layout_width="wrap_content"
                             android:src="@drawable/adaptive" />
                </LinearLayout>
                """;
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50,0L100,0 100,100 0,100 0,0z");
        renderAndVerify(params, "adaptive_icon.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        renderAndVerify(params, "adaptive_icon_circle.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50,0L92,0C96.42,0 100,4.58 100 8L100,92C100, 96.42 96.42 100 92 100L8 100C4.58," +
                        " 100 0 96.42 0 92L0 8 C 0 4.42 4.42 0 8 0L50 0Z");
        renderAndVerify(params, "adaptive_icon_rounded_corners.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z");
        renderAndVerify(params, "adaptive_icon_squircle.png");
    }

    @Test
    public void testColorStateList() throws Exception {
        final String STATE_LIST = """
                <selector xmlns:android="http://schemas.android.com/apk/res/android">
                    <item android:state_pressed="true"
                          android:color="?android:attr/colorForeground"/>\s
                    <item android:state_focused="true"
                          android:color="?android:attr/colorBackground"/>\s
                    <item android:color="#a000"/> <!-- default -->
                </selector>""";

        File tmpColorList = File.createTempFile("statelist", "xml");
        try (PrintWriter output = new PrintWriter(new FileOutputStream(tmpColorList))) {
            output.println(STATE_LIST);
        }

        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        ParserFactory.setParserFactory(new XmlParserFactory() {
            @Override
            @Nullable
            public XmlPullParser createXmlParserForPsiFile(@NonNull String fileName) {
                return null;
            }

            @Override
            @Nullable
            public XmlPullParser createXmlParserForFile(@NonNull String fileName) {
                return null;
            }

            @Override
            @NonNull
            public XmlPullParser createXmlParser() {
                return new KXmlParser();
            }
        });

        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(RenderTestBase.getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setConfigGenerator(ConfigGenerator.NEXUS_4)
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material", false)
                .build();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);

        BridgeContext mContext =
                new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                        params.getAssets(), params.getLayoutlibCallback(), configuration,
                        params.getTargetSdkVersion(), params.isRtlSupported());
        mContext.initResources(params.getAssets());
        BridgeContext oldContext = RenderActionTestUtil.setBridgeContext(mContext);

        try {
            ColorStateList stateList = ResourceHelper.getColorStateList(
                    new ResourceValueImpl(
                            ResourceNamespace.RES_AUTO,
                            ResourceType.COLOR,
                            "test_list",
                            tmpColorList.getAbsolutePath()),
                    mContext,
                    null);
            assertNotNull(stateList);
            assertEquals(Color.parseColor("#ffffffff"), stateList.getColorForState(
                    StateSet.get(StateSet.VIEW_STATE_PRESSED),
                    0
            ));
            assertEquals(Color.parseColor("#ff303030"), stateList.getColorForState(
                    StateSet.get(StateSet.VIEW_STATE_FOCUSED),
                    0
            ));
            assertEquals(Color.parseColor("#AA000000"), stateList.getDefaultColor());

            // Now apply theme overlay and check the colors changed
            Resources.Theme theme = mContext.getResources().newTheme();
            theme.applyStyle(R.style.ThemeOverlay_Material_Light, true);
            stateList = ResourceHelper.getColorStateList(
                    new ResourceValueImpl(
                            ResourceNamespace.RES_AUTO,
                            ResourceType.COLOR,
                            "test_list",
                            tmpColorList.getAbsolutePath()),
                    mContext,
                    theme);
            assertNotNull(stateList);
            assertEquals(Color.parseColor("#ff000000"), stateList.getColorForState(
                    StateSet.get(StateSet.VIEW_STATE_PRESSED),
                    0
            ));
            assertEquals(Color.parseColor("#fffafafa"), stateList.getColorForState(
                    StateSet.get(StateSet.VIEW_STATE_FOCUSED),
                    0
            ));
            assertEquals(Color.parseColor("#AA000000"), stateList.getDefaultColor());
        } finally {
            RenderActionTestUtil.setBridgeContext(oldContext);
        }
        mContext.disposeResources();
    }

    @Test
    public void testResourcesGetIdentifier() throws Exception {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setConfigGenerator(ConfigGenerator.NEXUS_4)
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .build();
        AssetManager assetManager = AssetManager.getSystem();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics,
                params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());
        Resources resources = Resources_Delegate.initSystem(context, assetManager, metrics,
                configuration, params.getLayoutlibCallback());
        Integer id =
                Resources_Delegate.getLayoutlibCallback(resources)
                        .getOrGenerateResourceId(
                                new ResourceReference(
                                        ResourceNamespace.RES_AUTO,
                                        ResourceType.STRING,
                                        "app_name"));
        assertNotNull(id);
        assertEquals(id.intValue(),
                resources.getIdentifier("com.android.layoutlib.test.myapplication:string/app_name",
                        null, null));
        assertEquals(id.intValue(), resources.getIdentifier("app_name", "string",
                "com.android.layoutlib.test.myapplication"));
        assertEquals(0, resources.getIdentifier("string/app_name", null, null));
        assertEquals(0, resources.getIdentifier("string/app_name", null, "com.foo.bar"));
        assertEquals(0, resources.getIdentifier("string/does_not_exist", null,
                "com.android.layoutlib.test.myapplication"));
        assertEquals(R.string.accept, resources.getIdentifier("android:string/accept", null,
                null));
        assertEquals(R.string.accept, resources.getIdentifier("string/accept", null,
                "android"));
        assertEquals(R.id.message, resources.getIdentifier("id/message", null,
                "android"));
        assertEquals(R.string.accept, resources.getIdentifier("accept", "string",
                "android"));

        context.disposeResources();
    }

    /**
     * If a 9patch image was in the nodpi or anydpi folder, the density of the image was 0 resulting
     * in a float division by 0 and thus an infinite padding
     * when layoutlib tries to scale the padding of the 9patch.
     *
     * http://b/37136109
     */
    @Test
    public void test9PatchNoDPIBackground() throws Exception {
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        String layoutCompiled = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="@drawable/ninepatch"
                    android:layout_margin="20dp"
                    android:orientation="vertical">

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />
                </LinearLayout>""";

        LayoutPullParser parser = LayoutPullParser.createFromString(layoutCompiled);
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "ninepatch_background.png");

        String layoutNonCompiled = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="@drawable/uncompiled_ninepatch"
                    android:layout_margin="20dp"
                    android:orientation="vertical">

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />
                </LinearLayout>""";

        parser = LayoutPullParser.createFromString(layoutNonCompiled);
        params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "ninepatch_background.png");
    }

    @Test
    public void testAssetManager() throws Exception {
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <com.android.layoutlib.test.myapplication.widgets.AssetView
                             android:layout_height="wrap_content"
                             android:layout_width="wrap_content" />
                </LinearLayout>
                """;
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "asset.png");
    }

    /**
     * Tests that calling setTheme in a ContextThemeWrapper actually applies the theme
     *
     * http://b/66902070
     */
    @Test
    public void testContextThemeWrapper() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <com.android.layoutlib.test.myapplication.ThemableWidget
                     xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="wrap_content"
                              android:layout_height="wrap_content" />
                """);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "context_theme_wrapper.png", TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Tests that a crashing view does not prevent others from working. This is meant to prevent
     * crashes in framework views since custom views are already handled by Android Studio by
     * rewriting the byte code.
     */
    @Test
    public void testCrashes() throws ClassNotFoundException {
        final String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">
                <com.android.layoutlib.bridge.test.widgets.HookWidget
                              android:layout_width="100dp"
                              android:layout_height="200dp" />
                <LinearLayout
                              android:background="#CBBAF0"
                              android:layout_width="100dp"
                              android:layout_height="200dp" />
                </LinearLayout>""";
        {
            com.android.layoutlib.bridge.test.widgets.HookWidget.setOnPreDrawHook(() -> {
                throw new NullPointerException();
            });

            // Create the layout pull parser.
            LayoutPullParser parser = LayoutPullParser.createFromString(layout);
            // Create LayoutLibCallback.
            LayoutLibTestCallback layoutLibCallback =
                    new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
            layoutLibCallback.initResources();

            SessionParams params = getSessionParamsBuilder()
                    .setParser(parser)
                    .setCallback(layoutLibCallback)
                    .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                    .setRenderingMode(RenderingMode.V_SCROLL)
                    .build();

            renderAndVerify(params, "ondraw_crash.png", TimeUnit.SECONDS.toNanos(2));
        }

        com.android.layoutlib.bridge.test.widgets.HookWidget.reset();

        {
            com.android.layoutlib.bridge.test.widgets.HookWidget.setOnPreMeasure(() -> {
                throw new NullPointerException();
            });

            LayoutPullParser parser = LayoutPullParser.createFromString(layout);
            LayoutLibTestCallback layoutLibCallback =
                    new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
            layoutLibCallback.initResources();

            SessionParams params = getSessionParamsBuilder()
                    .setParser(parser)
                    .setCallback(layoutLibCallback)
                    .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                    .setRenderingMode(RenderingMode.V_SCROLL)
                    .build();

            renderAndVerify(params, "onmeasure_crash.png", TimeUnit.SECONDS.toNanos(2));
        }

        // We expect the view error messages. Fail for anything else.
        sRenderMessages.removeIf(message -> message.equals("View draw failed"));
        sRenderMessages.removeIf(message -> message.equals("View measure failed"));
    }

    /**
     * Paints the borders of the given {@link ViewInfo} and its children to the passed
     * {@link Graphics2D} context.
     * The method will used the given parentLeft and parentTop as the given vInfo coordinates.
     * The depth is used to calculate different colors for the borders depending on the hierarchy
     * depth.
     */
    private void paintBorders(@NonNull Graphics2D g, int parentLeft, int parentTop, int depth,
            @NonNull ViewInfo vInfo) {
        int leftMargin = Math.max(0, vInfo.getLeftMargin());
        int topMargin = Math.max(0, vInfo.getTopMargin());
        int x = parentLeft + vInfo.getLeft() + leftMargin;
        int y = parentTop + vInfo.getTop() + topMargin;
        int w = vInfo.getRight() - vInfo.getLeft();
        int h = vInfo.getBottom() - vInfo.getTop();
        g.setXORMode(java.awt.Color.decode(Integer.toString(depth * 50000)));
        g.drawRect(x, y, w, h);

        for (ViewInfo child : vInfo.getChildren()) {
            paintBorders(g, x, y, depth + 1, child);
        }
    }

    @Test
    public void testViewBoundariesReporting() throws Exception {
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:background="@drawable/ninepatch"
                              android:layout_margin="20dp"
                              android:orientation="vertical">

                    <TextView
                        android:layout_width="150dp"
                        android:layout_height="50dp"
                        android:background="#FF0"/>
                    <TextView
                        android:layout_width="150dp"
                        android:layout_height="50dp"
                        android:background="#F00"/>
                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:paddingLeft="10dp">
                        <TextView
                            android:layout_width="150dp"
                            android:layout_height="50dp"
                            android:background="#00F"/>
                    </LinearLayout>
                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginLeft="30dp"
                        android:layout_marginTop="15dp">
                        <TextView
                            android:layout_width="150dp"
                            android:layout_height="50dp"
                            android:background="#F0F"/>
                    </LinearLayout>
                </LinearLayout>""";

        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        RenderResult result = render(sBridge, params, -1);
        BufferedImage image = result.getImage();
        assertNotNull(image);
        Graphics2D g = (Graphics2D) image.getGraphics();
        g.setStroke(new BasicStroke(8));
        for (ViewInfo vInfo : result.getSystemViews()) {
            paintBorders(g, 0, 0, 0, vInfo);
        }

        verify("view_boundaries.png", image);
    }

    /**
     * Test rendering of strings that have mixed RTL and LTR scripts.
     * <p>
     * http://b/37510906
     */
    @Test
    public void testMixedRtlLtrRendering() throws Exception {
        //
        final String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textSize="30sp"
                        android:background="#55FF0000"
                        android:text="این یک رشته ایرانی است"/>
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textSize="30sp"
                        android:background="#55FF00FF"
                        android:text="این یک رشته ایرانی است("/>
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textSize="30sp"
                        android:background="#55FAF012"
                        android:text=")(این یک رشته ایرانی است("/>
                </LinearLayout>""";

        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "rtl_ltr.png", -1);
    }

    @Test
    public void testViewStub() throws Exception {
        //
        final String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">

                      <ViewStub
                        xmlns:tools="http://schemas.android.com/tools"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:layout="@layout/four_corners"
                        tools:visibility="visible" />
                </LinearLayout>""";

        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);

        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "view_stub.png", -1);
    }

    @Test
    public void testImageResize() throws ClassNotFoundException {
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="@drawable/ninepatch"
                    android:layout_margin="20dp"
                    android:orientation="vertical">

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />
                </LinearLayout>""");

        // Ask for an image that it's 1/10th the size of the actual device image
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setSizeProvider((width, height) ->
                        new Dimension(width / 10, height / 10))
                .setFlag(RenderParamsFlags.FLAG_KEY_RESULT_IMAGE_AUTO_SCALE, true)
                .build();

        renderAndVerify(params, "auto-scale-image.png");
    }

    @Test
    public void testTranslation() throws ClassNotFoundException, FileNotFoundException {
        RenderResult res = renderAndVerify("translate_test.xml", "translate_test.png", false);
        ViewInfo rootInfo = res.getRootViews().get(0);
        ViewInfo buttonInfo = rootInfo.getChildren().get(0);
        assertEquals(100, buttonInfo.getLeft());
    }

    /**
     * Test a vector drawable that uses trimStart and trimEnd. It also tests all the primitives
     * for vector drawables (lines, moves and cubic and quadratic curves).
     */
    @Test
    public void testCanvas() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <com.android.layoutlib.test.myapplication.widgets.CanvasTestView
                             android:layout_height="fill_parent"
                             android:layout_width="fill_parent"
                             android:src="@drawable/android" />

                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "canvas.png", TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testTypedArrays() throws ClassNotFoundException, FileNotFoundException {
        renderAndVerify("typed_array.xml", "typed_arrays.png", false);
    }

    /**
     * Tests that the gradients are correctly displayed when using transparent colors
     * and a wide range of offset values.
     * <p/>
     * http://b/112759140
     */
    @Test
    public void testAnimatedVectorDrawableWithColorInterpolator() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">
                    <ImageView
                             android:layout_height="match_parent"
                             android:layout_width="match_parent"
                             android:src="@drawable/avd_color_interpolator" />

                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "color_interpolation.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testJustified() throws ClassNotFoundException, FileNotFoundException {
        renderAndVerify("justified_inter_word.xml", "justified_inter_word.png", false);
        renderAndVerify("justified_none.xml", "justified_none.png", false);
    }

    @Test
    public void testManyLineBreaks() throws Exception {
        String layout = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">

                    <EditText
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:fallbackLineSpacing="true"
                        android:text="A very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very very very very very very very very \
                very very very very very very very long text"/>
                </FrameLayout>""";

        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "many_line_breaks.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testNinePatchDrawable() throws Exception {
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <ImageView
                             android:layout_height="fill_parent"
                             android:layout_width="fill_parent"
                             android:src="@drawable/ninepatch_drawable" />
                </LinearLayout>
                """;
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .disableDecoration()
                .build();

        renderAndVerify(params, "ninepatch_drawable.png");
    }

    @Test
    public void testContentId() throws ClassNotFoundException {
        final String layout = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">

                    <com.android.layoutlib.bridge.test.widgets.ContentWidget
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"/>
                </FrameLayout>""";

        {
            // Create the layout pull parser.
            LayoutPullParser parser = LayoutPullParser.createFromString(layout);
            // Create LayoutLibCallback.
            LayoutLibTestCallback layoutLibCallback = new LayoutLibTestCallback(getLogger(),
                    mDefaultClassLoader);
            layoutLibCallback.initResources();

            SessionParams params = getSessionParamsBuilder()
                    .setParser(parser)
                    .setCallback(layoutLibCallback)
                    .build();

            RenderResult result = render(sBridge, params, TimeUnit.SECONDS.toNanos(2));
            BufferedImage image = result.getImage();
            assertNotNull(image);
        }

        {
            // Create the layout pull parser.
            LayoutPullParser parser = LayoutPullParser.createFromString(layout);
            // Create LayoutLibCallback.
            LayoutLibTestCallback layoutLibCallback = new LayoutLibTestCallback(getLogger(),
                    mDefaultClassLoader);
            layoutLibCallback.initResources();

            SessionParams params = getSessionParamsBuilder()
                    .setParser(parser)
                    .setCallback(layoutLibCallback)
                    .disableDecoration()
                    .build();

            RenderResult result = render(sBridge, params, TimeUnit.SECONDS.toNanos(2));
            BufferedImage image = result.getImage();
            assertNotNull(image);
        }
    }

    /**
     * Tests that the TextClock widget renders without error
     * <p/>
     * http://b/150151293
     */
    @Test
    public void testTextClock() throws ClassNotFoundException {
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <TextClock
                             android:layout_height="wrap_content"
                             android:layout_width="wrap_content"
                             android:text="12:34"\
                             android:textSize="18sp" />
                </LinearLayout>
                """;
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .disableDecoration()
                .build();

        renderAndVerify(params, "textclock.png");
    }

    @Test
    public void testChangeSize() throws ClassNotFoundException {
        final String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:orientation="vertical"
                              android:layout_width="wrap_content"
                              android:layout_height="wrap_content">
                    <Button
                             android:layout_height="50dp"
                             android:layout_width="100dp"
                             android:text="Hello" />
                </LinearLayout>
                """;

        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback = new LayoutLibTestCallback(getLogger(),
                mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setRenderingMode(RenderingMode.SHRINK)
                .disableDecoration()
                .build();

        System_Delegate.setBootTimeNanos(TimeUnit.MILLISECONDS.toNanos(871732800000L));
        System_Delegate.setNanosTime(TimeUnit.MILLISECONDS.toNanos(871732800000L));
        RenderSession session = sBridge.createSession(params);

        try {
            session.setElapsedFrameTimeNanos(TimeUnit.SECONDS.toNanos(2));

            if (!session.getResult().isSuccess()) {
                getLogger().error(session.getResult().getException(),
                        session.getResult().getErrorMessage());
            } else {
                // Render the session with a timeout of 50s.
                Result renderResult = session.render(50000);
                if (!renderResult.isSuccess()) {
                    getLogger().error(session.getResult().getException(),
                            session.getResult().getErrorMessage());
                }
            }

            RecyclableImage recyclableImage = session.getRecyclableImage();
            BufferedImage resultImage = recyclableImage.getCopy();

            assertNotNull(resultImage);
            verify("button_resize.png", resultImage);
            recyclableImage.close();

            Object viewObject = session.getRootViews().get(0)
                    .getChildren().get(0).getViewObject();

            Button btn = (Button) viewObject;
            btn.setLayoutParams(new LinearLayout.LayoutParams(300, 300));

            Result renderResult = session.render(50000);
            if (!renderResult.isSuccess()) {
                getLogger().error(session.getResult().getException(),
                        session.getResult().getErrorMessage());
            }

            recyclableImage = session.getRecyclableImage();
            resultImage = recyclableImage.getCopy();

            assertNotNull(resultImage);
            verify("button_resize2.png", resultImage);
            recyclableImage.close();
        } finally {
            session.dispose();
        }
    }

    /**
     * Tests that theme attributes are not resolved when using Resources_Delegate.obtainAttributes
     * <p/>
     * http://b/175943371
     */
    @Test
    public void testNonStyledResources() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:background="#999"\
                              android:orientation="horizontal"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">
                    <com.android.layoutlib.bridge.test.widgets.CustomImageView
                        android:layout_width="100dp"
                        android:layout_height="100dp"/>
                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "non-styled_resources.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testRenderEffect() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString("""
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:background="#999"\
                              android:orientation="horizontal"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">
                    <com.android.layoutlib.bridge.test.widgets.BlurryImageView
                        android:layout_width="100dp"
                        android:layout_height="100dp"/>
                </LinearLayout>""");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "render_effect.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testDialog() throws Exception {
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <com.android.layoutlib.test.myapplication.widgets.DialogView
                             android:layout_height="wrap_content"
                             android:layout_width="wrap_content" />
                </LinearLayout>
                """;
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "dialog.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testWindowBackgroundWithThemeAttribute() throws Exception {
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <TextView
                             android:layout_height="wrap_content"
                             android:layout_width="wrap_content"
                             android:text="Hello World!" />
                </LinearLayout>
                """;
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("WindowBackgroundTheme", true)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "window_background.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testThemedAdaptiveIcon() throws ClassNotFoundException {
        // Create the layout pull parser.
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <ImageView
                             android:layout_height="wrap_content"
                             android:layout_width="wrap_content"
                             android:src="@drawable/adaptive" />
                </LinearLayout>
                """;
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        renderAndVerify(params, "adaptive_icon_circle.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH,
                "/com/android/layoutlib/testdata/wallpaper1.webp");
        params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, true);
        renderAndVerify(params, "adaptive_icon_dynamic_orange.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH,
                "/com/android/layoutlib/testdata/wallpaper2.webp");
        params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, true);
        renderAndVerify(params, "adaptive_icon_dynamic_green.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH,
                "/com/android/layoutlib/testdata/wallpaper2.webp");
        params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, false);
        renderAndVerify(params, "adaptive_icon_circle.png");
    }

    @Test
    public void testThemedAdaptiveIconNoMonochrome() throws ClassNotFoundException {
        // Create the layout pull parser.
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <ImageView
                             android:layout_height="wrap_content"
                             android:layout_width="wrap_content"
                             android:src="@drawable/adaptive_no_monochrome" />
                </LinearLayout>
                """;
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        params.setFlag(RenderParamsFlags.FLAG_KEY_FORCE_MONOCHROME_ICON, true);
        renderAndVerify(params, "adaptive_icon_circle.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH,
                "/com/android/layoutlib/testdata/wallpaper1.webp");
        params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, true);
        params.setFlag(RenderParamsFlags.FLAG_KEY_FORCE_MONOCHROME_ICON, true);
        renderAndVerify(params, "adaptive_no_monochrome_orange.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH,
                "/com/android/layoutlib/testdata/wallpaper2.webp");
        params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, true);
        params.setFlag(RenderParamsFlags.FLAG_KEY_FORCE_MONOCHROME_ICON, true);
        renderAndVerify(params, "adaptive_no_monochrome_green.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH,
                "/com/android/layoutlib/testdata/wallpaper2.webp");
        params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, false);
        params.setFlag(RenderParamsFlags.FLAG_KEY_FORCE_MONOCHROME_ICON, true);
        renderAndVerify(params, "adaptive_icon_circle.png");

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH,
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH,
                "/com/android/layoutlib/testdata/wallpaper2.webp");
        params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, true);
        params.setFlag(RenderParamsFlags.FLAG_KEY_FORCE_MONOCHROME_ICON, false);
        renderAndVerify(params, "adaptive_icon_circle.png");
    }

    @Test
    public void testHtmlText() throws ClassNotFoundException {
        final String layout = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">

                    <com.android.layoutlib.bridge.test.widgets.HtmlTextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textSize="30sp"/>
                </FrameLayout>""";
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "html.png", TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testStatusBar() throws ClassNotFoundException {
        final String layout = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Test status bar colour"
                        android:textSize="30sp"/>
                </FrameLayout>""";
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("DarkStatusBarTheme", true)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "dark_status_bar.png", TimeUnit.SECONDS.toNanos(2));

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("LightStatusBarTheme", true)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();

        renderAndVerify(params, "light_status_bar.png", TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testSoftwareLayer() throws Exception {
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <com.android.layoutlib.test.myapplication.widgets.SoftwareTextView
                             android:layout_height="200dp"
                             android:layout_width="wrap_content" />
                </LinearLayout>
                """;
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "software_layer.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testHighSimulatedSdk() throws Exception {
        String layout = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:padding="16dp"
                              android:orientation="horizontal"
                              android:layout_width="fill_parent"
                              android:layout_height="fill_parent">
                    <TextView
                             android:layout_height="wrap_content"
                             android:layout_width="wrap_content"
                             android:text="This is a TextView" />
                </LinearLayout>
                """;
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .setSimulatedSdk(SDK_INT + 1)
                .disableDecoration()
                .build();

        render(sBridge, params, -1);
        boolean hasApiError = sRenderMessages.removeIf(message -> message.equals(String.format(
                "The current rendering only supports APIs up to %d. You may encounter crashes if " +
                        "using with higher APIs. To avoid, you can set a lower API for your " +
                        "previews.", SDK_INT)));
        assertTrue(hasApiError);
    }

    @Test
    public void testGestureNavBar() throws ClassNotFoundException {
        final String layout = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Test gesture nav bar"
                        android:textSize="30sp"/>
                </FrameLayout>""";
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(FLAG_KEY_USE_GESTURE_NAV, true);

        renderAndVerify(params, "light_gesture_nav.png", TimeUnit.SECONDS.toNanos(2));

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(FLAG_KEY_USE_GESTURE_NAV, true);

        renderAndVerify(params, "dark_gesture_nav.png", TimeUnit.SECONDS.toNanos(2));

        params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setConfigGenerator(ConfigGenerator.NEXUS_5_LAND)
                .setTheme("Theme.Material", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .build();
        params.setFlag(FLAG_KEY_USE_GESTURE_NAV, true);

        renderAndVerify(params, "land_gesture_nav.png", TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testCutouts() throws ClassNotFoundException {
        final String layout = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Test cutouts"
                        android:textSize="30sp"/>
                </FrameLayout>""";
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        ResourceValueMap stringOverlay = ResourceValueMap.create();
        stringOverlay.put(new ResourceValueImpl(ResourceNamespace.ANDROID, ResourceType.STRING,
                "config_mainBuiltInDisplayCutout",
                "M 128,83 A 44,44 0 0 1 84,127 44,44 0 0 1 40,83 44,44 0 0 1 84,39 44,44 0 0 1 128,83 Z @left"));
        stringOverlay.put(new ResourceValueImpl(ResourceNamespace.ANDROID, ResourceType.STRING,
                "config_mainBuiltInDisplayCutoutRectApproximation",
                "M 0.0,0.0 h 136 v 136 h -136 Z @left"));

        ResourceValueMap booleanOverlay = ResourceValueMap.create();
        booleanOverlay.put(new ResourceValueImpl(ResourceNamespace.ANDROID, ResourceType.BOOL,
                "config_fillMainBuiltInDisplayCutout", "true"));

        ResourceValueMap dimenOverlay = ResourceValueMap.create();
        dimenOverlay.put(new ResourceValueImpl(ResourceNamespace.ANDROID, ResourceType.DIMEN,
                "status_bar_height_portrait", "136px"));
        dimenOverlay.put(new ResourceValueImpl(ResourceNamespace.ANDROID, ResourceType.DIMEN,
                "status_bar_height_landscape", "28dp"));

        Map<ResourceType, ResourceValueMap> frameworkOverlay = Map.of(
                ResourceType.STRING, stringOverlay,
                ResourceType.BOOL, booleanOverlay,
                ResourceType.DIMEN, dimenOverlay);

        SessionParams params = getSessionParamsBuilder()
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .setFrameworkOverlayResources(frameworkOverlay)
                .build();
        params.setFlag(FLAG_KEY_SHOW_CUTOUT, true);

        renderAndVerify(params, "hole_cutout.png", TimeUnit.SECONDS.toNanos(2));

        params = getSessionParamsBuilder()
                .setConfigGenerator(ConfigGenerator.NEXUS_5_LAND)
                .setParser(LayoutPullParser.createFromString(layout))
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .setFrameworkOverlayResources(frameworkOverlay)
                .build();
        params.setFlag(FLAG_KEY_SHOW_CUTOUT, true);

        renderAndVerify(params, "hole_cutout_landscape.png", TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    public void testHyphenation() throws ClassNotFoundException {
        final String layout = """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">

                    <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:hyphenationFrequency="full"
                            android:breakStrategy="balanced"
                            android:text="A material metaphor is the unifying theory of a rationalized space hyperextended hyperextended hyperextended and a system of motion."
                            android:textSize="20sp" />
                </FrameLayout>""";
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material.Light.NoActionBar.Fullscreen", false)
                .setRenderingMode(RenderingMode.V_SCROLL)
                .disableDecoration()
                .build();

        renderAndVerify(params, "hyphenation.png", TimeUnit.SECONDS.toNanos(2));
    }

    /** Test expand_layout.xml */
    @Test
    public void testUpdateHardwareConfig() throws ClassNotFoundException {
        LayoutPullParser parser = createParserFromPath("allwidgets.xml");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(ConfigGenerator.NEXUS_5)
                .setCallback(layoutLibCallback)
                .build();

        System_Delegate.setBootTimeNanos(TimeUnit.MILLISECONDS.toNanos(871732800000L));
        System_Delegate.setNanosTime(TimeUnit.MILLISECONDS.toNanos(871732800000L));
        RenderSession session = sBridge.createSession(params);
        session.setElapsedFrameTimeNanos(TimeUnit.SECONDS.toNanos(2));

        try {
            // Render the session with a timeout of 50s.
            session.render(50000);
            RenderResult result = RenderResult.getFromSession(session);
            verify("allwidgets.png", result.getImage());

            ConfigGenerator enlargedConfig = new ConfigGenerator()
                    .setScreenHeight(2200)
                    .setScreenWidth(2000)
                    .setXdpi(445)
                    .setYdpi(445)
                    .setDensity(Density.XXHIGH);
            session.updateHardwareConfiguration(enlargedConfig.getHardwareConfig());
            session.render(50000, true);
            result = RenderResult.getFromSession(session);
            verify("allwidgets_resized.png", result.getImage());
        } finally {
            session.dispose();
        }
    }

    @Test
    public void testDisableBitmapFlag() throws ClassNotFoundException {
        LayoutPullParser parser = createParserFromPath("allwidgets.xml");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(ConfigGenerator.NEXUS_5)
                .setCallback(layoutLibCallback)
                .setFlag(RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING, true)
                .build();

        System_Delegate.setBootTimeNanos(TimeUnit.MILLISECONDS.toNanos(871732800000L));
        System_Delegate.setNanosTime(TimeUnit.MILLISECONDS.toNanos(871732800000L));
        RenderSession session = sBridge.createSession(params);
        session.setElapsedFrameTimeNanos(TimeUnit.SECONDS.toNanos(2));

        try {
            // Render the session with a timeout of 50s.
            session.render(50000);
            RenderResult result = RenderResult.getFromSession(session);
            verify("allwidgets.png", result.getImage());

            session.render(50000);
            result = RenderResult.getFromSession(session);
            verify("allwidgets.png", result.getImage());
        } finally {
            session.dispose();
        }
    }
}
