package com.cmcinnis.craig.photogallery;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.util.LruCache;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends VisibleFragment {

    private static final String TAG = "PhotoGalleryFragment";
    private static final int PHOTO_JOB_ID = 1;

    private RecyclerView mPhotoRecyclerView;
    private GridLayoutManager mPhotoLayoutManager;
    private List<GalleryItem> mItems = new ArrayList<>();
    private boolean updating;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    private static final int PHOTO_CACHE_SIZE = 4 * 1024 * 1024;
    private static final int MAX_PHOTOS_CACHED = 60;
    private LruCache<String, Drawable> mPhotoCache;

    private ProgressBar mProgressBar;

    public static PhotoGalleryFragment newInstance(){
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        updateItems();

        //use JobService if running >= LOLLIPOP
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            JobScheduler scheduler = (JobScheduler)
                    getContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);

            //check if it is already scheduled
            boolean hasBeenScheduled = false;
            for(JobInfo jobInfo : scheduler.getAllPendingJobs()){
                if(jobInfo.getId() == PHOTO_JOB_ID){
                    hasBeenScheduled = true;
                }
            }
            //otherwise create it
            if(!hasBeenScheduled){
                JobInfo jobInfo = new JobInfo.Builder(PHOTO_JOB_ID, new ComponentName(getContext(), PollServiceJS.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setPeriodic(1000 * 60 * 15)
                        .setPersisted(true)
                        .build();
            }
        } else {
            Intent i = PollService.newIntent(getActivity());
            getActivity().startService(i);
        }

        //initialize bitmap cache
        mPhotoCache = new LruCache<String, Drawable>(PHOTO_CACHE_SIZE);


        //starting handler for downloading images
        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(
                new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder target, Bitmap thumbnail, String url) {
                        Drawable drawable = new BitmapDrawable(getResources(), thumbnail);
                        target.bindDrawable(drawable);
                        mPhotoCache.put(url, drawable);
                        mPhotoCache.trimToSize(MAX_PHOTOS_CACHED);
                    }
                });
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater){
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.fragment_photo_gallery, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                Log.d(TAG, "QueryTextSubmit " + s);
                QueryPreferences.setStoredQuery(getActivity(), s);

                mItems.clear();
                searchView.clearFocus();
                QueryPreferences.setStoredPage(getActivity(), 1);
                updateItems();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                Log.d(TAG, "QueryTextChange: " + s);

                return false;
            }
        });

        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                searchView.setQuery(query, false);
            }
        });

        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
        if(PollServiceUtils.isServiceAlarmOn(getActivity())){
            toggleItem.setTitle(R.string.stop_polling);
        } else {
            toggleItem.setTitle(R.string.start_polling);
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                updateItems();
                return true;
            case R.id.menu_item_toggle_polling:
                boolean shouldStartAlarm = !PollServiceUtils.isServiceAlarmOn(getActivity());
                PollServiceUtils.setServiceAlarm(getActivity(), shouldStartAlarm);
                getActivity().invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems(){
        String query = QueryPreferences.getStoredQuery(getActivity());
        if(mProgressBar != null){
            mProgressBar.setVisibility(View.VISIBLE);
        }
        new FetchItemsTask(query).execute();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoLayoutManager = new GridLayoutManager(getActivity(), 3);
        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.photo_recycler_view);
        mPhotoRecyclerView.setLayoutManager(mPhotoLayoutManager);
        updating = false;

        QueryPreferences.setStoredPage(getActivity(), 1);

        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if(!recyclerView.canScrollVertically(1)){
                    int lastVisibleItem = mPhotoLayoutManager.findLastVisibleItemPosition();

                    if((!updating) && (dy > 0) && (lastVisibleItem >= (mItems.size() - 1))) {

                        //if user reaches end of page
                        int currPage = QueryPreferences.getStoredPage(getActivity());
                        QueryPreferences.setStoredPage(getActivity(), currPage + 1);

                        //request new page
                        String query = QueryPreferences.getStoredQuery(getActivity());
                        new FetchItemsTask(query).execute();

                        Log.d(TAG, "Scrolled to " + currPage + 1);
                    }
                }
            }
        });

        mProgressBar = v.findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.GONE);

        setupAdapter();

        return v;

    }

    private void setupAdapter(){


        if(isAdded() && (mPhotoRecyclerView.getAdapter() == null)){ //checks that fragment has been attached to activity
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }else{
            mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView mItemImageView;
        private GalleryItem mGalleryItem;

        public PhotoHolder(View itemView){
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
            itemView.setOnClickListener(this);
        }

        public void bindDrawable(Drawable drawable){
            mItemImageView.setImageDrawable(drawable);
        }

        public void bindGalleryItem(GalleryItem galleryItem){
            mGalleryItem = galleryItem;
        }

        @Override
        public void onClick(View v) {
            Intent i = PhotoPageActivity.newIntent(getActivity(), mGalleryItem.getPhotoPageUri());
            startActivity(i);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder>{
        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems){
            mGalleryItems = galleryItems;

        }

        @NonNull
        @Override
        public PhotoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.list_item_gallery, parent, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoHolder photoHolder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            photoHolder.bindGalleryItem(galleryItem);
            Drawable placeholder = getResources().getDrawable(R.drawable.bill_up_close);

            photoHolder.bindDrawable(placeholder);

            //check if we have the image cached already
            if(mPhotoCache.get(galleryItem.getUrl()) == null) {
                mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());
            }else{
                photoHolder.bindDrawable(mPhotoCache.get(galleryItem.getUrl()));
            }
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>>{
        private String mQuery;

        public FetchItemsTask(String query){
            mQuery = query;
        }

        @Override
        protected List<GalleryItem> doInBackground(Void... params){
            updating = true;
            int pageNumber = QueryPreferences.getStoredPage(getActivity());

            if(mQuery == null){
                return new FlickrFetcher().fetchRecentPhotos(pageNumber);
            }else{
                return new FlickrFetcher().searchPhotos(mQuery, pageNumber);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items){
            updating = false;
            if(mProgressBar != null) {
                mProgressBar.setVisibility(View.GONE);
            }

            if(mItems == null)
            {
                mItems = new ArrayList<GalleryItem>();
            }

            mItems.addAll(items);
            setupAdapter();
        }
    }
}
