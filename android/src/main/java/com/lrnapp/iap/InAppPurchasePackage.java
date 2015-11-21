package com.lrnapp.iap;

import android.content.Context;
import android.content.Intent;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InAppPurchasePackage implements ReactPackage {

    private InAppPurchaseModule mModuleInstance;
    private final Context mActivityContext;

    public InAppPurchasePackage(Context activityContext) {
        mActivityContext = activityContext;
    }

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        mModuleInstance = new InAppPurchaseModule(reactContext, mActivityContext);

        return Arrays.<NativeModule>asList(mModuleInstance);
    }

    @Override
    public List<Class<? extends JavaScriptModule>> createJSModules() {
        return Collections.emptyList();
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }

    public boolean handleActivityResult(final int requestCode, final int resultCode, final Intent data) {
        return mModuleInstance != null && mModuleInstance.handleActivityResult(requestCode, resultCode, data);
    }

    public void onDestroy() {
        if (mModuleInstance != null) {
            mModuleInstance.unBindService();
        }
    }
}
