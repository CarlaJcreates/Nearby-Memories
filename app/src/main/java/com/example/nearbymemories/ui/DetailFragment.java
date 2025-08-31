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
        TextView txtTitle = view.findViewById(R.id.dTitle);
        TextView txtCoords = view.findViewById(R.id.dCoords);
        TextView txtContact = view.findViewById(R.id.dContact);
        TextView txtWeather = view.findViewById(R.id.dWeather);
        ImageView img = view.findViewById(R.id.dImage);

        int id = getArguments() != null ? getArguments().getInt(ARG_ID) : -1;
        if (id == -1) return;

        AppExecutors.IO.execute(() -> {
            Memory mem = Db.instance.memoryDao().getByIdSync(id);
            if (mem == null) return;
            requireActivity().runOnUiThread(() -> {
                txtTitle.setText(mem.title);
                txtCoords.setText(mem.latitude + ", " + mem.longitude);
                txtContact.setText(mem.contactName != null ? mem.contactName :
                        (mem.contactUri != null ? mem.contactUri : "(none)"));
                txtWeather.setText(mem.weatherSummary != null ? mem.weatherSummary : "(no network info)");
                if (mem.mediaUri != null) img.setImageURI(Uri.parse(mem.mediaUri));
                else img.setImageResource(android.R.drawable.ic_menu_report_image);

                txtContact.setOnClickListener(v -> {
                    if (mem.contactUri != null) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(mem.contactUri));
                        startActivity(i);
                    }
                });
            });
        });
    }
}
