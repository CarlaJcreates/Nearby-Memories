package com.example.nearbymemories.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;

import com.example.nearbymemories.R;
import com.example.nearbymemories.data.Memory;
import com.example.nearbymemories.util.AppExecutors;
import com.example.nearbymemories.util.Db;

import java.lang.reflect.Field;

public class DetailFragment extends Fragment {
    private static final String ARG_ID = "id";

    public static DetailFragment newInstance(int id) {
        DetailFragment f = new DetailFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_ID, id);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView txtTitle   = view.findViewById(R.id.dTitle);
        TextView txtCoords  = view.findViewById(R.id.dCoords);
        TextView txtContact = view.findViewById(R.id.dContact);
        TextView txtWeather = view.findViewById(R.id.dWeather);
        ImageView img       = view.findViewById(R.id.dImage);
        TextView tvNoImage  = view.findViewById(R.id.dNoImage);

        int id = (getArguments() != null) ? getArguments().getInt(ARG_ID, -1) : -1;
        if (id == -1) return;

        AppExecutors.IO.execute(() -> {
            Memory mem = Db.instance.memoryDao().getByIdSync(id);
            if (mem == null) return;

            requireActivity().runOnUiThread(() -> {
                // Title + coordinates
                String title = (mem.title != null && !mem.title.trim().isEmpty()) ? mem.title : "Untitled memory";
                txtTitle.setText(title);
                txtCoords.setText(String.format("%.5f, %.5f", mem.latitude, mem.longitude));

                // Weather (simple default)
                String weather = tryStringField(mem, "weatherSummary");
                if (weather == null || weather.trim().isEmpty()) weather = "(no network info)";
                txtWeather.setText(weather);

                // Contact name & URI (with defaults)
                String contactName = firstNonEmpty(
                        tryStringField(mem, "contactName"),
                        tryStringField(mem, "contactDisplayName")
                );
                String contactUri = firstNonEmpty(
                        tryStringField(mem, "contactUri"),
                        tryStringField(mem, "contactURI") // just in case
                );

                if (contactName != null) {
                    txtContact.setText("Contact: " + contactName);
                } else if (contactUri != null) {
                    txtContact.setText("Contact available");
                } else {
                    txtContact.setText("No contact added");
                }

                // Make contact clickable only if we have a URI
                txtContact.setOnClickListener(null);
                if (contactUri != null) {
                    txtContact.setOnClickListener(v -> {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(contactUri));
                            startActivity(i);
                        } catch (Exception ignored) { }
                    });
                }

                // Photo vs placeholder
                String photo = firstNonEmpty(
                        tryStringField(mem, "mediaUri"),
                        tryStringField(mem, "photoUri")
                );
                if (photo != null) {
                    try {
                        img.setVisibility(View.VISIBLE);
                        tvNoImage.setVisibility(View.GONE);
                        img.setImageURI(Uri.parse(photo));
                    } catch (Exception e) {
                        // Fall back to placeholder if loading fails
                        img.setImageDrawable(null);
                        img.setVisibility(View.GONE);
                        tvNoImage.setVisibility(View.VISIBLE);
                    }
                } else {
                    img.setImageDrawable(null);
                    img.setVisibility(View.GONE);
                    tvNoImage.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    // === tiny helpers ===
    private @Nullable String firstNonEmpty(@Nullable String a, @Nullable String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        if (b != null && !b.trim().isEmpty()) return b;
        return null;
    }

    private @Nullable String tryStringField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(obj);
            return (v instanceof String) ? (String) v : null;
        } catch (NoSuchFieldException ignore) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
