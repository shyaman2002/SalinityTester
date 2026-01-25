package com.example.salinitytester;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private View statusIndicator;
    private ListView projectListView;
    private ArrayList<String> projectList = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private DatabaseReference mRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIndicator = findViewById(R.id.statusIndicator);
        projectListView = findViewById(R.id.projectListView);
        FloatingActionButton fabAdd = findViewById(R.id.fabAddProject);
        mRef = FirebaseDatabase.getInstance().getReference();

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, projectList);
        projectListView.setAdapter(adapter);

        // --- 1. Online Status Logic ---
        mRef.child("status/esp32_online").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isOnline = snapshot.getValue(Boolean.class);
                statusIndicator.setBackgroundColor(isOnline != null && isOnline ? Color.GREEN : Color.RED);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // --- 2. Load Existing Projects ---
        mRef.child("projects").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                projectList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    projectList.add(ds.getKey());
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // --- 3. Create Project Dialog ---
        fabAdd.setOnClickListener(view -> showCreateProjectDialog());

        projectListView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(MainActivity.this, RiverAnalysisActivity.class);
            intent.putExtra("RIVER_NAME", projectList.get(position));
            startActivity(intent);
        });
    }

    private void showCreateProjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New River Project");

        final EditText input = new EditText(this);
        input.setHint("Enter River Name (e.g., Nilwala)");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String projectName = input.getText().toString().trim();
            if (!projectName.isEmpty()) {
                // Creates the folder in Firebase
                mRef.child("projects").child(projectName).child("created_at").setValue(System.currentTimeMillis());
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }
}