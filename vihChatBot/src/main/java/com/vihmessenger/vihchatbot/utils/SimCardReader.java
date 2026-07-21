package com.vihmessenger.vihchatbot.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.core.app.ActivityCompat;

import java.util.List;

public class SimCardReader {
    private Context context;

    public SimCardReader(Context context) {
        this.context = context;
    }

    public String getSimCardNumber() {
        // Check if READ_PHONE_STATE permission is granted
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
            != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("READ_PHONE_STATE permission not granted");
        }

        String iccid = null;

        try {
            // For devices with Android 10 (API 29) and above
            SubscriptionManager subscriptionManager = (SubscriptionManager) 
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

            if (subscriptionManager != null) {
                List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
                
                if (subscriptionInfos != null && !subscriptionInfos.isEmpty()) {
                    // Get ICCID for the first SIM card
                    SubscriptionInfo subscriptionInfo = subscriptionInfos.get(0);
                    iccid = subscriptionInfo.getIccId();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return iccid;
    }
}