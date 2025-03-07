package com.cato.connect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public class ImageRequest {
    private static final int CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 8); // Use 1/8th of available memory
    private static final LruCache<String, Bitmap> memoryCache = new LruCache<String, Bitmap>(CACHE_SIZE) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };
    static OkHttpClient okHttpClient = null;
    static ExecutorService executor = Executors.newFixedThreadPool(2);
    public static void makeImageRequest(Context context, String url, OkHttpClient client, ImageCallback callback) {
        executor.execute(() -> {
            Bitmap cachedBitmap = memoryCache.get(url);
            if (cachedBitmap != null) { //TODO: remove cache stuff, not needed
                callback.onImageLoaded(cachedBitmap);
                return;
            }
            if (okHttpClient == null) {
                okHttpClient = client;
            }
            RequestOptions requestOptions = new RequestOptions()
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .skipMemoryCache(false);

            GlideApp.with(context)
                    .asBitmap()
                    .load(url)
                    .placeholder(R.drawable.placeholder_art)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .apply(requestOptions)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            Log.d("ImageRequest", "Image loaded from network");
                            memoryCache.put(url, resource);
                            callback.onImageLoaded(resource);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            // Clean up resources if needed
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            Log.e("ImageRequest", "Image load failed");
                            super.onLoadFailed(errorDrawable);
                            callback.onImageLoaded(null);
                        }
                    });
        });
    }

    public interface ImageCallback {
        void onImageLoaded(Bitmap bitmap);
    }
}
