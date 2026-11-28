package org.telegram.messenger.ahbsolute;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.widget.Toast;

import static org.telegram.messenger.ahbsolute.AHbsolute.t;

/**
 * AHbsolute's own trick: shake the phone to switch the design (Absolute Design 1 ⇄ Absolute Design 2 ⇄ off).
 * Off by default; listens only while the app is on screen.
 */
public class AHbsoluteShake implements SensorEventListener {

    private static AHbsoluteShake instance;
    private final Context app;
    private long lastShake, firstPeak;
    private int peaks;

    private AHbsoluteShake(Context c) { app = c.getApplicationContext(); }

    public static boolean enabled() { return "1".equals(AHbsoluteUi.getStr("shake_design", "0")); }

    public static void setEnabled(Context c, boolean v) {
        AHbsoluteUi.putStr("shake_design", v ? "1" : "0");
        if (v) resume(c); else pause();
    }

    public static void resume(Context c) {
        try {
            if (!enabled() || c == null) return;
            if (instance == null) instance = new AHbsoluteShake(c);
            SensorManager sm = (SensorManager) instance.app.getSystemService(Context.SENSOR_SERVICE);
            Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (s != null) sm.registerListener(instance, s, SensorManager.SENSOR_DELAY_UI);
        } catch (Throwable ignored) { }
    }

    public static void pause() {
        try {
            if (instance == null) return;
            SensorManager sm = (SensorManager) instance.app.getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) sm.unregisterListener(instance);
        } catch (Throwable ignored) { }
    }

    @Override public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        float g = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
        long now = SystemClock.uptimeMillis();
        if (g < 2.4f) return;
        // three strong peaks within 0.8 s = a deliberate shake
        if (now - firstPeak > 800) { firstPeak = now; peaks = 0; }
        if (++peaks < 3 || now - lastShake < 2500) return;
        lastShake = now;
        peaks = 0;
        int next = (AHbsoluteDesign.variant(app) + 1) % 3;
        String err = AHbsoluteDesign.setVariant(app, next);
        String name = next == 0 ? t("обычный вид", "regular look") : next == 1 ? "Absolute Design 1" : "Absolute Design 2";
        Toast.makeText(app, err == null ? "✨ " + name : err, Toast.LENGTH_SHORT).show();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
