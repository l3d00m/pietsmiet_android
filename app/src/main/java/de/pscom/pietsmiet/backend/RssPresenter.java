package de.pscom.pietsmiet.backend;

import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.mcsoxford.rss.RSSFeed;
import org.mcsoxford.rss.RSSItem;
import org.mcsoxford.rss.RSSReader;
import org.mcsoxford.rss.RSSReaderException;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import de.pscom.pietsmiet.MainActivity;
import de.pscom.pietsmiet.adapters.CardItem;
import de.pscom.pietsmiet.util.PsLog;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static de.pscom.pietsmiet.adapters.CardItem.CardItemType.TYPE_UPLOAD_PLAN;

public class RssPresenter {
    private static final int DEFAULT_MAX = 10;
    Context mContext;
    static String uploadplanUrl = "http://pietsmiet.de/news?format=feed&type=rss";
    static String pietcastUrl = "http://www.pietcast.de/pietcast/feed/podcast/";
    public Subscription mPlanSubscription;
    public Subscription mPietcastSubscription;

    private MainActivity view;
    private String uploadplan;

    public RssPresenter() {
        parseUploadplan(DEFAULT_MAX);
    }

    /**
     * Loads the latest uploadplan URLS and parses them
     *
     * @param max Max URLs to parse, should be as low as possible
     */
    public void parseUploadplan(int max) {
        mPlanSubscription = Observable.defer(() -> Observable.just(loadRss(uploadplanUrl)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(Observable::from)
                .map(element -> element.getLink().toString())
                .take(max)
                .filter(link -> link != null)
                .flatMap(link -> Observable.defer(() -> Observable.just(parseHtml(link)))
                        .subscribeOn(Schedulers.io()))
                .filter(content -> content != null)
                .subscribe(uploadplan -> {
                    this.uploadplan = uploadplan;
                    publish();
                }, Throwable::printStackTrace);
    }

    private void publish() {
        if (view != null && uploadplan != null) {
            view.addNewCard(new CardItem("Uploadplan vom 21.10.", uploadplan, new Date(), TYPE_UPLOAD_PLAN)); //fixme Date
        }
    }

    public void onTakeView(MainActivity view) {
        this.view = view;
        publish();
    }

    /**
     * Loads the latests Piecasts
     */
    public void parsePietcast() {
        mPietcastSubscription = Observable.defer(() -> Observable.just(loadRss(pietcastUrl)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(Observable::from)
                .map(element -> element.getTitle())
                .subscribe(PsLog::v, Throwable::printStackTrace);
    }

    private List<RSSItem> loadRss(String url) {
        RSSReader reader = new RSSReader();

        try {
            RSSFeed feed = reader.load(url);
            return feed.getItems();
        } catch (RSSReaderException rssException) {
            PsLog.e(rssException.toString());
        }
        return null;
    }

    private String parseHtml(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/4.0")
                    .get();
            Elements content = doc.select("[itemprop=articleBody]");
            return content.toString();
        } catch (IOException e) {
            PsLog.e("Couldn't parse HTML: " + e.toString());
            return null;
        }
    }

}
