# Gravações reais usadas nos sons das armas

Todas em domínio público (CC0 1.0) — podem ser usadas, alteradas e redistribuídas sem crédito,
mas deixamos o registro de onde vieram:

| Arquivo | Origem | Autor | Licença |
|---|---|---|---|
| `kenney/impact-*.wav` | "Impact Sounds" — https://kenney.nl/assets/impact-sounds (cópia em https://github.com/novincode/atomcut-library/tree/main/packs/kenney-impact-sounds) | Kenney | CC0 1.0 |
| `sp_gun-1.wav` … `sp_gun-5.wav`, `sp_reload.wav`, `sp_no-ammo.wav` | "Superpowers assets sound effects" — https://opengameart.org/content/superpowers-assets-sound-effects (cópia em https://github.com/novincode/atomcut-library/tree/main/packs/opengameart-superpowers-assets-sound-effects) | medicinestorm / Sparklin Labs | CC0 1.0 |

Os sons finais em `assets/sfx/` são gerados por `tools/make_real_gun_sounds.py` a partir destas
gravações (mudança de tom, corte, equalização, reforço grave e eco).

Os sons de impacto (`hit`, `hit1`–`hit3`, `hurt`, `hitmarker`, `hithead`, `killhit`) são gerados por
`tools/make_impact_sounds.py` a partir das gravações da Kenney.
