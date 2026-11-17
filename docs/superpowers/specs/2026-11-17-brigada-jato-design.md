# Brigada Jato — design

## Intenção
Jogo mobile de tiro 2D com jetpack no estilo do gênero "soldadinhos com jetpack" (inspiração: Mini Militia),
com identidade própria (nome, personagens, arte, sons e mapas originais). Feito para jogar offline contra bots
e com amigos no Wi‑Fi local, e para ser aberto no Android Studio.

## Decisões do usuário (rodada de perguntas)
- Tecnologia: **libGDX (Java)**.
- Multiplayer: **bots offline + Wi‑Fi local** (um aparelho é o anfitrião).
- Visual: mesma pegada do gênero (soldadinhos cartoon de lado), com arte própria.
- Modos: mata-mata, times, sobrevivência, treino, capture a bandeira e **plantar a bomba (estilo tático)**.
- Nome: **Brigada Jato**. Tiro: as duas opções (mirar = atirar e botão de tiro), escolhidas nas configurações.
- Idioma: português e inglês. APK gerado automaticamente no GitHub Actions.

## Abordagens consideradas
1. HTML5 + WebView — mais rápido de testar, mas o usuário escolheu libGDX.
2. Kotlin nativo com Canvas — sem motor, mais código de infraestrutura.
3. **libGDX (escolhida)** — mesmo código roda no PC (testes, screenshots) e no Android.

## Arquitetura
- `core/world`: simulação determinística em passo fixo (60 Hz) sem dependência de OpenGL — testável em JUnit.
- `core/render`: tudo desenhado por formas num atlas gerado por código (sem imagens externas).
- `core/net`: anfitrião autoritativo; clientes enviam comandos (30 Hz) e recebem snapshots (20 Hz) + eventos.
- Bots: Dijkstra na grade com custo maior no ar (economia de combustível), mira com atraso/erro por dificuldade.

## Fora do escopo (por enquanto)
- Multiplayer pela internet, contas, loja com dinheiro real.
