package com.example.salinitytester;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RiverAnalysisActivity extends AppCompatActivity {

    private LineChart riverChart;
    private TextView tvRiverName, tvAiResult;
    private View chartBackground;
    private Button btnShowMap;
    private DatabaseReference mDb;
    private String riverName, currentLocationName;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean isRecording = false;
    private ValueEventListener stagingListener;

    // YOUR NEW ML SERVER URL
    private static final String ML_SERVER_URL = "https://adulatory-cordie-nonmonistic.ngrok-free.dev/predict_bridge";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_river_analysis);

        riverChart = findViewById(R.id.riverChart);
        tvRiverName = findViewById(R.id.tvRiverName);
        tvAiResult = findViewById(R.id.tvAiResult);
        chartBackground = findViewById(R.id.chartBackground);
        btnShowMap = findViewById(R.id.btnShowMap);

        mDb = FirebaseDatabase.getInstance().getReference();
        riverName = getIntent().getStringExtra("RIVER_NAME");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (riverName != null) {
            tvRiverName.setText(riverName);
            setupButtons();
            loadProjectData();
        }
    }

    private void setupButtons() {
        Button btnRecord = findViewById(R.id.btnRecord);
        Button btnPredict = findViewById(R.id.btnGeminiAI); // Using existing ID for ML Button

        btnPredict.setText("RUN ML PREDICTION");
        btnRecord.setOnClickListener(v -> {
            if (!isRecording) showNewLocationDialog(btnRecord);
            else stopRecording(btnRecord);
        });

        btnPredict.setOnClickListener(v -> performMlPrediction());
    }

    // --- 1. RECORDING LOGIC ---

    private void showNewLocationDialog(Button btnRecord) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Location");
        final EditText input = new EditText(this);
        input.setHint("Location Name");
        builder.setView(input);
        builder.setPositiveButton("Start", (d, w) -> {
            currentLocationName = input.getText().toString().trim();
            if (!currentLocationName.isEmpty()) startRecording(btnRecord);
        });
        builder.show();
    }

    private void startRecording(Button btnRecord) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                DatabaseReference locRef = mDb.child("projects").child(riverName).child(currentLocationName);
                locRef.child("latitude").setValue(location.getLatitude());
                locRef.child("longitude").setValue(location.getLongitude());
                mDb.child("projects").child(riverName).get().addOnSuccessListener(snap -> locRef.child("order").setValue(snap.getChildrenCount()));

                mDb.child("control/status").setValue("START");
                isRecording = true;
                btnRecord.setText("STOP RECORDING");
                btnRecord.setBackgroundColor(Color.RED);
                attachStagingListener(locRef.child("measurements"));
            }
        });
    }

    private void stopRecording(Button btnRecord) {
        isRecording = false;
        btnRecord.setText("START NEW LOCATION");
        btnRecord.setBackgroundColor(Color.parseColor("#4CAF50"));
        mDb.child("control/status").setValue("STOP");
        if (stagingListener != null) mDb.child("device_staging").removeEventListener(stagingListener);
    }

    private void attachStagingListener(DatabaseReference targetRef) {
        stagingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && isRecording) targetRef.push().setValue(snapshot.getValue());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        mDb.child("device_staging").addValueEventListener(stagingListener);
    }

    // --- 2. SCATTER CHART LOGIC ---

    private void loadProjectData() {
        mDb.child("projects").child(riverName).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Entry> entries = new ArrayList<>();
                float totalTds = 0; int count = 0;
                for (DataSnapshot loc : snapshot.getChildren()) {
                    if (loc.hasChild("measurements") && loc.hasChild("order")) {
                        float order = loc.child("order").getValue(Integer.class).floatValue();
                        for (DataSnapshot m : loc.child("measurements").getChildren()) {
                            float h = m.child("height").getValue(Double.class).floatValue();
                            float t = m.child("tds").getValue(Double.class).floatValue();
                            Entry e = new Entry(order, h);
                            Map<String, Object> d = new HashMap<>();
                            d.put("tds", t); d.put("name", loc.getKey());
                            e.setData(d);
                            entries.add(e);
                            totalTds += t; count++;
                        }
                    }
                }
                if (!entries.isEmpty()) updateChart(entries, totalTds/count);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateChart(List<Entry> entries, float avgTds) {
        entries.sort((e1, e2) -> Float.compare(e1.getX(), e2.getX()));
        LineDataSet set = new LineDataSet(entries, "Water Depth");
        set.setLineWidth(0f); set.setColor(Color.TRANSPARENT); set.setDrawCircles(true);
        List<Integer> colors = new ArrayList<>();
        for (Entry e : entries) {
            float t = (float)((Map)e.getData()).get("tds");
            if (t < 50) colors.add(Color.GREEN);
            else if (t < 300) colors.add(Color.YELLOW);
            else if (t < 700) colors.add(Color.parseColor("#FFA500"));
            else colors.add(Color.RED);
        }
        set.setCircleColors(colors);
        set.setCircleRadius(5f);
        riverChart.setData(new LineData(set));
        riverChart.invalidate();

        int alpha = 50;
        if (avgTds > 700) chartBackground.setBackgroundColor(Color.argb(alpha, 255, 0, 0));
        else chartBackground.setBackgroundColor(Color.argb(alpha, 0, 255, 0));
    }

    // --- 3. ML PREDICTION & MAPS ---

    private void performMlPrediction() {
        tvAiResult.setText("Analyzing on ML Server...");
        mDb.child("projects").child(riverName).get().addOnSuccessListener(snapshot -> {
            try {
                JSONObject root = new JSONObject();
                JSONArray locs = new JSONArray();
                for (DataSnapshot locSnap : snapshot.getChildren()) {
                    if (!locSnap.hasChild("measurements")) continue;
                    JSONObject l = new JSONObject();
                    l.put("order", locSnap.child("order").getValue());
                    l.put("latitude", locSnap.child("latitude").getValue());
                    l.put("longitude", locSnap.child("longitude").getValue());
                    JSONArray ms = new JSONArray();
                    for (DataSnapshot m : locSnap.child("measurements").getChildren()) {
                        JSONObject mObj = new JSONObject();
                        mObj.put("height", m.child("height").getValue());
                        mObj.put("tds", m.child("tds").getValue());
                        ms.put(mObj);
                    }
                    l.put("measurements", ms);
                    locs.put(l);
                }
                root.put("locations", locs);
                sendToServer(root.toString());
            } catch (Exception e) { Log.e("ML", "JSON Error", e); }
        });
    }

    private void sendToServer(String json) {
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder().url(ML_SERVER_URL).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> tvAiResult.setText("Server Connection Failed"));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject res = new JSONObject(response.body().string());
                        String reason = res.getString("reasoning");

                        if (res.isNull("bridge_lat") || res.isNull("bridge_lng")) {
                            runOnUiThread(() -> {
                                tvAiResult.setText(reason + "\n\n(No bridge location — salinity below threshold)");
                                btnShowMap.setVisibility(View.GONE);
                            });
                            return;
                        }

                        double lat = res.getDouble("bridge_lat");
                        double lng = res.getDouble("bridge_lng");

                        runOnUiThread(() -> {
                            tvAiResult.setText(reason);
                            btnShowMap.setVisibility(View.VISIBLE);
                            btnShowMap.setOnClickListener(v -> {
                                Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + lat + "," + lng + "(Salinity Bridge Prediction)");
                                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                                mapIntent.setPackage("com.google.android.apps.maps");
                                startActivity(mapIntent);
                            });
                        });

                        runOnUiThread(() -> {
                            tvAiResult.setText(reason);
                            btnShowMap.setVisibility(View.VISIBLE);
                            btnShowMap.setOnClickListener(v -> {
                                Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + lat + "," + lng + "(Salinity Bridge Prediction)");
                                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                                mapIntent.setPackage("com.google.android.apps.maps");
                                startActivity(mapIntent);
                            });
                        });
                    } catch (Exception e) { Log.e("ML", "Parse error", e); }
                }
            }
        });
    }
}