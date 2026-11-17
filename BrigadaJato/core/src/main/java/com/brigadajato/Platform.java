package com.brigadajato;

/** Recursos que dependem da plataforma (Android / PC). */
public interface Platform {
    /** No Android, libera o recebimento de pacotes UDP de broadcast (descoberta de salas). */
    void setMulticastLock(boolean enabled);

    String deviceName();

    boolean isMobile();

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
    };
}
