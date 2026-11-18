package com.brigadajato.android;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;

import com.brigadajato.I18n;
import com.brigadajato.Platform;
import com.brigadajato.net.Link;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Multiplayer por Bluetooth classico (RFCOMM). O anfitriao escuta num servico com UUID fixo; os
 * outros celulares (ja pareados) conectam nele. Os dados passam pelo mesmo protocolo do Wi-Fi.
 */
class BluetoothNet {
    static final UUID SERVICE = UUID.fromString("6b1f3c2e-5a4d-4c8b-9a77-b7a1d2e3f405");
    private static final int REQ = 4711;
    private final Activity act;
    private volatile BluetoothServerSocket server;
    private volatile boolean hosting;
    private Runnable afterPermission;

    BluetoothNet(Activity act) {
        this.act = act;
    }

    private BluetoothAdapter adapter() {
        try {
            return BluetoothAdapter.getDefaultAdapter();
        } catch (Exception e) {
            return null;
        }
    }

    boolean supported() {
        return adapter() != null;
    }

    /** Android 12+ pede a permissao BLUETOOTH_CONNECT em tempo de execucao. */
    private boolean ensurePermission(Runnable then) {
        if (Build.VERSION.SDK_INT < 31) return true;
        if (act.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) return true;
        afterPermission = then;
        act.runOnUiThread(() -> act.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ));
        return false;
    }

    void onPermissionResult(int requestCode, int[] results) {
        if (requestCode != REQ) return;
        Runnable r = afterPermission;
        afterPermission = null;
        if (r != null && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) new Thread(r).start();
    }

    private static class BtLink implements Link {
        final BluetoothSocket s;

        BtLink(BluetoothSocket s) {
            this.s = s;
        }

        @Override
        public InputStream in() throws IOException {
            return s.getInputStream();
        }

        @Override
        public OutputStream out() throws IOException {
            return s.getOutputStream();
        }

        @Override
        public void close() {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    void host(final Link.Sink sink, final Platform.BtListener l) {
        final BluetoothAdapter a = adapter();
        if (a == null) {
            l.status(I18n.t("bt_off"));
            return;
        }
        if (!ensurePermission(() -> host(sink, l))) {
            l.status(I18n.pt ? "Permita o Bluetooth e toque de novo" : "Allow Bluetooth and tap again");
            return;
        }
        try {
            if (!a.isEnabled()) {
                l.status(I18n.pt ? "Ligue o Bluetooth do celular" : "Turn on Bluetooth");
                return;
            }
        } catch (SecurityException e) {
            l.status(I18n.t("bt_off"));
            return;
        }
        if (hosting) {
            l.status(I18n.pt ? "Sala aberta no Bluetooth" : "Bluetooth room open");
            return;
        }
        hosting = true;
        Thread t = new Thread(() -> {
            try {
                server = a.listenUsingInsecureRfcommWithServiceRecord("BrigadaJato", SERVICE);
                l.status(I18n.pt ? "Sala aberta no Bluetooth: " + safeName(a) : "Bluetooth room open: " + safeName(a));
                while (hosting) {
                    BluetoothSocket s = server.accept();
                    if (s != null) sink.accept(new BtLink(s));
                }
            } catch (Exception e) {
                if (hosting) l.status(I18n.t("bt_off") + " (" + e.getMessage() + ")");
            }
            hosting = false;
        }, "bj-bt-accept");
        t.setDaemon(true);
        t.start();
    }

    private static String safeName(BluetoothAdapter a) {
        try {
            return a.getName();
        } catch (SecurityException e) {
            return "?";
        }
    }

    void stopHost() {
        hosting = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        server = null;
    }

    void scan(final Platform.BtListener l) {
        final BluetoothAdapter a = adapter();
        if (a == null) {
            l.status(I18n.t("bt_off"));
            return;
        }
        if (!ensurePermission(() -> scan(l))) {
            l.status(I18n.pt ? "Permita o Bluetooth" : "Allow Bluetooth");
            return;
        }
        try {
            Set<BluetoothDevice> bonded = a.getBondedDevices();
            List<String> names = new ArrayList<>(), addrs = new ArrayList<>();
            if (bonded != null) for (BluetoothDevice d : bonded) {
                names.add(d.getName() == null ? "?" : d.getName());
                addrs.add(d.getAddress());
            }
            l.devices(names.toArray(new String[0]), addrs.toArray(new String[0]));
            l.status(names.isEmpty() ? I18n.t("bt_hint") : (I18n.pt ? "Escolha o celular que criou a sala" : "Pick the phone hosting the room"));
        } catch (SecurityException e) {
            l.status(I18n.t("bt_off"));
        }
    }

    Link.Factory link(final String address, final String name) {
        final BluetoothAdapter a = adapter();
        if (a == null) return null;
        return new Link.Factory() {
            @Override
            public Link open() throws IOException {
                try {
                    BluetoothDevice d = a.getRemoteDevice(address);
                    try {
                        a.cancelDiscovery();
                    } catch (SecurityException ignored) {
                    }
                    BluetoothSocket s = d.createInsecureRfcommSocketToServiceRecord(SERVICE);
                    s.connect();
                    return new BtLink(s);
                } catch (SecurityException e) {
                    throw new IOException("bluetooth: " + e.getMessage());
                }
            }

            @Override
            public String describe() {
                return name;
            }
        };
    }
}
