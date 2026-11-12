package ro.alynsampmobile.launcher.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.joom.paranoid.Obfuscate;

import java.io.File;

import ro.alynsampmobile.launcher.R;
import ro.alynsampmobile.launcher.databinding.FragmentSettingsPageBinding;
import ro.alynsampmobile.launcher.utils.Logcat;
import ro.alynsampmobile.launcher.utils.Utils;

@Obfuscate
public class SettingsPageFragment extends Fragment {
    private FragmentSettingsPageBinding binding;
    private SharedPreferences settingsPrefs;

    public SettingsPageFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsPageBinding.inflate(inflater, container, false);
        settingsPrefs = requireActivity().getSharedPreferences("samp_settings", Context.MODE_PRIVATE);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Back button
        binding.btnBackSettings.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        // Send Log button
        binding.btnSendLog.setOnClickListener(v -> showLogDialog());

        // Setup Chat Font Size Segment Buttons
        setupFontSizeButtons();

        // Setup Chat Lines Segment Buttons
        setupChatLinesButtons();

        // Setup FPS Limit Segment Buttons
        setupFpsLimitButtons();

        // Setup Switches
        setupSwitches();
    }

    private void setupFontSizeButtons() {
        float currentSize = settingsPrefs.getFloat("font_size", 20.0f);
        updateFontSizeUI(currentSize);

        binding.btnFont5.setOnClickListener(v -> saveFontSize(15.0f));
        binding.btnFont7.setOnClickListener(v -> saveFontSize(20.0f));
        binding.btnFont10.setOnClickListener(v -> saveFontSize(26.0f));
        binding.btnFont12.setOnClickListener(v -> saveFontSize(32.0f));
    }

    private void saveFontSize(float size) {
        settingsPrefs.edit().putFloat("font_size", size).apply();
        Utils.saveSettings(requireContext());
        updateFontSizeUI(size);
    }

    private void updateFontSizeUI(float size) {
        resetButtonUI(binding.btnFont5, binding.btnFont7, binding.btnFont10, binding.btnFont12);

        if (size <= 17.0f) {
            setSelectedButton(binding.btnFont5);
        } else if (size <= 23.0f) {
            setSelectedButton(binding.btnFont7);
        } else if (size <= 29.0f) {
            setSelectedButton(binding.btnFont10);
        } else {
            setSelectedButton(binding.btnFont12);
        }
    }

    private void setupChatLinesButtons() {
        int currentLines = settingsPrefs.getInt("chat_strings", 6);
        updateChatLinesUI(currentLines);

        binding.btnLines6.setOnClickListener(v -> saveChatLines(6));
        binding.btnLines8.setOnClickListener(v -> saveChatLines(8));
        binding.btnLines10.setOnClickListener(v -> saveChatLines(10));
        binding.btnLines15.setOnClickListener(v -> saveChatLines(15));
    }

    private void saveChatLines(int lines) {
        settingsPrefs.edit().putInt("chat_strings", lines).apply();
        Utils.saveSettings(requireContext());
        updateChatLinesUI(lines);
    }

    private void updateChatLinesUI(int lines) {
        resetButtonUI(binding.btnLines6, binding.btnLines8, binding.btnLines10, binding.btnLines15);

        if (lines <= 6) {
            setSelectedButton(binding.btnLines6);
        } else if (lines <= 8) {
            setSelectedButton(binding.btnLines8);
        } else if (lines <= 10) {
            setSelectedButton(binding.btnLines10);
        } else {
            setSelectedButton(binding.btnLines15);
        }
    }

    private void setupFpsLimitButtons() {
        int currentFps = settingsPrefs.getInt("fps_limit", 60);
        updateFpsLimitUI(currentFps);

        binding.btnFps30.setOnClickListener(v -> saveFpsLimit(30));
        binding.btnFps60.setOnClickListener(v -> saveFpsLimit(60));
        binding.btnFps90.setOnClickListener(v -> saveFpsLimit(90));
        binding.btnFps120.setOnClickListener(v -> saveFpsLimit(120));
    }

    private void saveFpsLimit(int fps) {
        settingsPrefs.edit().putInt("fps_limit", fps).apply();
        Utils.saveSettings(requireContext());
        updateFpsLimitUI(fps);
    }

    private void updateFpsLimitUI(int fps) {
        resetButtonUI(binding.btnFps30, binding.btnFps60, binding.btnFps90, binding.btnFps120);

        if (fps <= 40) {
            setSelectedButton(binding.btnFps30);
        } else if (fps <= 75) {
            setSelectedButton(binding.btnFps60);
        } else if (fps <= 105) {
            setSelectedButton(binding.btnFps90);
        } else {
            setSelectedButton(binding.btnFps120);
        }
    }

    private void setupSwitches() {
        bindSwitch(binding.switchDisplayFps, "display_fps");
        bindSwitch(binding.switchVoice, "voice_chat");
        bindSwitch(binding.switchFullscreen, "fullscreen");
        bindSwitch(binding.switchSystemKeyboard, "system_keyboard");
        bindSwitch(binding.switchTimestamp, "timestamp");
    }

    private void bindSwitch(androidx.appcompat.widget.SwitchCompat switchCompat, String key) {
        switchCompat.setOnCheckedChangeListener(null);
        switchCompat.setChecked(settingsPrefs.getBoolean(key, false));
        switchCompat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsPrefs.edit().putBoolean(key, isChecked).apply();
            Utils.saveSettings(requireContext());
        });
    }

    private void setSelectedButton(TextView button) {
        button.setBackgroundResource(R.drawable.bg_segment_button_selected);
        button.setTextColor(Color.BLACK);
    }

    private void resetButtonUI(TextView... buttons) {
        for (TextView btn : buttons) {
            btn.setBackgroundResource(R.drawable.bg_segment_button_normal);
            btn.setTextColor(Color.WHITE);
        }
    }

    private void showLogDialog() {
        try {
            File logFile = new File(requireContext().getExternalFilesDir(null) + "/logcat", "Alyn_SAMPMOBILE.log");
            String logContent = "";
            if (logFile.exists()) {
                logContent = Utils.readJSONFromFile(logFile);
            }
            final String logs = logContent;

            new AlertDialog.Builder(requireContext())
                    .setTitle("Kirim Log")
                    .setMessage(logs.isEmpty() ? "No logs found." : (logs.length() > 2000 ? logs.substring(logs.length() - 2000) : logs))
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Copy", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Logcat", logs);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(requireContext(), "Logs copied to clipboard!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Logs unavailable.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
