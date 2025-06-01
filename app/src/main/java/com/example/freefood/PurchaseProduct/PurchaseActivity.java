// PurchaseActivity.java
package com.example.freefood.PurchaseProduct;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.freefood.Main.MainMenuActivity;
import com.example.freefood.Main.NetworkTask;
import com.example.freefood.Model.Product;
import com.example.freefood.R;
import com.google.gson.Gson;

/** UI for finishing a purchase, now with post-purchase rating. */
public class PurchaseActivity extends AppCompatActivity
        implements PurchasePresenter.PurchaseView {

    private PurchasePresenter presenter;
    private EditText    etQty;
    private TextView    tvAvail;
    private Product     currentProduct;
    private String      storeName;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_purchase);

        // ─── toolbar ───
        Toolbar tb = findViewById(R.id.toolbarPurchase);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            tb.setNavigationOnClickListener(v -> onBackPressed());
        }

        // ─── unwrap intent extras ───
        storeName      = getIntent().getStringExtra("STORE_NAME");
        currentProduct = new Gson()
                .fromJson(getIntent().getStringExtra("PRODUCT_JSON"), Product.class);
        getSupportActionBar().setTitle(storeName);

        // ─── bind views ───
        TextView tvName  = findViewById(R.id.tvPurchaseProductName);
        TextView tvPrice = findViewById(R.id.tvPurchasePrice);
        tvAvail          = findViewById(R.id.tvPurchaseAvailable);
        etQty            = findViewById(R.id.etPurchaseQty);
        Button btnBuy    = findViewById(R.id.btnConfirmPurchase);

        tvName.setText(currentProduct.getProductName());
        tvPrice.setText(String.format("$%.2f", currentProduct.getPrice()));

        // Initial stock display (will be overwritten in onResume)
        tvAvail.setText("In stock: " + currentProduct.getAvailableAmount());

        // ─── MVP hookup ───
        PurchaseViewModel vm = new PurchaseViewModelFactory()
                .create(MainMenuActivity.SERVER_HOST,
                        MainMenuActivity.SERVER_PORT);
        presenter = new PurchasePresenter(vm, this);

        btnBuy.setOnClickListener(v ->
                presenter.purchaseProduct(
                        storeName,
                        currentProduct.getProductName(),
                        etQty.getText().toString().trim()
                )
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        new NetworkTask(
                MainMenuActivity.SERVER_HOST,
                MainMenuActivity.SERVER_PORT,
                res -> runOnUiThread(() -> {
                    String payload = res;
                    if (payload.startsWith("[")) {
                        try {
                            String[] arr = new Gson().fromJson(payload, String[].class);
                            if (arr.length > 0) {
                                payload = arr[0];
                            }
                        } catch (Exception e) {
                        }
                    }

                    try {
                        int latest = Integer.parseInt(payload.trim());
                        currentProduct.setAvailableAmount(latest);
                        tvAvail.setText("In stock: " + latest);
                    } catch (NumberFormatException e) {
                        // fallback on failure
                        Toast.makeText(this, "Failed to load stock", Toast.LENGTH_SHORT).show();
                        Log.e("PurchaseActivity", "GET_STOCK parse error on \"" + payload + "\"", e);
                    }
                })
        )
                .execute("GET_STOCK", storeName + "|" + currentProduct.getProductName());
    }



    /* ─── PurchasePresenter.PurchaseView ─── */
    @Override public void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override public void clearQuantityField() {
        etQty.setText("");
    }

    @Override
    public void updateAvailableStock(int newAmount) {
        // called after a purchase; keep the server‑fetched approach
        currentProduct.setAvailableAmount(newAmount);
        tvAvail.setText("In stock: " + newAmount);
    }

    @Override public void onPurchaseSuccess() {
        showRatingDialog();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* ─── Rating dialog ─── */
    private void showRatingDialog() {
        Dialog dlg = new Dialog(this);
        dlg.setContentView(R.layout.dialog_rating);
        dlg.setTitle("Rate your experience");

        RatingBar ratingBar = dlg.findViewById(R.id.rating_bar);
        Button    submit    = dlg.findViewById(R.id.submit_button);

        submit.setOnClickListener(v -> {
            int rating = Math.max(1, Math.round(ratingBar.getRating()));
            new NetworkTask(
                    MainMenuActivity.SERVER_HOST,
                    MainMenuActivity.SERVER_PORT,
                    res -> runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Thanks for rating!",
                                    Toast.LENGTH_SHORT
                            ).show()
                    )
            ).execute("REVIEW", storeName + "|" + rating);
            dlg.dismiss();
        });

        dlg.show();
    }
}
