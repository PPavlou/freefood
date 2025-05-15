package com.example.freefood.StoreDetail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.freefood.Model.Product;
import com.example.freefood.R;

import java.util.List;
import java.util.Locale;

/** Binds a product row inside StoreDetail. */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    public interface OnProductClickListener { void onClick(Product product); }

    private List<Product> products;
    private final OnProductClickListener listener;

    public ProductAdapter(List<Product> products,
                          OnProductClickListener listener) {
        this.products  = products;
        this.listener  = listener;
    }

    /** Replace list & refresh. */
    public void updateList(List<Product> newList) {
        this.products = newList;
        notifyDataSetChanged();           // fine for short lists
    }

    // ───────── Recycler plumbing ─────────
    @NonNull
    @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vType) {
        View v = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_product, p, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Product p = products.get(pos);

        h.tvName .setText(p.getProductName());
        h.tvPrice.setText(String.format(Locale.US,"$%.2f", p.getPrice()));

        // There is no image-URL field in Product yet → simple placeholder
        h.img.setImageResource(R.drawable.ic_product_placeholder);

        h.btnView.setOnClickListener(v -> listener.onClick(p));
    }

    @Override public int getItemCount() { return products == null ? 0 : products.size(); }

    /* ViewHolder must be static + package-private to silence IDE warnings. */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView img;
        final TextView  tvName, tvPrice;
        final Button    btnView;
        ViewHolder(@NonNull View item) {
            super(item);
            img      = item.findViewById(R.id.imgProduct);
            tvName   = item.findViewById(R.id.tvProductName);
            tvPrice  = item.findViewById(R.id.tvProductPrice);
            btnView  = item.findViewById(R.id.btnView);
        }
    }
}
