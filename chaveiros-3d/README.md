# Chaveiros de gatinhos (impressão 3D)

Dois gatinhos originais: **Soneca** (branco, olhos fechados) e **Faísca** (lilás, olhos grandes e estrela).
Tamanho ~48 × 45 × 5 mm, furo da argola de 5,2 mm.

- `*_completo.stl` — peça única (impressora de uma cor; pinte os detalhes depois).
- `*_base_*.stl` + `*_relevo_*.stl` — partes por cor, para AMS/MMU: importe todas juntas
  no fatiador ("carregar como um objeto com várias partes") e atribua uma cor a cada uma.

Sugestão: PLA, camada 0,12–0,2 mm, 3 paredes, 20% de preenchimento, sem suporte.
Para regerar/editar: `python3 gerar_chaveiros.py`.

## Versão lisa (sem relevo): Gato Tédio e Gato Sorriso

Gerados por `gerar_chaveiros_lisos.py`. A superfície é 100% plana: as cores ficam embutidas
nos últimos 0,6 mm da peça (4 mm de espessura total).

- `gato_tedio_*` / `gato_sorriso_*`: `_base_<cor>.stl` + `_cor_<cor>.stl` (multicor) ou `_completo.stl` (uma cor).

## Corpo inteiro (estilo cartoon, sem relevo)

Gerados por `gerar_gatos_corpo.py`: `corpo_gato_tedio_*` e `corpo_gato_sorriso_*`.
~45 × 69 × 4 mm, contorno escuro e cores embutidas nos últimos 0,6 mm (9 cores cada).
