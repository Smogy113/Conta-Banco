#!/usr/bin/env python3
"""Tiros e explosoes pesados (44.1 kHz), no mesmo estilo dos sons de abate:
estalo seco + corpo de ruido filtrado + soco grave (sub) + mecanismo metalico + cauda com eco,
tudo saturado. Armas automaticas tem cauda curta para nao embolar."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from make_kill_sounds import SR, t, noise, lp, hp, sweep, lin_sweep, env, fm, place, echo, save  # noqa: E402

rng = np.random.default_rng(4242)


def bandish(x, low_k, high_k):
    """passa-faixa grosseiro: tira graves (hp) e agudos (lp)"""
    return lp(hp(x, high_k), low_k)


def gunshot(total, crack_gain, body_k, body_decay, sub_f0, sub_f1, sub_decay, sub_gain, mech_at, mech_f,
            tail_decay, tail_gain, echo_delay=0.09, echo_fb=0.32, echo_taps=3, ring=0.0):
    buf = np.zeros(int(SR * total))
    # 1) estalo supersonico: clique + crack agudo
    place(buf, hp(noise(0.003), 2) * 1.0, 0)
    d = 0.06
    crack = bandish(noise(d), 3, 8) * env(d, 0.0004, 70)
    place(buf, np.tanh(crack * 3) * crack_gain * 0.55, 0.0005)
    # 2) corpo: ruido mais grave decaindo (o "BAM")
    d = min(total, 0.5)
    body = lp(lp(noise(d), body_k * 6), body_k * 3) * env(d, 0.001, body_decay)
    body = body / (np.max(np.abs(body)) or 1)
    place(buf, np.tanh(body * 3.0) * 0.9, 0.001)
    # meio-grave encorpado (o "estouro" do cano)
    mid = lp(lp(noise(d), 30), 30) * env(d, 0.001, body_decay * 1.6)
    mid = mid / (np.max(np.abs(mid)) or 1)
    place(buf, np.tanh(mid * 2.5) * 0.7, 0.0015)
    # 3) soco grave (sub) - o peso
    d = min(total, 0.45)
    sub = sweep(sub_f0, sub_f1, d, 30) * env(d, 0.0015, sub_decay)
    place(buf, np.tanh(sub * 2.4) * sub_gain * 1.25, 0)
    # 4) mecanismo / ferrolho
    if mech_at > 0:
        d = 0.08
        mech = fm(mech_f, d, 2.71, 4, 55) * 0.35 + hp(noise(d), 4) * env(d, 0.0005, 70) * 0.3
        place(buf, mech, mech_at)
    # 5) zumbido metalico (armas grandes)
    if ring > 0:
        d = min(total, 0.6)
        place(buf, fm(1900, d, 1.41, 3, 9) * ring, 0.004)
    # 6) cauda / reverberacao do ambiente
    d = total
    tail = lp(lp(noise(d), 70), 40) * env(d, 0.01, tail_decay)
    tail = tail / (np.max(np.abs(tail)) or 1) * tail_gain * 1.4
    place(buf, tail, 0.008)
    out = echo(buf, echo_delay, echo_taps, echo_fb)
    return out


def explosion(total, size):
    buf = np.zeros(int(SR * total))
    place(buf, hp(noise(0.004), 2), 0)
    d = total
    boom = sweep(140 * size, 28, d, 6) * env(d, 0.002, 3.2 / size)
    place(buf, np.tanh(boom * 3) * 1.0, 0)
    rumble = lp(lp(noise(d), 120), 60) * env(d, 0.003, 2.4 / size)
    rumble = rumble / (np.max(np.abs(rumble)) or 1)
    place(buf, np.tanh(rumble * 3) * 0.9, 0)
    blast = lp(lp(noise(0.5), 25), 15) * env(0.5, 0.001, 9)
    blast = blast / (np.max(np.abs(blast)) or 1)
    place(buf, np.tanh(blast * 3) * 0.8, 0)
    # estalos de destrocos
    for i in range(int(10 * size)):
        at = 0.05 + rng.random() * total * 0.6
        d2 = 0.03 + rng.random() * 0.05
        place(buf, hp(noise(d2), 3) * env(d2, 0.0005, 70) * (0.2 + rng.random() * 0.25), at)
    return echo(buf, 0.14, 3, 0.35)


def main():
    save("pistol", gunshot(0.55, 0.7, 6, 22, 220, 55, 16, 0.75, 0.05, 2200, 7, 0.22))
    save("magnum", gunshot(1.0, 0.95, 9, 12, 180, 40, 9, 1.0, 0.08, 1700, 4, 0.38, 0.12, 0.38, 3, ring=0.12))
    save("smg", gunshot(0.32, 0.55, 5, 34, 260, 70, 24, 0.6, 0.035, 2600, 14, 0.14, 0.06, 0.22, 2))
    save("rifle", gunshot(0.6, 0.8, 7, 20, 200, 48, 14, 0.85, 0.045, 2000, 6, 0.26, 0.09, 0.3, 3))
    save("lmg", gunshot(0.4, 0.75, 8, 26, 190, 45, 18, 0.85, 0.03, 1800, 11, 0.18, 0.07, 0.25, 2))
    save("shotgun", gunshot(1.0, 0.9, 12, 10, 160, 36, 8, 1.0, 0.32, 1300, 3.5, 0.42, 0.13, 0.38, 3))
    save("sniper", gunshot(1.5, 1.0, 10, 8, 150, 30, 6, 1.0, 0.45, 1500, 2.4, 0.5, 0.18, 0.42, 4, ring=0.18))

    # bazuca: estouro + chiado do foguete saindo
    d = 1.0
    buf = gunshot(1.0, 0.6, 14, 9, 120, 30, 6, 1.0, 0, 0, 3, 0.3, 0.12, 0.3, 2)
    whoosh = bandish(noise(d), 3, 10) * env(d, 0.06, 3) * 0.55
    place(buf, whoosh, 0.02)
    save("rocket", buf)
    save("explosion", explosion(2.2, 1.0))
    save("explosion_small", explosion(1.3, 0.7))

    # laser: zap com grave e brilho
    d = 0.6
    buf = np.zeros(int(SR * d))
    place(buf, np.tanh(sweep(3200, 180, d, 10) * env(d, 0.001, 6) * 2) * 0.7, 0)
    place(buf, np.tanh(sweep(160, 40, 0.3, 25) * env(0.3, 0.001, 14) * 2.5) * 0.8, 0)
    place(buf, fm(2400, d, 1.5, 5, 8) * 0.25, 0)
    save("laser", echo(buf, 0.08, 2, 0.3))
    # tesla: descarga eletrica forte
    d = 0.6
    zap = np.sign(np.sin(2 * np.pi * 70 * t(d) + 7 * np.sin(2 * np.pi * 9 * t(d)))) * rng.uniform(0.3, 1, int(SR * d))
    buf = np.zeros(int(SR * d))
    place(buf, hp(zap, 3) * env(d, 0.001, 6) * 0.7, 0)
    place(buf, hp(noise(d), 2) * env(d, 0.0005, 10) * 0.6, 0)
    place(buf, np.tanh(sweep(200, 45, 0.35, 20) * env(0.35, 0.001, 10) * 2.5) * 0.8, 0)
    save("tesla", echo(buf, 0.07, 2, 0.3))
    # lanca-chamas e congelante: rajada curta e encorpada
    d = 0.25
    save("flame", np.tanh(lp(noise(d), 7) * env(d, 0.01, 12) * 4) * 0.8)
    save("frost", np.tanh((hp(noise(d), 3) * 0.6 + lin_sweep(3000, 2400, d) * 0.2) * env(d, 0.005, 14) * 3) * 0.7)
    # disco serra: lancamento metalico
    d = 0.45
    buf = np.zeros(int(SR * d))
    place(buf, np.tanh(sweep(220, 60, 0.25, 25) * env(0.25, 0.001, 16) * 2) * 0.7, 0)
    place(buf, fm(1400, d, 2.2, 5, 7) * 0.4, 0.005)
    save("disc", buf)
    # arco: corda + estalo
    d = 0.45
    buf = np.zeros(int(SR * d))
    place(buf, np.tanh(sweep(260, 70, 0.3, 20) * env(0.3, 0.001, 12) * 2.5) * 0.75, 0)
    place(buf, hp(lp(noise(d), 2), 10) * env(d, 0.004, 14) * 0.6, 0)
    save("bow", buf)
    # arpao: disparo pneumatico pesado
    d = 0.6
    buf = gunshot(0.6, 0.4, 10, 14, 150, 40, 10, 0.9, 0.06, 900, 6, 0.2, 0.09, 0.25, 2)
    place(buf, lin_sweep(900, 300, 0.4) * env(0.4, 0.002, 6) * 0.3, 0.01)
    save("harpoon", buf)
    # gravitacional: implosao grave
    d = 1.3
    buf = np.zeros(int(SR * d))
    place(buf, np.tanh(sweep(400, 30, d, 3) * env(d, 0.05, 1.8) * 2.5) * 0.9, 0)
    place(buf, lp(noise(d), 20) * env(d, 0.2, 2) * 0.5, 0)
    save("gravity", echo(buf, 0.15, 2, 0.3))
    # golpe corpo a corpo: pancada seca
    d = 0.3
    buf = np.zeros(int(SR * d))
    place(buf, np.tanh(sweep(180, 50, d, 30) * env(d, 0.001, 18) * 3) * 0.9, 0)
    place(buf, lp(noise(0.08), 3) * env(0.08, 0.0005, 45) * 0.8, 0)
    save("melee", buf)


if __name__ == "__main__":
    main()
