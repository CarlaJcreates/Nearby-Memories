package com.example.nearbymemories.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.fragment.app.Fragment;

import com.example.nearbymemories.MainActivity;
import com.example.nearbymemories.R;
import com.example.nearbymemories.data.Memory;
import com.example.nearbymemories.util.AppExecutors;
import com.example.nearbymemories.util.Db;
import com.example.nearbymemories.util.WeatherClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MapFragment extends Fragment {
    private FusedLocationProviderClient fused;
    private Uri pickedContactUri = null;
    private String pickedContactName = null;
    private Uri pickedPhotoUri = null;

    private final ActivityResultLauncher<String[]> askPerms = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> enableMyLocation()
    );

    private final ActivityResultLauncher<Intent> pickContactLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult result) -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        pickedContactUri = uri;
                        pickedContactName = queryContactName(uri);
                        Toast.makeText(requireContext(), "Contact: " + pickedContactName, Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    // Simpler, super-compatible photo picker:
    private final ActivityResultLauncher<String> pickPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                pickedPhotoUri = uri;
                if (uri != null) Toast.makeText(requireContext(), "Photo attached", Toast.LENGTH_SHORT).show();
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        fused = LocationServices.getFusedLocationProviderClient(requireContext());

        SupportMapFragment mapFrag = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapContainer);
        if (mapFrag == null) {
            mapFrag = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.mapContainer, mapFrag)
                    .commitNow();
        }

        mapFrag.getMapAsync(googleMap -> {
            googleMap.getUiSettings().setZoomControlsEnabled(true);
            enableMyLocation();

            Db.instance.memoryDao().getAll().observe(getViewLifecycleOwner(), memories -> {
                googleMap.clear();
                for (Memory m : memories) {
                    LatLng pos = new LatLng(m.latitude, m.longitude);
                    googleMap.addMarker(new MarkerOptions().position(pos).title(m.title)).setTag(m.id);
                }
            });

            googleMap.setOnMarkerClickListener(marker -> {
                Object tag = marker.getTag();
                if (tag instanceof Integer) {
                    ((MainActivity) requireActivity()).openDetail((Integer) tag);
                    return true;
                }
                return false;
            });

            fused.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(me, 14f));
                }
            });
        });

        FloatingActionButton fab = view.findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> startAddFlow());
    }

    private void startAddFlow() {
        pickedContactUri = null;
        pickedContactName = null;
        pickedPhotoUri = null;

        final EditText titleInput = new EditText(requireContext());
        titleInput.setHint("What do you want to remember?");

        new AlertDialog.Builder(requireContext())
                .setTitle("New Memory")
                .setView(titleInput)
                .setPositiveButton("Next", (d, w) -> {
                    String title = titleInput.getText().toString().trim();
                    if (title.isEmpty()) title = "Untitled memory";
                    showAttachChooser(title);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAttachChooser(String title) {
        String[] options = new String[]{"Attach Contact", "Attach Photo", "No Extra"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Attach anything?")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        ensureContactsPermissionThenPick();
                        continueSaveAfterPicks(title);
                    } else if (which == 1) {
                        pickPhotoLauncher.launch("image/*");
                        continueSaveAfterPicks(title);
                    } else {
                        continueSaveAfterPicks(title);
                    }
                })
                .show();
    }

    private void continueSaveAfterPicks(String title) {
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    if (loc == null) {
                        Toast.makeText(requireContext(), "Couldn't get location", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    AppExecutors.IO.execute(() -> {
                        String weather = WeatherClient.getWeatherSummary(loc.getLatitude(), loc.getLongitude());
                        Memory mem = new Memory(
                                title,
                                loc.getLatitude(),
                                loc.getLongitude(),
                                pickedContactUri != null ? pickedContactUri.toString() : null,
                                pickedContactName,
                                pickedPhotoUri != null ? pickedPhotoUri.toString() : null,
                                weather,
                                System.currentTimeMillis()
                        );
                        Db.instance.memoryDao().insert(mem);
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "Memory saved!", Toast.LENGTH_SHORT).show()
                        );
                    });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Location failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void enableMyLocation() {
        int fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION);
        int coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION);
        boolean granted = (fine == PermissionChecker.PERMISSION_GRANTED) || (coarse == PermissionChecker.PERMISSION_GRANTED);
        if (!granted) {
            askPerms.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        SupportMapFragment mapFrag = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapContainer);
        if (mapFrag != null) {
            mapFrag.getMapAsync(gMap -> gMap.setMyLocationEnabled(true));
        }
    }

    private void ensureContactsPermissionThenPick() {
        int has = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS);
        if (has != PermissionChecker.PERMISSION_GRANTED) {
            askPerms.launch(new String[]{Manifest.permission.READ_CONTACTS});
        }
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        pickContactLauncher.launch(intent);
    }

    private String queryContactName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = requireContext().getContentResolver().query(
                    uri,
                    new String[]{ContactsContract.Contacts.DISPLAY_NAME},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
