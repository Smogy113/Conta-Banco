# Brigada Jato 🪖🚀

Jogo de tiro 2D com jetpack para celular (Android) e PC, feito em **Java + libGDX**.
Soldadinhos cartoon, duplo joystick, bots com inteligência artificial e **multiplayer no Wi‑Fi local** (sem internet).

Tudo é original: personagens, mapas, sons e música foram gerados por código (pasta `tools/`).

## Como jogar no celular

### Opção 1: baixar o APK pronto (sem Android Studio)
1. No GitHub, abra a aba **Actions** do repositório.
2. Clique na execução mais recente de **"Brigada Jato - APK Android"** (bolinha verde).
3. Em **Artifacts**, baixe **BrigadaJato-APK** (vem em .zip; dentro está o `BrigadaJato.apk`).
4. Passe o APK para o celular e instale (permita "instalar apps de fontes desconhecidas").

### Opção 2: Android Studio
1. Android Studio → **File → Open** → escolha a pasta `BrigadaJato` (não a raiz do repositório).
2. Espere o Gradle sincronizar (ele cria o `local.properties` com o caminho do SDK, e aí o módulo `android` aparece).
3. Conecte o celular com **Depuração USB** ligada e clique em **Run ▶** com a configuração `android`.
   - Se não aparecer configuração: **Run → Edit Configurations → + → Android App → Module: BrigadaJato.android**.

### PC (Windows / Linux / Mac)
- Baixe o artefato **BrigadaJato-PC** nas Actions e rode: `java -jar BrigadaJato-PC.jar` (precisa de Java 17+).
- Ou no terminal, dentro de `BrigadaJato`: `./gradlew desktop:run`

## Controles

| Ação | Celular | PC |
|---|---|---|
| Andar | joystick esquerdo | A / D |
| Jetpack (voar) | joystick esquerdo para cima | W ou Espaço |
| Agachar / descer de plataforma | joystick esquerdo para baixo | S |
| Mirar | joystick direito | mouse |
| Atirar | empurrar o joystick direito até a borda (ou botão de tiro, nas configurações) | botão esquerdo |
| Granada / mina | botão da granada (o "M"/"G" troca o tipo) | G (T troca o tipo) ou botão direito |
| Trocar arma | tocar no painel da arma | Q ou rodinha |
| Recarregar | botão ↻ | R |
| Soco | botão do punho | F |
| Pegar arma do chão | botão "Pegar" (aparece perto da arma) | E |
| Plantar / desarmar bomba | segurar "Usar" | segurar E |
| Zoom | lupa | Z |
| Placar | botão ☰ | Tab |
| Pausar | botão ⏸ / voltar | Esc |

## Modos de jogo
- **Mata-mata**: todos contra todos.
- **Times**: vermelho contra azul.
- **Capture a Bandeira**: roube a bandeira inimiga e leve até a sua base.
- **Plantar a Bomba**: rodadas sem renascer, fase de compra com dinheiro, ataque planta a bomba em A ou B, defesa desarma. Troca de lado no meio da partida.
- **Sobrevivência**: ondas de inimigos cada vez mais fortes, 3 vidas (dá para jogar em dupla no Wi‑Fi).
- **Treino**: tutorial passo a passo com bonecos-alvo e todas as armas.

## Conteúdo
- **12 armas**: pistola, magnum, submetralhadora, fuzil, escopeta, sniper, metralhadora, bazuca, lança-granadas, lança-chamas, laser e disco serra. Mais soco, granadas e minas.
- **Itens**: vida, granadas, colete, dano em dobro, jetpack infinito e super velocidade.
- **5 mapas**: Fortaleza do Deserto, Fábrica Abandonada, Ilhas do Céu, Caverna Gelada e Campo de Treino.
- **Bots** com 3 dificuldades: buscam caminho (algoritmo de Dijkstra), economizam o jetpack, pegam armas e itens, jogam granadas e seguem o objetivo de cada modo.
- **Personalização**: nome, cores do uniforme, chapéu, óculos, pele e 7 estilos de chapéu.
- **Estatísticas**: partidas, vitórias, abates, nível e recorde da sobrevivência.
- **Português e inglês** (troca nas configurações).

## Multiplayer Wi‑Fi local
1. Todos no mesmo Wi‑Fi **ou** conectados no hotspot (roteador) do celular de quem vai criar a sala.
2. Um jogador toca em **Multiplayer Wi‑Fi → Criar Sala**, escolhe modo/mapa/bots.
3. Os outros abrem **Multiplayer Wi‑Fi** e a sala aparece sozinha na lista (ou digitam o IP mostrado na tela do anfitrião).
4. O anfitrião toca em **Iniciar Partida**. Até 8 jogadores + bots.

Detalhes técnicos: o anfitrião roda a simulação (autoritativo), clientes mandam comandos 30x/s e recebem o estado 20x/s (TCP porta 47130). A descoberta de salas usa broadcast UDP na porta 47131. Se alguém sair no meio, vira bot.

## Estrutura do código
```
BrigadaJato/
├── core/        lógica do jogo (compartilhada)
│   ├── world/       física, armas, jogadores, projéteis, itens
│   │   ├── modes/   regras de cada modo de jogo
│   │   └── ai/      bots (BotBrain) e busca de caminho (Pathfinder)
│   ├── render/      desenho do mundo, soldados, partículas e HUD
│   ├── input/       joysticks de toque + teclado/mouse
│   ├── net/         multiplayer local (protocolo, anfitrião, cliente, descoberta)
│   └── screens/     menus e tela de jogo
├── android/     app Android (AndroidLauncher)
├── desktop/     versão PC (DesktopLauncher)
├── assets/      mapas (.txt), sons (.wav), fonte e ícone
└── tools/       scripts Python que geram mapas, sons e ícones
```

### Editar / criar mapas
Os mapas são texto puro em `assets/maps/`. Edite `tools/make_maps.py` (você desenha só a metade esquerda e ele espelha) e rode `python3 tools/make_maps.py`.
Legenda: `#` bloco, `=` plataforma, `^` espinhos, `S` spawn, `r`/`b` spawn vermelho/azul, `R`/`B` bandeiras, `a`/`c` pontos de bomba, `W` arma, `H` vida, `G` granadas, `P` power-up, `T` boneco de treino.

### Testes
`./gradlew core:test` roda partidas inteiras só com bots em todos os modos e mapas, e um teste de rede (anfitrião + cliente na mesma máquina).

### Opções de teste no PC
`./gradlew desktop:run --args="--start=game:BOMB:fabrica:7 --autoplay=true"` inicia direto uma partida (a IA joga por você com `--autoplay`).
