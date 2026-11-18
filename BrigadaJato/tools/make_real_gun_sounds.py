#!/usr/bin/env python3
"""Sons das armas a partir de GRAVACOES REAIS de tiro (CC0, ver tools/sound_sources/CREDITS.md).

Cada arma usa uma gravacao de disparo de verdade, ajustada: tom (mais grave = arma maior), corte
da cauda (automaticas curtas para nao embolar), um soco grave por baixo para dar peso no celular,
o mecanismo (ferrolho / bomba) quando faz sentido e eco do ambiente nas armas grandes.
"""
import os
import sys
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from make_kill_sounds import SR, noise, lp, hp, sweep, env, place, echo, save  # noqa: E402
from make_gun_sounds import bandish  # noqa: E402

SRC = os.path.join(os.path.dirname(__file__), "sound_sources")


def load(name):
    with wave.open(os.path.join(SRC, name + ".wav")) as w:
        x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
        if w.getnchannels() == 2:
            x = x.reshape(-1, 2).mean(axis=1)
        sr = w.getframerate()
    if sr != SR:
        x = np.interp(np.arange(0, len(x), sr / SR), np.arange(len(x)), x)
    # comeca no ataque (tira silencio inicial)
    i = int(np.argmax(np.abs(x) > 0.05 * np.max(np.abs(x))))
    x = x[max(0, i - int(0.002 * SR)):]
    return x / (np.max(np.abs(x)) or 1)


def pitch(x, f):
    """f > 1 mais agudo e curto, f < 1 mais grave e longo"""
    n = int(len(x) / f)
    return np.interp(np.arange(n) * f, np.arange(len(x)), x)


def trim(x, d, fade=0.06):
    n = min(len(x), int(d * SR))
    y = x[:n].copy()
    fn = min(n, int(fade * SR))
    y[n - fn:] *= np.linspace(1, 0, fn) ** 2
    return y


def thump(f0, f1, d, gain):
    """soco grave (o peso do tiro)"""
    return np.tanh(sweep(f0, f1, d, 30) * env(d, 0.0015, 6 / d) * 2.2) * gain


def crack(gain):
    d = 0.012
    return hp(noise(d), 2) * env(d, 0.0003, 300) * gain


def build(total, layers):
    buf = np.zeros(int(SR * total))
    for x, at, g in layers:
        place(buf, x * g, at)
    return buf


def shot(src, p, length, sub, extra=(), tail=None, warmth=0.0, dry=()):
    """dry = camadas sem eco (mecanismo da arma, que fica perto do ouvido)"""
    base = trim(pitch(load(src), p), length)
    if warmth > 0:  # engorda o meio-grave da gravacao
        base = base * (1 - warmth) + lp(base, 6) * warmth * 2.2
    layers = [(base, 0, 1.0), (crack(0.35), 0, 1.0)]
    if sub:
        layers.append((thump(*sub), 0.001, 1.0))
    layers += list(extra)
    total = max(length, max((at + len(x) / SR for x, at, g in layers), default=length))
    buf = build(total + 0.02, layers)
    if tail:
        buf = echo(buf, *tail)
    for x, at, g in dry:
        need = int((at + len(x) / SR) * SR) + 1
        if need > len(buf):
            buf = np.concatenate([buf, np.zeros(need - len(buf))])
        place(buf, x * g, at)
    return buf


def mech(kind):
    r = load("sp_reload")
    half = int(len(r) * 0.55)
    part = r[:half] if kind == "bolt" else r[half:]
    i = int(np.argmax(np.abs(part) > 0.1))
    return trim(part[i:], 0.3, 0.05)


def main():
    # pistola: estalo seco e curto
    save("pistol", shot("sp_gun-5", 1.06, 0.40, (230, 60, 0.16, 0.55), warmth=0.15))
    # magnum: mais grave, cauda longa e soco forte
    save("magnum", shot("sp_gun-2", 0.93, 0.85, (170, 40, 0.34, 0.9), warmth=0.25, tail=(0.11, 3, 0.3)))
    # submetralhadora: curta e um pouco mais aguda para os tiros nao embolarem
    save("smg", shot("sp_gun-3", 1.18, 0.21, (260, 70, 0.1, 0.4)))
    # fuzil: disparo cheio com cauda media
    save("rifle", shot("sp_gun-1", 1.0, 0.46, (200, 50, 0.2, 0.65), warmth=0.2))
    # metralhadora: grave e curta (atira muito rapido)
    save("lmg", shot("sp_gun-4", 0.92, 0.30, (180, 45, 0.22, 0.75), warmth=0.2))
    # escopeta: duas gravacoes graves juntas + bomba (tchac-tchac)
    sg2 = trim(pitch(load("sp_gun-2"), 0.84), 0.8)
    save("shotgun", shot("sp_gun-4", 0.78, 0.95, (140, 35, 0.4, 1.0), warmth=0.3,
                         extra=[(sg2, 0.002, 0.55)], dry=[(mech("pump"), 0.42, 0.5)], tail=(0.13, 2, 0.28)))
    # sniper: estrondo grave com eco longo de vale + ferrolho
    save("sniper", shot("sp_gun-2", 0.8, 1.3, (130, 30, 0.5, 1.0), warmth=0.3,
                        dry=[(mech("bolt"), 0.62, 0.45)], tail=(0.19, 4, 0.36)))
    # bazuca / lanca-granadas: disparo bem grave + chiado do foguete
    d = 1.0
    whoosh = bandish(noise(d), 3, 10) * env(d, 0.06, 3) * 0.5
    save("rocket", shot("sp_gun-4", 0.6, 0.6, (120, 30, 0.45, 1.0), warmth=0.35, extra=[(whoosh, 0.02, 1.0)], tail=(0.12, 2, 0.3)))
    # recarga e arma vazia: o mecanismo de verdade
    save("reload", trim(load("sp_reload"), 0.9, 0.05), peak=0.8)
    save("empty", trim(load("sp_no-ammo"), 0.2, 0.03), peak=0.7)


if __name__ == "__main__":
    main()
