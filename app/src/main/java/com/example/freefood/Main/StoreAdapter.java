package com.example.freefood.Main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_store, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int pos) {
        Store s = stores.get(pos);
        holder.name.setText(s.getStoreName());
        holder.itemView.setOnClickListener(v -> clickListener.onStoreClick(s));
    }

    @Override public int getItemCount() { return stores.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView name;
        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvStoreName);
        }
    }
}
