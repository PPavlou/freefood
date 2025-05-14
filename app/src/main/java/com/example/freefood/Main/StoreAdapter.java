package com.example.freefood.Main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.freefood.Model.Store;
import com.example.freefood.R;
import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.Holder> {
    private final List<Store> stores;
    private final OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onStoreClick(Store s);
    }

    public StoreAdapter(List<Store> stores, OnItemClickListener listener) {
        this.stores = stores;
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_store, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int pos) {
        Store s = stores.get(pos);
        h.name.setText(s.getStoreName());
        h.tagline.setText(s.getTagline());
        h.rating.setText(s.getStarRatingString());
        h.distance.setText(" • " + s.getDistanceKm() + " km");
        // TODO: load real image URL if you have one:
        // Glide.with(h.imgStore.getContext())
        //      .load(s.getImageUrl())
        //      .placeholder(R.drawable.ic_store_placeholder)
        //      .into(h.imgStore);

        h.itemView.setOnClickListener(v -> clickListener.onStoreClick(s));
    }

    @Override public int getItemCount() { return stores.size(); }

    /** in StoreAdapter class **/
    public void updateStores(List<Store> newStores) {
        this.stores.clear();
        this.stores.addAll(newStores);
        notifyDataSetChanged();
    }

    public static class Holder extends RecyclerView.ViewHolder {
        public final ImageView imgStore;
        public final TextView name, tagline, rating, distance;
        public Holder(@NonNull View itemView) {
            super(itemView);
            imgStore = itemView.findViewById(R.id.imgStore);
            name     = itemView.findViewById(R.id.tvStoreName);
            tagline  = itemView.findViewById(R.id.tvStoreTagline);
            rating   = itemView.findViewById(R.id.tvRating);
            distance = itemView.findViewById(R.id.tvDistance);
        }
    }
}
