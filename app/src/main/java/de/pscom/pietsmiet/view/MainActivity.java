package de.pscom.pietsmiet.view;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.Serializable;
import java.util.Date;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.pscom.pietsmiet.R;
import de.pscom.pietsmiet.adapter.CardViewAdapter;
import de.pscom.pietsmiet.customtabsclient.CustomTabActivityHelper;
import de.pscom.pietsmiet.generic.EndlessScrollListener;
import de.pscom.pietsmiet.generic.Post;
import de.pscom.pietsmiet.json_model.twitchApi.TwitchStream;
import de.pscom.pietsmiet.presenter.PostPresenter;
import de.pscom.pietsmiet.repository.PostRepositoryImpl;
import de.pscom.pietsmiet.service.MyFirebaseMessagingService;
import de.pscom.pietsmiet.stetho.StethoHelper;
import de.pscom.pietsmiet.util.CacheUtil;
import de.pscom.pietsmiet.util.DatabaseHelper;
import de.pscom.pietsmiet.util.FirebaseUtil;
import de.pscom.pietsmiet.util.LinkUtil;
import de.pscom.pietsmiet.util.NetworkUtil;
import de.pscom.pietsmiet.util.PsLog;
import de.pscom.pietsmiet.util.SecretConstants;
import de.pscom.pietsmiet.util.SettingsHelper;
import de.pscom.pietsmiet.util.SharedPreferenceHelper;
import de.pscom.pietsmiet.util.TimeUtils;
import de.pscom.pietsmiet.util.TwitchHelper;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

import static de.pscom.pietsmiet.util.SettingsHelper.isOnlyType;
import static de.pscom.pietsmiet.util.SharedPreferenceHelper.KEY_APP_FIRST_RUN;
import static de.pscom.pietsmiet.util.SharedPreferenceHelper.KEY_NOTIFY_VIDEO_SETTING;

public class MainActivity extends BaseActivity implements MainActivityView, NavigationView.OnNavigationItemSelectedListener {
    public static final int RESULT_CLEAR_CACHE = 17;
    public static final int REQUEST_SETTINGS = 16;
    private static final long MAX_TWITCH_CHECK_TIME_DIFF = 5 * TimeUtils.MINUTE_MILLIS;
    private static final long MAX_INACTIVITY_TIME_TO_RELOAD = 15 * TimeUtils.MINUTE_MILLIS;

    private CustomTabActivityHelper mCustomTabActivityHelper;

    private boolean CLEAR_CACHE_FLAG_DRAWER = false;

    private long lastDateCheckedTwitch;

    private CardViewAdapter adapter;
    @BindView(R.id.dl_root)
    DrawerLayout mDrawer;
    @BindView(R.id.nav_view)
    NavigationView mNavigationView;
    public EndlessScrollListener scrollListener;
    @BindView(R.id.swipeContainer)
    SwipeRefreshLayout refreshLayout;
    @BindView(R.id.btnToTop)
    FloatingActionButton fabToTop;
    @BindView(R.id.cardList)
    RecyclerView recyclerView;
    private MenuItem pietstream_banner;

    private PostPresenter postPresenter;
    private long exitTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseUtil.disableCollectionOnDebug(this.getApplicationContext());
        StethoHelper.init(this);

        FirebaseUtil.loadRemoteConfig();
        ButterKnife.bind(this);
        SettingsHelper.loadAllSettings(this);
        FirebaseUtil.setupTopicSubscriptions();

        setupToolbar(null);

        mCustomTabActivityHelper = new CustomTabActivityHelper();

        postPresenter = new PostPresenter(this,
                new PostRepositoryImpl(this),
                DatabaseHelper.getInstance(this.getApplicationContext()),
                new NetworkUtil(this.getApplicationContext()),
                this.getApplicationContext());

        setupNotificationChannels();
        setupRecyclerView();
        setupDrawer();

        refreshLayout.setOnRefreshListener(() -> postPresenter.fetchNewPosts());
        refreshLayout.setProgressViewOffset(false, -130, 80); //todo Find another way. Just added to support Android 4.x
        refreshLayout.setColorSchemeColors(getResources().getColor(R.color.pietsmiet));
        refreshLayout.setColorSchemeResources(R.color.colorPrimary, R.color.pietsmiet, R.color.colorPrimaryDark);

