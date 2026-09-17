/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.layoutlib.bridge.intensive.util;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.AssetRepository;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.RenderSizeProvider;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.layoutlib.bridge.intensive.setup.ConfigGenerator;
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser;
import com.android.resources.ResourceType;

import android.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Table;

/**
 * Builder to help setting up {@link SessionParams} objects.
 */
public class SessionParamsBuilder {

    private LayoutPullParser mLayoutParser;
    private RenderingMode mRenderingMode = RenderingMode.NORMAL;
    private ConfigGenerator mConfigGenerator = ConfigGenerator.NEXUS_5;
    private ResourceRepository mFrameworkResources;
    private ResourceRepository mProjectResources;
    private String mThemeName;
    private boolean isProjectTheme;
    private LayoutlibCallback mLayoutlibCallback;
    private int mTargetSdk;
    private int mMinSdk = 0;
    private int mSimulatedSdk = 0;
    private ILayoutLog mLayoutLog;
    private final Map<SessionParams.Key, Object> mFlags = new HashMap<>();
    private AssetRepository mAssetRepository = null;
    private boolean mDecor = true;
    private RenderSizeProvider mSizeProvider = null;
    private boolean enableLayoutValidator = false;
    private boolean transparentBackground = false;
    private Map<ResourceType, ResourceValueMap> mFrameworkOverlayResources;

    @NonNull
    public SessionParamsBuilder setParser(@NonNull LayoutPullParser layoutParser) {
        mLayoutParser = layoutParser;

        return this;
    }

    @NonNull
    public SessionParamsBuilder setRenderingMode(@NonNull RenderingMode renderingMode) {
        mRenderingMode = renderingMode;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setConfigGenerator(@NonNull ConfigGenerator configGenerator) {
        mConfigGenerator = configGenerator;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setProjectResources(@NonNull ResourceRepository resources) {
        mProjectResources = resources;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setFrameworkResources(@NonNull ResourceRepository resources) {
        mFrameworkResources = resources;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setTheme(@NonNull String themeName, boolean isProjectTheme) {
        mThemeName = themeName;
        this.isProjectTheme = isProjectTheme;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setTheme(@NonNull String themeName) {
        boolean isProjectTheme;
        if (themeName.startsWith(SdkConstants.PREFIX_ANDROID)) {
            themeName = themeName.substring(SdkConstants.PREFIX_ANDROID.length());
            isProjectTheme = false;
        } else {
            isProjectTheme = true;
        }
        return setTheme(themeName, isProjectTheme);
    }

    @NonNull
    public SessionParamsBuilder setCallback(@NonNull LayoutlibCallback callback) {
        mLayoutlibCallback = callback;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setTargetSdk(int targetSdk) {
        mTargetSdk = targetSdk;
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    public SessionParamsBuilder setMinSdk(int minSdk) {
        mMinSdk = minSdk;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setSimulatedSdk(int simulatedSdk) {
        mSimulatedSdk = simulatedSdk;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setLayoutLog(@NonNull ILayoutLog layoutLog) {
        mLayoutLog = layoutLog;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setFlag(@NonNull SessionParams.Key<?> flag, Object value) {
        mFlags.put(flag, value);
        return this;
    }

    @NonNull
    public SessionParamsBuilder setAssetRepository(@NonNull AssetRepository repository) {
        mAssetRepository = repository;
        return this;
    }

    @NonNull
    public SessionParamsBuilder disableDecoration() {
        mDecor = false;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setSizeProvider(@NonNull RenderSizeProvider sizeProvider) {
        mSizeProvider = sizeProvider;
        return this;
    }

    @NonNull
    public SessionParamsBuilder enableLayoutValidation() {
        this.enableLayoutValidator = true;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setTransparentBackground() {
        this.transparentBackground = true;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setFrameworkOverlayResources(
            Map<ResourceType, ResourceValueMap> resources) {
        this.mFrameworkOverlayResources = resources;
        return this;
    }

    @NonNull
    public SessionParams build() {
        assert mFrameworkResources != null;
        assert mProjectResources != null;
        assert mThemeName != null;
        assert mLayoutLog != null;
        assert mLayoutlibCallback != null;

        FolderConfiguration config = mConfigGenerator.getFolderConfig();
        Map<ResourceType, ResourceValueMap> frameworkConfigResources =
                ResourceRepositoryUtil.getConfiguredResources(mFrameworkResources, config).row(
                        ResourceNamespace.ANDROID);
        Table<ResourceNamespace, ResourceType, ResourceValueMap> projectConfigResources =
                ResourceRepositoryUtil.getConfiguredResources(mProjectResources, config);
        if (mFrameworkOverlayResources != null) {
            mFrameworkOverlayResources.keySet().forEach(
                    type -> mFrameworkOverlayResources.get(type).values().forEach(
                            resourceValue -> frameworkConfigResources.get(type).put(
                                    resourceValue)));
        }
        Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> allResourcesMap =
                new HashMap<>();
        allResourcesMap.put(ResourceNamespace.ANDROID, frameworkConfigResources);
        for (ResourceNamespace namespace : projectConfigResources.rowKeySet()) {
            allResourcesMap.put(namespace, projectConfigResources.row(namespace));
        }
        ResourceResolver resourceResolver = ResourceResolver.create(
                allResourcesMap,
                new ResourceReference(
                        ResourceNamespace.fromBoolean(!isProjectTheme),
                        ResourceType.STYLE,
                        mThemeName));

        SessionParams params = new SessionParams(mLayoutParser, mRenderingMode, null /* for
        caching */, mConfigGenerator.getHardwareConfig(), resourceResolver, mLayoutlibCallback,
                mMinSdk, mTargetSdk, mLayoutLog, mSimulatedSdk);
        params.setLayoutValidationChecker(() -> enableLayoutValidator);
        if (mSizeProvider != null) {
            params.setSizeProvider(mSizeProvider);
        }

        mFlags.forEach(params::setFlag);
        params.setAssetRepository(mAssetRepository);

        if (!mDecor) {
            params.setForceNoDecor();
        }

        if (transparentBackground) {
            params.setTransparentBackground();
        }

        return params;
    }
}
