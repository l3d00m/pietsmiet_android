package de.pscom.pietsmiet.util;

import android.app.Activity;
import android.content.Context;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import de.pscom.pietsmiet.BuildConfig;

@SuppressWarnings("WeakerAccess")
public abstract class FirebaseUtil {
    private static final String PARAM_FIREBASE_DB_URL = "FIREBASE_DB_URL";

    public static final String TOPIC_VIDEO = "video";
    public static final String TOPIC_UPLOADPLAN = "uploadplan";
    public static final String TOPIC_NEWS = "news";
    public static final String TOPIC_PIETCAST = "pietcast";
    public static final String TOPIC_TEST = "test2";

    public static final String EVENT_NEXT_COMPLETED = "next_loading_completed";
    public static final String EVENT_NEW_COMPLETED = "new_loading_completed";
    public static final String EVENT_FRESH_COMPLETED = "fresh_loading_completed";

    public static final String PARAM_START_POSITION = "start_position";
    public static final String PARAM_ITEM_COUNT = "item_count";


    public static void loadRemoteConfig(Activity context) {
        FirebaseRemoteConfig mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        mFirebaseRemoteConfig.fetch()
                .addOnCompleteListener(context, task -> {
                    if (task.isSuccessful()) {
                        mFirebaseRemoteConfig.activateFetched();

                        String firebaseDbUrl = mFirebaseRemoteConfig.getString(PARAM_FIREBASE_DB_URL);
                        if (firebaseDbUrl != null && firebaseDbUrl != "") {
                            SharedPreferenceHelper.setSharedPreferenceString(
                                    context, SharedPreferenceHelper.KEY_FIREBASE_DB_URL, firebaseDbUrl);
                            SettingsHelper.loadAllSettings(context);
                        }
                    }
                });
    }

    public static void disableCollectionOnDebug(Context context) {
        if (BuildConfig.DEBUG) {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false);
            FirebasePerformance.getInstance().setPerformanceCollectionEnabled(false);
        }
    }

    public static void setupTopicSubscriptions() {
        setFirebaseTopicSubscription(TOPIC_TEST, BuildConfig.DEBUG);
        setFirebaseTopicSubscription(TOPIC_UPLOADPLAN, SettingsHelper.boolUploadplanNotification);
        setFirebaseTopicSubscription(TOPIC_NEWS, SettingsHelper.boolNewsNotification);
        setFirebaseTopicSubscription(TOPIC_VIDEO, SettingsHelper.boolVideoNotification);
        setFirebaseTopicSubscription(TOPIC_PIETCAST, SettingsHelper.boolPietcastNotification);
    }

    public static void setFirebaseTopicSubscription(String topic, boolean subscribe) {
        if (subscribe) {
            FirebaseMessaging.getInstance().subscribeToTopic(topic);
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
        }
    }

}
