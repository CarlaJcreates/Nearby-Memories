package com.example.nearbymemories.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nearbymemories.MainActivity;
import com.example.nearbymemories.R;
import com.example.nearbymemories.data.Memory;
import com.example.nearbymemories.util.Db;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ListFragment extends Fragment {
    private final MemoryAdapter adapter = new MemoryAdapter(id -> ((MainActivity) requireActivity()).openDetail(id));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RecyclerView rv = view.findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        Db.instance.memoryDao().getAll().observe(getViewLifecycleOwner(), adapter::submit);
    }
}

class MemoryAdapter extends RecyclerView.Adapter<MemoryVH> {
    interface OnClick { void run(int id); }

    private final OnClick onClick;
    private final List<Memory> items = new ArrayList<>();

    MemoryAdapter(OnClick onClick) { this.onClick = onClick; }

    void submit(List<Memory> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged(); // simple student-y approach
    }

    @NonNull @Override
    public MemoryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
        return new MemoryVH(v, onClick);
    }

    @Override
    public void onBindViewHolder(@NonNull MemoryVH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }
}

class MemoryVH extends RecyclerView.ViewHolder {
    private final TextView title;
    private final TextView coords;
    private final TextView tvContact;
    private final ImageView thumb;
    private final TextView tvNoImage;
    private final MemoryAdapter.OnClick onClick;

    MemoryVH(@NonNull View itemView, MemoryAdapter.OnClick onClick) {
        super(itemView);
        this.onClick = onClick;
        title = itemView.findViewById(R.id.txtTitle);
        coords = itemView.findViewById(R.id.txtCoords);
        thumb = itemView.findViewById(R.id.imgThumb);
        tvNoImage = itemView.findViewById(R.id.tvNoImage);
        tvContact = itemView.findViewById(R.id.tvContact);
    }

    void bind(Memory m) {
        // Title + coords
        String t = (m.title != null && !m.title.trim().isEmpty()) ? m.title : "Untitled memory";
        title.setText(t);
        coords.setText(String.format("Lat: %.5f, Lng: %.5f", m.latitude, m.longitude));

        // Contact line: "Contact: {name}" or "No contact added"
        String contact = tryStringField(m, "contactName");
        if (contact == null || contact.trim().isEmpty()) {
            // try alternate common name
            contact = tryStringField(m, "contactDisplayName");
        }
        if (contact != null && !contact.trim().isEmpty()) {
            tvContact.setText("Contact: " + contact);
        } else {
            tvContact.setText("No contact added");
        }

        // Photo: prefer m.mediaUri, else m.photoUri; show stub text if none
        String photo = tryStringField(m, "mediaUri");
        if (photo == null || photo.trim().isEmpty()) {
            photo = tryStringField(m, "photoUri");
        }

        if (photo != null && !photo.trim().isEmpty()) {
            tvNoImage.setVisibility(View.GONE);
            thumb.setVisibility(View.VISIBLE);
            try {
                thumb.setImageURI(Uri.parse(photo));
            } catch (Exception e) {
                // If anything goes wrong, fall back to the "No image added" stub
                thumb.setImageDrawable(null);
                thumb.setVisibility(View.GONE);
                tvNoImage.setVisibility(View.VISIBLE);
            }
        } else {
            thumb.setImageDrawable(null);
            thumb.setVisibility(View.GONE);
            tvNoImage.setVisibility(View.VISIBLE);
        }

        itemView.setOnClickListener(v -> onClick.run(m.id));
    }

    // Tiny helper to read optional fields without breaking your build if the name differs
    private @Nullable String tryStringField(Memory m, String fieldName) {
        try {
            Field f = m.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(m);
            return v instanceof String ? (String) v : null;
        } catch (NoSuchFieldException ignored) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
