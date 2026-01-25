package com.example.salinitytester;

import android.content.Context;
import android.widget.TextView;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import java.util.Map;

public class CustomMarkerView extends MarkerView {
    private TextView tvContent;

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvContent = findViewById(R.id.tvMarkerContent);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        Map<String, Object> data = (Map<String, Object>) e.getData();
        if (data != null) {
            String name = (String) data.get("name");
            float tds = (float) data.get("tds");
            float temp = (float) data.get("temp");
            tvContent.setText(String.format("%s\nTDS: %.1f\nTemp: %.1f C", name, tds, temp));
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2), -getHeight());
    }
}