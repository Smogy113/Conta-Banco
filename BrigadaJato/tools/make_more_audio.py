#!/usr/bin/env python3
"""Sons das armas/objetos novos e uma musica por tipo de mapa (OGG via ffmpeg).
Tudo sintetizado aqui (sem samples externos)."""
import os
import subprocess
import sys
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from make_sounds import (SR, OUT, t, noise, lowpass, highpass, env_exp, env_ad, sine, square, saw, mix, at, save)  # noqa: E402

rng = np.random.default_rng(77)
MUSIC_OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "music")


def sweep(f0, f1, d, wave_fn=np.sin):
    tt = t(d)
    f = f0 + (f1 - f0) * tt / d
    ph = 2 * np.pi * np.cumsum(f) / SR
    return wave_fn(ph)


def sfx():
    d = 0.35
    save("bow", mix(highpass(lowpass(noise(d), 2), 12) * env_ad(d, 0.01, 18) * 0.6,
                    sine(lambda tt: 180 + 60 * np.exp(-tt * 30), 0.25) * env_exp(0.25, 14)), 0.8)
    d = 0.5
    wh = highpass(lowpass(noise(d), 3), 20) * (0.5 + 0.5 * np.sin(2 * np.pi * 14 * t(d))) * env_ad(d, 0.05, 5)
    save("boomerang", wh, 0.6)
    save("catch", mix(lowpass(noise(0.05), 3) * env_exp(0.05, 60), at(sine(700, 0.08) * env_exp(0.08, 30), 0.02)), 0.6)
    d = 0.3
    save("frost", mix(highpass(noise(d), 4) * env_ad(d, 0.02, 9) * 0.5,
                      sine(lambda tt: 2600 + 400 * np.sin(tt * 90), d) * env_exp(d, 14) * 0.25), 0.6)
    d = 0.6
    shards = mix(*[at(sine(2000 + i * 370, 0.15) * env_exp(0.15, 25), i * 0.035) for i in range(7)])
    save("freeze", mix(shards, highpass(noise(d), 2) * env_exp(d, 6) * 0.4), 0.8)
    d = 0.45
    zap = np.sign(np.sin(2 * np.pi * 60 * t(d) + 6 * np.sin(2 * np.pi * 7 * t(d)))) * (rng.uniform(0.4, 1, int(SR * d)))
    save("tesla", mix(highpass(zap, 3) * env_exp(d, 7) * 0.6, highpass(noise(d), 2) * env_exp(d, 12) * 0.5,
                      sine(lambda tt: 120 * np.exp(-tt * 6), d) * env_exp(d, 6) * 0.5), 0.85)
    d = 1.2
    save("gravity", mix(sweep(300, 40, d) * env_ad(d, 0.05, 2.2) * 0.7,
                        sweep(330, 45, d, lambda p: np.sign(np.sin(p))) * env_ad(d, 0.05, 2.5) * 0.15,
                        lowpass(noise(d), 12) * env_ad(d, 0.2, 2) * 0.4), 0.8)
    d = 0.4
    save("harpoon", mix(lowpass(noise(0.1), 2) * env_exp(0.1, 40), sweep(900, 300, d) * env_exp(d, 8) * 0.4,
                        at(sine(1800, 0.06) * env_exp(0.06, 60) * 0.3, 0.02)), 0.8)
    d = 0.5
    chain = mix(*[at(highpass(noise(0.03), 2) * env_exp(0.03, 90), i * 0.045) for i in range(10)])
    save("harpoon_pull", mix(chain, sweep(200, 500, d) * env_exp(d, 4) * 0.3), 0.7)
    save("hook", mix(highpass(lowpass(noise(0.25), 2), 10) * env_ad(0.25, 0.01, 12), sweep(1200, 600, 0.2) * env_exp(0.2, 20) * 0.3), 0.6)
    save("hook_hit", mix(sine(1700, 0.12) * env_exp(0.12, 35), sine(2550, 0.1) * env_exp(0.1, 45) * 0.5,
                         lowpass(noise(0.05), 3) * env_exp(0.05, 70)), 0.6)
    save("block", mix(sine(1300, 0.25) * env_exp(0.25, 16), sine(1950, 0.2) * env_exp(0.2, 20) * 0.6,
                      sine(3100, 0.12) * env_exp(0.12, 30) * 0.3, lowpass(noise(0.04), 2) * env_exp(0.04, 80)), 0.7)
    d = 0.5
    cr = mix(*[at(lowpass(noise(0.07), 3 + i) * env_exp(0.07, 50), i * 0.03 + rng.uniform(0, 0.02)) for i in range(8)])
    save("crate_break", mix(cr, sine(lambda tt: 140 * np.exp(-tt * 8), d) * env_exp(d, 10) * 0.6), 0.85)
    save("lever", mix(lowpass(noise(0.04), 2) * env_exp(0.04, 80), at(sine(600, 0.06) * env_exp(0.06, 50), 0.05),
                      at(lowpass(noise(0.05), 3) * env_exp(0.05, 60), 0.09)), 0.6)
    d = 0.9
    save("door", mix(lowpass(saw(55, d), 6) * env_ad(d, 0.05, 3) * 0.6, highpass(lowpass(noise(d), 6), 30) * env_ad(d, 0.1, 3) * 0.3,
                     at(lowpass(noise(0.12), 4) * env_exp(0.12, 30), d - 0.15)), 0.7)
    d = 2.0
    siren = np.sin(2 * np.pi * np.cumsum(500 + 250 * np.sin(2 * np.pi * 1.2 * t(d))) / SR)
    save("boss", mix(siren * env_ad(d, 0.1, 1.2) * 0.4, saw(55, d) * env_ad(d, 0.2, 1.5) * 0.4,
                     sine(lambda tt: 80 * np.exp(-tt * 2), d) * env_exp(d, 2) * 0.6), 0.85)
    d = 1.6
    save("boss_down", mix(lowpass(noise(d), 20) * env_exp(d, 2.5), sine(lambda tt: 70 * np.exp(-tt * 1.5), d) * env_exp(d, 2),
                          *[at((square(f, 0.3) * 0.3 + sine(f, 0.3)) * env_exp(0.3, 6) * 0.35, 0.4 + i * 0.12)
                            for i, f in enumerate([523, 659, 784, 1046])]), 0.9)
    d = 0.9
    save("charge", sweep(200, 1800, d, lambda p: np.sin(p) + 0.3 * np.sign(np.sin(p))) * env_ad(d, 0.6, 3) * 0.6, 0.6)
    d = 0.6
    save("stomp", mix(sine(lambda tt: 70 * np.exp(-tt * 6), d) * env_exp(d, 6), lowpass(noise(d), 25) * env_exp(d, 9) * 0.7), 0.9)
    save("levelup", mix(*[at((square(f, 0.14) * 0.3 + sine(f, 0.14)) * env_exp(0.14, 12), i * 0.07)
                          for i, f in enumerate([523, 659, 784, 1046, 1318])]), 0.6)
    d = 0.8
    growl = lowpass(saw(70, d) * (1 + 0.5 * np.sin(2 * np.pi * 9 * t(d))), 5)
    save("infect", mix(growl * env_ad(d, 0.05, 3) * 0.7, highpass(noise(d), 3) * env_ad(d, 0.02, 6) * 0.2), 0.8)
    save("emote", mix(sine(880, 0.08) * env_exp(0.08, 30), at(sine(1320, 0.1) * env_exp(0.1, 25), 0.06)), 0.45)
    save("dance", mix(*[at(sine(f, 0.1) * env_exp(0.1, 20), i * 0.09) for i, f in enumerate([660, 880, 660, 990])]), 0.45)
    save("round", mix(*[at(sine(f, 0.18) * env_exp(0.18, 12), i * 0.1) for i, f in enumerate([784, 988])]), 0.5)


