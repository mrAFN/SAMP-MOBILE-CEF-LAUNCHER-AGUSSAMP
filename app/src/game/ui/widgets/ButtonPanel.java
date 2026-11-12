package ro.alynsampmobile.game.ui.widgets;

import android.app.Activity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.joom.paranoid.Obfuscate;
import ro.alynsampmobile.launcher.R;

/**
 * Panel tombol in-game (ESC, TAB, ALT, SPC, F, H, 2, Y, N, C, G).
 *
 * Tampilan: handle bulat + dock kaca berisi keycap. Dock membuka/menutup dengan animasi halus,
 * chevron pada handle berputar, dan tiap tombol memberi getar ringan (mengikuti pengaturan sistem).
 * ID view dan kode tombol (1..11) sama seperti sebelumnya, jadi UI.java tidak perlu diubah.
 */
@Obfuscate
public class ButtonPanel {
    private static final long OPEN_MS = 200L;
    private static final long CLOSE_MS = 150L;
    private static final float DOCK_START_SCALE = 0.85f;

    private boolean extended;
    private final ConstraintLayout buttonpanel_layout;
    private final View buttons_dock;
    private final ImageView button_extend;

    public interface Listener {
        void _sendButtonPanelKey(int id);
    }

    private final Listener listener;

    public ButtonPanel(Activity activity, Listener listener) {
        this.listener = listener;

        extended = true;

        buttonpanel_layout = (ConstraintLayout) activity.getLayoutInflater().inflate(R.layout.activity_game_buttonpanel, null);
        activity.addContentView(buttonpanel_layout, new ConstraintLayout.LayoutParams(-1, -1));

        buttons_dock = buttonpanel_layout.findViewById(R.id.buttons_dock);
        button_extend = buttonpanel_layout.findViewById(R.id.button_extend);

        button_extend.setOnClickListener(view -> extend(!extended, true));

        // kode tombol dikirim ke native (sendButtonPanelKey) - urutan sama seperti sebelumnya
        bindKey(R.id.button_esc, 1);
        bindKey(R.id.button_tab, 2);
        bindKey(R.id.button_alt, 3);
        bindKey(R.id.button_spc, 4);
        bindKey(R.id.button_f, 5);
        bindKey(R.id.button_h, 6);
        bindKey(R.id.button_2, 7);
        bindKey(R.id.button_y, 8);
        bindKey(R.id.button_n, 9);
        bindKey(R.id.button_c, 10);
        bindKey(R.id.button_g, 11);

        show(false);
        extend(false, false);
    }

    private void bindKey(int viewId, int keyId) {
        TextView key = buttonpanel_layout.findViewById(viewId);
        key.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            listener._sendButtonPanelKey(keyId);
        });
    }

    public boolean isVisible() {
        return buttonpanel_layout.getVisibility() == View.VISIBLE;
    }

    public void show(boolean z) {
        if (z == isVisible()) return;

        buttonpanel_layout.animate().cancel();

        if (z) {
            buttonpanel_layout.setAlpha(0f);
            buttonpanel_layout.setVisibility(View.VISIBLE);
            buttonpanel_layout.animate().alpha(1f).setDuration(180L).start();
        } else {
            buttonpanel_layout.setAlpha(1f);
            buttonpanel_layout.setVisibility(View.GONE);
        }
    }

    private void extend(boolean ext, boolean animate) {
        extended = ext;

        button_extend.setContentDescription(ext ? "Sembunyikan tombol" : "Tampilkan tombol");
        button_extend.animate().cancel();
        buttons_dock.animate().cancel();

        // chevron menghadap kanan saat tertutup, berputar 180 derajat (menghadap kiri) saat terbuka
        float rotation = ext ? 180f : 0f;
        if (animate) {
            button_extend.animate().rotation(rotation).setDuration(180L).start();
        } else {
            button_extend.setRotation(rotation);
        }

        // dock tumbuh dari sisi handle (kiri), bukan dari tengah
        buttons_dock.setPivotX(0f);
        buttons_dock.setPivotY(buttons_dock.getHeight() / 2f);

        if (ext) {
            buttons_dock.setVisibility(View.VISIBLE);
            if (animate) {
                buttons_dock.setAlpha(0f);
                buttons_dock.setScaleX(DOCK_START_SCALE);
                buttons_dock.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .setDuration(OPEN_MS)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                buttons_dock.setAlpha(1f);
                buttons_dock.setScaleX(1f);
            }
        } else if (animate) {
            buttons_dock.animate()
                    .alpha(0f)
                    .scaleX(DOCK_START_SCALE)
                    .setDuration(CLOSE_MS)
                    .withEndAction(() -> {
                        if (!extended) { // belum dibuka lagi selama animasi
                            buttons_dock.setVisibility(View.GONE);
                        }
                    })
                    .start();
        } else {
            buttons_dock.setVisibility(View.GONE);
        }
    }
}
