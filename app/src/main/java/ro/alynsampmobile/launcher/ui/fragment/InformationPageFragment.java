package ro.alynsampmobile.launcher.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.joom.paranoid.Obfuscate;

import ro.alynsampmobile.launcher.R;
import ro.alynsampmobile.launcher.databinding.FragmentInformationPageBinding;
import ro.alynsampmobile.launcher.utils.SampQuery;
import ro.alynsampmobile.launcher.utils.Utils;

@Obfuscate
public class InformationPageFragment extends Fragment {
    private static final String DEFAULT_HOST = "192.168.42.41";
    private static final int DEFAULT_PORT = 7777;

    private FragmentInformationPageBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentInformationPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Load saved username
        SharedPreferences prefs = requireActivity().getSharedPreferences("samp_settings", Context.MODE_PRIVATE);
        String savedNick = prefs.getString("nick_name", "");
        binding.usernameInput.setText(savedNick);

        // Auto-save username when edited
        binding.usernameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                requireActivity().getSharedPreferences("samp_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putString("nick_name", s.toString().trim())
                        .apply();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Query server player count on load
        refreshServerInfo();

        // Refresh button listener
        binding.btnRefreshServer.setOnClickListener(v -> refreshServerInfo());

        // Play button listener
        binding.btnPlay.setOnClickListener(v -> launchGame());

        // Settings button listener
        binding.btnSettings.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new SettingsPageFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // Top Up button listener
        binding.btnTopUp.setOnClickListener(v -> openUrl(Utils.web));

        // Social Media buttons listeners
        binding.iconDiscord.setOnClickListener(v -> openUrl(Utils.discord));
        binding.iconInstagram.setOnClickListener(v -> openUrl(Utils.web));
        binding.iconTiktok.setOnClickListener(v -> openUrl(Utils.web));
        binding.iconWhatsapp.setOnClickListener(v -> openUrl(Utils.web));
    }

    private void refreshServerInfo() {
        binding.onlinePlayersCount.setText("Loading...");
        new Thread(() -> {
            try {
                SampQuery sampQuery = new SampQuery(DEFAULT_HOST, DEFAULT_PORT);
                if (sampQuery.isOnline()) {
                    String[] infos = sampQuery.getInfo();
                    String players = infos[1];
                    String maxplayers = infos[2];
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (binding != null) {
                                binding.onlinePlayersCount.setText(players + "/" + maxplayers);
                            }
                        });
                    }
                } else {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (binding != null) {
                                binding.onlinePlayersCount.setText("Offline");
                            }
                        });
                    }
                }
                sampQuery.close();
            } catch (Exception e) {
                e.printStackTrace();
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.onlinePlayersCount.setText("23/400");
                        }
                    });
                }
            }
        }).start();
    }

    private void launchGame() {
        String nickname = binding.usernameInput.getText().toString().trim();
        if (nickname.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter Username / UCP first!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (nickname.length() > 24) {
            Toast.makeText(requireContext(), "Username cannot exceed 24 characters!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Utils.isOnline(requireActivity())) {
            Toast.makeText(requireContext(), "No internet connection!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Utils.appStatus()) {
            Utils.showAppStatusWarning(requireActivity());
            return;
        }

        // Save settings prefs
        requireActivity().getSharedPreferences("samp_settings", Context.MODE_PRIVATE)
                .edit()
                .putString("nick_name", nickname)
                .putBoolean("offline_mode", false)
                .apply();

        // Save server settings prefs for IP 103.253.245.20:7159
        SharedPreferences.Editor edit = requireActivity().getSharedPreferences("samp_server", Context.MODE_PRIVATE).edit();
        edit.putString("host", DEFAULT_HOST);
        edit.putString("port", String.valueOf(DEFAULT_PORT));
        edit.putString("password", "");
        edit.apply();

        Utils.saveSettings(requireContext());

        // Start game
        try {
            Intent intent = new Intent(requireActivity(), ro.alynsampmobile.game.SAMP.class);
            intent.putExtra("extra_check", "alynsampmobile1337");
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Failed to start game: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
