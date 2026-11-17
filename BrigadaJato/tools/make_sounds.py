#!/usr/bin/env python3
"""Sintetiza todos os efeitos sonoros e a musica do Brigada Jato (WAV 22kHz mono).
Nenhum som externo: tudo gerado aqui, entao nao ha problema de direitos autorais."""
import os
import wave

import numpy as np

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "sfx")
rng = np.random.default_rng(1234)


def t(d):
    return np.arange(int(SR * d)) / SR


def noise(d):
    return rng.uniform(-1, 1, int(SR * d))


def lowpass(x, k):
    """Media movel (FIR) simples; k = tamanho da janela."""
    if k <= 1:
        return x
    ker = np.ones(k) / k
    return np.convolve(x, ker, mode="same")


def highpass(x, k):
    return x - lowpass(x, k)


def env_exp(d, decay):
    return np.exp(-t(d) * decay)


def env_ad(d, a, dec):
    tt = t(d)
    e = np.minimum(1, tt / max(a, 1e-4)) * np.exp(-np.maximum(0, tt - a) * dec)
    return e


def sine(f, d, phase=0):
    if callable(f):
        tt = t(d)
        freq = f(tt)
        ph = 2 * np.pi * np.cumsum(freq) / SR
        return np.sin(ph + phase)
    return np.sin(2 * np.pi * f * t(d) + phase)


def square(f, d):
    return np.sign(sine(f, d))


def saw(f, d):
    tt = t(d)
    return 2 * ((tt * f) % 1) - 1


def pad(x, d):
    n = int(SR * d)
    if len(x) >= n:
        return x[:n]
    return np.concatenate([x, np.zeros(n - len(x))])


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def at(x, delay):
    return np.concatenate([np.zeros(int(SR * delay)), x])


def save(name, x, gain=0.9):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x)) or 1.0
    x = x / peak * gain
    # fade curto para evitar estalos
    f = min(len(x), int(SR * 0.004))
    if f > 0:
        x[:f] *= np.linspace(0, 1, f)
        x[-f:] *= np.linspace(1, 0, f)
    data = (x * 32767).astype(np.int16)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def gunshot(d, body_freq, noise_lp, decay, crack=0.5, tail=0.0):
    n = lowpass(noise(d), noise_lp) * env_exp(d, decay)
    body = sine(lambda tt: body_freq * (1 + 2 * np.exp(-tt * 40)), d) * env_exp(d, decay * 1.3)
    c = highpass(noise(d), 3) * env_exp(d, decay * 6) * crack
    x = mix(n, body * 0.8, c)
    if tail > 0:
        x = mix(x, at(lowpass(noise(d * 2), 12) * env_exp(d * 2, 6) * tail, d * 0.3))
    return x


