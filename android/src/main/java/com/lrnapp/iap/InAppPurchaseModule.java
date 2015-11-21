package com.lrnapp.iap;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class InAppPurchaseModule extends ReactContextBaseJavaModule {

    private static final int REQUEST_CODE_PURCHASE = 149455;

    private static final String ITEM_ID_LIST = "ITEM_ID_LIST";
    private static final String INAPP = "inapp";
    private static final String RESPONSE_CODE = "RESPONSE_CODE";
    private static final String DETAILS_LIST = "DETAILS_LIST";
    private static final String BUY_INTENT = "BUY_INTENT";
    private static final String INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    private static final String INAPP_PURCHASE_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
    private static final String INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    private static final String INAPP_DATA_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    private static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

    private static final int BILLING_API_VERSION = 3;
    private static final int BILLING_RESPONSE_RESULT_OK = 0;

    private static final String PROP_AUTO_RENEWING = "autoRenewing";
    private static final String PROP_ORDER_ID = "orderId";
    private static final String PROP_PACKAGE_NAME = "packageName";
    private static final String PROP_PRODUCT_ID = "productId";
    private static final String PROP_PURCHASE_TIME = "purchaseTime";
    private static final String PROP_PURCHASE_STATE = "purchaseState";
    private static final String PROP_PURCHASE_TOKEN = "purchaseToken";
    private static final String PROP_DEVELOPER_PAYLOAD = "developerPayload";

    private static final String ERROR_PRODUCTS_LOAD_FAILED = "Failed to load products";
    private static final String ERROR_PURCHASE_VERIFICATION_FAILED = "Failed to verify purchase";
    private static final String ERROR_PURCHASE_CANCELLED = "Purchase was cancelled";
    private static final String ERROR_PURCHASE_UNKNOWN = "An error occurred while purchase";

    private static final class Purchase {
        private String mProductId;
        private String mToken;
        private Promise mPromise;

        Purchase(final String productId, final String token, final Promise promise) {
            mProductId = productId;
            mToken = token;
            mPromise = promise;
        }

        public String getProductId() {
            return mProductId;
        }

        public String getToken() {
            return mToken;
        }

        public Promise getPromise() {
            return mPromise;
        }
    }

    private List<Purchase> queuedPurchases = new ArrayList<>();

    private Purchase pendingPurchase;
    private Context mActivityContext;
    private IInAppBillingService mService;

    // Establish a connection with Billing Service on Google Play
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

    public InAppPurchaseModule(ReactApplicationContext reactContext, Context activityContext) {
        super(reactContext);

        mActivityContext = activityContext;

        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");

        // Explicitly set the intent's target package name to protect the security of billing transactions
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

    private void onPurchaseHandled() {
        pendingPurchase = null;

        // Handle queued purchases if there are any
        if (!queuedPurchases.isEmpty()) {
            handlePendingPurchase(queuedPurchases.remove(queuedPurchases.size() - 1));
        }
    }

    private void onPurchaseError(final String reason) {
        if (pendingPurchase != null) {
            pendingPurchase.getPromise().reject(reason);
            onPurchaseHandled();
        }
    }

    private void onPurchaseSuccess(final String data) {
        if (pendingPurchase != null) {
            try {
                WritableMap details = convertDataToMap(data);

                if (pendingPurchase.getToken().equals(details.getString(PROP_PURCHASE_TOKEN))) {
                    pendingPurchase.getPromise().resolve(details);
                } else {
                    onPurchaseError(ERROR_PURCHASE_VERIFICATION_FAILED);
                }
            } catch (JSONException e) {
                onPurchaseError(e.getMessage());
            }
        }
    }

    @ReactMethod
    public void loadProducts(final ReadableArray products, final Promise promise) {
        // Use a separate thread for the request as it does a network request
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> skuList = new ArrayList<>();

                for (int i = 0; i < products.size(); i++) {
                    skuList.add(products.getString(i));
                }

                Bundle querySkus = new Bundle();

                querySkus.putStringArrayList(ITEM_ID_LIST, skuList);

                try {
                    Bundle skuDetails = mService.getSkuDetails(BILLING_API_VERSION, mActivityContext.getPackageName(), INAPP, querySkus);

                    int response = skuDetails.getInt(RESPONSE_CODE);

                    if (response == BILLING_RESPONSE_RESULT_OK) {
                        ArrayList<String> responseList = skuDetails.getStringArrayList(DETAILS_LIST);

                        if (responseList == null) {
                            promise.reject(ERROR_PRODUCTS_LOAD_FAILED);

                            return;
                        }

                        WritableMap details = Arguments.createMap();

                        for (String thisResponse : responseList) {
                            WritableMap map = convertDataToMap(thisResponse);

                            details.putMap(map.getString(PROP_PRODUCT_ID), map);
                        }
                    } else {
                        promise.reject(ERROR_PRODUCTS_LOAD_FAILED);
                    }
                } catch (JSONException|RemoteException e) {
                    promise.reject(e.getMessage());
                }
            }
        }).start();
    }

    @ReactMethod
    public void loadPurchases(final String token, final Promise promise) {
        try {
            WritableMap purchases = Arguments.createMap();
            Bundle ownedItems = mService.getPurchases(BILLING_API_VERSION, mActivityContext.getPackageName(), INAPP, token);

            int response = ownedItems.getInt(RESPONSE_CODE);

            if (response == 0) {
                purchases.putString(INAPP_CONTINUATION_TOKEN, ownedItems.getString(INAPP_CONTINUATION_TOKEN));

                ArrayList<String> ownedSkus =
                        ownedItems.getStringArrayList(INAPP_PURCHASE_ITEM_LIST);
                ArrayList<String> purchaseDataList =
                        ownedItems.getStringArrayList(INAPP_PURCHASE_DATA_LIST);
                ArrayList<String> signatureList =
                        ownedItems.getStringArrayList(INAPP_DATA_SIGNATURE_LIST);

                WritableArray items = Arguments.createArray();

                for (int i = 0, l = purchaseDataList.size(); i < l; ++i) {
                    WritableMap data = Arguments.createMap();

                    data.putString("data", purchaseDataList.get(i));
                    data.putString("signature", signatureList.get(i));
                    data.putString("item", ownedSkus.get(i));

                    items.pushMap(data);
                }

                purchases.putArray("items", items);
                promise.resolve(purchases);
            }
        } catch (RemoteException e) {
            promise.reject(e.getMessage());
        }
    }

    private void handlePendingPurchase(final Purchase purchase) {
        try {
            Bundle buyIntentBundle = mService.getBuyIntent(
                    BILLING_API_VERSION, mActivityContext.getPackageName(),
                    purchase.getProductId(), INAPP, purchase.getToken());

            PendingIntent pendingIntent = buyIntentBundle.getParcelable(BUY_INTENT);

            pendingPurchase = purchase;

            ((Activity) mActivityContext).startIntentSenderForResult(pendingIntent.getIntentSender(),
                    REQUEST_CODE_PURCHASE, new Intent(), 0, 0, 0);
        } catch (Exception e) {
            purchase.getPromise().reject(e.getMessage());
        }
    }

    @ReactMethod
    public void purchaseProduct(final String productId, final Promise promise) {
        // TODO: Generate a more secure token for verifying purchases
        String token = UUID.randomUUID().toString();
        Purchase purchase = new Purchase(productId, token, promise);

        // Android provides us "onActivityResult" to handle purchase requests
        // It means that there is no way to detect cancellation and other errors
        // We can workaround that by handling only one purchase at a time
        // We'll add puchases to a queue to process them
        // It looks like  nasty hack, but hey, whatever works
        if (pendingPurchase != null) {
            queuedPurchases.add(purchase);
        } else {
            handlePendingPurchase(purchase);
        }
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PURCHASE) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    onPurchaseSuccess(data.getStringExtra(INAPP_PURCHASE_DATA));
                    break;
                case Activity.RESULT_CANCELED:
                    onPurchaseError(ERROR_PURCHASE_CANCELLED);
                    break;
                default:
                    onPurchaseError(ERROR_PURCHASE_UNKNOWN);
            }
        }

        return false;
    }

    public void unBindService() {
        if (mService != null) {
            // Unbind from the In-app Billing service when we are done
            // Otherwise, the open service connection could cause the device’s performance to degrade
            mActivityContext.unbindService(mServiceConn);
        }
    }
}
