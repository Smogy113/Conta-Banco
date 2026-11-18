package com.brigadajato.render;

/** Cache de textos de numeros (evita criar Strings a cada quadro). */
public final class Num {
    private Num() {
    }

    private static final int MIN = -999, MAX = 99999;
    private static final String[] CACHE = new String[MAX - MIN + 1];

    public static String of(int n) {
        if (n < MIN || n > MAX) return String.valueOf(n);
        String s = CACHE[n - MIN];
        if (s == null) {
            s = String.valueOf(n);
            CACHE[n - MIN] = s;
        }
        return s;
    }

    /** "mag/reserva" com cache de um valor. */
    public static final class Pair {
        private int a = Integer.MIN_VALUE, b;
        private String text = "";
        private final String sep;

        public Pair(String sep) {
            this.sep = sep;
        }

        public String of(int a, int b) {
            if (a != this.a || b != this.b) {
                this.a = a;
                this.b = b;
                text = a + sep + b;
            }
            return text;
        }
    }

    /** Relogio m:ss com cache. */
    public static final class Clock {
        private int last = Integer.MIN_VALUE;
        private String text = "";

        public String of(float seconds) {
            int t = (int) Math.ceil(Math.max(0, seconds));
            if (t != last) {
                last = t;
                text = (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60);
            }
            return text;
        }
    }
}
