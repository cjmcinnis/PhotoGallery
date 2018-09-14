package com.cmcinnis.craig.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
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

public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private GridLayoutManager mPhotoLayoutManager;
    private List<GalleryItem> mItems = new ArrayList<>();
    private int mPhotoPageNumber;
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

        //initialize bitmap cache
        mPhotoCache = new LruCache<String, Drawable>(PHOTO_CACHE_SIZE);

        /*Handler responseHandler = new Handler();
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
        Log.i(TAG, "Background thread started");*/
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
                updateItems();
                mItems.clear();
                searchView.clearFocus();
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

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                updateItems();
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

        mPhotoPageNumber = 1;

        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if(!recyclerView.canScrollVertically(1)){
                    int lastVisibleItem = mPhotoLayoutManager.findLastVisibleItemPosition();

                    if((!updating) && (dy > 0) && (lastVisibleItem >= (mItems.size() - 1))) {

                        //if user reaches end of page
                        mPhotoPageNumber++;

                        //request new page
                        String query = QueryPreferences.getStoredQuery(getActivity());
                        new FetchItemsTask(query).execute();

                        Log.d(TAG, "Scrolled to " + mPhotoPageNumber);
                    }
                }
            }
        });

        mProgressBar = v.findViewById(R.id.progress_bar);
        //mProgressBar.setVisibility(View.GONE);

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

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView){
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
        }

        public void bindDrawable(Drawable drawable){
            mItemImageView.setImageDrawable(drawable);
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
            Drawable placeholder = getResources().getDrawable(R.drawable.bill_up_close);

            photoHolder.bindDrawable(placeholder);
            //mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());
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

            if(mQuery == null){
                return new FlickrFetcher().fetchRecentPhotos(mPhotoPageNumber);
            }else{
                return new FlickrFetcher().searchPhotos(mQuery);
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