def main():
    os.makedirs(OUT, exist_ok=True)
    save("pistol", gunshot(0.16, 180, 3, 28))
    save("magnum", gunshot(0.32, 120, 4, 14, crack=0.8, tail=0.3))
    save("smg", gunshot(0.09, 220, 2, 45))
    save("rifle", gunshot(0.14, 150, 3, 30, crack=0.7))
    save("shotgun", gunshot(0.38, 90, 6, 10, crack=0.9, tail=0.4))
    save("sniper", gunshot(0.55, 80, 5, 9, crack=1.0, tail=0.6))
    save("lmg", gunshot(0.11, 130, 3, 38, crack=0.6))

    # foguete: ruido com filtro abrindo
    d = 0.7
    x = noise(d)
    x = mix(lowpass(x, 14) * env_ad(d, 0.05, 3), highpass(noise(d), 2) * env_exp(d, 30) * 0.6)
    save("rocket", x)

    # explosao
    d = 1.5
    x = mix(
        lowpass(noise(d), 30) * env_ad(d, 0.01, 3.2) * 1.4,
        lowpass(noise(d), 6) * env_exp(d, 9),
        sine(lambda tt: 70 * np.exp(-tt * 1.5) + 30, d) * env_exp(d, 3.5) * 1.2,
    )
    save("explosion", x, 0.95)
    d = 0.6
    save("explosion_small", mix(lowpass(noise(d), 18) * env_exp(d, 7), sine(90, d) * env_exp(d, 9)))

    save("bounce", mix(sine(1900, 0.08) * env_exp(0.08, 60), sine(2600, 0.08) * env_exp(0.08, 80) * 0.5), 0.5)
    save("laser", mix(sine(lambda tt: 2400 * np.exp(-tt * 9) + 200, 0.4) * env_exp(0.4, 7),
                      square(lambda tt: 1200 * np.exp(-tt * 9) + 100, 0.4) * env_exp(0.4, 12) * 0.25)
         if False else
         mix(sine(lambda tt: 2400 * np.exp(-tt * 9) + 200, 0.4) * env_exp(0.4, 7),
             np.sign(sine(lambda tt: 1200 * np.exp(-tt * 9) + 100, 0.4)) * env_exp(0.4, 12) * 0.25))
    d = 0.22
    save("flame", lowpass(noise(d), 5) * env_ad(d, 0.03, 10), 0.6)
    d = 0.35
    save("disc", mix(sine(lambda tt: 900 + 600 * tt, d) * env_exp(d, 8) * 0.6,
                     sine(lambda tt: 1400 + 900 * tt, d) * env_exp(d, 8) * 0.4,
                     highpass(noise(d), 2) * env_exp(d, 25) * 0.4))
    click = highpass(noise(0.03), 2) * env_exp(0.03, 150)
    save("reload", mix(click, at(lowpass(noise(0.06), 3) * env_exp(0.06, 70), 0.18), at(click * 1.2, 0.42)), 0.6)
    save("empty", mix(click, sine(3000, 0.03) * env_exp(0.03, 200) * 0.3), 0.5)
    save("pickup", mix(sine(660, 0.08) * env_exp(0.08, 20), at(sine(990, 0.12) * env_exp(0.12, 18), 0.07)), 0.6)
    notes = [523, 659, 784]
    save("health", mix(*[at(sine(f, 0.15) * env_exp(0.15, 14), i * 0.06) for i, f in enumerate(notes)]), 0.6)
    notes = [523, 659, 784, 1046]
    save("powerup", mix(*[at((sine(f, 0.18) + 0.3 * square(f, 0.18)) * env_exp(0.18, 12), i * 0.07)
                          for i, f in enumerate(notes)]), 0.6)
    # jetpack em loop: ruido filtrado estacionario
    d = 0.6
    j = lowpass(noise(d + 0.2), 9)[int(SR * 0.1): int(SR * 0.1) + int(SR * d)]
    save("jetpack", j * 0.8 + lowpass(noise(d), 2)[: len(j)] * 0.1, 0.5)
    d = 0.12
    save("hit", mix(sine(lambda tt: 160 * np.exp(-tt * 10), d) * env_exp(d, 25), lowpass(noise(d), 4) * env_exp(d, 40) * 0.6), 0.7)
    save("hitmarker", sine(2200, 0.035) * env_exp(0.035, 90), 0.4)
    d = 0.6
    save("death", mix(sine(lambda tt: 400 * np.exp(-tt * 4), d) * env_exp(d, 5) * 0.6,
                      lowpass(noise(d), 8) * env_exp(d, 10) * 0.5))
    d = 0.2
    save("melee", mix(highpass(lowpass(noise(d), 3), 30) * env_ad(d, 0.06, 20), at(sine(110, 0.1) * env_exp(0.1, 30), 0.08)), 0.7)
    save("ui_click", mix(sine(1200, 0.04) * env_exp(0.04, 80), click * 0.3), 0.45)
    save("beep", sine(1500, 0.09) * env_ad(0.09, 0.005, 20), 0.5)
    save("planted", mix(*[at(sine(1500, 0.08) * env_ad(0.08, 0.005, 25), i * 0.15) for i in range(3)]), 0.6)
    save("defused", mix(*[at(sine(f, 0.2) * env_exp(0.2, 10), i * 0.1) for i, f in enumerate([1046, 784, 523])]), 0.6)
    fan = [523, 659, 784, 1046, 784, 1046]
    save("flag", mix(*[at((sine(f, 0.2) + 0.4 * square(f, 0.2)) * env_exp(0.2, 9), i * 0.09) for i, f in enumerate(fan)]), 0.6)
    win = [392, 523, 659, 784, 1046]
    save("win", mix(*[at((sine(f, 0.35) + 0.3 * saw(f, 0.35)) * env_exp(0.35, 5), i * 0.12) for i, f in enumerate(win)]), 0.7)
    lose = [523, 466, 392, 311]
    save("lose", mix(*[at((sine(f, 0.4) + 0.3 * saw(f, 0.4)) * env_exp(0.4, 4), i * 0.18) for i, f in enumerate(lose)]), 0.7)
    d = 0.9
    save("wave", mix(saw(220, d) * env_ad(d, 0.08, 2.5) * 0.5, saw(330, d) * env_ad(d, 0.08, 2.5) * 0.4, saw(110, d) * env_ad(d, 0.08, 2) * 0.4), 0.7)
    d = 0.15
    save("throw", highpass(lowpass(noise(d), 4), 25) * env_ad(d, 0.05, 15), 0.5)
    save("arm", mix(sine(880, 0.06) * env_exp(0.06, 40), at(sine(1320, 0.06) * env_exp(0.06, 40), 0.07)), 0.5)
    save("streak", mix(*[at((square(f, 0.12)) * env_exp(0.12, 15) * 0.5, i * 0.06) for i, f in enumerate([784, 988, 1175, 1568])]), 0.5)
    save("spawn", mix(sine(lambda tt: 300 + 1500 * tt, 0.3) * env_ad(0.3, 0.05, 8)), 0.5)
    save("step", lowpass(noise(0.05), 6) * env_exp(0.05, 70), 0.3)

    # ------------------------------------------------------------ musica
    bpm = 124
    beat = 60 / bpm
    bars = 8
    total = bars * 4 * beat
    n = int(SR * total)
    music = np.zeros(n)

    def put(x, start):
        s = int(SR * start)
        e = min(n, s + len(x))
        music[s:e] += x[: e - s]

    # progressao em La menor: Am F C G
    chords = [(220.0, [220, 261.6, 329.6]), (174.6, [174.6, 220, 261.6]),
              (130.8, [130.8, 164.8, 196]), (196.0, [196, 246.9, 293.7])]
    for bar in range(bars):
        root, triad = chords[bar % 4]
        for b in range(8):  # baixo em colcheias
            st = bar * 4 * beat + b * beat / 2
            f = root / 2 if b % 2 == 0 else root
            put(square(f, beat / 2 * 0.9) * env_exp(beat / 2 * 0.9, 6) * 0.22, st)
        for b in range(4):  # bumbo + caixa
            st = bar * 4 * beat + b * beat
            put(sine(lambda tt: 120 * np.exp(-tt * 25) + 40, 0.2) * env_exp(0.2, 18) * 0.6, st)
            if b % 2 == 1:
                put(lowpass(noise(0.15), 2) * env_exp(0.15, 25) * 0.25, st)
            put(highpass(noise(0.04), 2) * env_exp(0.04, 90) * 0.12, st + beat / 2)
        # arpejo da melodia
        for k in range(8):
            st = bar * 4 * beat + k * beat / 2
            f = triad[k % 3] * (2 if (k // 3) % 2 == 0 else 4)
            put((square(f, beat / 2 * 0.7) * 0.4 + sine(f, beat / 2 * 0.7)) * env_exp(beat / 2 * 0.7, 9) * 0.09, st)
    save("music", music, 0.7)
    print("sons gerados em", os.path.abspath(OUT))


if __name__ == "__main__":
    main()
