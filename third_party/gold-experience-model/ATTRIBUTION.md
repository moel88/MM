# Gold Experience's body (2.155.0)

Gold Experience's 3D body in the game is taken from:

- **"Giorno Giovanna & gold experience"** by **shamus** (https://sketchfab.com/super_sonic_agncies_BETA)
- Source: https://sketchfab.com/3d-models/giorno-giovanna-gold-experience-39f6d565da584521b7b5939fadb76dbb
- Licence (as given on the model's page and in the file): **CC BY 4.0**, http://creativecommons.org/licenses/by/4.0/

What was changed (tools/stand/import_gold_experience.py, from the .glb the user sent; the file itself is not kept in
this repository):

- only Gold Experience is used (the file's figure `Mesh_0112.rip`; Giorno, `Mesh_0113.rip`, and the backdrop are left out);
- its arms and legs are turned from the T-pose it is bound in to the game's rest pose (arms down, palms in, legs straight)
  and its trunk stood upright from side to side, by its own skin;
- it is scaled to 40.49 units (2.0 blocks in the game) to the top of its helmet and set in the game's frame;
- its skin is handed from its Mixamo skeleton to the game's skeleton (each joint's weight to the bone it moves with);
- its texture is cropped to the upper half of the file's (Gold Experience's half).

The mesh names in the file (`.rip`) suggest it was ripped from a game; the original rights to the character and its
design belong to their owners.

The same notice ships inside the mod as `assets/celestialfall/licenses/GoldExperienceModel-CC-BY-4.0.txt`.
