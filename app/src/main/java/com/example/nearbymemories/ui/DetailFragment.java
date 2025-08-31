package com.example.nearbymemories.ui;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;

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
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        return new android.widget.FrameLayout(requireContext());
    }
}
