package de.pscom.pietsmiet.util;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.pscom.pietsmiet.MainActivity;
import de.pscom.pietsmiet.generic.Post;
import rx.Observable;
import rx.schedulers.Schedulers;


public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int VERSION_NUMBER = 2;

    private static final String DATABASE_NAME = "PietSmiet.db";
    private static final String TABLE_POSTS = "posts";
    private static final String POSTS_COLUMN_ID = "id";
    private static final String POSTS_COLUMN_TITLE = "title";
    private static final String POSTS_COLUMN_DESC = "desc";
    private static final String POSTS_COLUMN_URL = "url";
    private static final String POSTS_COLUMN_TYPE = "type";
    private static final String POSTS_COLUMN_TIME = "time";
    private static final String POSTS_COLUMN_DURATION = "duration";
    private static final String POSTS_COLUMN_HAS_THUMBNAIL = "thumbnail";

    private static final int MAX_ADDITIONAL_POSTS_STORED = 50;

    @SuppressLint("SimpleDateFormat")
    //private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, VERSION_NUMBER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table " + TABLE_POSTS + " (" +
                        POSTS_COLUMN_ID + " INTEGER PRIMARY KEY," +
                        POSTS_COLUMN_TITLE + " TINY_TEXT, " +
                        POSTS_COLUMN_DESC + " TEXT," +
                        POSTS_COLUMN_URL + " TEXT," +
                        POSTS_COLUMN_TYPE + " INT," +
                        POSTS_COLUMN_TIME + " INT," +
                        POSTS_COLUMN_DURATION + " INT," +
                        POSTS_COLUMN_HAS_THUMBNAIL + " TEXT)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POSTS);
        onCreate(db);
    }

    private void deleteTable() {
        getWritableDatabase().delete(TABLE_POSTS, null, null);
    }

    /**
     * Adds posts to the database (and stores their thumbnails). This is done asynchronous
     *
     * @param posts   Posts to store
     * @param context Context for storing thumbnails
     */
    public void insertPosts(List<Post> posts, Context context) {
        deleteTable();
        SQLiteDatabase db = getWritableDatabase();
        Observable.just(posts)
                .flatMap(Observable::from)
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.io())
                .take(50)
                .subscribe(post -> {
                    if (post.hasThumbnail()) {
                        DrawableFetcher.saveDrawableToFile(post.getThumbnail(), context, Integer.toString(post.hashCode()));
                    }
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(POSTS_COLUMN_ID, post.hashCode());
                    contentValues.put(POSTS_COLUMN_TITLE, post.getTitle());
                    contentValues.put(POSTS_COLUMN_DESC, post.getDescription());
                    contentValues.put(POSTS_COLUMN_URL, post.getUrl());
                    contentValues.put(POSTS_COLUMN_TYPE, post.getPostType());
                    contentValues.put(POSTS_COLUMN_TIME, post.getDate().getTime());
                    contentValues.put(POSTS_COLUMN_DURATION, post.getDuration());
                    contentValues.put(POSTS_COLUMN_HAS_THUMBNAIL, post.hasThumbnail());
                    db.insert(TABLE_POSTS, null, contentValues);
                }, (throwable) -> {
                    throwable.printStackTrace();
                    db.close();
                }, () -> {
                    PsLog.v("Stored " + getPostsInDbCount() + " posts in db");
                    db.close();
                });
    }

    private int getPostsInDbCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        long cnt = DatabaseUtils.queryNumEntries(db, TABLE_POSTS);
        db.close();
        return (int) Math.max(Math.min(Integer.MAX_VALUE, cnt), Integer.MIN_VALUE);
    }

    public void clearDB() {
        deleteTable();
        this.close();
    }

    /**
     * Loads all post objects from the database and displays it
     * Clears the database if it's too big
     *
     * @param context For loading the drawable & displaying the post after finished loading
     */
    @SuppressWarnings("WeakerAccess")
    public void displayPostsFromCache(MainActivity context) {
        List<Post> toReturn = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();
        long DAY_IN_MS = 1000 * 60 * 60 * 24;
        // Don't retrieve posts older than two days
        long time = new Date(System.currentTimeMillis() - (2 * DAY_IN_MS)).getTime();
        PsLog.v("OLDEST TIME: " + time);
        Cursor res = db.rawQuery("SELECT * FROM " + TABLE_POSTS + " WHERE " + POSTS_COLUMN_TIME + " > " + time, null);

        Observable.just(res)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .onBackpressureBuffer()
                .filter(Cursor::moveToFirst)
                .subscribe(cursor -> {
                    try {
                        do {
                            int old_hashcode = cursor.getInt(cursor.getColumnIndex(POSTS_COLUMN_ID));

                            Post.PostBuilder postBuilder = new Post.PostBuilder(cursor.getInt(cursor.getColumnIndex(POSTS_COLUMN_TYPE)))
                                    .title(cursor.getString(cursor.getColumnIndex(POSTS_COLUMN_TITLE)))
                                    .description(cursor.getString(cursor.getColumnIndex(POSTS_COLUMN_DESC)))
                                    .url(cursor.getString(cursor.getColumnIndex(POSTS_COLUMN_URL)))
                                    .duration(cursor.getInt(cursor.getColumnIndex(POSTS_COLUMN_DURATION)))
                                    .date(new Date(cursor.getLong(cursor.getColumnIndex(POSTS_COLUMN_TIME))));
                            if (cursor.getInt(cursor.getColumnIndex(POSTS_COLUMN_HAS_THUMBNAIL)) == 1) {
                                String filename = Integer.toString(old_hashcode);
                                Drawable thumb = DrawableFetcher.loadDrawableFromFile(context, filename);
                                if (thumb != null) {
                                    postBuilder.thumbnail(thumb);
                                }
                            }
                            Post post = postBuilder.build();
                            if (post.hashCode() == old_hashcode) {
                                toReturn.add(postBuilder.build());
                            } else {
                                PsLog.v("Post in db has a different hashcode than before, not using it");
                            }
                        } while (cursor.moveToNext());
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        cursor.close();
                        db.close();
                    }
                    int postsInDb = getPostsInDbCount();
                    /*if (postsInDb != toReturn.size()) {
                        // Reload all posts when not all posts from db not all posts are stored in db (/ db defect).
                        PsLog.v("Loading all posts this time because database was incomplete.\n" +
                                " Posts in DB: " + postsInDb +
                                ", Posts loaded from DB: " + toReturn.size());
                        SharedPreferenceHelper.shouldUseCache = false;
                        deleteTable();
                        this.close();
                    } else */if (toReturn.size() < context.getPostManager().getAllPostsCount()) {
                        // Reload all posts when not all posts from db are loaded / not all posts are stored in db.
                        // The loaded posts from db are applied nevertheless.
                        PsLog.v("Loading all posts this time because database was incomplete.\n" +
                                " Posts in DB: " + postsInDb +
                                ", Should have loaded at least: " + context.getPostManager().getAllPostsCount());
                        SharedPreferenceHelper.shouldUseCache = false;
                    }
                    // Clear db when it's too big / old
                    /*if (postsInDb > (context.getPostManager().getAllPostsCount() + MAX_ADDITIONAL_POSTS_STORED)) {
                        PsLog.v("Db cleared because it was too big (" + postsInDb + " entries)\n" +
                                "Loading all posts this time.");
                        SharedPreferenceHelper.shouldUseCache = false;
                        deleteTable();
                        this.close();
                        return;
                    }*/
                    // Apply posts otherwise
                    if (context != null) {
                        PsLog.v("Applying " + toReturn.size() + " posts from db");
                        context.getPostManager().addPosts(toReturn);
                    } else {
                        PsLog.v("Context is null!");
                    }

                    this.close();
                }, Throwable::printStackTrace, () -> {
                    if (context.getPostManager().getAllPostsCount() < context.NUM_POST_TO_LOAD_ON_START) {
                        context.getPostManager().fetchNextPosts(context.NUM_POST_TO_LOAD_ON_START);
                        //todo not ready WIP
                    }
                });
    }

}
