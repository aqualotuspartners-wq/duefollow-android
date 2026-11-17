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

    private android.graphics.drawable.GradientDrawable surface(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private android.graphics.drawable.Drawable interactive(android.graphics.drawable.Drawable background) {
        return new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x284f46e5), background, surface(Color.WHITE, 20));
    }

    private android.graphics.drawable.Drawable icon(String kind, int color) {
        return new android.graphics.drawable.Drawable() {
            final android.graphics.Paint paint = new android.graphics.Paint(3);
            @Override public void draw(android.graphics.Canvas canvas) {
                canvas.save();
                android.graphics.Rect bounds = getBounds();
                canvas.translate(bounds.left, bounds.top);
                canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
                paint.setColor(color);
                paint.setStrokeWidth(1.8f);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                if (kind.equals("add")) {
                    canvas.drawLine(12, 5, 12, 19, paint);
                    canvas.drawLine(5, 12, 19, 12, paint);
                } else if (kind.equals("clients")) {
                    canvas.drawCircle(9, 7, 3, paint);
                    canvas.drawArc(3, 12, 15, 23, 180, 180, false, paint);
                    canvas.drawArc(13, 4, 20, 11, -75, 160, false, paint);
                    canvas.drawArc(14, 12, 22, 22, -90, 90, false, paint);
                } else if (kind.equals("mail")) {
                    canvas.drawRoundRect(2, 5, 22, 19, 3, 3, paint);
                    canvas.drawLine(3, 6, 12, 13, paint);
                    canvas.drawLine(12, 13, 21, 6, paint);
                } else if (kind.equals("check")) {
                    canvas.drawCircle(12, 12, 9, paint);
                    canvas.drawLine(7, 12, 11, 16, paint);
                    canvas.drawLine(11, 16, 17, 9, paint);
                } else if (kind.equals("chat")) {
                    canvas.drawRoundRect(3, 3, 21, 18, 5, 5, paint);
                    canvas.drawLine(5, 18, 5, 22, paint);
                    canvas.drawLine(5, 22, 10, 18, paint);
                } else if (kind.equals("pro")) {
                    android.graphics.Path diamond = new android.graphics.Path();
                    diamond.moveTo(12, 2); diamond.lineTo(21, 11);
                    diamond.lineTo(12, 22); diamond.lineTo(3, 11); diamond.close();
                    canvas.drawPath(diamond, paint);
                    canvas.drawLine(3, 11, 21, 11, paint);
                } else {
                    canvas.drawRoundRect(4, 2, 20, 22, 3, 3, paint);
                    canvas.drawLine(8, 8, 16, 8, paint);
                    canvas.drawLine(8, 12, 16, 12, paint);
                    canvas.drawLine(8, 16, 13, 16, paint);
                }
                canvas.restore();
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    private void clickable(View view, Runnable action) {
        view.setFocusable(true);
        view.setOnClickListener(clicked -> {
            clicked.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                clicked.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70)
                    .withEndAction(() -> {
                        clicked.animate().scaleX(1).scaleY(1).setDuration(120).start();
                        action.run();
                    }).start();
            } else action.run();
        });
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        if (bold) view.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        return view;
    }

    private void page(String title) {
        screen = title;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{0xfff7f7ff, 0xfff4f6fa, 0xffedf1ff}));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setPadding(dp(10), dp(8), dp(10), dp(8));
        navigation.setBackgroundColor(Color.WHITE);
        String[] labels = {"Overview", "Clients", "Pro"};
        Runnable[] actions = {this::home, this::clientList, this::plans};
        for (int i = 0; i < labels.length; i++) {
            final Runnable action = actions[i];
            boolean selected = i == 0 ? title.equals("DueFollow") || title.startsWith("Invoice") :
                i == 1 ? title.startsWith("Client") : title.equals("DueFollow Pro");
            TextView item = text(labels[i], 12, selected ? 0xff4f46e5 : 0xff747a91, selected);
            item.setGravity(Gravity.CENTER);
            android.graphics.drawable.Drawable symbol = icon(
                i == 0 ? "invoice" : i == 1 ? "clients" : "pro", selected ? 0xff4f46e5 : 0xff747a91);
            symbol.setBounds(0, 0, dp(23), dp(23));
            item.setCompoundDrawables(null, symbol, null, null);
            item.setCompoundDrawablePadding(dp(4));
            item.setPadding(dp(4), dp(8), dp(4), dp(8));
            item.setBackground(interactive(surface(selected ? 0xffeeedff : Color.WHITE, 16)));
            item.setContentDescription(labels[i] + (selected ? ", selected" : ""));
            clickable(item, action);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(68), 1);
            params.setMargins(dp(4), 0, dp(4), 0);
            navigation.addView(item, params);
        }
        root.addView(navigation);
        setContentView(root);
        TextView brand = text("DueFollow", 15, 0xff4f46e5, true);
        content.addView(brand);
        label(title.equals("DueFollow") ? "Get paid.\nStay in control." : title, 30);
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            content.setAlpha(0);
            content.setTranslationY(dp(10));
            content.animate().alpha(1).translationY(0).setDuration(220).start();
        }
    }

    private void label(String value, int size) {
        TextView view = text(value, size, size >= 22 ? 0xff171b35 : 0xff687089, size >= 22);
        view.setPadding(0, dp(size >= 22 ? 14 : 8), 0, dp(10));
        content.addView(view);
    }

    private void button(String title, Runnable action) {
        if (title.equals("Home") || title.equals("Clients") || title.equals("Explore Pro") ||
            title.equals("Manage Pro")) return;
        boolean primary = title.startsWith("Add") || title.startsWith("Save") ||
            title.equals("Open email draft") || title.equals("Mark paid") || title.contains(" / month") || title.contains(" / year");
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(16);
        button.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        button.setTextColor(primary ? Color.WHITE : 0xff4338ca);
        android.graphics.drawable.GradientDrawable background = primary ?
            new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xff635bff, 0xff4737d6}) : surface(Color.WHITE, 18);
        background.setCornerRadius(dp(18));
        if (!primary) background.setStroke(dp(1), 0xffdfe2ef);
        button.setBackground(interactive(background));
        button.setElevation(dp(primary ? 4 : 1));
        String kind = title.startsWith("Add") ? "add" :
            title.contains("email") ? "mail" : title.contains("WhatsApp") ? "chat" :
            title.startsWith("Save") || title.contains("paid") || title.contains("payment") ?
                "check" : "invoice";
        android.graphics.drawable.Drawable symbol = icon(kind, primary ? Color.WHITE : 0xff4f46e5);
        symbol.setBounds(0, 0, dp(22), dp(22));
        button.setCompoundDrawablesRelative(symbol, null, null, null);
        button.setCompoundDrawablePadding(dp(12));
        clickable(button, action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(6));
        button.setMinHeight(dp(64));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        content.addView(button, params);
    }

    private EditText field(String title, String value, int input) {
        label(title, 14);
        EditText view = new EditText(this);
        view.setInputType(input);
        view.setText(value);
        view.setTextColor(0xff171b35);
        view.setTextSize(17);
        view.setPadding(dp(16), dp(15), dp(16), dp(15));
        android.graphics.drawable.GradientDrawable normal = surface(Color.WHITE, 16);
        normal.setStroke(dp(1), 0xffdfe2ef);
        android.graphics.drawable.GradientDrawable focused = surface(Color.WHITE, 16);
        focused.setStroke(dp(2), 0xff635bff);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        view.setMinHeight(dp(60));
        content.addView(view, -1, -2);
        return view;
    }

    private void invoiceCard(JSONObject invoice) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        card.setBackground(interactive(surface(Color.WHITE, 22)));
        card.setElevation(dp(2));
        JSONObject client = find(clients(), invoice.optString("client"));
        TextView name = text(client == null ? "Invoice" : client.optString("name"), 17, 0xff171b35, true);
        card.addView(name);
        TextView number = text(invoice.optString("number") + "  ·  Due " + invoice.optString("due"),
            13, 0xff687089, false);
        number.setPadding(0, dp(7), 0, dp(16));
        card.addView(number);
        card.addView(text(invoice.optString("currency") + " " + amount(balance(invoice)), 25, 0xff171b35, true));
        String state = status(invoice);
        TextView badge = text(state, 13, state.toLowerCase(Locale.ROOT).contains("overdue")
            ? 0xffb83e3e : 0xff4f46e5, true);
        boolean late = state.toLowerCase(Locale.ROOT).contains("overdue");
        boolean paid = state.equals("Paid");
        badge.setTextColor(late ? 0xffa52b38 : paid ? 0xff117551 : 0xff4f46e5);
        badge.setBackground(surface(late ? 0xffffe9ec : paid ? 0xffdef5eb : 0xffeeedff, 10));
        badge.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
        badgeParams.topMargin = dp(14);
        card.addView(badge, badgeParams);
        card.setContentDescription(name.getText() + ", " + number.getText() + ", " +
            invoice.optString("currency") + " " + amount(balance(invoice)) + ", " + state);
        clickable(card, () -> detail(invoice));
        card.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(9));
        content.addView(card, params);
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
        label("Stay on top of every payment.", 16);
        LinearLayout summary = new LinearLayout(this);
        String[] values = {String.valueOf(open), String.valueOf(overdue)};
        String[] captions = {"Open invoices", "Overdue"};
        for (int i = 0; i < values.length; i++) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(18), dp(18), dp(18), dp(18));
            android.graphics.drawable.GradientDrawable tileBackground =
                new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    i == 0 ? new int[]{0xff7568ff, 0xff4737d6} : new int[]{0xffffeee5, 0xffffe4e9});
            tileBackground.setCornerRadius(dp(24));
            tile.setBackground(tileBackground);
            tile.setElevation(dp(3));
            tile.addView(text(values[i], 36, i == 0 ? Color.WHITE : 0xffb83e49, true));
            tile.addView(text(captions[i], 14, i == 0 ? 0xffeeedff : 0xff9a535c, false));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(i == 0 ? 0 : dp(6), dp(10), i == 0 ? dp(6) : 0, dp(18));
            summary.addView(tile, params);
        }
        content.addView(summary);
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
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            button("Enable reminders", () -> requestPermissions(new String[]{
                android.Manifest.permission.POST_NOTIFICATIONS}, 8));
        label("Invoices", 22);
        if (rows.length() == 0) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(32), dp(24), dp(32));
            empty.setBackground(surface(Color.WHITE, 24));
            TextView art = text("", 20, 0xff4f46e5, true);
            android.graphics.drawable.Drawable symbol = icon("check", 0xff635bff);
            symbol.setBounds(0, 0, dp(52), dp(52));
            art.setCompoundDrawables(null, symbol, null, null);
            empty.addView(art);
            TextView heading = text("Your next payment starts here", 19, 0xff171b35, true);
            heading.setGravity(Gravity.CENTER);
            heading.setPadding(0, dp(18), 0, dp(8));
            empty.addView(heading);
            TextView help = text("Add your first invoice and we’ll remind you when to follow up.", 15, 0xff687089, false);
            help.setGravity(Gravity.CENTER);
            empty.addView(help);
            content.addView(empty);
        }
        for (int i = rows.length() - 1; i >= 0; i--) {
            JSONObject row = rows.optJSONObject(i);
            invoiceCard(row);
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
        clientPicker.setBackground(surface(Color.WHITE, 16));
        clientPicker.setPadding(dp(12), dp(4), dp(12), dp(4));
        content.addView(clientPicker, -1, dp(60));
        clientPicker.setSelection(selected);
        EditText number = field("Invoice number", row.optString("number"), 1);
        EditText total = field("Total", row.has("total") ? amount(row.optLong("total")) : "", 8194);
        EditText currency = field("Currency (ISO code)", row.optString("currency", "USD"), 1);
        EditText due = field("Due date (YYYY-MM-DD)", row.optString("due", LocalDate.now().toString()), 1);
        due.setFocusable(false);
        due.setContentDescription("Choose payment due date");
        due.setOnClickListener(view -> {
            LocalDate current = LocalDate.parse(value(due));
            new DatePickerDialog(this, (picker, year, month, day) ->
                due.setText(LocalDate.of(year, month + 1, day).toString()),
                current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
        });
        EditText link = field("Payment link (optional)", row.optString("link"), 17);
        pdf = field("Attached PDF", row.optString("pdf"), 1);
        pdf.setFocusable(false);
        button("Choose PDF", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 7));
        button("Remove PDF", () -> pdf.setText(""));
        CheckBox repeat = new CheckBox(this);
        repeat.setText("Repeat every 3 days (Pro)");
        repeat.setTextSize(16);
        repeat.setTextColor(0xff4338ca);
        repeat.setMinHeight(dp(60));
        repeat.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff635bff));
        repeat.setPadding(dp(8), dp(10), dp(8), dp(10));
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
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(24), dp(28), dp(24), dp(28));
        android.graphics.drawable.GradientDrawable glow = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{0xff7063fa, 0xff302478});
        glow.setCornerRadius(dp(26));
        hero.setBackground(glow);
        hero.addView(text("More clients. More control.", 26, Color.WHITE, true));
        TextView description = text("Keep every payment moving with DueFollow Pro.", 16, 0xffe2ddff, false);
        description.setPadding(0, dp(12), 0, 0);
        hero.addView(description);
        content.addView(hero);
        label("Unlimited open invoices", 20);
        label("Track every client’s outstanding payments.", 15);
        label("Recurring reminders", 20);
        label("Get a follow-up reminder every three days.", 15);
        label("Personal client templates", 20);
        label("Keep your message consistent and ready to send.", 15);
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
