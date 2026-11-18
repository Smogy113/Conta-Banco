package com.brigadajato.net;

import com.brigadajato.world.Look;
import com.brigadajato.world.PlayerInput;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Cliente: conecta no anfitriao, envia comandos e recebe o estado do jogo. */
public class NetClient {
    public enum State {CONNECTING, CONNECTED, FAILED, CLOSED}

    public volatile State state = State.CONNECTING;
    public volatile String error;
    private volatile Link link;
    private final ConcurrentLinkedQueue<Net.Packet> inbox = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<byte[]> sendQ = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    public final String host;
    public final Link.Factory factory;

    public NetClient(final String host, final String name, final Look look) {
        this(TcpLink.to(host), name, look, "", 0L);
    }

    public NetClient(final Link.Factory factory, final String name, final Look look, final String password, final long token) {
        this.factory = factory;
        this.host = factory.describe();
        Thread t = new Thread(() -> run(name, look, password, token), "bj-client");
        t.setDaemon(true);
        t.start();
    }

    private void run(String name, Look look, String password, long token) {
        try {
            link = factory.open();
            if (!running) {
                link.close();
                return;
            }
            DataInputStream in = new DataInputStream(new BufferedInputStream(link.in()));
            final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(link.out(), 8192));
            Thread writer = new Thread(() -> {
                try {
                    while (running) {
                        byte[] m = sendQ.poll(500, TimeUnit.MILLISECONDS);
                        if (m == null) continue;
                        Net.writeFrame(out, m);
                        byte[] more;
                        while ((more = sendQ.poll()) != null) Net.writeFrame(out, more);
                        out.flush();
                    }
                } catch (Exception e) {
                    fail(e);
                }
            }, "bj-client-write");
            writer.setDaemon(true);
            writer.start();
            Net.Writer hello = new Net.Writer(Net.C_HELLO);
            hello.out.writeInt(Net.PROTOCOL);
            hello.out.writeUTF(name);
            Net.writeLook(hello.out, look);
            hello.out.writeUTF(password == null ? "" : password);
            hello.out.writeLong(token);
            sendQ.add(hello.build());
            state = State.CONNECTED;
            while (running) {
                byte[] frame = Net.readFrame(in);
                byte[] body = new byte[frame.length - 1];
                System.arraycopy(frame, 1, body, 0, body.length);
                inbox.add(new Net.Packet(frame[0], body, 0));
            }
        } catch (IOException e) {
            fail(e);
        }
    }

    private void fail(Exception e) {
        if (!running) return;
        error = e.getMessage();
        state = state == State.CONNECTING ? State.FAILED : State.CLOSED;
        running = false;
        Link l = link;
        if (l != null) l.close();
    }

    public Net.Packet poll() {
        return inbox.poll();
    }

    public void sendInput(PlayerInput in) {
        if (state != State.CONNECTED) return;
        try {
            Net.Writer w = new Net.Writer(Net.C_INPUT);
            w.out.writeFloat(in.moveX);
            w.out.writeFloat(in.moveY);
            w.out.writeFloat(in.aim);
            w.out.writeByte((in.fire ? 1 : 0) | (in.use ? 2 : 0));
            w.out.writeShort(in.presses);
            w.out.writeShort(in.buy);
            w.out.writeByte(in.emote);
            if (sendQ.size() < 60) sendQ.add(w.build());
        } catch (IOException ignored) {
        }
    }

    public void send(byte[] msg) {
        if (state == State.CLOSED || state == State.FAILED) return;
        sendQ.add(msg);
    }

    public void requestTeam(int team) {
        try {
            Net.Writer w = new Net.Writer(Net.C_TEAM);
            w.out.writeByte(team);
            sendQ.add(w.build());
        } catch (IOException ignored) {
        }
    }

    public void vote(int option) {
        try {
            Net.Writer w = new Net.Writer(Net.C_VOTE);
            w.out.writeByte(option);
            sendQ.add(w.build());
        } catch (IOException ignored) {
        }
    }

    public void close() {
        running = false;
        state = State.CLOSED;
        Link l = link;
        if (l != null) l.close();
    }
}
