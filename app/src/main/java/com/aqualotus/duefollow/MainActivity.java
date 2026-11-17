package com.aqualotus.duefollow;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.android.billingclient.api.*;
import org.json.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class MainActivity extends Activity {
    static final String PRODUCT = "duefollow_pro";
    static final String DEFAULT = "Hi {client}, just checking in on invoice {invoice}. The remaining balance is {balance}. Please let me know when payment is expected. Thanks!";
    private LinearLayout content;
    private BillingClient billing;
    private EditText pdf;
    private String screen = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        billing = BillingClient.newBuilder(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection().setListener((result, purchases) -> {
                if (result.getResponseCode() == 0) refreshBilling();
                else if (result.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED)
                    runOnUiThread(() -> toast(result.getDebugMessage()));
            }).build();
        billing.startConnection(new BillingClientStateListener() {
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == 0) refreshBilling();
            }
            public void onBillingServiceDisconnected() {}
        });
        open(getIntent());
    }

    @Override protected void onResume() {
        super.onResume();
        if (billing != null && billing.isReady()) refreshBilling();
        JSONArray rows = Reminders.read(this);
        for (int i = 0; i < rows.length(); i++) Reminders.schedule(this, rows.optJSONObject(i));
    }

    @Override protected void onDestroy() {
        billing.endConnection();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        open(intent);
    }

    private void open(Intent intent) {
        JSONObject invoice = find(Reminders.read(this), intent.getAction());
        if (invoice == null) home(); else detail(invoice);
    }

    private JSONObject find(JSONArray rows, String id) {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row.optString("id").equals(id)) return row;
        }
        return null;
    }

    private JSONArray clients() {
        try { return new JSONArray(Reminders.store(this).getString("clients", "[]")); }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }

    private void save(String key, JSONObject row, JSONArray rows) {
        try {
            int index = 0;
            while (index < rows.length() && !rows.optJSONObject(index).optString("id")
                .equals(row.optString("id"))) index++;
            rows.put(index, row);
            Reminders.store(this).edit().putString(key, rows.toString()).apply();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }

    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }

    private void page(String title) {
        screen = title;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xfff2f6f5);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(content);
        setContentView(scroll);
        label(title, 32);
    }

    private void label(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(0xff14372f);
        text.setTextSize(size);
        text.setPadding(0, dp(8), 0, dp(8));
        content.addView(text);
    }

    private void button(String title, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff205d4c));
        button.setOnClickListener(view -> action.run());
        content.addView(button, -1, dp(52));
    }

    private EditText field(String title, String value, int input) {
        label(title, 14);
        EditText inputView = new EditText(this);
        inputView.setInputType(input);
        inputView.setText(value);
        content.addView(inputView, -1, -2);
        return inputView;
    }

    private boolean pro() { return Reminders.store(this).getBoolean("pro", false); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private long balance(JSONObject row) { return row.optLong("total") - row.optLong("paid"); }
    private String amount(long value) { return BigDecimal.valueOf(value, 2).toPlainString(); }
    private String value(EditText field) { return field.getText().toString().trim(); }
    private long cents(String value) { return new BigDecimal(value).movePointRight(2).longValueExact(); }

    private void home() {
        page("DueFollow");
        JSONArray rows = Reminders.read(this);
        int open = 0, overdue = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (balance(row) > 0) {
                open++;
                if (LocalDate.parse(row.optString("due")).isBefore(LocalDate.now())) overdue++;
            }
        }
        label(open + " open  ·  " + overdue + " overdue", 18);
        final int count = open;
        button("Add invoice", () -> {
            if (count >= 3 && !pro()) { plans(); return; }
            if (clients().length() == 0) {
                toast("Add your first client, then create an invoice.");
                clientForm(new JSONObject());
            } else invoiceForm(new JSONObject());
        });
        button("Clients", this::clientList);
        button(pro() ? "Manage Pro" : "Explore Pro", this::plans);
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED)
            button("Enable reminders", () -> requestPermissions(new String[]{
                android.Manifest.permission.POST_NOTIFICATIONS}, 8));
        label("Invoices", 22);
        if (rows.length() == 0) label("Add an invoice to start tracking a payment.", 16);
        for (int i = rows.length() - 1; i >= 0; i--) {
            JSONObject row = rows.optJSONObject(i);
            button(row.optString("number") + "  ·  " + row.optString("currency") + " "
                + amount(balance(row)) + "  ·  " + status(row), () -> detail(row));
        }
    }

    private String status(JSONObject row) {
        if (balance(row) == 0) return "Paid";
        if (row.optBoolean("paused")) return "Paused";
        boolean late = LocalDate.parse(row.optString("due")).isBefore(LocalDate.now());
        if (row.optLong("paid") > 0) return late ? "Part paid / overdue" : "Part paid";
        return late ? "Overdue" : "Open";
    }

    private void clientList() {
        page("Clients");
        button("Home", this::home);
        button("Add client", () -> clientForm(new JSONObject()));
        JSONArray rows = clients();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            button(row.optString("name"), () -> clientForm(row));
        }
    }

    private void clientForm(JSONObject row) {
        page("Client details");
        button("Back", this::clientList);
        EditText name = field("Client name", row.optString("name"), 1);
        EditText email = field("Email", row.optString("email"), 33);
        EditText phone = field("WhatsApp number with country code", row.optString("phone"), 3);
        EditText template = pro() ? field("Follow-up template · {client} {invoice} {balance}",
            row.optString("template", DEFAULT), 131073) : null;
        button("Save client", () -> {
            if (value(name).isEmpty()) { toast("Enter a client name."); return; }
            if (!value(email).isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(value(email)).matches()) {
                toast("Check the email address."); return;
            }
            try {
                if (!row.has("id")) row.put("id", UUID.randomUUID().toString());
                row.put("name", value(name)).put("email", value(email)).put("phone", value(phone));
                if (template != null) row.put("template", value(template));
                save("clients", row, clients());
                clientList();
            } catch (JSONException e) { throw new IllegalStateException(e); }
        });
        JSONArray invoices = Reminders.read(this);
        for (int i = 0; i < invoices.length(); i++) {
            JSONObject invoice = invoices.optJSONObject(i);
            if (row.optString("id").equals(invoice.optString("client")))
                button(invoice.optString("number") + " · " + status(invoice), () -> detail(invoice));
        }
    }

    private void invoiceForm(JSONObject row) {
        page("Invoice details");
        button("Home", this::home);
        JSONArray allClients = clients();
        ArrayList<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < allClients.length(); i++) {
            JSONObject client = allClients.optJSONObject(i);
            names.add(client.optString("name"));
            if (client.optString("id").equals(row.optString("client"))) selected = i;
        }
        Spinner clientPicker = new Spinner(this);
        clientPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        label("Client", 14);
        content.addView(clientPicker, -1, dp(50));
        clientPicker.setSelection(selected);
        EditText number = field("Invoice number", row.optString("number"), 1);
        EditText total = field("Total", row.has("total") ? amount(row.optLong("total")) : "", 8194);
        EditText currency = field("Currency (ISO code)", row.optString("currency", "USD"), 1);
        EditText due = field("Due date (YYYY-MM-DD)", row.optString("due", LocalDate.now().toString()), 1);
        EditText link = field("Payment link (optional)", row.optString("link"), 17);
        pdf = field("Attached PDF", row.optString("pdf"), 1);
        pdf.setFocusable(false);
        button("Choose PDF", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 7));
        button("Remove PDF", () -> pdf.setText(""));
        CheckBox repeat = new CheckBox(this);
        repeat.setText("Repeat every 3 days (Pro)");
        repeat.setEnabled(pro());
        repeat.setChecked(row.optBoolean("repeat"));
        content.addView(repeat);
        button("Save invoice", () -> {
            try {
                long invoiceTotal = cents(value(total));
                String code = value(currency).toUpperCase(Locale.ROOT);
                Currency.getInstance(code);
                LocalDate date = LocalDate.parse(value(due));
                String payment = value(link);
                Uri address = Uri.parse(payment);
                if (invoiceTotal <= 0 || invoiceTotal < row.optLong("paid") || value(number).isEmpty()
                    || (!payment.isEmpty() && (!"https".equalsIgnoreCase(address.getScheme())
                    || address.getHost() == null))) throw new IllegalArgumentException();
                if (!row.has("id")) row.put("id", UUID.randomUUID().toString());
                if (!date.toString().equals(row.optString("due"))) {
                    row.put("reminded", false).put("next", 0);
                }
                row.put("client", allClients.optJSONObject(clientPicker.getSelectedItemPosition()).optString("id"))
                    .put("number", value(number)).put("total", invoiceTotal).put("currency", code)
                    .put("due", date.toString()).put("link", payment).put("pdf", value(pdf))
                    .put("repeat", repeat.isChecked());
                commit(row);
            } catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException | JSONException ex) {
                toast("Check the invoice number, amount, currency, date and HTTPS payment link.");
            }
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 7 && result == RESULT_OK && data != null && data.getData() != null && pdf != null) {
            try {
                getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                pdf.setText(data.getData().toString());
            } catch (SecurityException e) { toast("This provider could not grant access to the PDF."); }
        }
    }

    private void commit(JSONObject row) {
        save("invoices", row, Reminders.read(this));
        Reminders.schedule(this, row);
        detail(row);
    }

    private void detail(JSONObject row) {
        page("Invoice " + row.optString("number"));
        button("Home", this::home);
        JSONObject client = find(clients(), row.optString("client"));
        label((client == null ? "" : client.optString("name") + " · ") + status(row), 18);
        label(row.optString("currency") + " " + amount(balance(row)) + " remaining", 26);
        label("Due " + row.optString("due") + " · Paid " + amount(row.optLong("paid")), 16);
        button("Edit invoice", () -> invoiceForm(row));
        if (!row.optString("pdf").isEmpty()) button("View PDF", () -> launch(new Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(row.optString("pdf")), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)));
        if (balance(row) <= 0) return;
        if (client != null) {
            button("Open email draft", () -> followUp(row, client, false));
            button("Open WhatsApp draft", () -> followUp(row, client, true));
        }
        button("Record payment", () -> {
            EditText entry = new EditText(this);
            entry.setInputType(8194);
            entry.setHint(amount(balance(row)));
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Payment received")
                .setView(entry).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    long payment = cents(value(entry));
                    if (payment <= 0 || payment > balance(row)) throw new IllegalArgumentException();
                    row.put("paid", row.optLong("paid") + payment);
                    dialog.dismiss();
                    commit(row);
                } catch (IllegalArgumentException | ArithmeticException | JSONException e) {
                    entry.setError("Enter an amount within the remaining balance.");
                }
            }));
            dialog.show();
        });
        button("Mark paid", () -> new AlertDialog.Builder(this).setTitle("Mark invoice paid?")
            .setNegativeButton("Cancel", null).setPositiveButton("Mark paid", (dialog, which) -> {
                try { row.put("paid", row.optLong("total")); commit(row); }
                catch (JSONException e) { throw new IllegalStateException(e); }
            }).show());
        button(row.optBoolean("paused") ? "Resume reminders" : "Pause reminders", () -> {
            try { row.put("paused", !row.optBoolean("paused")); commit(row); }
            catch (JSONException e) { throw new IllegalStateException(e); }
        });
    }

    private void followUp(JSONObject row, JSONObject client, boolean whatsapp) {
        String template = pro() ? client.optString("template", DEFAULT) : DEFAULT;
        if (template.trim().isEmpty()) template = DEFAULT;
        String message = template.replace("{client}", client.optString("name"))
            .replace("{invoice}", row.optString("number"))
            .replace("{balance}", row.optString("currency") + " " + amount(balance(row)));
        if (!row.optString("link").isEmpty()) message += "\n\nPayment link: " + row.optString("link");
        if (whatsapp) {
            String phone = client.optString("phone").replaceAll("[^0-9]", "");
            if (phone.isEmpty()) { toast("Add a WhatsApp number to this client."); return; }
            launch(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone + "?text=" + Uri.encode(message))));
        } else {
            if (client.optString("email").isEmpty()) { toast("Add an email address to this client."); return; }
            launch(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(client.optString("email"), "@")
                + "?subject=" + Uri.encode("Payment follow-up · " + row.optString("number"))
                + "&body=" + Uri.encode(message))));
        }
    }

    private void launch(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) { toast("No app is available to open this."); }
    }

    private void refreshBilling() {
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
            (result, purchases) -> {
                if (result.getResponseCode() != 0) return;
                boolean active = false;
                for (Purchase purchase : purchases) {
                    if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED ||
                        !purchase.getProducts().contains(PRODUCT)) continue;
                    active = true;
                    if (!purchase.isAcknowledged()) {
                        billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken()).build(), ignored -> {});
                    }
                }
                final boolean entitlement = active;
                runOnUiThread(() -> {
                    if (entitlement == pro()) return;
                    Reminders.store(this).edit().putBoolean("pro", entitlement).apply();
                    JSONArray rows = Reminders.read(this);
                    for (int i = 0; i < rows.length(); i++) Reminders.schedule(this, rows.optJSONObject(i));
                    if (screen.equals("DueFollow Pro")) plans();
                    else if (screen.equals("DueFollow")) home();
                });
            });
    }

    private void plans() {
        page("DueFollow Pro");
        button("Home", this::home);
        label("Unlimited open invoices, recurring reminders and client templates.", 18);
        if (pro()) {
            label("Your subscription is active.", 16);
            button("Manage subscription", () -> launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/account/subscriptions?sku=" + PRODUCT
                    + "&package=" + getPackageName()))));
            return;
        }
        if (!billing.isReady()) {
            label("Google Play is connecting. Try again in a moment.", 16);
            button("Retry", this::plans);
            return;
        }
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build();
        billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
            .setProductList(Collections.singletonList(product)).build(), (result, details) ->
            runOnUiThread(() -> {
                if (!screen.equals("DueFollow Pro") || pro()) return;
                int count = 0;
                for (ProductDetails item : details.getProductDetailsList()) {
                    List<ProductDetails.SubscriptionOfferDetails> offers = item.getSubscriptionOfferDetails();
                    if (offers == null) continue;
                    for (ProductDetails.SubscriptionOfferDetails offer : offers) {
                        String id = offer.getBasePlanId();
                        if (offer.getOfferId() != null || (!id.equals("monthly") && !id.equals("yearly"))) continue;
                        String price = offer.getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
                        button(price + (id.equals("monthly") ? " / month" : " / year"), () -> {
                            BillingResult launch = billing.launchBillingFlow(this, BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(Collections.singletonList(
                                    BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(item)
                                        .setOfferToken(offer.getOfferToken()).build())).build());
                            if (launch.getResponseCode() != 0) toast(launch.getDebugMessage());
                        });
                        count++;
                    }
                }
                if (count == 0) label("Plans are unavailable. Check your Google Play account and try again.", 16);
            }));
        label("Renews automatically. Manage or cancel anytime in Google Play.", 14);
    }
}
