#!/usr/bin/env python3
"""Sons do banner de abates em sequencia (kill1..kill5) e do tiro na cabeca.

Camadas por nivel: soco (bumbo saturado), estalo, 'shing' metalico, acorde de supersaw com
filtro, grave caindo, explosao, brilhos, laser subindo e eco. Cada nivel sobe o tom e
acrescenta camadas; o nivel 5 vira uma explosao com coro. 44.1 kHz para ter brilho.
"""
import os
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "sfx")
rng = np.random.default_rng(777)


def t(d):
    return np.arange(int(SR * d)) / SR


def noise(d):
    return rng.uniform(-1, 1, int(SR * d))


def lp(x, k):
    if k <= 1:
        return x
    return np.convolve(x, np.ones(k) / k, mode="same")


def hp(x, k):
    return x - lp(x, k)


def sweep(f0, f1, d, curve=8.0):
    tt = t(d)
    f = f1 + (f0 - f1) * np.exp(-tt * curve)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def lin_sweep(f0, f1, d):
    tt = t(d)
    f = f0 + (f1 - f0) * tt / d
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def env(d, attack, decay):
    tt = t(d)
    return np.minimum(1, tt / max(attack, 1e-4)) * np.exp(-np.maximum(0, tt - attack) * decay)


def saw(f, d, phase=0.0):
    tt = t(d)
    return 2 * ((tt * f + phase) % 1) - 1


def supersaw(f, d, detune=0.008, voices=5):
    out = np.zeros(int(SR * d))
    for v in range(voices):
        k = (v - (voices - 1) / 2) / ((voices - 1) / 2)
        out += saw(f * (1 + k * detune), d, rng.random())
    return out / voices


def fm(f, d, ratio, index, decay):
    tt = t(d)
    m = np.sin(2 * np.pi * f * ratio * tt) * index * np.exp(-tt * decay * 1.7)
    return np.sin(2 * np.pi * f * tt + m) * np.exp(-tt * decay)


def place(buf, x, at, gain=1.0):
    s = int(SR * at)
    e = min(len(buf), s + len(x))
    if e > s:
        buf[s:e] += x[: e - s] * gain


def echo(x, delay=0.115, taps=3, fb=0.38):
    out = np.concatenate([x, np.zeros(int(SR * (delay * taps + 0.05)))])
    src = lp(x, 6)
    for i in range(1, taps + 1):
        n = int(SR * delay * i)
        out[n:n + len(src)] += src * (fb ** i)
    return out


def save(name, x, peak=0.95):
    x = np.tanh(1.7 * x)  # saturacao: deixa "gordo" e alto
    x = x / (np.max(np.abs(x)) or 1) * peak
    f = int(SR * 0.003)
    x[:f] *= np.linspace(0, 1, f)
    x[-f * 4:] *= np.linspace(1, 0, f * 4)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())


SEMIS = [0, 3, 5, 7, 12]


def kill_sound(level):
    st = SEMIS[level - 1]
    up = 2 ** (st / 12)
    root = 164.81 * up  # E3
    top = 659.25 * up   # E5
    total = 1.3 + level * 0.22
    buf = np.zeros(int(SR * total))

    # soco: bumbo com queda de tom, saturado
    d = 0.4
    kick = sweep(300, 46, d, 38) * env(d, 0.001, 8.5)
    place(buf, np.tanh(kick * 2.6) * 0.9, 0)
    # estalo
    d = 0.14
    crack = hp(lp(noise(d), 3), 18) * env(d, 0.0005, 34)
    place(buf, np.tanh(crack * 3) * 0.55, 0.002)
    place(buf, hp(noise(0.004), 2) * 0.8, 0)
    # shing metalico (sobe de tom)
    d = 0.55
    shing = fm(top * 2, d, 1.414, 6.5, 7.5) * 0.45 + lin_sweep(top * 1.5, top * 3.2, d) * env(d, 0.002, 11) * 0.25
    place(buf, hp(shing, 3) * (0.55 + level * 0.06), 0.012)
    # acorde de supersaw com filtro abrindo e fechando
    d = 0.75 + level * 0.12
    chord = supersaw(root * 2, d) + supersaw(root * 3, d) * 0.8 + supersaw(root * 4, d) * 0.6
    if level >= 3:
        chord += supersaw(root * 5.04, d) * 0.45  # terca maior em cima: brilho
    bright, dark = lp(chord, 2), lp(chord, 22)
    mixk = np.exp(-t(d) * (5.5 - level * 0.5))
    stab = (bright * mixk + dark * (1 - mixk)) * env(d, 0.004, 3.2 - level * 0.25)
    place(buf, np.tanh(stab * 1.8) * (0.42 + level * 0.04), 0.008)
    # grave caindo
    if level >= 2:
        d = 0.75
        sub = sweep(95, 34, d, 4.5) * env(d, 0.005, 3.6)
        place(buf, sub * (0.35 + level * 0.08), 0.01)
    # explosao (cresce por nivel)
    d = 0.9 + level * 0.15
    boom = lp(noise(d), 60) * env(d, 0.004, 5.5 - level * 0.5) * 3.2 + lp(noise(d), 10) * env(d, 0.002, 12)
    place(buf, boom * (0.12 + level * 0.07), 0.004)
    # brilhos
    if level >= 3:
        for _ in range(6 + level * 3):
            f = rng.uniform(2500, 7000)
            dd = rng.uniform(0.04, 0.09)
            place(buf, np.sin(2 * np.pi * f * t(dd)) * env(dd, 0.002, 40) * 0.12, rng.uniform(0.08, 0.6))
    # laser subindo
    if level >= 4:
        d = 0.18
        tt = t(d)
        zap = np.sin(2 * np.pi * np.cumsum(500 + 3200 * (tt / d) ** 1.6 + 60 * np.sin(tt * 90)) / SR) * env(d, 0.01, 6)
        place(buf, zap * 0.22, 0.03)
    # nivel 5: explosao grande + coro
    if level == 5:
        d = 2.0
        big = lp(noise(d), 120) * env(d, 0.01, 2.0) * 4.0 + lp(noise(d), 30) * env(d, 0.005, 4) * 1.5
        place(buf, big * 0.5, 0.0)
        pad = np.zeros(int(SR * 1.6))
        for mult in (2, 2.52, 3, 4):
            pad += lp(supersaw(root * mult, 1.6, 0.012, 7), 8)
        place(buf, pad * env(1.6, 0.12, 1.6) * 0.35, 0.05)
    return echo(buf)


def main():
    for level in range(1, 6):
        save("kill%d" % level, kill_sound(level))
    # tiro na cabeca: "tink" metalico com estalo e eco curto
    d = 0.6
    buf = np.zeros(int(SR * d))
    place(buf, fm(2349, d, 3.73, 5, 9) * 0.7, 0)
    place(buf, fm(3520, d, 1.41, 2.5, 14) * 0.35, 0.004)
    place(buf, np.tanh(hp(noise(0.05), 3) * env(0.05, 0.0005, 70) * 3) * 0.5, 0)
    place(buf, sweep(900, 2600, 0.12, 30) * env(0.12, 0.002, 22) * 0.25, 0.002)
    save("headshot", echo(buf, 0.09, 2, 0.3), 0.85)
    print("sons de abate gerados")


if __name__ == "__main__":
    main()
