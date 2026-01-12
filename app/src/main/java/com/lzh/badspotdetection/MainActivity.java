package com.lzh.badspotdetection;

import android.app.Service;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final int[] colors = new int[]{
            Color.BLACK,
            Color.WHITE,
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW,
            Color.MAGENTA,
            Color.CYAN
    };

    private int colorIndex = 0;

    // 定义系统手势边缘区（调出状态栏和导航栏不触发手势）
    private static final int EDGE_GESTURE_UP = 24;
    private int edgeSizePx;
    private float icon_alpha;

    // 主视图
    private View colorView;
    // 振动器
    private Vibrator myVibrator;
    private boolean isLocked = false;
    private boolean isIconVisible = true;
    private ImageView lockView;

    private ImageView hintView;

    private static List<ImageView> iconList;

    // 通过Looper.getMainLooper()获取主线程的消息循环器
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hintRunnable;
    private Runnable hideIconRunnable;
    private Runnable showIconRunnable;
    // 图标显示5s
//    private static final int ICON_DURATION = 5000;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        edgeSizePx = (int) (EDGE_GESTURE_UP * getResources().getDisplayMetrics().density);
        TypedValue tmpValue = new TypedValue();
        getResources().getValue(R.dimen.icon_alpha, tmpValue, true);
        icon_alpha = tmpValue.getFloat();
        // 屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 亮度调到最大
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = 1.0f;
        getWindow().setAttributes(params);
        // 创建纯色View
        colorView = findViewById(R.id.color_view);
        // 锁图
        lockView = findViewById(R.id.lock_view);
        hintView = findViewById(R.id.hint_view);

        iconList = List.of(lockView, hintView);

        myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);

        // 触摸检测
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            // 滑动后触发
            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                int screenHeight = colorView.getHeight();
                // 忽略顶部或底部边缘开始的滑动
                assert e1 != null;
                if (e1.getY() < edgeSizePx || e1.getY() > screenHeight - edgeSizePx) {
                    return true;
                }

                if (isLocked) return true;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                // 只处理水平滑动
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                    // 右滑上一个，左滑下一个（翻页顺序）
                    if (diffX > 0) {
                        colorIndex = (colorIndex - 1 + colors.length) % colors.length;
                    } else {
                        colorIndex = (colorIndex + 1) % colors.length;
                    }
                    colorView.setBackgroundColor(colors[colorIndex]);
                    updateLockIcon();
                    updateHintIcon();
                    return true;
                }
                // 垂直滑动触发手机震动和提示
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    myVibrator.vibrate(new long[]{200, 200}, -1);
                    hint();
                    return true;
                }
                return false;
            }
        });
        // 点击切换下一个颜色
        colorView.setOnClickListener(this);
        colorView.setOnTouchListener((v, event) -> {
            // 触摸监听
            boolean consumed = gestureDetector.onTouchEvent(event);
            if (!consumed && event.getAction() == MotionEvent.ACTION_UP) {
                // 没滑动那就是点击
                v.performClick();
            }
            return true;
        });
        // 切换锁状态
        lockView.setOnClickListener(this);
        // 提示图标
        hintView.setOnClickListener(this);

        // Snackbar提示框
        Snackbar snackbar = Snackbar.make(colorView, "右滑上一个，左滑下一个，点击显示/隐藏图标", Snackbar.LENGTH_SHORT);
        snackbar.setDuration(1500);
        hintRunnable = snackbar::show;

        hideIconRunnable = () -> iconList.forEach(icon -> icon.animate()
                .alpha(0f)
                .setDuration(500)
                .start()
        );
        showIconRunnable = () -> iconList.forEach(icon -> icon.animate()
                .alpha(icon_alpha)
                .setDuration(500)
                .start()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 设置沉浸模式显示
        enableImmersiveMode();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.color_view) {
            if (isIconVisible) {
                hideIcons();
            } else {
                showIcons();
            }
        } else if (v.getId() == R.id.lock_view) {
            isLocked = !isLocked;
            updateLockIcon();
        } else if (v.getId() == R.id.hint_view) {
            hint();
        }
    }

    // 切换锁图标
    private void updateLockIcon() {
        boolean isBlack = ((ColorDrawable) colorView.getBackground()).getColor() == Color.BLACK;
        if (isLocked) {
            lockView.setImageResource(isBlack ? R.drawable.iconmonstr_lock_close_white : R.drawable.iconmonstr_lock_close_black);
        } else {
            lockView.setImageResource(isBlack ? R.drawable.iconmonstr_lock_open_white : R.drawable.iconmonstr_lock_open_black);
        }
    }

    // 切换提示图标
    private void updateHintIcon() {
        boolean isBlack = ((ColorDrawable) colorView.getBackground()).getColor() == Color.BLACK;
        hintView.setImageResource(isBlack ? R.drawable.baseline_question_mark_white : R.drawable.baseline_question_mark_black);
    }

    private void hideIcons() {
        isIconVisible = false;
        handler.removeCallbacks(hideIconRunnable);
        handler.post(hideIconRunnable);
    }

    private void showIcons() {
        isIconVisible = true;
        handler.removeCallbacks(showIconRunnable);
        handler.post(showIconRunnable);
    }

    private void enableImmersiveMode() {
        // 沉浸模式，全屏，隐藏状态栏和导航栏
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private void hint() {
        handler.removeCallbacks(hintRunnable);
        handler.post(hintRunnable);
    }
}