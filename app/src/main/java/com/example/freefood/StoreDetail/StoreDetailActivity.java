package com.example.freefood.StoreDetail;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.freefood.Model.Product;
import com.example.freefood.Model.Store;
import com.example.freefood.PurchaseProduct.PurchaseActivity;
import com.example.freefood.R;
import com.example.freefood.util.ImageUtils;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.gson.Gson;

import java.util.ArrayList;

public class StoreDetailActivity extends AppCompatActivity implements StoreDetailView {

    private ProductAdapter adapter;
    private StoreDetailPresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_detail);

        /* ─── toolbar & up-button ─── */
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            tb.setNavigationOnClickListener(v -> {
                // mimic the default back behavior
                onBackPressed();
            });
        }

        /* ─── unwrap the Store object passed from MainMenuActivity ─── */
        String json = getIntent().getStringExtra("STORE_JSON");
        Store store = new Gson().fromJson(json, Store.class);

        /* ─── collapsing header ─── */
        CollapsingToolbarLayout ctl = findViewById(R.id.collapseToolbar);
        ctl.setTitle(store.getStoreName());

        ImageView header = findViewById(R.id.imgStoreHeader);
        String logo = store.getStoreLogo();
        ImageUtils.loadStoreLogo(header, logo);

        /* ─── RecyclerView ─── */
        RecyclerView rv = findViewById(R.id.rvProducts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter(new ArrayList<>(), product -> {
            Intent i = new Intent(this, PurchaseActivity.class);
            i.putExtra("STORE_NAME", store.getStoreName());
            i.putExtra("PRODUCT_JSON", new Gson().toJson(product));
            startActivity(i);
        });

        rv.setAdapter(adapter);

        /* ─── MVP hookup ─── */
        StoreDetailViewModel vm   = new StoreDetailViewModelFactory().create(store);
        presenter = new StoreDetailPresenter(vm, this);
        presenter.loadProducts();
    }

    /* ─── View callbacks ─── */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // this is the toolbar’s “up” button
            onBackPressed();   // or just finish()
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override public void showProducts(java.util.List<Product> list) { adapter.updateList(list); }
    @Override public void showError(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
}
