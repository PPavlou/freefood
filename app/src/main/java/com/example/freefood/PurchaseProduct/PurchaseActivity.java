package com.example.freefood.PurchaseProduct;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.freefood.Main.MainActivityTEMP;
import com.example.freefood.Main.NetworkTask;
import com.example.freefood.Model.Product;
import com.example.freefood.R;
import com.google.gson.Gson;

/** UI for finishing a purchase, now with post-purchase rating. */
public class PurchaseActivity extends AppCompatActivity
        implements PurchasePresenter.PurchaseView {

    private PurchasePresenter presenter;
    private EditText  etQty;
    private String    storeName;          // keep for the rating call

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_purchase);

        /* ─── toolbar ─── */
        Toolbar tb = findViewById(R.id.toolbarPurchase);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        /* ─── unwrap intent extras ─── */
        storeName = getIntent().getStringExtra("STORE_NAME");
        Product product = new Gson()
                .fromJson(getIntent().getStringExtra("PRODUCT_JSON"), Product.class);

        getSupportActionBar().setTitle(storeName);

        /* ─── bind views ─── */
        TextView tvName  = findViewById(R.id.tvPurchaseProductName);
        TextView tvPrice = findViewById(R.id.tvPurchasePrice);
        TextView tvAvail = findViewById(R.id.tvPurchaseAvailable);
        etQty            = findViewById(R.id.etPurchaseQty);
        Button btnBuy    = findViewById(R.id.btnConfirmPurchase);

        tvName.setText(product.getProductName());
        tvPrice.setText(String.format("$%.2f", product.getPrice()));
        tvAvail.setText("In stock: " + product.getAvailableAmount());

        /* ─── MVP hookup ─── */
        PurchaseViewModel vm = new PurchaseViewModelFactory()
                .create(MainActivityTEMP.SERVER_HOST, MainActivityTEMP.SERVER_PORT);
        presenter = new PurchasePresenter(vm, this);

        btnBuy.setOnClickListener(v ->
                presenter.purchaseProduct(storeName,
                        product.getProductName(),
                        etQty.getText().toString().trim()));
    }

    /* ─── View callbacks from Presenter ─── */
    @Override public void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override public void clearQuantityField() { etQty.setText(""); }

    /** Triggered *only* when the purchase succeeds. */
    @Override public void onPurchaseSuccess() { showRatingDialog(); }

    /* ─────────────────────────────────────────────── */
    private void showRatingDialog() {
        Dialog dlg = new Dialog(this);
        dlg.setContentView(R.layout.dialog_rating);
        dlg.setTitle("Rate your experience");

        RatingBar ratingBar = dlg.findViewById(R.id.rating_bar);
        Button    submit    = dlg.findViewById(R.id.submit_button);

        submit.setOnClickListener(v -> {
            int rating = Math.max(1, Math.round(ratingBar.getRating()));

            new NetworkTask(MainActivityTEMP.SERVER_HOST,
                    MainActivityTEMP.SERVER_PORT,
                    res -> runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Thanks for rating!", Toast.LENGTH_SHORT).show()
                    )).execute("REVIEW", storeName + "|" + rating);

            dlg.dismiss();
        });

        dlg.show();
    }
}
