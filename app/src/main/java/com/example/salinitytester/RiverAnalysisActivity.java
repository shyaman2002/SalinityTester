package com.example.salinitytester;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RiverAnalysisActivity extends AppCompatActivity {

    private LineChart riverChart;
    private TextView tvRiverName, tvAiResult;
    private View chartBackground;
    private DatabaseReference mDb;
    private String riverName, currentLocationName;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean isRecording = false;
    private ValueEventListener stagingListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_river_analysis);

        riverChart = findViewById(R.id.riverChart);
        tvRiverName = findViewById(R.id.tvRiverName);
        tvAiResult = findViewById(R.id.tvAiResult);
        chartBackground = findViewById(R.id.chartBackground);

        // IMPORTANT: Replace with your actual Gemini API Key in the GeminiApiClient class

        mDb = FirebaseDatabase.getInstance().getReference();
        riverName = getIntent().getStringExtra("RIVER_NAME");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (riverName != null) {
            tvRiverName.setText(riverName);
            setupButtons();
            loadProjectData(); // This loads the graph
        }
    }

    private void setupButtons() {
        Button btnRecord = findViewById(R.id.btnRecord);
        Button btnAnalyze = findViewById(R.id.btnGeminiAI);

        btnRecord.setOnClickListener(v -> {
            if (!isRecording) {
                showNewLocationDialog(btnRecord);
            } else {
                stopRecording(btnRecord);
            }
        });

        btnAnalyze.setOnClickListener(v -> performAiAnalysis());
    }

    // --- 1. Location & Recording Logic ---

    private void showNewLocationDialog(Button btnRecord) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Location");
        final EditText input = new EditText(this);
        input.setHint("Location Name (e.g., Bridge A)");
        builder.setView(input);

        builder.setPositiveButton("Start Recording", (dialog, which) -> {
            String locName = input.getText().toString().trim();
            if (!locName.isEmpty()) {
                currentLocationName = locName;
                startRecording(btnRecord);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void startRecording(Button btnRecord) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                // 1. Create the Location Node with GPS
                DatabaseReference locRef = mDb.child("projects").child(riverName).child(currentLocationName);
                locRef.child("lat").setValue(location.getLatitude());
                locRef.child("lng").setValue(location.getLongitude());

                // Set Order ID based on existing count
                mDb.child("projects").child(riverName).get().addOnSuccessListener(snap -> {
                    long count = snap.getChildrenCount();
                    locRef.child("order").setValue(count);
                });

                // 2. Tell ESP32 to START
                mDb.child("control/status").setValue("START");

                // 3. Attach Listener to Staging to copy data
                isRecording = true;
                btnRecord.setText("STOP RECORDING");
                btnRecord.setBackgroundColor(Color.RED);

                attachStagingListener(locRef.child("measurements"));
                Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopRecording(Button btnRecord) {
        isRecording = false;
        btnRecord.setText("START NEW LOCATION");
        btnRecord.setBackgroundColor(Color.parseColor("#4CAF50")); // Green

        mDb.child("control/status").setValue("STOP");

        if (stagingListener != null) {
            mDb.child("device_staging").removeEventListener(stagingListener);
        }
    }

    private void attachStagingListener(DatabaseReference targetRef) {
        stagingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && isRecording) {
                    // Copy data from Staging to Permanent History
                    targetRef.push().setValue(snapshot.getValue());
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        // Listen for changes in staging (ESP32 updates this)
        mDb.child("device_staging").addValueEventListener(stagingListener);
    }

    // --- 2. Charting Logic ---

    // 1. SCATTER PLOT DATA LOADING
    private void loadProjectData() {
        mDb.child("projects").child(riverName).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Entry> entries = new ArrayList<>();
                float totalTds = 0;
                int totalMeasurements = 0;

                // Loop through each Location (e.g., "Bridge A", "Point B")
                for (DataSnapshot locSnap : snapshot.getChildren()) {
                    if (locSnap.hasChild("measurements")) {
                        Integer order = locSnap.child("order").getValue(Integer.class);

                        // Loop through EVERY measurement in this location
                        for (DataSnapshot m : locSnap.child("measurements").getChildren()) {
                            try {
                                Double h = m.child("height").getValue(Double.class);
                                Double tds = m.child("tds").getValue(Double.class);
                                Double temp = m.child("temp").getValue(Double.class);

                                if (h != null && tds != null && order != null) {
                                    // Create a point for THIS specific reading
                                    // X = Location Order, Y = Height
                                    Entry entry = new Entry(order.floatValue(), h.floatValue());

                                    // Pack specific data for this dot (for the Marker)
                                    Map<String, Object> markerData = new HashMap<>();
                                    markerData.put("tds", tds.floatValue());
                                    markerData.put("temp", (temp != null) ? temp.floatValue() : 0f);
                                    markerData.put("name", locSnap.getKey());
                                    entry.setData(markerData);

                                    entries.add(entry);

                                    // Update totals for global background color
                                    totalTds += tds;
                                    totalMeasurements++;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                if (!entries.isEmpty()) {
                    // Calculate global average for the background color
                    float globalAvg = (totalMeasurements > 0) ? (totalTds / totalMeasurements) : 0;
                    updateChart(entries, globalAvg);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // 2. SCATTER CHART STYLING
    private void updateChart(List<Entry> entries, float globalAvgTds) {
        // Sort is required for LineChart even if we don't draw lines
        entries.sort((e1, e2) -> Float.compare(e1.getX(), e2.getX()));

        LineDataSet set = new LineDataSet(entries, "Water Level Readings");

        // DISABLE LINES to make it a Scatter Plot
        set.setLineWidth(0f);                // Set line width to zero
        set.setDrawValues(false);            // Keep it clean
        set.setDrawCircles(true);            // Ensure dots are drawn
        set.setColor(Color.TRANSPARENT); // No lines connecting points
        set.setDrawValues(false); // Hide numbers on dots (too messy for scatter)

        // Dynamic Dot Colors (Per measurement)
        List<Integer> colors = new ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> data = (Map<String, Object>) e.getData();
            float tds = (float) data.get("tds");

            if (tds < 50) colors.add(Color.GREEN);
            else if (tds <= 300) colors.add(Color.YELLOW);
            else if (tds <= 700) colors.add(Color.parseColor("#FFA500")); // Orange
            else colors.add(Color.RED);
        }
        set.setCircleColors(colors);
        set.setCircleRadius(5f); // Slightly smaller dots for better visibility
        set.setDrawCircleHole(false); // Solid dots

        // Background Color (Global Safety Status)
        int bgAlpha = 50; // Very faint transparency
        if (globalAvgTds > 1000) chartBackground.setBackgroundColor(Color.argb(bgAlpha, 255, 0, 0));
        else if (globalAvgTds > 300) chartBackground.setBackgroundColor(Color.argb(bgAlpha, 255, 165, 0));
        else if (globalAvgTds > 50) chartBackground.setBackgroundColor(Color.argb(bgAlpha, 255, 255, 0));
        else chartBackground.setBackgroundColor(Color.argb(bgAlpha, 0, 255, 0));

        LineData data = new LineData(set);
        riverChart.setData(data);
        riverChart.getDescription().setEnabled(false);

        // Set Custom Marker (Hover Effect)
        CustomMarkerView mv = new CustomMarkerView(this, R.layout.marker_view);
        mv.setChartView(riverChart); // Required for bounds control
        riverChart.setMarker(mv);

        riverChart.invalidate();
    }

    // 3. FIXED AI ANALYSIS (Prevents Empty Requests)
    private void performAiAnalysis() {
        tvAiResult.setText("Analyzing data...");

        mDb.child("projects").child(riverName).get().addOnSuccessListener(snapshot -> {
            StringBuilder sb = new StringBuilder();
            int sampleCount = 0;

            for (DataSnapshot ds : snapshot.getChildren()) {
                if (ds.hasChild("measurements")) {
                    sb.append("Location ").append(ds.getKey()).append(": ");

                    // Only take the last 3 readings per location to save API tokens
                    int limit = 0;
                    for(DataSnapshot m : ds.child("measurements").getChildren()) {
                        if(limit++ > 3) break;
                        Object t = m.child("tds").getValue();
                        Object h = m.child("height").getValue();
                        if(t!=null && h!=null) {
                            sb.append("[TDS:").append(t).append(", H:").append(h).append("] ");
                        }
                    }
                    sb.append("; ");
                    sampleCount++;
                }
            }

            if (sampleCount == 0) {
                tvAiResult.setText("No data available to analyze.");
                return;
            }

            // Send to Gemini
            GeminiApiClient.analyzeData(riverName, sb.toString(), new GeminiApiClient.GeminiCallback() {
                @Override
                public void onSuccess(String result) {
                    tvAiResult.setText(result);
                }
                @Override
                public void onError(String error) {
                    tvAiResult.setText("AI Error: " + error);
                }
            });
        });
    }
}