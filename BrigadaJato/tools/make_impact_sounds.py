#!/usr/bin/env python3
"""Sons de impacto (levar tiro, acertar, tiro na cabeca, abate) a partir de gravacoes reais CC0
do pacote "Impact Sounds" da Kenney (ver tools/sound_sources/CREDITS.md).

- hit1..hit3: bala acertando o corpo de alguem (soco abafado + estalo + peso grave)
- hurt:       VOCE levando tiro (mais grave e abafado, como se sentisse no corpo)
- hitmarker:  confirmacao seca de que seu tiro acertou ("tec")
- hithead:    acerto na cabeca (estalo metalico curto, "tink")
- killhit:    impacto do abate (baque fundo)
"""
import os
import sys
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from make_kill_sounds import SR, noise, lp, hp, sweep, env, place, save  # noqa: E402

SRC = os.path.join(os.path.dirname(__file__), "sound_sources", "kenney")


def load(name):
    with wave.open(os.path.join(SRC, "impact-" + name + ".wav")) as w:
        x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
        sr = w.getframerate()
    if sr != SR:
        x = np.interp(np.arange(0, len(x), sr / SR), np.arange(len(x)), x)
    i = int(np.argmax(np.abs(x) > 0.05 * np.max(np.abs(x))))
    x = x[max(0, i - int(0.001 * SR)):]
    return x / (np.max(np.abs(x)) or 1)


def pitch(x, f):
    n = int(len(x) / f)
    return np.interp(np.arange(n) * f, np.arange(len(x)), x)


def trim(x, d, fade=0.05):
    n = min(len(x), int(d * SR))
    y = x[:n].copy()
    fn = min(n, int(fade * SR))
    y[n - fn:] *= np.linspace(1, 0, fn) ** 2
    return y


def thump(f0, f1, d, gain):
    return np.tanh(sweep(f0, f1, d, 30) * env(d, 0.001, 7 / d) * 2.4) * gain


def splat(d, gain):
    """estalo umido curto: o "tchak" da bala entrando"""
    return lp(hp(noise(d), 3), 2) * env(d, 0.0004, 3 / d * 6) * gain


def mixdown(total, layers):
    buf = np.zeros(int(SR * total))
    for x, at, g in layers:
        place(buf, x, at, g)
    return buf


def main():
    # bala no corpo de outro: tres variacoes para nao repetir
    variants = [("punch-medium-000", 1.05, "soft-medium-001"), ("punch-medium-001", 0.95, "soft-medium-004"),
                ("punch-medium-003", 1.12, "soft-medium-001")]
    for i, (p, f, s) in enumerate(variants, 1):
        body = trim(pitch(load(p), f), 0.2)
        soft = trim(load(s), 0.12)
        snd = mixdown(0.24, [(body, 0, 1.0), (soft, 0.002, 0.7), (splat(0.03, 0.55), 0, 1.0),
                                    (thump(140, 60, 0.07, 0.6), 0, 1.0)])
        save("hit%d" % i, snd, 0.9)
        if i == 1:
            save("hit", snd, 0.9)  # nome antigo, usado por outros efeitos
    # voce levando tiro: grave, abafado e "dentro" do corpo
    hurt = mixdown(0.34, [(trim(lp(load("soft-heavy-001"), 2), 0.3), 0, 1.0),
                          (trim(pitch(load("punch-heavy-002"), 0.9), 0.26), 0.003, 0.8),
                          (thump(110, 42, 0.16, 0.9), 0, 1.0), (splat(0.025, 0.3), 0, 1.0)])
    save("hurt", hurt, 0.95)
    # confirmacao de acerto: "tec" seco e curto
    tick = trim(hp(pitch(load("plate-light-000"), 1.6), 2), 0.055, 0.03)
    save("hitmarker", mixdown(0.06, [(tick, 0, 1.0), (splat(0.012, 0.6), 0, 1.0),
                                     (thump(320, 180, 0.03, 0.35), 0, 1.0)]), 0.6)
    # tiro na cabeca: "tink" metalico + estalo
    d = 0.22
    t = np.arange(int(SR * d)) / SR
    ping = np.sin(2 * np.pi * 2650 * t) * np.exp(-t * 26) * 0.5 + np.sin(2 * np.pi * 3980 * t) * np.exp(-t * 40) * 0.25
    plate = trim(pitch(load("plate-light-002"), 1.2), 0.2)
    save("hithead", mixdown(0.24, [(plate, 0, 0.9), (ping, 0.002, 1.0), (splat(0.02, 0.7), 0, 1.0),
                                   (thump(200, 90, 0.05, 0.5), 0, 1.0)]), 0.8)
    # abate: baque fundo
    kill = mixdown(0.5, [(trim(pitch(load("punch-heavy-000"), 0.85), 0.42), 0, 1.0),
                         (trim(load("soft-heavy-001"), 0.4), 0.004, 0.8),
                         (thump(95, 34, 0.26, 1.0), 0, 1.0), (splat(0.04, 0.5), 0, 1.0)])
    save("killhit", kill, 0.95)
    print("ok: hit1 hit2 hit3 hurt hitmarker hithead killhit")


if __name__ == "__main__":
    main()
