package com.brigadajato.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Um canal de bytes ate outro aparelho (TCP no Wi-Fi ou RFCOMM no Bluetooth). */
public interface Link {
    InputStream in() throws IOException;

    OutputStream out() throws IOException;

    void close();

    /** Abre um canal novo (usado para conectar e reconectar). */
    interface Factory {
        Link open() throws IOException;

        /** Texto curto mostrado na tela (ip ou nome do aparelho). */
        String describe();
    }

    /** Recebe canais novos de quem quer entrar na sala (Bluetooth). */
    interface Sink {
        void accept(Link link);
    }
}
