package com.floatnotes;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;
import java.util.*;

public class FloatingNoteService extends Service {

    private static final String CHANNEL_ID = "float_notes_channel";
    private static final int NOTIF_ID = 42;

    private WindowManager windowManager;
    private TextView bubbleView;
    private View trashZoneView;
    private List<FloatingNote> activeNotes = new ArrayList<>();
    private int noteCounter = 0;

    private float globalAlpha = 0.92f;
    private float globalTextSize = 15f;

    private int[] noteColors = {
        Color.parseColor("#1A1A2E"), Color.parseColor("#16213E"),
        Color.parseColor("#1F1B33"), Color.parseColor("#0F3460"),
        Color.parseColor("#1B2838"), Color.parseColor("#2C1810"),
    };
    private int[] noteAccents = {
        Color.parseColor("#7B5FFF"), Color.parseColor("#FF5F9E"),
        Color.parseColor("#5FFFC8"), Color.parseColor("#5FA8FF"),
        Color.parseColor("#FFC85F"), Color.parseColor("#E05FFF"),
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        showTrashZone();
        showLauncherBubble();
    }

    // ── Trash Zone ─────────────────────────────────────────────────
    private void showTrashZone() {
        LinearLayout trash = new LinearLayout(this);
        trash.setOrientation(LinearLayout.VERTICAL);
        trash.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(20));
        bg.setColor(Color.argb(210, 50, 0, 0));
        bg.setStroke(dpToPx(2), Color.argb(200, 255, 80, 80));
        trash.setBackground(bg);
        trash.setPadding(dpToPx(18), dpToPx(10), dpToPx(18), dpToPx(10));

        TextView icon = new TextView(this);
        icon.setText("🗑");
        icon.setTextSize(22f);
        icon.setGravity(Gravity.CENTER);
        trash.addView(icon);

        TextView label = new TextView(this);
        label.setText("Drop to Remove");
        label.setTextColor(Color.argb(220, 255, 130, 130));
        label.setTextSize(9f);
        label.setGravity(Gravity.CENTER);
        trash.addView(label);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            dpToPx(140), dpToPx(70),
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dpToPx(20);
        params.alpha = 0f;

