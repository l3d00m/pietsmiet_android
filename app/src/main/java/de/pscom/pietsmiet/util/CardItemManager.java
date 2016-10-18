package de.pscom.pietsmiet.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.pscom.pietsmiet.MainActivity;
import de.pscom.pietsmiet.generic.Post;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

import static de.pscom.pietsmiet.util.CardType.FACEBOOK;
import static de.pscom.pietsmiet.util.CardType.TWITTER;


public class CardItemManager {
    public static final int DISPLAY_ALL = 10;
    public static final int DISPLAY_SOCIAL = DISPLAY_ALL + 1;

    private List<Post> currentCards = new ArrayList<>();
    private List<Post> allCards = new ArrayList<>();
    private MainActivity mView;
    @CardType.ItemTypeDrawer
    private int currentlyDisplayedType = DISPLAY_ALL;

    public CardItemManager(MainActivity view) {
        mView = view;
    }

    /**
     * Adds a card to the card list. If the card belongs to the current category / type, it'll be shown instant
     *
     * @param post Card Item
     */
    public void addCard(Post post) {
/*
        Observable.just(getAllCardItems())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(Observable::from)
                .filter(card -> !card.getTitle().equals(cardItem.getTitle()) || !card.getDescription().equals(cardItem.getDescription()))
                .doOnNext(card -> {
                    if (isAllowedType(cardItem)) {
                        currentCards.add(cardItem);
                        Collections.sort(currentCards);
                        if (mView != null) {
                            mView.updateAdapter();
                        }
                    }
                })
                .toSortedList()
                .subscribe(list -> {
                    allCards.clear();
                    allCards.addAll(list);
                });
        */

        //todo: do this iteration asynchrounous! (rxjava?) like above, but better & working
        boolean add = true;
        try {
            for (Post card : getAllCardItems()) {
                if (card.getTitle().equals(post.getTitle()) && card.getDescription().equals(post.getDescription())) {
                    add = false;
                }
            }
        } catch (Exception e){
            PsLog.i(e.getMessage());
        }


        if (add) {
            allCards.add(post);
            Collections.sort(allCards);

            if (isAllowedType(post)) {
                currentCards.add(post);
                Collections.sort(currentCards);
                if (mView != null) mView.updateAdapter();
            }
        }


    }

    /**
     * @return All fetched cards, whether they're currently shown or not
     */
    public List<Post> getAllCardItems() {
        return currentCards;
    }

    /**
     * Switches back to the "all" category. Shows all cards, independent of their category
     */
    public void displayAllCards() {
        currentlyDisplayedType = DISPLAY_ALL;
        currentCards.clear();
        currentCards.addAll(allCards);
        if (mView != null) mView.updateAdapter();
    }

    /**
     * Show only cards that belong to a certain category / type
     *
     * @param cardItemType Type that the cards should belong to
     */
    public void displayOnlyCardsFromType(@CardType.ItemTypeDrawer int cardItemType) {
        currentlyDisplayedType = cardItemType;
        Observable.just(allCards)
                .flatMap(Observable::from)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(AndroidSchedulers.mainThread())
                .filter(this::isAllowedType)
                .toList()
                .subscribe(cards -> {
                    currentCards.clear();
                    currentCards.addAll(cards);
                    if (mView != null) {
                        mView.updateAdapter();
                        mView.scrollToTop();
                    }
                }, Throwable::printStackTrace);
    }

    /**
     * @param post Card item
     * @return If the specified card item belongs to the currently shown category / type or not
     */
    private boolean isAllowedType(Post post) {
        int cardItemType = post.getCardItemType();
        if (currentlyDisplayedType == DISPLAY_ALL) return true;
        else if (currentlyDisplayedType == DISPLAY_SOCIAL) {
            if (cardItemType == TWITTER
                    || cardItemType == FACEBOOK) {
                return true;
            }
        } else {
            //noinspection WrongConstant
            if (cardItemType == currentlyDisplayedType) return true;
        }
        return false;
    }


}