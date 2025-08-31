package com.example.nearbymemories;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.example.nearbymemories.ui.DetailFragment;
import com.example.nearbymemories.ui.ListFragment;
import com.example.nearbymemories.ui.MapFragment;
import com.example.nearbymemories.util.Db;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Db.init(getApplicationContext());

        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        bottom.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.menu_map) replace(new MapFragment());
            else if (item.getItemId() == R.id.menu_list) replace(new ListFragment());
            return true;
        });

        if (savedInstanceState == null) {
            bottom.setSelectedItemId(R.id.menu_map);
        }
    }

    private void replace(@NonNull Fragment fragment) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragmentContainer, fragment);
        ft.commit();
    }

    public void openDetail(int memoryId) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragmentContainer, DetailFragment.newInstance(memoryId));
        ft.addToBackStack(null);
        ft.commit();
    }
}
