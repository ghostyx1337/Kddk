package dev.ghostyx.protonlocationpicker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_CONFIG = 41;
    private static final int VPN_PERMISSION = 42;
    private static final String CONFIG_DIR = "tunnels";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<File> configs = new ArrayList<>();
    private GoBackend backend;
    private Tunnel activeTunnel;
    private Spinner locations;
    private TextView status;
    private TextView details;
    private Button connectButton;
    private Button importButton;
    private boolean connected = false;
    private boolean busy = false;
    private File selectedConfig;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new GoBackend(this);
        buildUi();
        refreshConfigs();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        return t;
    }

    private void buildUi() {
        int bg = Color.rgb(12, 17, 31), panel = Color.rgb(23, 31, 51), accent = Color.rgb(115, 93, 255);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22), dp(28), dp(22), dp(24));
        scroll.addView(root);

        TextView brand = text("PROTON  /  LOCATION PICKER", 12, Color.rgb(160, 151, 255));
        brand.setTypeface(Typeface.DEFAULT_BOLD); root.addView(brand);
        TextView title = text("Choose your\nVPN location.", 31, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD); LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2); titleLp.topMargin = dp(14); root.addView(title, titleLp);
        TextView subtitle = text("Import a WireGuard .conf file you obtained for your account, then connect through the WireGuard tunnel engine.", 14, Color.rgb(185, 194, 214));
        subtitle.setLineSpacing(dp(3), 1f); LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2); subLp.topMargin = dp(10); root.addView(subtitle, subLp);

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackgroundColor(panel);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2); cardLp.topMargin = dp(26); root.addView(card, cardLp);
        TextView label = text("SAVED CONFIGURATIONS", 11, Color.rgb(158, 169, 194)); label.setTypeface(Typeface.DEFAULT_BOLD); card.addView(label);
        locations = new Spinner(this); LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(-1, dp(52)); spinLp.topMargin = dp(8); card.addView(locations, spinLp);
        status = text("●  Disconnected", 14, Color.rgb(255, 190, 110)); status.setTypeface(Typeface.DEFAULT_BOLD); LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2); statusLp.topMargin = dp(18); card.addView(status, statusLp);
        details = text("No configuration imported yet.", 12, Color.rgb(185, 194, 214)); LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2); detailsLp.topMargin = dp(8); card.addView(details, detailsLp);

        connectButton = new Button(this); connectButton.setText("Connect"); connectButton.setTextColor(Color.WHITE); connectButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        LinearLayout.LayoutParams connectLp = new LinearLayout.LayoutParams(-1, dp(52)); connectLp.topMargin = dp(18); card.addView(connectButton, connectLp);
        connectButton.setOnClickListener(v -> onConnectClick());

        importButton = new Button(this); importButton.setText("＋  Import WireGuard config"); importButton.setTextColor(Color.WHITE); importButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(48, 61, 88)));
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(-1, dp(50)); importLp.topMargin = dp(10); card.addView(importButton, importLp);
        importButton.setOnClickListener(v -> pickConfig());

        TextView note = text("IMPORTANT\nThis is an unofficial client, not made by Proton. It does not sign in to Proton or discover server locations automatically. The list only contains configs you import. Whether a config works depends on the account and provider rules. Keep .conf files private: they contain VPN keys.", 12, Color.rgb(170, 182, 205));
        note.setLineSpacing(dp(3), 1f); LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2); noteLp.topMargin = dp(20); root.addView(note, noteLp);
        setContentView(scroll);
    }

    private File configDirectory() { File d = new File(getFilesDir(), CONFIG_DIR); if (!d.exists()) d.mkdirs(); return d; }

    private void refreshConfigs() {
        configs.clear(); File[] files = configDirectory().listFiles((dir, name) -> name.toLowerCase().endsWith(".conf"));
        if (files != null) { java.util.Arrays.sort(files, (a,b) -> a.getName().compareToIgnoreCase(b.getName())); for (File f : files) configs.add(f); }
        List<String> labels = new ArrayList<>();
        if (configs.isEmpty()) labels.add("Import a .conf file first");
        else for (File f : configs) labels.add(f.getName().replace('_', ' ').replaceAll("(?i)\\.conf$", ""));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); locations.setAdapter(adapter);
        selectedConfig = configs.isEmpty() ? null : configs.get(0);
        updateDetails();
        locations.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedConfig = configs.isEmpty() || position >= configs.size() ? null : configs.get(position); updateDetails();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { selectedConfig = null; updateDetails(); }
        });
    }

    private void updateDetails() {
        if (details == null) return;
        if (selectedConfig == null) details.setText("No configuration imported yet. Get a supported WireGuard configuration from your VPN provider.");
        else details.setText("Selected: " + selectedConfig.getName() + "\nConfig is stored privately inside this app.");
        connectButton.setEnabled(!busy && (connected || selectedConfig != null));
        connectButton.setText(connected ? "Disconnect" : (busy ? "Working…" : "Connect"));
    }

    private void pickConfig() {
        if (connected || busy) { Toast.makeText(this, "Disconnect before importing another config.", Toast.LENGTH_LONG).show(); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/octet-stream"}); startActivityForResult(intent, PICK_CONFIG);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONFIG && resultCode == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData(); String name = "wireguard.conf";
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) { try { int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (idx >= 0 && c.moveToFirst()) name = c.getString(idx); } finally { c.close(); } }
            if (!name.toLowerCase().endsWith(".conf")) name += ".conf";
            name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
            File out = new File(configDirectory(), name);
            try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(out)) {
                if (in == null) throw new IllegalStateException("Could not open selected file");
                byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) > 0) fos.write(buffer, 0, n);
                fos.flush();
                // Validate before exposing it in the saved list.
                try (FileInputStream check = new FileInputStream(out)) { Config.parse(check); }
                refreshConfigs();
                for (int i = 0; i < configs.size(); i++) if (configs.get(i).equals(out)) { locations.setSelection(i); break; }
                Toast.makeText(this, "Configuration imported privately.", Toast.LENGTH_LONG).show();
            } catch (Exception e) { out.delete(); Toast.makeText(this, "Import failed: " + safeMessage(e), Toast.LENGTH_LONG).show(); refreshConfigs(); }
        } else if (requestCode == VPN_PERMISSION && resultCode == RESULT_OK) {
            startTunnel();
        }
    }

    private void onConnectClick() {
        if (busy) return;
        if (connected) { stopTunnel(); return; }
        if (selectedConfig == null) { Toast.makeText(this, "Import a WireGuard .conf file first.", Toast.LENGTH_LONG).show(); return; }
        Intent prepare = GoBackend.VpnService.prepare(this);
        if (prepare != null) startActivityForResult(prepare, VPN_PERMISSION); else startTunnel();
    }

    private void setBusy(boolean value, String message) {
        busy = value; status.setText(message); updateDetails(); importButton.setEnabled(!value && !connected); locations.setEnabled(!value && !connected);
    }

    private void startTunnel() {
        if (selectedConfig == null) return;
        final File configFile = selectedConfig;
        setBusy(true, "●  Connecting…");
        worker.execute(() -> {
            try (FileInputStream in = new FileInputStream(configFile)) {
                Config config = Config.parse(in);
                Tunnel tunnel = new Tunnel() {
                    @Override public String getName() { return "proton-picker"; }
                    @Override public void onStateChange(Tunnel.State state) {
                        runOnUiThread(() -> {
                            connected = state == Tunnel.State.UP;
                            if (state == Tunnel.State.UP) setBusy(false, "●  Connected");
                            else if (state == Tunnel.State.DOWN) { if (!busy) setBusy(false, "●  Disconnected"); }
                        });
                    }
                };
                backend.setState(tunnel, Tunnel.State.UP, config);
                activeTunnel = tunnel;
                runOnUiThread(() -> { connected = true; setBusy(false, "●  Connected"); Toast.makeText(this, "WireGuard tunnel started. Verify your public IP before relying on it.", Toast.LENGTH_LONG).show(); });
            } catch (Exception e) {
                runOnUiThread(() -> { connected = false; setBusy(false, "●  Connection failed"); details.setText("Could not connect: " + safeMessage(e) + "\nCheck that the config is valid and still authorized by its provider."); Toast.makeText(this, "Connection failed", Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private void stopTunnel() {
        setBusy(true, "●  Disconnecting…");
        worker.execute(() -> {
            try {
                if (activeTunnel != null) backend.setState(activeTunnel, Tunnel.State.DOWN, null);
                activeTunnel = null;
                runOnUiThread(() -> { connected = false; setBusy(false, "●  Disconnected"); });
            } catch (Exception e) {
                runOnUiThread(() -> { connected = false; setBusy(false, "●  Disconnect status uncertain"); details.setText("Disconnect returned an error: " + safeMessage(e) + ". Check Android VPN settings."); });
            }
        });
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage(); if (m == null || m.trim().isEmpty()) return e.getClass().getSimpleName();
        return m.length() > 180 ? m.substring(0, 180) : m;
    }

    @Override protected void onDestroy() { super.onDestroy(); worker.shutdown(); }
  }
