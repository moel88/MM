# Gold Experience's T, H and basic attack sounds: where they come from

Gold Experience's T (giving life) and H (the tree of life) sounds (2.153.0,
`src/main/resources/assets/celestialfall/sounds/gold_life_*.ogg`, `gold_tree_*.ogg`) and its basic attack's punches
(2.154.0, `gold_punch_swing_*.ogg`, `gold_punch_hit_*.ogg`: user request "마지막으로 평타 만들자 효과음도 잘 넣어서", asked
and answered: "주먹 소리 + 4타에 '무-다!'") are laid together from these
recordings by `tools/stand/make_gold_sounds.py` (stretches of them, mono, faded at the cuts, some played faster or slower,
backwards, or under a high-pass filter to take the hum of an outdoor recording off, each at its level among the others,
the whole set to its loudness under a limiter; nothing synthesized). The user asked for exactly that: "t스킬과 H스킬은
너가 창작해 ai소리로 넣지말고 어디서 다운로드 해서 넣던지". Every one is dedicated to the public domain under
**CC0 1.0 Universal** (https://creativecommons.org/publicdomain/zero/1.0/): no attribution is required; it is given here
anyway. The recordings themselves are not in the repository (the tool reads them as downloaded:
`bsb_<number>.wav`, from `https://bigsoundbank.com/UPLOAD/bwf-en/<number>.wav`).

| recording | by | page | used in |
|---|---|---|---|
| One frog | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/one-frog-s0819.html | `gold_life_croak_1`–`_3` (6.36, 9.11, 12.87 s), `gold_life_born` (its croak) |
| Fly and Glass | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/fly-and-glass-s0759.html | `gold_life_fly_buzz` (30.45–33.2 s, looped) |
| Chimes "dream" #5 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/chimes-dream-5-s2083.html | `gold_life_gather`, `gold_life_born`, `gold_life_frog_back`, `gold_life_fly_back`, `gold_tree_touch` (some backwards) |
| Chimes "Dream" #1 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/chimes-dream-1-s2079.html | `gold_life_gather`, `gold_life_frog_burst`, `gold_tree_touch` |
| Chimes "dream" #3 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/chimes-dream-3-s2081.html | `gold_life_fly_born` |
| Chimes "butterfly" #1 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/chimes-butterfly-1-s2087.html | `gold_life_reflect`, `gold_life_frog_back` |
| Champagne cork #2 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/champagne-cork-2-s0648.html | `gold_life_born`, `gold_life_frog_burst`, `gold_life_strike` (the pop) |
| Cap cider #1 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/cap-cider-s0547.html | `gold_life_frog_back` (the item's pop) |
| Whoosh #4 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/whoosh-4-s1796.html | `gold_tree_burst` (its user flung up), `gold_punch_swing_4` (the whole body behind it) |
| Whoosh #5 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/whoosh-5-s1797.html | `gold_life_strike`, `gold_life_fly_back`, `gold_punch_swing_4`, `gold_punch_hit_4` (the air as they are thrown) |
| Whoosh #6 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/whoosh-6-s1799.html | `gold_life_punch`, `gold_life_reflect`, `gold_punch_swing_2` |
| Whoosh #9 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/whoosh-9-s1802.html | `gold_life_hop`, `gold_life_frog_back`, `gold_punch_swing_1` (a little weight under it) |
| Sword through the air | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/sword-through-the-air-s0128.html | `gold_punch_swing_1` (the jab's quick swish, faster) |
| Whoosh #3 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/whoosh-3-s1795.html | `gold_punch_swing_2` (the cross) |
| Whoosh #7 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/whoosh-7-s1800.html | `gold_punch_swing_3` (the hook's arm swept round) |
| Whoosh #10 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/whoosh-10-s1798.html | `gold_punch_swing_3` |
| Whoosh #8 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/whoosh-8-s1801.html | `gold_punch_swing_4` |
| Slaps | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/slaps-s0597.html | `gold_life_swat` (the slap), `gold_life_hop` (a pat, faster) |
| Punch #5 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/punch-5-s2460.html | `gold_life_strike` (the blow), `gold_punch_hit_4` (the crack) |
| Punch #6 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/punch-6-s2461.html | `gold_punch_hit_3` (a snap over it, high-passed) |
| Punch #8 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/punch-8-s2463.html | `gold_punch_hit_2` (the smack) |
| Punch #11 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/punch-11-s2466.html | `gold_punch_hit_3` (a meatier smack) |
| Punch #12 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/punch-12-s2467.html | `gold_punch_hit_1` (the jab's sharp smack) |
| Punches #3 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/punches-3-s0806.html | `gold_punch_hit_1`–`_4` (the thump in a body: one of its blows each, low-passed) |
| Body Fall #2 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/body-fall-2-s2453.html | `gold_life_fall`, `gold_life_strike` (its weight), `gold_punch_hit_1`–`_4` (a body's weight behind each blow) |
| Gore (pepper) #2 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/gore-pepper-2-s2594.html | `gold_life_strike` (wetly) |
| Gore (pepper) #4 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/gore-pepper-4-s2596.html | `gold_life_swat` (the squelch) |
| Steps in the Mud | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/steps-in-the-mud-s0495.html | `gold_life_swat` |
| Jumping on Grass #1 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/jumping-on-grass-1-s1839.html | `gold_tree_touch` (the palm on the ground), `gold_tree_wither` |
| Jumping on dirt road #1 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/jumping-on-dirt-road-1-s1837.html | `gold_tree_burst` (the thud in the ground) |
| Jumping on dirt road #2 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/jumping-on-dirt-road-2-s1838.html | `gold_tree_burst` |
| Fall of Stone | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/fall-of-stone-s1022.html | `gold_tree_burst` (stones knocked loose) |
| Chainsaw and falling tree | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/chainsaw-and-falling-tree-s2750.html | `gold_tree_burst` (18.62–19.95 s: the tree's wood tearing; none of the saw) |
| Broken twigs #1 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/broken-twigs-1-s1299.html | `gold_tree_burst` (twigs snapping) |
| Broken twigs #2 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/broken-twigs-2-s1300.html | `gold_tree_burst` |
| Steps on the Twigs | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/steps-on-the-twigs-s1301.html | `gold_tree_wither` (twigs crackling) |
| Floating Floor Squeaks | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/floating-floor-squeaks-s0518.html | `gold_tree_burst`, `gold_tree_wither` (a groan of wood, slower) |
| Miscanthus sinensis #1 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/miscanthus-sinensis-1-s1811.html | `gold_tree_burst` (its leaves) |
| Miscanthus sinensis #2 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/miscanthus-sinensis-2-s1812.html | `gold_tree_knock_1`, `_2` |
| Miscanthus sinensis #3 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/miscanthus-sinensis-3-s1813.html | `gold_tree_burst`, `gold_tree_knock_3` |
| Feet in leaves #2 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/feet-in-leaves-2-s2889.html | `gold_tree_wither` (dry leaves) |
| Dig With a Shovel | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/dig-with-a-shovel-s1305.html | `gold_tree_wither` (soil) |
| Ax on log | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/ax-on-log-s0705.html | `gold_tree_knock_1`–`_3` (the thud on wood) |
| Jumping on wooden pontoon #1 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/jumping-on-wooden-pontoon-1-s1841.html | `gold_tree_knock_1`, `_2` (its body) |
| Jumping on wooden pontoon #2 | Joseph SARDIN, BigSoundBank.com | https://bigsoundbank.com/jumping-on-wooden-pontoon-2-s1842.html | `gold_tree_knock_3` |

BigSoundBank (https://bigsoundbank.com/licenses.html): "Creative Commons CC0 1.0 Universal ... Public Domain
Dedication"; each sound page: "CC0 (public domain): Free and royalty-free".

Gold Experience's R and Y sounds (`gold_experience_overdrive`, `gold_experience_mend`, `gold_experience_self_heal`) are the
user's own recordings, only cut (`tools/stand/cut_gold_sounds.py`). The basic attack's fourth punch shouts R's "무-다!"
(`gold_experience_overdrive`, the same file).
