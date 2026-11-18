package com.brigadajato.android;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.Platform;
import com.brigadajato.net.Link;

/** Ponto de entrada no Android. */
public class AndroidLauncher extends AndroidApplication implements Platform {

    private WifiManager.MulticastLock multicastLock;
    private BluetoothNet bt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGyroscope = false;
        config.numSamples = 0;
        bt = new BluetoothNet(this);
        initialize(new BrigadaJatoGame(this), config);
    }

    @Override
    public void setMulticastLock(boolean enabled) {
        try {
            if (enabled) {
                if (multicastLock == null) {
                    WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wifi == null) return;
                    multicastLock = wifi.createMulticastLock("brigadajato");
                    multicastLock.setReferenceCounted(false);
                }
                multicastLock.acquire();
            } else if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public String deviceName() {
        String model = Build.MODEL;
        return model == null ? "Android" : model;
    }

    @Override
    public boolean isMobile() {
        return true;
    }

    @Override
    public boolean bluetoothSupported() {
        return bt != null && bt.supported();
    }

    @Override
    public void bluetoothHost(Link.Sink sink, BtListener listener) {
        bt.host(sink, listener);
    }

    @Override
    public void bluetoothStopHost() {
        if (bt != null) bt.stopHost();
    }

    @Override
    public void bluetoothScan(BtListener listener) {
        bt.scan(listener);
    }

    @Override
    public Link.Factory bluetoothLink(String address, String name) {
        return bt.link(address, name);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (bt != null) bt.onPermissionResult(requestCode, grantResults);
    }

    @Override
    protected void onDestroy() {
        if (bt != null) bt.stopHost();
        setMulticastLock(false);
        super.onDestroy();
    }
}
