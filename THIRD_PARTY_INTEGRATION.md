# Third-party distributions

The installation archive ships these original, unmodified upstream JARs beside Botand.
Their licenses apply separately to their respective contents. Botand's existing LICENSE is retained.

| Distribution | Declared license | Source |
|---|---|---|
| Player Animator 1.0.2-rc1+1.20 | MIT | https://github.com/KosmX/minecraftPlayerAnimator |
| GeckoLib 4.8.4 | MIT | https://github.com/bernie-g/geckolib |
| Veil 1.0.0.296 / Fabric 1.20.1 | Upstream 1.20 branch: LGPL-3.0; pinned fabric.mod.json says All Rights Reserved | https://github.com/FoundryMC/Veil/tree/1.20 |
| Immersive Cinematics 0.3.5 | MIT | https://github.com/SanZiNEO/ImmersiveCinematics |
| Fabric API 0.92.2+1.20.1 | Apache-2.0 | https://github.com/FabricMC/fabric |

Original license notices and nested dependencies remain inside each upstream JAR.
Exact downloaded file URLs and SHA-512 digests are in DEPENDENCIES.lock.json.
Photon, LDLib and YSM are not distributed in 2.31.2 through 2.31.10.

The original Veil artifact and its matching source artifact are supplied unchanged. The upstream 1.20 branch license and GPL text are in `third_party/veil`. The legacy JAR metadata license text is preserved as-is; it differs from the upstream branch license. Nested ImGui/LWJGL/Molang files keep their original notices. Veil can be replaced as a separate JAR.

Veil binary URL: https://maven.blamejared.com/foundry/veil/Veil-fabric-1.20.1/1.0.0.296/Veil-fabric-1.20.1-1.0.0.296.jar

Matching source URL: https://maven.blamejared.com/foundry/veil/Veil-fabric-1.20.1/1.0.0.296/Veil-fabric-1.20.1-1.0.0.296-sources.jar

The Git repository keeps the binary in `libs/` but does not commit the source artifact; it is available unchanged at the matching source URL above.

Official Maven SHA-1 was verified: `85a91a10ee1595dbbb70e089d837e3bc52c0df56`. The local SHA-512 is recorded in the lock file.
