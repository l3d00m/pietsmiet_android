package de.pscom.pietsmiet.util;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static de.pscom.pietsmiet.util.CardItemManager.DISPLAY_ALL;
import static de.pscom.pietsmiet.util.CardItemManager.DISPLAY_SOCIAL;


public class CardType {
    //Scales dynamically
    //Renamed to shorter version
    public static final int VIDEO = 0;
    public static final int STREAM = VIDEO + 1;
    public static final int PIETCAST = STREAM + 1;
    public static final int TWITTER = PIETCAST + 1;
    public static final int FACEBOOK = TWITTER + 1;
    public static final int UPLOAD_PLAN = FACEBOOK + 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FACEBOOK, TWITTER, UPLOAD_PLAN})
    public @interface ItemTypeNoThumbnail {
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({VIDEO, FACEBOOK, TWITTER, PIETCAST, STREAM})
    public @interface ItemTypeThumbnail {
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PIETCAST, UPLOAD_PLAN, DISPLAY_SOCIAL, DISPLAY_ALL})
    public @interface ItemTypeDrawer {
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PIETCAST, UPLOAD_PLAN, FACEBOOK, TWITTER, STREAM, VIDEO})
    public @interface ItemTypeAll {
    }
}