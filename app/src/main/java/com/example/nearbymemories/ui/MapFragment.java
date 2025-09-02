package com.example.nearbymemories.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

// Places SDK
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;

import java.util.Arrays;
import java.util.Collections;

public class MapFragment extends Fragment {

    private FusedLocationProviderClient fused;

    private Uri pickedContactUri = null;
    private String pickedContactName = null;
    private Uri pickedPhotoUri = null;

    // User-chosen location (by search or long-press)
    private LatLng selectedLatLng = null;
    private Marker dropMarker = null;

    // Jamaica approx bounds (SW, NE)
    private static final RectangularBounds JAMAICA_BOUNDS = RectangularBounds.newInstance(
            new LatLng(17.70, -78.40), // SW
            new LatLng(18.60, -76.20)  // NE
    );
    private boolean jamaicaFirst = true; // default scope

    // Permissions
    private final ActivityResultLauncher<String[]> askPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> enableMyLocation());

    // Contact picker
    private final ActivityResultLauncher<Intent> pickContactLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    (ActivityResult result) -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                pickedContactUri = uri;
                                pickedContactName = queryContactName(uri);
                                Toast.makeText(requireContext(),
                                        "Contact: " + (pickedContactName != null ? pickedContactName : "(unknown)"),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    // Photo picker (GetContent for broad compatibility)
    private final ActivityResultLauncher<String> pickPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                pickedPhotoUri = uri;
                if (uri != null) {
                    Toast.makeText(requireContext(), "Photo attached", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        fused = LocationServices.getFusedLocationProviderClient(requireContext());

        // Ensure SupportMapFragment exists in our container
        SupportMapFragment mapFrag =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapContainer);
        if (mapFrag == null) {
            mapFrag = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.mapContainer, mapFrag)
                    .commitNow();
        }

        // Initialize Places with the SAME API key already in AndroidManifest
        String apiKey = getMapsApiKeyFromManifest();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            Toast.makeText(requireContext(), "API key missing", Toast.LENGTH_SHORT).show();
        } else if (!Places.isInitialized()) {
            Places.initialize(requireContext().getApplicationContext(), apiKey);
        }

        // Hook the Autocomplete search bar (child fragment)
        AutocompleteSupportFragment ac =
                (AutocompleteSupportFragment) getChildFragmentManager()
                        .findFragmentById(R.id.autoCompleteFragment);
        if (ac != null) {
            ac.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG));
            ac.setHint("Search place or address...");
            styleAutocompleteBar(ac);           // give it a background on load
            applyPlacesScope(ac, true);         // Jamaica-first initially

            ac.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(@NonNull Place place) {
                    if (place.getLatLng() == null) {
                        Toast.makeText(requireContext(), "No coordinates for that place", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedLatLng = place.getLatLng();
                    SupportMapFragment mf = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapContainer);
                    if (mf != null) {
                        mf.getMapAsync(gm -> {
                            if (dropMarker != null) dropMarker.remove();
                            gm.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 16f));
                            dropMarker = gm.addMarker(new MarkerOptions()
                                    .position(selectedLatLng)
                                    .title(place.getName() != null ? place.getName() : "Selected")
                                    .draggable(true));
                        });
                    }
                }

                @Override
                public void onError(@NonNull com.google.android.gms.common.api.Status status) {
                    Toast.makeText(requireContext(), "Search error: " + status, Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Toggle scope button (Jamaica <-> Worldwide)
        ImageButton btnScope = view.findViewById(R.id.btnScope);
        if (btnScope != null) {
            btnScope.setOnClickListener(v -> {
                jamaicaFirst = !jamaicaFirst;
                applyPlacesScope(ac, jamaicaFirst);
                Toast.makeText(requireContext(),
                        jamaicaFirst ? "Searching Jamaica first" : "Searching worldwide",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // Map ready
        mapFrag.getMapAsync(googleMap -> {
            googleMap.getUiSettings().setZoomControlsEnabled(true);
            enableMyLocation();

            // Render saved memories
            Db.instance.memoryDao().getAll().observe(getViewLifecycleOwner(), memories -> {
                googleMap.clear();
                for (Memory m : memories) {
                    LatLng pos = new LatLng(m.latitude, m.longitude);
                    Marker mk = googleMap.addMarker(new MarkerOptions().position(pos).title(m.title));
                    if (mk != null) mk.setTag(m.id);
                }
                // Re-add user's selected pin if it exists
                if (selectedLatLng != null) {
                    dropMarker = googleMap.addMarker(new MarkerOptions()
                            .position(selectedLatLng)
                            .title("Selected spot")
                            .draggable(true));
                }
            });

            // Tap memory marker -> detail
            googleMap.setOnMarkerClickListener(marker -> {
                Object tag = marker.getTag();
                if (tag instanceof Integer) {
                    ((MainActivity) requireActivity()).openDetail((Integer) tag);
                    return true;
                }
                return false;
            });

            // Long-press to drop a pin
            googleMap.setOnMapLongClickListener(latLng -> {
                selectedLatLng = latLng;
                if (dropMarker != null) dropMarker.remove();
                dropMarker = googleMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Selected spot")
                        .draggable(true));
            });

            // Drag to fine-tune the pin
            googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
                @Override public void onMarkerDragStart(Marker marker) { }
                @Override public void onMarkerDrag(Marker marker) { }
                @Override public void onMarkerDragEnd(Marker marker) {
                    if (marker.equals(dropMarker)) {
                        selectedLatLng = marker.getPosition();
                        Toast.makeText(requireContext(),
                                String.format("Chosen: %.5f, %.5f",
                                        selectedLatLng.latitude, selectedLatLng.longitude),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // Move camera to last known location
            fused.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(me, 14f));
                }
            });
        });

        // Add Memory button
        FloatingActionButton fab = view.findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> startAddFlow());
    }

    // ==== New Memory flow ====

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
        // Use user-selected pin if available
        if (selectedLatLng != null) {
            LatLng chosen = selectedLatLng;
            AppExecutors.IO.execute(() -> {
                try {
                    String weather = WeatherClient.getWeatherSummary(chosen.latitude, chosen.longitude);
                    Memory mem = new Memory(
                            title,
                            chosen.latitude, chosen.longitude,
                            pickedContactUri != null ? pickedContactUri.toString() : null,
                            pickedContactName,
                            pickedPhotoUri != null ? pickedPhotoUri.toString() : null,
                            weather,
                            System.currentTimeMillis()
                    );
                    Db.instance.memoryDao().insert(mem);
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Memory saved at pin!", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                    "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
            return;
        }

        // Fallback: use current device location
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    if (loc == null) {
                        Toast.makeText(requireContext(),
                                "Couldn't get location. Try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    AppExecutors.IO.execute(() -> {
                        try {
                            String weather = WeatherClient.getWeatherSummary(loc.getLatitude(), loc.getLongitude());
                            Memory mem = new Memory(
                                    title,
                                    loc.getLatitude(), loc.getLongitude(),
                                    pickedContactUri != null ? pickedContactUri.toString() : null,
                                    pickedContactName,
                                    pickedPhotoUri != null ? pickedPhotoUri.toString() : null,
                                    weather,
                                    System.currentTimeMillis()
                            );
                            Db.instance.memoryDao().insert(mem);
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "Memory saved!", Toast.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(),
                                            "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Location failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ==== Permissions & helpers ====

    private void enableMyLocation() {
        int fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION);
        int coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION);
        boolean granted = (fine == PermissionChecker.PERMISSION_GRANTED) || (coarse == PermissionChecker.PERMISSION_GRANTED);
        if (!granted) {
            askPerms.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        SupportMapFragment mapFrag =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapContainer);
        if (mapFrag != null) {
            mapFrag.getMapAsync(gMap -> {
                try {
                    gMap.setMyLocationEnabled(true);
                } catch (SecurityException ignored) { }
            });
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

    private @Nullable String queryContactName(Uri uri) {
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

    // Styling: give the Places Autocomplete bar a rounded background + readable text on load
    private void styleAutocompleteBar(@Nullable AutocompleteSupportFragment ac) {
        if (ac == null || ac.getView() == null) return;

        View acRoot = ac.getView();
        int barId = com.google.android.libraries.places.R.id.places_autocomplete_search_bar;
        int inputId = com.google.android.libraries.places.R.id.places_autocomplete_search_input;

        View bar = acRoot.findViewById(barId);
        View input = acRoot.findViewById(inputId);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);                 // white
        float radius = dp(12);
        bg.setCornerRadius(radius);

        if (bar != null) {
            int pad = (int) dp(8);
            bar.setPadding(pad, pad, pad, pad);
            bar.setBackground(bg);
            bar.setElevation(dp(2));             // subtle shadow
        }

        if (input instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) input;
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setHintTextColor(0xFF888888);
            tv.setTextColor(0xFF111111);
        }
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // Apply or clear a Jamaica-first scope for the autocomplete
    private void applyPlacesScope(@Nullable AutocompleteSupportFragment ac, boolean jmFirst) {
        if (ac == null) return;

        if (jmFirst) {
            ac.setCountries(Collections.singletonList("JM"));
            ac.setLocationBias(JAMAICA_BOUNDS);
            // To strictly limit results within the rectangle, uncomment:
            // ac.setLocationRestriction(JAMAICA_BOUNDS);
            ac.setHint("Search Jamaica…");
        } else {
            ac.setCountries(Collections.emptyList()); // <-- no restriction (avoid null ambiguity)
            ac.setLocationBias(null);
            ac.setLocationRestriction(null);
            ac.setHint("Search place or address…");
        }
    }

    // Read the SAME API key already placed in AndroidManifest <application> meta-data
    private @Nullable String getMapsApiKeyFromManifest() {
        try {
            ApplicationInfo ai = requireContext().getPackageManager()
                    .getApplicationInfo(requireContext().getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle == null) return null;
            return bundle.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
