package com.example.salinitytester;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    FirebaseDatabase database;
    DatabaseReference configRef, statusRef, dataRef;

    TextView tvStatus;
    EditText etLocation;
    Button btnStart, btnStop, btnUpload;
    ScatterChart scatterChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI Init
        tvStatus = findViewById(R.id.tvStatus);
        etLocation = findViewById(R.id.etLocation);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnUpload = findViewById(R.id.btnUpload);
        scatterChart = findViewById(R.id.scatterChart);

        // Firebase Init
        database = FirebaseDatabase.getInstance();
        configRef = database.getReference("config");
        statusRef = database.getReference("status");
        dataRef = database.getReference("measurements");

        // 1. STATUS LISTENER (Is ESP32 Connected?)
        statusRef.child("connection").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if (status != null) {
                    tvStatus.setText("ESP32 Status: " + status);
                    tvStatus.setTextColor(Color.GREEN);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // 2. BUTTON ACTIONS
        btnStart.setOnClickListener(v -> {
            String loc = etLocation.getText().toString();
            if(loc.isEmpty()){
                Toast.makeText(this, "Enter Location Name first", Toast.LENGTH_SHORT).show();
                return;
            }
            configRef.child("locationName").setValue(loc);
            configRef.child("recording").setValue(true);
            Toast.makeText(this, "Command Sent: START", Toast.LENGTH_SHORT).show();
        });

        btnStop.setOnClickListener(v -> {
            configRef.child("recording").setValue(false);
            Toast.makeText(this, "Command Sent: STOP", Toast.LENGTH_SHORT).show();
        });

        btnUpload.setOnClickListener(v -> {
            configRef.child("uploadTrigger").setValue(true);
            Toast.makeText(this, "Command Sent: UPLOAD", Toast.LENGTH_SHORT).show();
            // Optional: Auto-load chart after a delay or listen for data changes
            loadChartData(etLocation.getText().toString());
        });
    }

    // 3. LOAD & PLOT DATA
    // Inside MainActivity class
    private void startConnectionMonitor() {
        DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference("status/lastHeartbeat");

        statusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long lastTime = snapshot.getValue(Long.class);
                if (lastTime != null) {
                    long currentTime = System.currentTimeMillis() / 1000;
                    // If the last update was less than 10 seconds ago
                    if (currentTime - lastTime < 10) {
                        tvStatus.setText("Status: Online");
                        tvStatus.setTextColor(Color.GREEN);
                    } else {
                        tvStatus.setText("Status: Offline (Last seen " + (currentTime - lastTime) + "s ago)");
                        tvStatus.setTextColor(Color.RED);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
    private void loadChartData(String locationName) {
        if(locationName.isEmpty()) return;

        dataRef.child(locationName).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<Entry> entries = new ArrayList<>();
                int index = 0;

                for (DataSnapshot dp : snapshot.getChildren()) {
                    // Extract TDS (or Temp)
                    Float tds = dp.child("tds").getValue(Float.class);
                    if (tds != null) {
                        // Plotting X = Index (Sequence), Y = TDS Value
                        entries.add(new Entry(index++, tds));
                    }
                }

                if (!entries.isEmpty()) {
                    ScatterDataSet dataSet = new ScatterDataSet(entries, "TDS Levels for " + locationName);
                    dataSet.setColor(Color.BLUE);
                    dataSet.setScatterShapeSize(10f);

                    ScatterData scatterData = new ScatterData(dataSet);
                    scatterChart.setData(scatterData);
                    scatterChart.invalidate(); // Refresh
                } else {
                    Toast.makeText(MainActivity.this, "No data found for this location", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}