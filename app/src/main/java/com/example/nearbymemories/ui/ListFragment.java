package com.example.nearbymemories.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nearbymemories.MainActivity;
import com.example.nearbymemories.R;
import com.example.nearbymemories.data.Memory;
import com.example.nearbymemories.util.Db;

import java.util.ArrayList;
import java.util.List;

public class ListFragment extends Fragment {
    private final MemoryAdapter adapter = new MemoryAdapter(id -> ((MainActivity) requireActivity()).openDetail(id));

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
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
        notifyDataSetChanged();
    }

    @NonNull @Override public MemoryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
        return new MemoryVH(v, onClick);
    }
    @Override public void onBindViewHolder(@NonNull MemoryVH holder, int position) { holder.bind(items.get(position)); }
    @Override public int getItemCount() { return items.size(); }
}

class MemoryVH extends RecyclerView.ViewHolder {
    private final TextView title;
    private final TextView coords;
    private final ImageView thumb;
    private final MemoryAdapter.OnClick onClick;

    MemoryVH(@NonNull View itemView, MemoryAdapter.OnClick onClick) {
        super(itemView);
        this.onClick = onClick;
        title = itemView.findViewById(R.id.txtTitle);
        coords = itemView.findViewById(R.id.txtCoords);
        thumb = itemView.findViewById(R.id.imgThumb);
    }

    void bind(Memory m) {
        title.setText(m.title);
        coords.setText(String.format("%.5f, %.5f", m.latitude, m.longitude));
        if (m.mediaUri != null) thumb.setImageURI(Uri.parse(m.mediaUri));
        else thumb.setImageResource(android.R.drawable.ic_menu_camera);
        itemView.setOnClickListener(v -> onClick.run(m.id));
    }
}
