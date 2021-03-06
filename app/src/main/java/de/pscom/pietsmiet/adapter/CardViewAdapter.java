package de.pscom.pietsmiet.adapter;

import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import de.pscom.pietsmiet.R;
import de.pscom.pietsmiet.generic.Post;
import de.pscom.pietsmiet.generic.ViewItem;
import de.pscom.pietsmiet.util.LinkUtil;
import de.pscom.pietsmiet.util.SettingsHelper;
import de.pscom.pietsmiet.util.TimeUtils;
import de.pscom.pietsmiet.view.MainActivity;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;
import static de.pscom.pietsmiet.generic.Post.PostType.FACEBOOK;
import static de.pscom.pietsmiet.generic.Post.PostType.NEWS;
import static de.pscom.pietsmiet.generic.Post.PostType.PIETCAST;
import static de.pscom.pietsmiet.generic.Post.PostType.TWITTER;
import static de.pscom.pietsmiet.generic.Post.PostType.UPLOADPLAN;
import static de.pscom.pietsmiet.generic.Post.PostType.YOUTUBE;

public class CardViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ViewItem> items;
    private final MainActivity context;

    public CardViewAdapter(List<ViewItem> items, MainActivity context) {
        this.items = items;
        this.context = context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;
        switch (viewType) {
            case ViewItem.TYPE_POST:
                v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.card_view_main, parent, false);
                return new CardViewHolder(v);
            case ViewItem.TYPE_DATE_TAG:
                v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.card_view_day_mark, parent, false);
                return new DateTagCardViewHolder(v);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder_, int position) {
        // Don't use int position to avoid crashes!
        if (holder_ == null) return;

        switch (holder_.getItemViewType()) {
            case ViewItem.TYPE_DATE_TAG:
                DateTagCardViewHolder dtcvHolder = (DateTagCardViewHolder) holder_;
                dtcvHolder.tvDate.setText(new SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault()).format(items.get(holder_.getAdapterPosition()).getDate()));
                break;
            case ViewItem.TYPE_POST:
                CardViewHolder holder = (CardViewHolder) holder_;
                Post currentItem = (Post) items.get(holder.getLayoutPosition()); // todo errored fixed now?
                Post.PostType currentType = currentItem.getPostType();

                // Set basic information (title, time, color)
                Glide.with(context).clear(holder.wideImage);
                holder.time.setText(TimeUtils.getTimeSince(currentItem.getDate(), context));
                holder.title.setText(currentItem.getTitle());
                holder.headlineContainer.setBackgroundColor(ContextCompat.getColor(context, currentItem.getPostType().colorId));
                holder.text.setLinkTextColor(ContextCompat.getColor(context, currentItem.getPostType().colorId));

                holder.time.setTextColor(ContextCompat.getColor(context, android.R.color.white));
                holder.timeClockImage.setImageResource(R.drawable.ic_access_time_white_24dp);

                RelativeLayout.LayoutParams paramsTimeContainer =
                        new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT);
                paramsTimeContainer.removeRule(RelativeLayout.BELOW);
                paramsTimeContainer.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.rlImageContainer);
                holder.timeContainer.setLayoutParams(paramsTimeContainer);

                RelativeLayout.LayoutParams params =
                        new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT);

                // WITHOUT EMBEDDED IMAGE BUT PROFILE IMAGE IN BG: TODO
                //if(currentItem.getProfilePictureUrl() == null && currentItem.getProfilePictureHDUrl() == null) {
                //    params.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.rlImageContainer);
                //    params.addRule(RelativeLayout.ALIGN_TOP, R.id.rlImageContainer);
                //    params.removeRule(RelativeLayout.BELOW);
                //      holder.wideImage.setVisibility(VISIBLE);
                //      holder.wideImage.setImageDrawable(null);

                // WITHOUT ANY IMAGE
                if (currentItem.getThumbnailUrl() == null && currentItem.getThumbnailHDUrl() == null) {
                    params.removeRule(RelativeLayout.ALIGN_TOP);
                    params.removeRule(RelativeLayout.ALIGN_BOTTOM);
                    params.addRule(RelativeLayout.BELOW, R.id.rlHeadlineContainer);
                    holder.wideImage.setVisibility(GONE);
                    holder.wideImage.setImageDrawable(null);
                    holder.time.setTextColor(ContextCompat.getColor(context, android.R.color.black));
                    holder.timeClockImage.setImageResource(R.drawable.ic_access_time_black_24dp);
                    RelativeLayout.LayoutParams paramsTimeContainerChange =
                            new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                                    RelativeLayout.LayoutParams.WRAP_CONTENT);
                    paramsTimeContainerChange.addRule(RelativeLayout.BELOW, R.id.rlExpandableContainer);
                    paramsTimeContainerChange.removeRule(RelativeLayout.ALIGN_BOTTOM);
                    holder.timeContainer.setLayoutParams(paramsTimeContainerChange);
                } else {
                    //WITH EMBEDDED IMAGE:
                    params.removeRule(RelativeLayout.ALIGN_TOP);
                    params.removeRule(RelativeLayout.ALIGN_BOTTOM);
                    params.addRule(RelativeLayout.BELOW, R.id.rlImageContainer);
                    holder.wideImage.setVisibility(VISIBLE);
                    holder.wideImage.setImageDrawable(null);
                }
                // apply RULES
                holder.descriptionContainer.setLayoutParams(params);


                // Setup default visibilities as you never can trust the view holder
                holder.line.setVisibility(GONE);
                holder.username.setVisibility(GONE);
                holder.btnExpand.setVisibility(GONE);
                holder.descriptionContainer.setVisibility(VISIBLE);
                holder.expandableContainer.setVisibility(GONE);
                holder.expandedDescription.setVisibility(VISIBLE);
                holder.text.setVisibility(GONE);

                int resPostTypeImage;

                switch (currentType) {
                    case PIETCAST:
                        resPostTypeImage = R.drawable.ic_radio_white_24dp;
                        break;
                    case UPLOADPLAN:
                        resPostTypeImage = R.drawable.ic_assignment_white_24dp;
                        break;
                    case NEWS:
                        resPostTypeImage = R.drawable.ic_rss_feed_white_24dp;
                        break;
                    case PS_VIDEO:
                        resPostTypeImage = R.drawable.ic_ondemand_video_white_24dp;
                        setupImageViews(holder.wideImage, currentItem);
                        break;
                    case YOUTUBE:
                        resPostTypeImage = R.drawable.ic_youtube_light_logo;
                        setupImageViews(holder.wideImage, currentItem);
                        break;
                    case TWITTER:
                        resPostTypeImage = R.drawable.ic_twitter_social_icon_circle_white_24dp;
                        holder.line.setVisibility(VISIBLE);
                        holder.username.setVisibility(VISIBLE);
                        holder.username.setText(String.format("@%s", currentItem.getUsername()));
                        setupImageViews(holder.wideImage, currentItem);
                        // Setup text for social media
                        if (currentItem.getDescription() != null && !currentItem.getDescription().isEmpty()) {
                            holder.text.setVisibility(VISIBLE);
                            holder.text.setText(Html.fromHtml(currentItem.getDescription()));
                        }
                        break;
                    case FACEBOOK:
                        resPostTypeImage = R.drawable.ic_facebook_white;
                        setupImageViews(holder.wideImage, currentItem);
                        // Setup text for social media
                        if (currentItem.getDescription() != null && !currentItem.getDescription().isEmpty()) {
                            holder.text.setVisibility(VISIBLE);
                            holder.text.setText(Html.fromHtml(currentItem.getDescription()));
                        }
                        break;
                    default:
                        resPostTypeImage = R.drawable.ic_radio_white_24dp;
                }

                holder.postTypeLogo.setImageResource(resPostTypeImage);


                // Setup expand container and description
                if (currentType == UPLOADPLAN || currentType == NEWS || currentType == PIETCAST) {
                    holder.descriptionContainer.setVisibility(GONE);
                    if (currentItem.getDescription() != null && !currentItem.getDescription().isEmpty()) {
                        holder.expandedDescription.setText(Html.fromHtml(currentItem.getDescription()));
                        holder.btnExpand.setVisibility(VISIBLE);

                        if (SettingsHelper.isOnlyType(currentItem.getPostType())) {
                            holder.btnExpand.setVisibility(GONE);
                            holder.expandableContainer.setVisibility(VISIBLE);
                        } else {
                            holder.btnExpand.setBackground(ContextCompat.getDrawable(context, R.drawable.ic_expand_more_black_24dp));
                            holder.expandableContainer.setVisibility(GONE);
                        }
                    }


                    holder.btnExpand.setOnClickListener(view -> {
                        if (holder.expandableContainer.getVisibility() == GONE) {
                            holder.expandableContainer.setVisibility(VISIBLE);
                            view.setBackground(ContextCompat.getDrawable(context, R.drawable.ic_expand_less_black_24dp));
                        } else {
                            holder.expandableContainer.setVisibility(GONE);
                            view.setBackground(ContextCompat.getDrawable(context, R.drawable.ic_expand_more_black_24dp));
                        }
                    });
                }

                // Open card externally on click
                holder.itemView.setOnClickListener(ignored -> {
                    if (currentType == FACEBOOK || currentType == TWITTER || currentType == YOUTUBE) {
                        LinkUtil.openUrlExternally(context, currentItem.getUrl());
                    } else {
                        LinkUtil.openUrl(context, currentItem.getUrl());
                    }
                });
                break;
        }

    }

    private void setupImageViews(ImageView view, Post currentItem) {
        RequestOptions options = new RequestOptions().centerCrop();
        if (currentItem.getThumbnailUrl() != null || currentItem.getThumbnailHDUrl() != null) {
            Glide.with(context)
                    .load((SettingsHelper.shouldLoadHDImages(context) ?
                            currentItem.getThumbnailHDUrl() :
                            currentItem.getThumbnailUrl()))
                    .apply(options)
                    .transition(withCrossFade())
                    .into(view);
        } else {
            view.setVisibility(GONE);
            Glide.with(context).clear(view);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

}
