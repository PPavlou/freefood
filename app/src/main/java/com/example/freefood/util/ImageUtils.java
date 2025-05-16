package com.example.freefood.util;

import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.example.freefood.R;

public final class ImageUtils {
    private ImageUtils() { /* no‑op */ }

    /**
     * Loads a store logo URL into an ImageView, falling back to a placeholder
     * if the URL is empty, invalid, or the load fails.
     */
    public static void loadStoreLogo(ImageView imgView, String logoUrl) {
        if (logoUrl == null || logoUrl.isEmpty()) {
            imgView.setImageResource(R.drawable.ic_store_placeholder);
        } else if (logoUrl.startsWith("http://") || logoUrl.startsWith("https://")) {
            try {
                Glide.with(imgView.getContext())
                        .load(logoUrl)
                        .placeholder(R.drawable.ic_store_placeholder)
                        .error(R.drawable.ic_store_placeholder)
                        .into(imgView);
            } catch (IllegalArgumentException e) {
                imgView.setImageResource(R.drawable.ic_store_placeholder);
            }
        } else {
            imgView.setImageResource(R.drawable.ic_store_placeholder);
        }
    }
}
