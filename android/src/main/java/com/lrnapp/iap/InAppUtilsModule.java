package com.lrnapp.iap;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

public class InAppUtilsModule extends ReactContextBaseJavaModule {

    public InAppUtilsModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "InAppUtilsModule";
    }

    @ReactMethod
    public void loadProducts(final ReadableArray products, final Promise promise) {
    }

    @ReactMethod
    public void purchaseProduct(final int productIdentifier, final Promise promise) {

    }

    @ReactMethod
    public void restorePurchases(final Promise promise) {

    }

    @ReactMethod
    public void receiptData(final Promise promise) {

    }
}
