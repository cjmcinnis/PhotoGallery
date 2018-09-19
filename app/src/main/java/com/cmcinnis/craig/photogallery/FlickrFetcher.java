package com.cmcinnis.craig.photogallery;

import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FlickrFetcher {
    private static final String TAG = "FlickrFetchr";
    private static final String API_KEY = "9650de53af26a027620effb89e77d645";

    private static final String FETCH_RECENT_METHODS = "flickr.photos.getRecent";
    private static final String SEARCH_METHOD = "flickr.photos.search";
    private static final Uri ENDPOINT = Uri
            .parse("https://api.flickr.com/services/rest")
            .buildUpon()
            .appendQueryParameter("api_key", API_KEY)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("nojsoncallback", "1")
            .appendQueryParameter("extras", "url_s")
            .build();

    public byte[] getUrlBytes(String urlSpec) throws IOException{
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try{
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = connection.getInputStream();

            if(connection.getResponseCode() != HttpURLConnection.HTTP_OK){
                throw new IOException(connection.getResponseMessage() + ": with " + urlSpec);
            }

            int bytesRead = 0;
            byte[] buffer = new byte[1024];
            while((bytesRead = in.read(buffer)) > 0){
                out.write(buffer, 0, bytesRead);
            }

            out.close();
            return out.toByteArray();
        }finally {
            connection.disconnect();
        }
    }

    public String getUrlString(String urlSpec) throws IOException{
        return new String(getUrlBytes(urlSpec));
    }


    public List<GalleryItem> downloadGalleryItems(String url){

        List<GalleryItem> items = new ArrayList<>();
        try{
            String jsonString = getUrlString(url);
            Log.i(TAG, "Received JSON: " + jsonString);
            JSONObject jsonBody = new JSONObject(jsonString);
            parseItems(items, jsonBody);
        }catch (IOException ioe){
            Log.e(TAG, "Failed to fetch items", ioe);
        }catch (JSONException je){
            Log.e(TAG, "Failed to parse JSON, je");
        }
        return items;
    }

    //pass in 0 if there is no page number
    private String buildUrl(String method, String query, int pageNumber){
        Uri.Builder uriBuilder = ENDPOINT.buildUpon()
                .appendQueryParameter("method", method);
        if(method.equals(SEARCH_METHOD)){
            uriBuilder.appendQueryParameter("text", query);
        }

        if(pageNumber != 0){
            uriBuilder.appendQueryParameter("page", String.valueOf(pageNumber));
        }

        return uriBuilder.build().toString();
    }

    public List<GalleryItem> fetchRecentPhotos(int pageNumber){
        String url = buildUrl(FETCH_RECENT_METHODS, null, pageNumber);
        return downloadGalleryItems(url);
    }

    public List<GalleryItem> searchPhotos(String query, int pageNumber){
        String url = buildUrl(SEARCH_METHOD, query, pageNumber);
        return downloadGalleryItems(url);
    }

    private void parseItems(List<GalleryItem> items, JSONObject jsonBody)
            throws IOException, JSONException{
        Gson gson = new Gson();
        Type galleryItemType = new TypeToken<ArrayList<GalleryItem>>() {}.getType();

        JSONObject photosJsonObject = jsonBody.getJSONObject("photos");
        JSONArray photosJsonArray = photosJsonObject.getJSONArray("photo");
        String jsonPhotoString = photosJsonArray.toString();

        List<GalleryItem> galleryItemList = gson.fromJson(jsonPhotoString, galleryItemType);

        for(int i = 0; i < galleryItemList.size(); i++){
            if(galleryItemList.get(i).getUrl() == null)
                galleryItemList.remove(i);
        }
        items.addAll(galleryItemList);
    }
}