# ------------------------------------------------------------------ musica

NOTE = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}


def hz(name, octave):
    return 440.0 * 2 ** ((NOTE[name] + 12 * (octave - 4) - 9) / 12)


def chord(root, minor, octave=3):
    r = hz(root, octave)
    third = r * 2 ** ((3 if minor else 4) / 12)
    fifth = r * 2 ** (7 / 12)
    return r, [r, third, fifth]


def track(name, bpm, prog, style, bars=16, swing=0.0):
    beat = 60 / bpm
    total = bars * 4 * beat
    n = int(SR * total)
    out = np.zeros(n)

    def put(x, start, g=1.0):
        s = int(SR * start)
        if s >= n:
            return
        e = min(n, s + len(x))
        out[s:e] += x[: e - s] * g

    lead_wave = {"square": square, "saw": saw, "sine": sine}[style.get("lead", "square")]
    bass_wave = {"square": square, "saw": saw, "sine": sine}[style.get("bass", "saw")]
    kick = sine(lambda tt: 130 * np.exp(-tt * 28) + 42, 0.25) * env_exp(0.25, 16)
    snare = mix(highpass(noise(0.18), 2) * env_exp(0.18, 22) * 0.6, sine(190, 0.1) * env_exp(0.1, 30) * 0.4)
    hat = highpass(noise(0.05), 1) * env_exp(0.05, 80) * 0.5
    melody_rng = np.random.default_rng(abs(hash(name)) % 10000)
    # motivo de 8 notas que se repete (graus da triade/escala)
    motif = melody_rng.integers(0, 5, 8)
    rhythm = melody_rng.random(8) < 0.7
    for bar in range(bars):
        root_name, minor = prog[bar % len(prog)]
        root, triad = chord(root_name, minor, style.get("oct", 3))
        scale = [triad[0], triad[0] * 2 ** (2 / 12), triad[1], triad[2], triad[0] * 2 ** (9 / 12 if not minor else 10 / 12)]
        b0 = bar * 4 * beat
        intro = bar < 2
        # baixo
        pattern = style.get("bass_pat", [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5])
        for k, pos in enumerate(pattern):
            f = root / 2 if k % 2 == 0 else root
            dur = beat * 0.45
            put(lowpass(bass_wave(f, dur), 3) * env_exp(dur, 7), b0 + pos * beat, 0.24)
        # bateria
        if not intro or style.get("drums_intro", False):
            for k in range(4):
                put(kick, b0 + k * beat, 0.55 if style.get("four", True) or k % 2 == 0 else 0)
                if k % 2 == 1:
                    put(snare, b0 + k * beat, 0.3)
                put(hat, b0 + k * beat + beat / 2 + swing * beat * 0.15, 0.14)
                if style.get("busy_hats"):
                    put(hat, b0 + k * beat + beat / 4, 0.07)
                    put(hat, b0 + k * beat + 3 * beat / 4, 0.07)
        # pad
        dur = 4 * beat
        pad = sum(sine(f, dur) + 0.25 * saw(f * 1.003, dur) for f in triad) / 3
        put(lowpass(pad, 8) * env_ad(dur, 0.6, 0.6), b0, style.get("pad", 0.08))
        # melodia (fica de fora nos 2 primeiros compassos e na metade das voltas)
        if not intro and (bar // 4) % 2 == 1 or bar >= bars - 4:
            for k in range(8):
                if not rhythm[k]:
                    continue
                f = scale[motif[k]] * (2 ** style.get("lead_oct", 1))
                if (bar % 4) == 3 and k >= 6:
                    f *= 2 ** (2 / 12)
                d = beat * 0.42
                tone = lead_wave(f, d) * 0.5 + sine(f, d)
                put(tone * env_ad(d, 0.01, 7), b0 + k * beat / 2, style.get("lead_gain", 0.07))
        # arpejo
        if style.get("arp") and not intro:
            for k in range(16):
                f = triad[k % 3] * 4
                d = beat / 4 * 0.8
                put(sine(f, d) * env_exp(d, 18), b0 + k * beat / 4, 0.045)
    # eco simples
    delay = int(SR * beat * 0.75)
    echo = np.zeros(n)
    echo[delay:] = out[:-delay] * 0.25
    out = out + echo
    peak = np.max(np.abs(out)) or 1
    out = out / peak * 0.8
    os.makedirs(MUSIC_OUT, exist_ok=True)
    wav = os.path.join(MUSIC_OUT, name + ".wav")
    data = (out * 32767).astype(np.int16)
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    ogg = os.path.join(MUSIC_OUT, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "2", ogg], check=True)
    os.remove(wav)
    print(name, os.path.getsize(ogg) // 1024, "KB")


def music():
    A, Am = ("A", False), ("A", True)
    track("desert", 118, [("D", True), ("A#", False), ("C", False), ("A", False)], {"lead": "saw", "swing": 0.4, "arp": True})
    track("factory", 128, [("E", True), ("E", True), ("C", False), ("D", False)], {"lead": "square", "busy_hats": True, "bass": "square"})
    track("sky", 122, [("C", False), ("G", False), ("A", True), ("F", False)], {"lead": "sine", "arp": True, "pad": 0.1})
    track("ice", 100, [("B", True), ("G", False), ("D", False), ("A", False)], {"lead": "sine", "arp": True, "pad": 0.12, "four": False})
    track("jungle", 112, [("G", True), ("D#", False), ("F", False), ("D", True)], {"lead": "square", "busy_hats": True, "bass_pat": [0, 0.75, 1.5, 2, 2.75, 3.5]})
    track("space", 104, [("F", True), ("C#", False), ("G#", False), ("D#", False)], {"lead": "sine", "arp": True, "pad": 0.14, "four": False})
    track("ship", 132, [("D", True), ("C", False), ("A#", False), ("A", False)], {"lead": "square", "bass_pat": [0, 1, 1.5, 2, 3, 3.5]})
    track("city", 96, [("C", True), ("G#", False), ("D#", False), ("A#", False)], {"lead": "saw", "pad": 0.13, "arp": True, "busy_hats": True})
    track("volcano", 140, [("E", True), ("F", False), ("E", True), ("D", False)], {"lead": "saw", "bass": "square", "busy_hats": True})
    track("undersea", 92, [("A", True), ("F", False), ("D", True), ("E", False)], {"lead": "sine", "pad": 0.14, "four": False, "arp": True})
    track("school", 126, [("F", False), ("C", False), ("D", True), ("A#", False)], {"lead": "square", "arp": True})
    track("market", 116, [("G", False), ("E", True), ("C", False), ("D", False)], {"lead": "saw", "bass_pat": [0, 0.75, 1.5, 2, 2.5, 3.25], "swing": 0.5})


if __name__ == "__main__":
    sfx()
    music()