        // Top Button init
        fabToTop.setVisibility(View.INVISIBLE);
        fabToTop.setOnClickListener(new View.OnClickListener() { //Butter Knife useful?
            @Override
            public void onClick(View v) {
                if (((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition() < 85) {
                    recyclerView.smoothScrollToPosition(0);
                } else {
                    recyclerView.scrollToPosition(0);
                }
                fabToTop.hide(new FloatingActionButton.OnVisibilityChangedListener() {
                    @Override
                    public void onHidden(FloatingActionButton fab) {
                        super.onHidden(fab);
                        fab.setVisibility(View.INVISIBLE);
                    }
                });
            }
        });
        // End Top Button init

        if (SettingsHelper.boolAppFirstRun) {
            displayNotificationSelection();

            // Set AppFirstRun to false //todo maybe position this in OnDestroy / OnPause, because of logic
            SettingsHelper.boolAppFirstRun = false;
            SharedPreferenceHelper.setSharedPreferenceBoolean(this, KEY_APP_FIRST_RUN, false);
        }
        new SecretConstants(this);
    }

    private void setupNotificationChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String id = "Default";
            CharSequence name = getString(R.string.channel_default_name);
            String description = getString(R.string.channel_default_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel mChannel = new NotificationChannel(id, name, importance);
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            mChannel.setLightColor(getResources().getColor(R.color.pietsmiet));
            mChannel.enableVibration(true);
            mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    private void displayNotificationSelection() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_video_notification)
                .setPositiveButton(R.string.yes, (dialog, id) -> {
                    SettingsHelper.boolVideoNotification = true;
                    SharedPreferenceHelper.setSharedPreferenceBoolean(this, KEY_NOTIFY_VIDEO_SETTING, true);
                    FirebaseUtil.setFirebaseTopicSubscription(FirebaseUtil.TOPIC_VIDEO, true);
                })
                .setNegativeButton(R.string.no, (dialog, id) -> {
                    SettingsHelper.boolVideoNotification = false;
                    SharedPreferenceHelper.setSharedPreferenceBoolean(this, KEY_NOTIFY_VIDEO_SETTING, false);
                    FirebaseUtil.setFirebaseTopicSubscription(FirebaseUtil.TOPIC_VIDEO, false);
                });
        builder.create().show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (postPresenter != null) postPresenter.stopSubscriptions();
        if (refreshLayout != null) refreshLayout.setRefreshing(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Serializable serCategory = getIntent().getSerializableExtra(MyFirebaseMessagingService.EXTRA_TYPE);
        if (serCategory instanceof Post.PostType) {
            Post.PostType category = (Post.PostType) serCategory;
            // As this code is onStart, remove the intent to avoid that it'll execute again
            getIntent().removeExtra(MyFirebaseMessagingService.EXTRA_TYPE);
            // Log an event to firebase
            Bundle bundle = new Bundle();
            // TODO rework bundle to not have to send ints -> dont use ordinals -> they can change -> Error
            bundle.putInt(FirebaseAnalytics.Param.ITEM_NAME, category.ordinal());
            FirebaseAnalytics.getInstance(this).logEvent("notification_clicked", bundle);
            // Select the category in the drawer (this will update sharedPrefs too)
            onNavigationItemSelected(mNavigationView.getMenu().findItem(category.drawerId));
            // Update settings from sharedPrefs
            SettingsHelper.loadAllSettings(getBaseContext());
            // Fetch posts based on the new settings
            if(postPresenter.getPostsToDisplay().size() > 0) {
                postPresenter.fetchNewPosts();
            } else {
                postPresenter.fetchNextPosts();
            }
        } else if (postPresenter.getPostsToDisplay().isEmpty()) {
            // Load posts from db
            DatabaseHelper.getInstance(this).displayPostsFromCache(postPresenter);
        } else if ((exitTime - System.currentTimeMillis()) > MAX_INACTIVITY_TIME_TO_RELOAD) {
            // Auto reload posts if going back to activity after the time specified in MAX_INACTIVITY_TIME_TO_RELOAD
            postPresenter.fetchNewPosts();
        }
        mCustomTabActivityHelper.bindCustomTabsService(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        exitTime = System.currentTimeMillis();
        mCustomTabActivityHelper.unbindCustomTabsService(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (postPresenter != null) postPresenter.stopSubscriptions();
        if (refreshLayout != null) refreshLayout.setRefreshing(false);
        if (scrollListener != null) scrollListener.resetState();
    }

    @Override
    public void onResume() {
        super.onResume();
        SettingsHelper.loadAllSettings(this);
        // Update adapter to refresh timestamps
        refreshAdapter();
    }

    private void setupRecyclerView() {
        adapter = new CardViewAdapter(postPresenter.getPostsToDisplay(), this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Retain an instance so that you can call `resetState()` for fresh searches
        scrollListener = new EndlessScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int totalItemsCount, RecyclerView view) {
                // Triggered only when new data needs to be appended to the list
                postPresenter.fetchNextPosts();
            }
        };
        // Adds the scroll listener to RecyclerView
        recyclerView.addOnScrollListener(scrollListener);
    }

    private void setupDrawer() {
        mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);
        pietstream_banner = mNavigationView.getMenu().findItem(R.id.nav_pietstream_banner);

        // Iterate through every menu item and save it's state
        for (Post.PostType item : Post.PostType.values()) {
            if (mNavigationView != null) {
                MenuItem mi = mNavigationView.getMenu().findItem(item.drawerId);
                if(mi != null) {
                    Switch checker = (Switch) mi.getActionView();
                    checker.setChecked(SettingsHelper.getSettingsValueForType(item));
                    checker.setOnCheckedChangeListener((view, check) -> {
                        if (check)
                            CLEAR_CACHE_FLAG_DRAWER = true; //todo improve if for example a user just switched on off on -> dont clear cache
                        SharedPreferenceHelper.setSharedPreferenceBoolean(getBaseContext(), SettingsHelper.getSharedPreferenceKeyForType(item), checker.isChecked());
                    });
                }
            }
        }

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, mDrawer, mToolbar, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                updatePostsCategoriesFromDrawer();
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                reloadTwitchBanner();
            }
        };
        mDrawer.addDrawerListener(toggle);
        toggle.syncState();
    }

    private void refreshAdapter() {
        Observable.just("")
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                            if (recyclerView != null) recyclerView.getRecycledViewPool().clear();
                            if (adapter != null) adapter.notifyDataSetChanged();
                        }
                );
    }

    private void scrollToTop() {
        Observable.just("")
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                            if (recyclerView != null) recyclerView.scrollToPosition(0);
                        }
                );
    }

    private void updatePostsCategoriesFromDrawer() {
        SettingsHelper.loadAllSettings(getBaseContext());
        if (CLEAR_CACHE_FLAG_DRAWER) {
            clearCache();
            postPresenter.fetchNextPosts();
            CLEAR_CACHE_FLAG_DRAWER = false;
        } else {
            postPresenter.updateSettingsFilters();
            scrollListener.resetState();
        }
    }

    /**
     * Reloads the stream status and updates the banner in the SideMenu
     */
    // TODO not the right place
    private void reloadTwitchBanner() {
        long current = new Date().getTime();
        if ((current - lastDateCheckedTwitch) > MAX_TWITCH_CHECK_TIME_DIFF) {
            lastDateCheckedTwitch = current;
            Observable<TwitchStream> obsTTV = new TwitchHelper().getStreamStatus(SettingsHelper.stringTwitchChannelIDPietstream);
            obsTTV.subscribe((stream) -> {
                if (stream != null) {
                    pietstream_banner.setVisible(true);
                } else {
                    pietstream_banner.setVisible(false);
                }
            }, (err) -> PsLog.e("Could not update Twitch status", err));
        }
    }

    private void updateAdapterItemRange(int startPosition, int size) {
        Observable.just("")
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                            if (adapter != null) adapter.notifyItemRangeInserted(startPosition, size);
                        }
                );
    }

    private void setRefreshAnim(boolean val) {
        Observable.just(val)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bool -> {
                    if (refreshLayout != null) refreshLayout.setRefreshing(bool);
                });
    }

    public void showMessage(String message, int length, boolean retryLoadingButton, boolean fetchDirectionDown) {
        Observable.just(message)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(msg -> {
                    if (findViewById(R.id.main_layout) != null) {
                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), msg, length);
                        if (retryLoadingButton) sb.setAction(R.string.info_retry, (view) -> {
                            scrollListener.resetState();
                            if (fetchDirectionDown) {
                                postPresenter.fetchNextPosts();
                            } else {
                                postPresenter.fetchNewPosts();
                            }
                        });
                        sb.show();
                    } else {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void clearCache() {
        DatabaseHelper.getInstance(this).clearDB();
        postPresenter.clearPosts();
        refreshAdapter();
        scrollListener.resetState();
        CacheUtil.trimCache(this);
        fabToTop.hide();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_upload_plan:
            case R.id.nav_facebook:
            case R.id.nav_twitter:
            case R.id.nav_pietcast:
            case R.id.nav_ps_news:
            case R.id.nav_video_ps:
            case R.id.nav_video_yt:
                Post.PostType pt = Post.PostType.getByDrawerId(item.getItemId());
                if (((Switch) item.getActionView()).isChecked() && (pt != null && isOnlyType(pt)) ) {
                    for (Post.PostType z : Post.PostType.values()) {
                        int id = z.drawerId;
                        Switch aSwitch = ((Switch) mNavigationView.getMenu().findItem(id).getActionView());
                        aSwitch.setChecked(true);
                        recyclerView.scrollToPosition(0);
                    }
                } else {
                    for (Post.PostType i : Post.PostType.values()) {
                        int id = i.drawerId;
                        Switch aSwitch = ((Switch) mNavigationView.getMenu().findItem(id).getActionView());
                        if (id == item.getItemId()) {
                            aSwitch.setChecked(true);
                            recyclerView.scrollToPosition(0);
                        } else aSwitch.setChecked(false);
                    }
                }
                break;
            case R.id.nav_feedback:
                LinkUtil.openUrl(this, SettingsHelper.stringFeedbackUrl);
                break;
            case R.id.nav_help:
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
                break;
            case R.id.nav_settings:
                startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), REQUEST_SETTINGS);
                break;
            case R.id.nav_pietstream_banner:
                LinkUtil.openUrlExternally(this, SettingsHelper.stringPietstreamUrl);
                break;
            default:
                return false;
        }
        // Close the navigation drawer
        mDrawer.closeDrawers();

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SETTINGS) {
            SettingsHelper.loadAllSettings(this);
            if (resultCode == RESULT_CLEAR_CACHE) {
                clearCache();
                showMessage(getString(R.string.info_cleared_cache));
            } else {
                // Update adapter to refresh timestamps
                refreshAdapter();
            }
        }
    }


    @Override
    public void freshLoadingCompleted() {
        refreshAdapter();
        setRefreshAnim(false);

        FirebaseAnalytics.getInstance(this).logEvent(FirebaseUtil.EVENT_FRESH_COMPLETED, new Bundle());
    }

    @Override
    public void loadingNextCompleted(int startPosition, int itemCount) {
        updateAdapterItemRange(startPosition, itemCount);
        setRefreshAnim(false);

        Bundle bundle = new Bundle();
        bundle.putInt(FirebaseUtil.PARAM_START_POSITION, startPosition);
        bundle.putInt(FirebaseUtil.PARAM_ITEM_COUNT, itemCount);
        FirebaseAnalytics.getInstance(this).logEvent(FirebaseUtil.EVENT_NEXT_COMPLETED, bundle);
    }

    @Override
    public void loadingNewCompleted(int itemCount) {
        updateAdapterItemRange(0, itemCount);
        setRefreshAnim(false);
        scrollToTop();

        Bundle bundle = new Bundle();
        bundle.putInt(FirebaseUtil.PARAM_ITEM_COUNT, itemCount);
        FirebaseAnalytics.getInstance(this).logEvent(FirebaseUtil.EVENT_NEW_COMPLETED, bundle);
    }

    @Override
    public void noNetworkError() {
        showMessage(getString(R.string.error_no_network));
        scrollListener.resetState();
    }

    @Override
    public void loadingStarted() {
        setRefreshAnim(true);
    }

    @Override
    public void loadingFailed(String message, boolean fetchDirectionDown) {
        showMessage(message, Snackbar.LENGTH_INDEFINITE, true, fetchDirectionDown);
        setRefreshAnim(false);
    }

    @Override
    public void showMessage(String message) {
        showMessage(message, Snackbar.LENGTH_LONG, false, false);
    }
}
