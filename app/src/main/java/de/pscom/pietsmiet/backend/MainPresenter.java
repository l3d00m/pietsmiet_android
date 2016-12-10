package de.pscom.pietsmiet.backend;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.pscom.pietsmiet.MainActivity;
import de.pscom.pietsmiet.generic.Post;

abstract class MainPresenter {
    MainActivity view;
    Post.PostBuilder postBuilder;
    @SuppressWarnings("CanBeFinal")
    List<Post> posts = new ArrayList<>();

    MainPresenter(MainActivity view) {
        this.view = view;
    }

    /**
     * FETCHES ALLLLL NEW POSTS!!!! MAYBE CHANGE!
     */
    public abstract void fetchPostsSince(Date dAfter);

    //todo nachschauen ob das geht mit eigtl. unlogischem generic als Wildcard und override -> potentiell gegen Regeln
    //protected abstract void fetchData(Observable call);
    /**
     *
     */
    public abstract void fetchPostsUntil(Date dBefore, int numPosts);


}
