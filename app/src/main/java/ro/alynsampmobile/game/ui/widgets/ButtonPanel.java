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
 * Panel tombol in-game: blok keycap kaca 4x3 yang padat.
 *
 *   [handle] [ESC] [TAB] [ALT]
 *   [ SPC  ] [ F ] [ H ] [ 2 ]
 *   [  Y   ] [ N ] [ C ] [ G ]
 *
 * Tertutup: hanya handle. Terbuka: seluruh blok muncul dengan animasi singkat (baris atas lalu dua baris
 * bawah menyusul), ikon handle berganti dari grid ke silang. Tiap tombol memberi getar ringan
 * (mengikuti pengaturan sistem). ID view dan kode tombol (1..11) tidak berubah, jadi UI.java dan
 * JNI sendButtonPanelKey tidak perlu diubah.
 */
@Obfuscate
public class ButtonPanel {
    private static final long OPEN_MS = 190L;
    private static final long CLOSE_MS = 130L;
    private static final long STAGGER_MS = 45L;
    private static final float START_SCALE = 0.88f;

    private boolean extended;
    private final ConstraintLayout buttonpanel_layout;
    private final View buttons_row_top;
    private final View buttons_rows_rest;
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

        buttons_row_top = buttonpanel_layout.findViewById(R.id.buttons_row_top);
        buttons_rows_rest = buttonpanel_layout.findViewById(R.id.buttons_rows_rest);
        button_extend = buttonpanel_layout.findViewById(R.id.button_extend);

        button_extend.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            extend(!extended, true);
        });

        // kode tombol dikirim ke native (sendButtonPanelKey) - sama seperti sebelumnya
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
        swapHandleIcon(ext ? R.drawable.ic_panel_close : R.drawable.ic_panel_apps, animate);

        animateGroup(buttons_row_top, ext, animate, 0L);
        animateGroup(buttons_rows_rest, ext, animate, STAGGER_MS);
    }

    /** Ikon handle berganti dengan efek "pop" singkat. */
    private void swapHandleIcon(int resId, boolean animate) {
        button_extend.animate().cancel();
        if (!animate) {
            button_extend.setImageResource(resId);
            button_extend.setScaleX(1f);
            button_extend.setScaleY(1f);
            return;
        }
        button_extend.animate()
                .scaleX(0.78f).scaleY(0.78f)
                .setDuration(70L)
                .withEndAction(() -> {
                    button_extend.setImageResource(resId);
                    button_extend.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
                })
                .start();
    }

    /** Memunculkan / menyembunyikan satu kelompok tombol; tumbuh dari sudut kiri-atas (sisi handle). */
    private void animateGroup(View group, boolean show, boolean animate, long delay) {
        group.animate().cancel();
        group.setPivotX(0f);
        group.setPivotY(0f);

        if (show) {
            group.setVisibility(View.VISIBLE);
            if (animate) {
                group.setAlpha(0f);
                group.setScaleX(START_SCALE);
                group.setScaleY(START_SCALE);
                group.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay(delay)
                        .setDuration(OPEN_MS)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                group.setAlpha(1f);
                group.setScaleX(1f);
                group.setScaleY(1f);
            }
        } else if (animate) {
            group.animate()
                    .alpha(0f).scaleX(START_SCALE).scaleY(START_SCALE)
                    .setStartDelay(0L)
                    .setDuration(CLOSE_MS)
                    .withEndAction(() -> {
                        if (!extended) { // belum dibuka lagi selama animasi
                            group.setVisibility(View.GONE);
                        }
                    })
                    .start();
        } else {
            group.setVisibility(View.GONE);
        }
    }
}
