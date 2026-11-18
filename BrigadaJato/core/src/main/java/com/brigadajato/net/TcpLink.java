package com.brigadajato.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Canal TCP (rede Wi-Fi / cabo / roteador do celular). */
public class TcpLink implements Link {
    private final Socket socket;

    public TcpLink(Socket s) throws IOException {
        this.socket = s;
        s.setTcpNoDelay(true);
    }

    @Override
    public InputStream in() throws IOException {
        return socket.getInputStream();
    }

    @Override
    public OutputStream out() throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public static Link.Factory to(final String host) {
        return new Link.Factory() {
            @Override
            public Link open() throws IOException {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, Net.TCP_PORT), 5000);
                return new TcpLink(s);
            }

            @Override
            public String describe() {
                return host;
            }
        };
    }
}