        trashZoneView = trash;
        windowManager.addView(trash, params);
    }

    private void setTrashVisible(boolean visible) {
        if (trashZoneView == null) return;
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) trashZoneView.getLayoutParams();
        p.alpha = visible ? 1f : 0f;
        try { windowManager.updateViewLayout(trashZoneView, p); } catch (Exception ignored) {}
    }

    private void setTrashHighlight(boolean highlight) {
        if (trashZoneView == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(20));
        if (highlight) {
            bg.setColor(Color.argb(240, 180, 0, 0));
            bg.setStroke(dpToPx(3), Color.argb(255, 255, 60, 60));
        } else {
            bg.setColor(Color.argb(210, 50, 0, 0));
            bg.setStroke(dpToPx(2), Color.argb(200, 255, 80, 80));
        }
        trashZoneView.setBackground(bg);
    }

    private boolean isOverTrash(float rawX, float rawY) {
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        float left = (sw - dpToPx(140)) / 2f;
        float top = sh - dpToPx(20) - dpToPx(70);
        return rawX >= left && rawX <= left + dpToPx(140)
            && rawY >= top && rawY <= sh - dpToPx(20);
    }

    // ── Launcher Bubble ────────────────────────────────────────────
    private void showLauncherBubble() {
        TextView btn = new TextView(this);
        btn.setText("+");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(28f);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        applyBubbleNormal(btn);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            dpToPx(62), dpToPx(62),
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dpToPx(8);
        params.y = dpToPx(120);

        final int[] startXY = {0, 0};
        final int[] startPos = {0, 0};
        final boolean[] moved = {false};
        final boolean[] onTrash = {false};

        btn.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startXY[0] = (int) e.getRawX();
                    startXY[1] = (int) e.getRawY();
                    startPos[0] = params.x;
                    startPos[1] = params.y;
                    moved[0] = false;
                    onTrash[0] = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int dx = (int) e.getRawX() - startXY[0];
                    int dy = (int) e.getRawY() - startXY[1];
                    if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                        if (!moved[0]) {
                            moved[0] = true;
                            setTrashVisible(true);
                        }
                    }
                    if (moved[0]) {
                        params.x = startPos[0] + dx;
                        params.y = startPos[1] + dy;
                        try { windowManager.updateViewLayout(btn, params); } catch (Exception ignored) {}
                        boolean nowOnTrash = isOverTrash(e.getRawX(), e.getRawY());
                        if (nowOnTrash != onTrash[0]) {
                            onTrash[0] = nowOnTrash;
                            setTrashHighlight(nowOnTrash);
                            if (nowOnTrash) applyBubbleTrash(btn);
                            else applyBubbleNormal(btn);
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    setTrashVisible(false);
                    setTrashHighlight(false);
                    if (moved[0] && isOverTrash(e.getRawX(), e.getRawY())) {
                        // Drag to trash = remove bubble permanently
                        try { windowManager.removeView(btn); } catch (Exception ignored) {}
                        bubbleView = null;
                    } else if (!moved[0]) {
                        // Tap = spawn note
                        spawnNote();
                        applyBubbleNormal(btn);
                    } else {
                        applyBubbleNormal(btn);
                    }
                    return true;
            }
            return false;
        });

        windowManager.addView(btn, params);
        bubbleView = btn;
    }

    private void applyBubbleNormal(TextView btn) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColors(new int[]{Color.parseColor("#7B5FFF"), Color.parseColor("#FF5F9E")});
        d.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        d.setOrientation(GradientDrawable.Orientation.TL_BR);
        btn.setBackground(d);
        btn.setText("+");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(28f);
    }

    private void applyBubbleTrash(TextView btn) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor("#FF2222"));
        d.setStroke(dpToPx(2), Color.WHITE);
        btn.setBackground(d);
        btn.setText("🗑");
        btn.setTextSize(20f);
    }

    // ── Spawn Note ─────────────────────────────────────────────────
    private void spawnNote() {
        noteCounter++;
        int idx = (noteCounter - 1) % noteColors.length;
        FloatingNote note = new FloatingNote(noteCounter, noteColors[idx], noteAccents[idx]);
        activeNotes.add(note);
        note.show();
    }

    // ── FloatingNote ───────────────────────────────────────────────
    private class FloatingNote {
        int id;
        int bgColor, accentColor;
        View rootView;
        EditText bodyEdit;
        ScrollView bodyScroll;
        WindowManager.LayoutParams params;

        TextView teleBtn;
        LinearLayout teleControlRow;
        TextView speedValTv;
        boolean teleOn = false;
        int scrollSpeed = 3;
        Handler scrollHandler = new Handler(Looper.getMainLooper());
        Runnable scrollRunnable;

        FloatingNote(int id, int bgColor, int accentColor) {
            this.id = id;
            this.bgColor = bgColor;
            this.accentColor = accentColor;
        }

        void show() {
            LinearLayout root = new LinearLayout(FloatingNoteService.this);
            root.setOrientation(LinearLayout.VERTICAL);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dpToPx(14));
            bg.setColor(bgColor);
            bg.setStroke(dpToPx(1), Color.argb(70,
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
            root.setBackground(bg);
            root.setElevation(dpToPx(12));

            // ── Header (drag handle) ──
            LinearLayout header = new LinearLayout(FloatingNoteService.this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dpToPx(10), dpToPx(8), dpToPx(8), dpToPx(6));

            View dot = new View(FloatingNoteService.this);
            dot.setBackground(makeCircle(accentColor));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dpToPx(8), dpToPx(8));
            dotLp.setMarginEnd(dpToPx(8));
            dot.setLayoutParams(dotLp);
            header.addView(dot);

            TextView titleTv = new TextView(FloatingNoteService.this);
            titleTv.setText("Note " + id);
            titleTv.setTextColor(Color.argb(180,
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
            titleTv.setTextSize(10f);
            titleTv.setAllCaps(true);
            titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(titleTv);

            // ▶ Teleprompter button
            teleBtn = new TextView(FloatingNoteService.this);
            teleBtn.setText("▶");
            teleBtn.setTextColor(Color.argb(180, 255, 255, 255));
            teleBtn.setTextSize(14f);
            teleBtn.setPadding(dpToPx(6), dpToPx(4), dpToPx(4), dpToPx(4));
            header.addView(teleBtn);

            // ⚙ Settings
            TextView settBtn = new TextView(FloatingNoteService.this);
            settBtn.setText("⚙");
            settBtn.setTextColor(Color.argb(150, 255, 255, 255));
            settBtn.setTextSize(14f);
            settBtn.setPadding(dpToPx(4), dpToPx(4), dpToPx(2), dpToPx(4));
            settBtn.setOnClickListener(v -> showSettingsPanel(this));
            header.addView(settBtn);

            // × Close
            TextView closeBtn = new TextView(FloatingNoteService.this);
            closeBtn.setText("×");
            closeBtn.setTextColor(Color.argb(190, 255, 90, 90));
            closeBtn.setTextSize(22f);
            closeBtn.setPadding(dpToPx(4), 0, dpToPx(6), dpToPx(2));
            closeBtn.setOnClickListener(v -> remove());
            header.addView(closeBtn);

            root.addView(header);

            // Divider
            View div = new View(FloatingNoteService.this);
            div.setBackgroundColor(Color.argb(50,
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
            root.addView(div, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));

            // ── Teleprompter control row (hidden until ▶ tapped) ──
            teleControlRow = new LinearLayout(FloatingNoteService.this);
            teleControlRow.setOrientation(LinearLayout.HORIZONTAL);
            teleControlRow.setGravity(Gravity.CENTER_VERTICAL);
            teleControlRow.setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5));
            teleControlRow.setBackgroundColor(Color.argb(80, 0, 0, 0));
            teleControlRow.setVisibility(View.GONE);

            TextView speedLabel = new TextView(FloatingNoteService.this);
            speedLabel.setText("SPEED");
            speedLabel.setTextColor(Color.argb(140, 255, 255, 255));
            speedLabel.setTextSize(9f);
            speedLabel.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.setMarginEnd(dpToPx(6));
            speedLabel.setLayoutParams(slp);
            teleControlRow.addView(speedLabel);

            TextView speedDown = new TextView(FloatingNoteService.this);
            speedDown.setText("−");
            speedDown.setTextColor(Color.WHITE);
            speedDown.setTextSize(18f);
            speedDown.setPadding(dpToPx(8), 0, dpToPx(8), dpToPx(2));
            teleControlRow.addView(speedDown);

            speedValTv = new TextView(FloatingNoteService.this);
            speedValTv.setText(scrollSpeed + "");
            speedValTv.setTextColor(Color.parseColor("#7B5FFF"));
            speedValTv.setTextSize(13f);
            speedValTv.setTypeface(null, Typeface.BOLD);
            speedValTv.setMinWidth(dpToPx(22));
            speedValTv.setGravity(Gravity.CENTER);
            teleControlRow.addView(speedValTv);

            TextView speedUp = new TextView(FloatingNoteService.this);
            speedUp.setText("+");
            speedUp.setTextColor(Color.WHITE);
            speedUp.setTextSize(18f);
            speedUp.setPadding(dpToPx(8), 0, dpToPx(8), dpToPx(2));
            teleControlRow.addView(speedUp);

            speedDown.setOnClickListener(v -> {
                if (scrollSpeed > 1) { scrollSpeed--; speedValTv.setText(scrollSpeed + ""); }
            });
            speedUp.setOnClickListener(v -> {
                if (scrollSpeed < 10) { scrollSpeed++; speedValTv.setText(scrollSpeed + ""); }
            });

            View spacer = new View(FloatingNoteService.this);
            teleControlRow.addView(spacer, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            TextView liveTv = new TextView(FloatingNoteService.this);
            liveTv.setText("● LIVE");
            liveTv.setTextColor(Color.parseColor("#FF5F9E"));
            liveTv.setTextSize(9f);
            teleControlRow.addView(liveTv);

            root.addView(teleControlRow);

            // ── Body ──
            bodyScroll = new ScrollView(FloatingNoteService.this);
            bodyScroll.setVerticalScrollBarEnabled(false);
            bodyScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

            bodyEdit = new EditText(FloatingNoteService.this);
            bodyEdit.setHint("Tap to type...");
            bodyEdit.setHintTextColor(Color.argb(70, 255, 255, 255));
            bodyEdit.setTextColor(Color.WHITE);
            bodyEdit.setTextSize(globalTextSize);
            bodyEdit.setLineSpacing(dpToPx(2), 1.45f);
            bodyEdit.setBackground(null);
            bodyEdit.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            bodyEdit.setGravity(Gravity.TOP | Gravity.START);
            bodyEdit.setMinHeight(dpToPx(80));
            bodyEdit.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            bodyEdit.setMaxLines(Integer.MAX_VALUE);

            bodyScroll.addView(bodyEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(bodyScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(130)));

            // ── Teleprompter toggle logic ──
            teleBtn.setOnClickListener(v -> {
                teleOn = !teleOn;
                if (teleOn) {
                    teleBtn.setText("⏹");
                    teleBtn.setTextColor(Color.parseColor("#FF5F9E"));
                    teleControlRow.setVisibility(View.VISIBLE);
                    bodyEdit.setFocusable(false);
                    bodyEdit.setFocusableInTouchMode(false);
                    bodyScroll.scrollTo(0, 0);
                    startScroll();
                } else {
                    teleBtn.setText("▶");
                    teleBtn.setTextColor(Color.argb(180, 255, 255, 255));
                    teleControlRow.setVisibility(View.GONE);
                    bodyEdit.setFocusable(true);
                    bodyEdit.setFocusableInTouchMode(true);
                    stopScroll();
                }
            });

            // ── Resize handle ──
            LinearLayout bottomBar = new LinearLayout(FloatingNoteService.this);
            bottomBar.setGravity(Gravity.END);
            TextView resizeH = new TextView(FloatingNoteService.this);
            resizeH.setText("⠿");
            resizeH.setTextColor(Color.argb(70, 255, 255, 255));
            resizeH.setTextSize(15f);
            resizeH.setPadding(dpToPx(10), dpToPx(2), dpToPx(6), dpToPx(4));
            bottomBar.addView(resizeH);
            root.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            params = new WindowManager.LayoutParams(
                dpToPx(240), WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = dpToPx(20 + (id % 4) * 15);
            params.y = dpToPx(60 + (id % 5) * 40);
            params.alpha = globalAlpha;

            rootView = root;
            makeDraggableByHeader(header, root, params);
            makeResizable(resizeH, root, params, bodyScroll);
            windowManager.addView(root, params);
        }

        void startScroll() {
            scrollRunnable = new Runnable() {
                @Override public void run() {
                    if (!teleOn) return;
                    bodyScroll.scrollBy(0, scrollSpeed);
                    int max = bodyScroll.getChildAt(0).getHeight() - bodyScroll.getHeight();
                    if (max > 0 && bodyScroll.getScrollY() >= max) {
                        stopScroll();
                        teleOn = false;
                        teleBtn.setText("▶");
                        teleBtn.setTextColor(Color.argb(180, 255, 255, 255));
                        teleControlRow.setVisibility(View.GONE);
                        bodyEdit.setFocusable(true);
                        bodyEdit.setFocusableInTouchMode(true);
                        return;
                    }
                    scrollHandler.postDelayed(this, 50);
                }
            };
            scrollHandler.post(scrollRunnable);
        }

        void stopScroll() {
            if (scrollRunnable != null) {
                scrollHandler.removeCallbacks(scrollRunnable);
                scrollRunnable = null;
            }
        }

        void updateAlpha(float a) {
            params.alpha = a;
            try { windowManager.updateViewLayout(rootView, params); } catch (Exception ignored) {}
        }

        void updateTextSize(float s) {
            if (bodyEdit != null) bodyEdit.setTextSize(s);
        }

        void remove() {
            stopScroll();
            try { windowManager.removeView(rootView); } catch (Exception ignored) {}
            activeNotes.remove(this);
        }
    }

    private void makeDraggableByHeader(View header, View root, WindowManager.LayoutParams params) {
        final int[] sXY = {0, 0}, sPos = {0, 0};
        header.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sXY[0] = (int) e.getRawX(); sXY[1] = (int) e.getRawY();
                    sPos[0] = params.x; sPos[1] = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = sPos[0] + (int) e.getRawX() - sXY[0];
                    params.y = sPos[1] + (int) e.getRawY() - sXY[1];
                    try { windowManager.updateViewLayout(root, params); } catch (Exception ignored) {}
                    return true;
            }
            return false;
        });
    }

    private void makeResizable(View handle, View root, WindowManager.LayoutParams params, ScrollView sv) {
        final int[] sXY = {0, 0}, sSize = {0, 0};
        final boolean[] resizing = {false};
        handle.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sXY[0] = (int) e.getRawX(); sXY[1] = (int) e.getRawY();
                    sSize[0] = params.width; sSize[1] = sv.getLayoutParams().height;
                    resizing[0] = true; return true;
                case MotionEvent.ACTION_MOVE:
                    if (!resizing[0]) return false;
                    params.width = Math.max(dpToPx(160), Math.min(dpToPx(420),
                        sSize[0] + (int) e.getRawX() - sXY[0]));
                    sv.getLayoutParams().height = Math.max(dpToPx(80), Math.min(dpToPx(500),
                        sSize[1] + (int) e.getRawY() - sXY[1]));
                    sv.requestLayout();
                    try { windowManager.updateViewLayout(root, params); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    resizing[0] = false; return true;
            }
            return false;
        });
    }

    private void showSettingsPanel(FloatingNote note) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(16));

        GradientDrawable pbg = new GradientDrawable();
        pbg.setCornerRadius(dpToPx(16));
        pbg.setColor(Color.parseColor("#0D0D1A"));
        pbg.setStroke(dpToPx(1), Color.argb(100, 123, 95, 255));
        panel.setBackground(pbg);
        panel.setElevation(dpToPx(24));

        TextView title = new TextView(this);
        title.setText("⚙  Note Settings");
        title.setTextColor(Color.parseColor("#7B5FFF"));
        title.setTextSize(13f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(14));
        panel.addView(title);

        panel.addView(makeLabel("Transparency"));
        SeekBar ab = makeSeekBar((int)(globalAlpha * 100), 20, 100);
        TextView av = makeValueLabel((int)(globalAlpha * 100) + "%");
        ab.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                globalAlpha = p / 100f; av.setText(p + "%");
                for (FloatingNote n : activeNotes) n.updateAlpha(globalAlpha);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        panel.addView(makeSliderRow(ab, av));

        panel.addView(makeLabel("Text Size"));
        SeekBar sb = makeSeekBar((int) globalTextSize, 10, 30);
        TextView sv2 = makeValueLabel((int) globalTextSize + "sp");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                globalTextSize = p; sv2.setText(p + "sp");
                for (FloatingNote n : activeNotes) n.updateTextSize(p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        panel.addView(makeSliderRow(sb, sv2));

        panel.addView(makeLabel("Note Width"));
        int cw = (int)(note.params.width / getResources().getDisplayMetrics().density);
        SeekBar wb = makeSeekBar(Math.min(cw, 360), 150, 360);
        TextView wv = makeValueLabel(cw + "dp");
        wb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                note.params.width = dpToPx(p); wv.setText(p + "dp");
                try { windowManager.updateViewLayout(note.rootView, note.params); } catch (Exception ignored) {}
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        panel.addView(makeSliderRow(wb, wv));

        LinearLayout br = new LinearLayout(this);
        br.setOrientation(LinearLayout.HORIZONTAL);
        br.setPadding(0, dpToPx(14), 0, 0);
        br.setWeightSum(2f);
        final View[] pRef = {panel};

        Button done = makeButton("Done", Color.parseColor("#7B5FFF"));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.setMarginEnd(dpToPx(6)); done.setLayoutParams(l1);
        done.setOnClickListener(v -> { try { windowManager.removeView(pRef[0]); } catch (Exception ignored) {} });
        br.addView(done);

        Button del = makeButton("Delete Note", Color.parseColor("#FF4444"));
        del.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        del.setOnClickListener(v -> {
            try { windowManager.removeView(pRef[0]); } catch (Exception ignored) {}
            note.remove();
        });
        br.addView(del);
        panel.addView(br);

        WindowManager.LayoutParams pp = new WindowManager.LayoutParams(
            dpToPx(280), WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        pp.gravity = Gravity.CENTER;
        windowManager.addView(panel, pp);
    }

    private TextView makeLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(Color.argb(150, 255, 255, 255));
        tv.setTextSize(10f); tv.setAllCaps(true); tv.setLetterSpacing(0.08f);
        tv.setPadding(0, dpToPx(8), 0, dpToPx(4)); return tv;
    }
    private TextView makeValueLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(Color.parseColor("#7B5FFF"));
        tv.setTextSize(10f); tv.setTypeface(null, Typeface.BOLD_ITALIC);
        tv.setMinWidth(dpToPx(36)); tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); return tv;
    }
    private SeekBar makeSeekBar(int progress, int min, int max) {
        SeekBar sb = new SeekBar(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sb.setMin(min);
        sb.setMax(max); sb.setProgress(progress);
        sb.getProgressDrawable().setColorFilter(Color.parseColor("#7B5FFF"), PorterDuff.Mode.SRC_IN);
        sb.getThumb().setColorFilter(Color.parseColor("#FF5F9E"), PorterDuff.Mode.SRC_IN);
        return sb;
    }
    private LinearLayout makeSliderRow(SeekBar bar, TextView val) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(bar);
        val.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(val); return row;
    }
    private Button makeButton(String text, int color) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(11f);
        GradientDrawable d = new GradientDrawable(); d.setCornerRadius(dpToPx(10)); d.setColor(color);
        b.setBackground(d); b.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8)); return b;
    }
    private GradientDrawable makeCircle(int color) {
        GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(color); return d;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Float Notes", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Float Notes Active")
            .setContentText("Tap + • ▶ teleprompter • Drag + to trash to hide bubble")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) stopSelf();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (bubbleView != null) windowManager.removeView(bubbleView); } catch (Exception ignored) {}
        try { if (trashZoneView != null) windowManager.removeView(trashZoneView); } catch (Exception ignored) {}
        for (FloatingNote n : new ArrayList<>(activeNotes)) {
            n.stopScroll();
            try { windowManager.removeView(n.rootView); } catch (Exception ignored) {}
        }
        activeNotes.clear();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
}
