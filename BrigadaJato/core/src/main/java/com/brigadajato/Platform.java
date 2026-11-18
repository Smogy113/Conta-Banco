package com.brigadajato;

import com.brigadajato.net.Link;

/** Recursos que dependem da plataforma (Android / PC). */
public interface Platform {
    /** No Android, libera o recebimento de pacotes UDP de broadcast (descoberta de salas). */
    void setMulticastLock(boolean enabled);

    String deviceName();

    boolean isMobile();

    /** Resposta assincrona do Bluetooth. */
    interface BtListener {
        void status(String text);

        /** aparelhos encontrados: pares nome/endereco */
        void devices(String[] names, String[] addresses);
    }

    /** O aparelho tem Bluetooth? */
    boolean bluetoothSupported();

    /** Comeca a aceitar jogadores por Bluetooth (pede permissao/visibilidade se precisar). */
    void bluetoothHost(Link.Sink sink, BtListener listener);

    void bluetoothStopHost();

    /** Lista os aparelhos pareados (e procura outros por perto). */
    void bluetoothScan(BtListener listener);

    /** Canal para conectar num aparelho (abre so quando chamado open()). */
    Link.Factory bluetoothLink(String address, String name);

    Platform DESKTOP = new Platform() {
        @Override
        public void setMulticastLock(boolean enabled) {
        }

        @Override
        public String deviceName() {
            return "PC";
        }

        @Override
        public boolean isMobile() {
            return false;
        }

        @Override
        public boolean bluetoothSupported() {
            return false;
        }

        @Override
        public void bluetoothHost(Link.Sink sink, BtListener listener) {
            listener.status(I18n.t("bt_off"));
        }

        @Override
        public void bluetoothStopHost() {
        }

        @Override
        public void bluetoothScan(BtListener listener) {
            listener.status(I18n.t("bt_off"));
        }

        @Override
        public Link.Factory bluetoothLink(String address, String name) {
            return null;
        }
    };
}
