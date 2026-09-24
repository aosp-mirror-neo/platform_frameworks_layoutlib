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

package com.android.layoutlib.bridge.intensive;

import com.android.layoutlib.bridge.BridgeRenderSessionTest;
import com.android.layoutlib.bridge.TestDelegates;
import com.android.layoutlib.bridge.android.AccessibilityTest;
import com.android.layoutlib.bridge.android.BitmapTest;
import com.android.layoutlib.bridge.android.BridgeContextTest;
import com.android.layoutlib.bridge.android.BridgeXmlBlockParserTest;
import com.android.layoutlib.bridge.android.DynamicRenderResourcesTest;
import com.android.layoutlib.bridge.android.RenderSessionDisposeTest;
import com.android.layoutlib.bridge.impl.LayoutParserWrapperTest;
import com.android.layoutlib.bridge.impl.LayoutlibRecyclableImageTest;
import com.android.layoutlib.bridge.impl.ResourceHelperTest;
import com.android.layoutlib.bridge.util.ChoreographerCallbacksTest;
import com.android.layoutlib.bridge.util.HandlerMessageQueueTest;
import com.android.tools.idea.validator.LayoutValidatorTests;
import com.android.tools.idea.validator.ValidatorResultTests;
import com.android.tools.idea.validator.AccessibilityValidatorTests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import android.content.res.BridgeTypedArrayTest;
import android.content.res.Resources_DelegateTest;
import android.provider.Settings_Secure_DelegateTest;
import android.provider.Settings_Global_DelegateTest;
import android.provider.Settings_System_DelegateTest;
import android.util.BridgeXmlPullAttributesTest;

/**
 * Suite used by the layoutlib build system
 */
@RunWith(Suite.class)
@SuiteClasses({RenderTests.class, LayoutParserWrapperTest.class, Settings_Secure_DelegateTest.class,
        Settings_System_DelegateTest.class, Settings_Global_DelegateTest.class,
        BridgeXmlBlockParserTest.class, BridgeXmlPullAttributesTest.class, TestDelegates.class,
        BridgeRenderSessionTest.class, ResourceHelperTest.class, BridgeContextTest.class,
        Resources_DelegateTest.class, ShadowsRenderTests.class, LayoutValidatorTests.class,
        AccessibilityValidatorTests.class, BridgeTypedArrayTest.class, ValidatorResultTests.class,
        BitmapTest.class, DynamicRenderResourcesTest.class, AccessibilityTest.class,
        ChoreographerCallbacksTest.class, HandlerMessageQueueTest.class, LayoutlibRecyclableImageTest.class,
        RenderSessionDisposeTest.class})
public class Main {
}
