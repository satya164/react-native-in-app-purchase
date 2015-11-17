package com.lrnapp.iap;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class InAppPurchaseModule extends ReactContextBaseJavaModule {

    private final static String TAG = "IN_APP_PURCHASE";

    private final static int REQUEST_CODE_PURCHASE = 149455;

    private final static String ITEM_ID_LIST = "ITEM_ID_LIST";
    private final static String INAPP = "inapp";
    private final static String RESPONSE_CODE = "RESPONSE_CODE";
    private final static String DETAILS_LIST = "DETAILS_LIST";
    private final static String BUY_INTENT = "BUY_INTENT";
    private final static String INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    private final static String INAPP_DATA_SIGNTAURE = "INAPP_DATA_SIGNTAURE";

    private final static int BILLING_RESPONSE_RESULT_OK = 0;

    private final static String PROP_AUTO_RENEWING = "autoRenewing";
    private final static String PROP_ORDER_ID = "orderId";
    private final static String PROP_PACKAGE_NAME = "packageName";
    private final static String PROP_PRODUCT_ID = "productId";
    private final static String PROP_PURCHASE_TIME = "purchaseTime";
    private final static String PROP_PURCHASE_STATE = "purchaseState";
    private final static String PROP_PURCHASE_TOKEN = "purchaseToken";
    private final static String PROP_DEVELOPER_PAYLOAD = "developerPayload";

    private final static String ERROR_UNAVAILABLE = "In app purchases are not available on this device";
    private final static String ERROR_IN_PROGRESS = "An operation is already in progress";
    private final static String ERROR_PRODUCTS_LOAD_FAILED = "Failed to load products";

    private Map<String, Promise> pendingPurchases = new HashMap<>();

    private String mLicenseKey;
    private Context mActivityContext;
    private IInAppBillingService mService;

    private ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
        }
    };

    public InAppPurchaseModule(ReactApplicationContext reactContext, Context activityContext, String licenseKey) {
        super(reactContext);

        mActivityContext = activityContext;
        mLicenseKey = licenseKey;

        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");

        serviceIntent.setPackage("com.android.vending");
        activityContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    public String getName() {
        return "InAppPurchaseModule";
    }

    private WritableMap convertDataToMap(final String data) throws JSONException {
        JSONObject object = new JSONObject(data);

        WritableMap map = Arguments.createMap();

        map.putBoolean(PROP_AUTO_RENEWING, object.getBoolean(PROP_AUTO_RENEWING));
        map.putString(PROP_ORDER_ID, object.getString(PROP_ORDER_ID));
        map.putString(PROP_PACKAGE_NAME, object.getString(PROP_PACKAGE_NAME));
        map.putString(PROP_PRODUCT_ID, object.getString(PROP_PRODUCT_ID));
        map.putInt(PROP_PURCHASE_TIME, object.getInt(PROP_PURCHASE_TIME));
        map.putInt(PROP_PURCHASE_STATE, object.getInt(PROP_PURCHASE_STATE));
        map.putString(PROP_PURCHASE_TOKEN, object.getString(PROP_PURCHASE_TOKEN));
        map.putString(PROP_DEVELOPER_PAYLOAD, object.getString(PROP_DEVELOPER_PAYLOAD));

        return map;
    }

    private void onPurchaseSuccess(final String data) {
        try {
            WritableMap map = convertDataToMap(data);
            String purchaseToken = map.getString(PROP_PRODUCT_ID);

            if (pendingPurchases.containsKey(purchaseToken)) {
                pendingPurchases.get(purchaseToken).resolve(map);
                pendingPurchases.remove(purchaseToken);
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @ReactMethod
    public void loadProducts(final ReadableArray products, final Promise promise) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> skuList = new ArrayList<>();

                for (int i = 0; i < products.size(); i++) {
                    skuList.add(products.getString(i));
                }

                Bundle querySkus = new Bundle();

                querySkus.putStringArrayList(ITEM_ID_LIST, skuList);

                try {
                    Bundle skuDetails = mService.getSkuDetails(3, mActivityContext.getPackageName(), INAPP, querySkus);

                    int response = skuDetails.getInt(RESPONSE_CODE);

                    if (response == BILLING_RESPONSE_RESULT_OK) {
                        ArrayList<String> responseList = skuDetails.getStringArrayList(DETAILS_LIST);

                        if (responseList == null) {
                            promise.reject(ERROR_PRODUCTS_LOAD_FAILED);

                            return;
                        }

                        WritableMap details = Arguments.createMap();

                        for (String thisResponse : responseList) {
                            try {
                                WritableMap map = convertDataToMap(thisResponse);

                                details.putMap(map.getString(PROP_PRODUCT_ID), map);
                            } catch (JSONException e) {
                                promise.reject(e.getMessage());

                                return;
                            }
                        }
                    } else {
                        promise.reject(ERROR_PRODUCTS_LOAD_FAILED);
                    }
                } catch (RemoteException e) {
                    promise.reject(e.getMessage());
                }
            }
        });
    }

    @ReactMethod
    public void purchaseProduct(final String productId, final Promise promise) {
        try {
            String purchaseToken = productId + ":" + UUID.randomUUID().toString();

            Bundle buyIntentBundle = mService.getBuyIntent(3, mActivityContext.getPackageName(), productId, INAPP, purchaseToken);

            pendingPurchases.put(purchaseToken, promise);

            PendingIntent pendingIntent = buyIntentBundle.getParcelable(BUY_INTENT);

            ((Activity) mActivityContext).startIntentSenderForResult(pendingIntent.getIntentSender(),
                    REQUEST_CODE_PURCHASE, new Intent(), 0, 0, 0);
        } catch (Exception e) {
            promise.reject(e.getMessage());
        }
    }

    @ReactMethod
    public void consumeProduct(final String productId, final Promise promise) {
    }

    @ReactMethod
    public void restorePurchases(final Promise promise) {
    }

    @ReactMethod
    public void receiptData(final Promise promise) {
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PURCHASE) {
            String purchaseData = data.getStringExtra(INAPP_PURCHASE_DATA);
            String dataSignature = data.getStringExtra(INAPP_DATA_SIGNTAURE);

            if (resultCode == Activity.RESULT_OK) {
                onPurchaseSuccess(purchaseData);
            }
        }

        return false;
    }

    public void unBindService() {
        if (mService != null) {
            mActivityContext.unbindService(mServiceConn);
        }
    }
}
