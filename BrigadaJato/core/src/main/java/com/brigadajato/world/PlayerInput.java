package com.brigadajato.world;

/** Comandos de um jogador num instante (humano, bot ou remoto). */
public class PlayerInput {
    public static final int P_MELEE = 1, P_GRENADE = 2, P_SWAP = 4, P_RELOAD = 8, P_PICKUP = 16, P_GRENADE_TYPE = 32, P_ZOOM = 64, P_HOOK = 128;

    /** -1..1 */
    public float moveX, moveY;
    /** angulo da mira em radianos */
    public float aim;
    public boolean fire;
    /** segurando o botao de usar (plantar/desarmar bomba) */
    public boolean use;
    /** botoes de toque unico acumulados ate serem processados */
    public int presses;
    /** compra pedida no modo bomba (ordinal da arma, -1 = nada) */
    public int buy = -1;
    /** emote pedido (-1 = nenhum) */
    public int emote = -1;

    public boolean pressed(int flag) {
        return (presses & flag) != 0;
    }

    public void press(int flag) {
        presses |= flag;
    }

    public void clearPresses() {
        presses = 0;
        buy = -1;
        emote = -1;
    }

    public void reset() {
        moveX = moveY = 0;
        fire = use = false;
        presses = 0;
        buy = -1;
        emote = -1;
    }

    public void copyFrom(PlayerInput o) {
        moveX = o.moveX;
        moveY = o.moveY;
        aim = o.aim;
        fire = o.fire;
        use = o.use;
        presses |= o.presses;
        if (o.buy >= 0) buy = o.buy;
        if (o.emote >= 0) emote = o.emote;
    }
}
