package com.example.freefood.PurchaseProduct;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.freefood.Main.MainActivityTEMP;      // ← host / port constants
import com.example.freefood.Model.Product;
import com.example.freefood.R;
import com.google.gson.Gson;

/** UI for purchasing a single product. */
public class PurchaseActivity extends AppCompatActivity
        implements PurchasePresenter.PurchaseView {

    private PurchasePresenter presenter;
    private EditText etQty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase);

        /* ─── toolbar ─── */
        Toolbar tb = findViewById(R.id.toolbarPurchase);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        /* ─── unwrap Intent extras ─── */
        String storeName       = getIntent().getStringExtra("STORE_NAME");
        String productJson     = getIntent().getStringExtra("PRODUCT_JSON");
        Product product        = new Gson().fromJson(productJson, Product.class);

        /* ─── bind UI ─── */
        TextView tvProdName  = findViewById(R.id.tvPurchaseProductName);
        TextView tvProdPrice = findViewById(R.id.tvPurchasePrice);
        TextView tvAvail     = findViewById(R.id.tvPurchaseAvailable);
        etQty                = findViewById(R.id.etPurchaseQty);
        Button btnPurchase   = findViewById(R.id.btnConfirmPurchase);

        tvProdName.setText(product.getProductName());
        tvProdPrice.setText(String.format("$%.2f", product.getPrice()));
        tvAvail.setText("In stock: " + product.getAvailableAmount());

        /* ─── MVP hookup ─── */
        PurchaseViewModel vm = new PurchaseViewModelFactory()
                .create(MainActivityTEMP.SERVER_HOST, MainActivityTEMP.SERVER_PORT);
        presenter = new PurchasePresenter(vm, this);

        btnPurchase.setOnClickListener(v ->
                presenter.purchaseProduct(storeName,
                        product.getProductName(),
                        etQty.getText().toString().trim()));
    }

    /* ─── callbacks from Presenter ─── */
    @Override public void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        Log.d("PurchaseActivity", msg);
    }

    @Override public void clearQuantityField() { etQty.setText(""); }
}
