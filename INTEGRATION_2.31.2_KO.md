# 보탠드 2.31.2 — 새 2.30.3 업로드 기반 연동

> **2.31.4 갱신**: G의 Veil 파이프라인 `celestialfall:atomic_overwhelm`이 여러 단계(밝은 부분 추출 → 가로·세로 블러 → 빛줄기 → 합성)로 늘어, 피해 범위 밖에서 기둥을 보는 모든 시청자에게 기둥 빛을 화면으로 번지게 합니다. 의존성 버전과 설치 JAR 6개는 그대로입니다. 기둥이 넓어지는 속도와 서버 피해 범위, 전용 맵 상자 점검은 [RELEASE_2.31.4_KO.md](RELEASE_2.31.4_KO.md)를 확인하세요.
>
> **2.31.3 갱신**: Veil이 처음으로 실제 스킬에 연결되었습니다. G 아토믹을 피해 범위 밖 가까이에서 볼 때 Veil 후처리 `celestialfall:atomic_overwhelm`이 서버 시전 시계에 맞춰 켜집니다. 기둥의 에너지 줄기는 보탠드 VFX 레이어(GPU 메시 + 정점 셰이더)로 만들었습니다. 또 2.31.2에서 Veil 때문에 보탠드 화면 처리(아토믹 내부 보라색 화면, 섬광 프레임, 블룸, 레터박스)가 지워지던 문제를 고쳤습니다. 자세한 내용은 [RELEASE_2.31.3_KO.md](RELEASE_2.31.3_KO.md)를 확인하세요. 아래 표에 2.31.3 기준 연결 상태를 정리했습니다.

이번에 업로드한 **Botand 2.30.3**을 기준으로 Immersive Cinematics와 Veil을 연결했습니다. Player Animator와 GeckoLib 연동도 포함했습니다. Photon과 LDLib는 포함하지 않습니다. **Immersive Cinematics는 하나의 모드 이름**입니다.

2.30.3에서 추가한 아토믹 외부 시점의 하늘·안개·조명·구름·충격파·레터박스 연출을 보존했습니다. 관련 코드·셰이더와 새 테스트 6개는 업로드본과 바이트 단위로 동일합니다. 두 버전에서 함께 바뀐 `GameRendererMixin.java`와 클라이언트 믹신 목록만 양쪽 변경을 병합했습니다.

## 설치

- Minecraft Java 1.20.1 / Java 17 / Fabric Loader 0.16.14.
- 설치 ZIP의 `mods` 안 JAR 6개를 게임의 `mods` 폴더에 넣습니다.
- 이전 Botand JAR, Photon JAR, 별도로 넣었던 LDLib JAR은 이 보탠드 구성에서 제거합니다. 다른 모드가 LDLib를 요구한다면 그 의존성은 별개입니다.
- 같은 모드의 버전 두 개를 함께 두지 마세요. Fabric API도 중복 설치하지 마세요.
- Veil의 내부 라이브러리와 GeckoLib의 mclib는 원본 JAR 안에 포함되어 있으므로 따로 설치하지 않습니다.

## 실제 연결 범위

| 모드 | 고정 배포본 | 연결 내용 |
|---|---|---|
| Immersive Cinematics | Fabric 1.20.1 / 0.3.5 | JSON 카메라 재생, 위치·FOV·롤·조작 소유권 조정, 종료 후 기존 카메라에 인계 |
| Veil | Fabric 1.20.1 / 1.0.0.296 | JSON 후처리 파이프라인과 GLSL 셰이더 예제, 카메라와 동시 실행, 시간 종료·수동 종료·접속 종료 시 해제 |
| Player Animator | 1.0.2-rc1+1.20 | 기존 관절 모델의 기본 뼈를 애니메이션 스택으로 연결, JSON 클립 레이어 |
| GeckoLib | Fabric 1.20.1 / 4.8.4 | 훈련용 더미 모델과 기존 전투 자세 연결 |

Veil의 Maven 배포 버전은 `1.0.0.296`이고 JAR 내부 Fabric 버전은 `1.0.0`입니다. 설치 파일 이름과 `DEPENDENCIES.lock.json`의 해시로 정확한 배포본을 구분합니다.

### 실제 스킬 적용 현황 (2.31.4)

| 대상 | 현재 구현 | 새 라이브러리 적용 |
|---|---|---|
| G 아토믹 외부 기둥 | 보탠드 VFX 레이어(`client/vfx`, `client/atomic/AtomicPillar`): GPU 원기둥 + 노이즈 재질 + 에너지 줄기 2,600개(정점 셰이더) | 기둥 빛(빛무리·헤일로·빛줄기, 2.31.4)과 근거리 압도 화면을 **Veil 후처리** 여러 단계로 실제 연결(`client/integration/AtomicOverwhelm`) |
| G 아토믹 내부(피격 시점) | 2.30.1 렌더러 + 보라색 화면(`AnimePostProcessor`) + 지진 | 적용하지 않음(유지 요구) |
| G 외부 환경(하늘·조명·구름·충격파·레터박스·폭음) | 2.30.3 그대로 + 근거리 지진·시야 맥동 추가 | 없음 |
| H 카메라 컷신 | 기존 `CinematicDirector` | Immersive Cinematics로 재제작하지 않음(예제만 있음) |
| 스킬 모션(평타·E·T·H·G 등) | 기존 관절 모델·`KagenoClips` | Player Animator 레이어는 연결만, 스킬 클립은 미이전 |
| 훈련용 더미 | GeckoLib 모델로 표시 | 적용됨(2.31.2) |
| 다른 플레이어에게 보이는 연출 | 서버 시전 패킷 + 각자의 시전 시계 | 새 G 연출도 같은 시계로 계산(별도 패킷 없음). `/botandtools` 예제는 로컬 전용 |

기존 H/G/E/T/평타 모션, 피해 범위, H 카메라 경로, G 내부 보라색 효과와 지진, 아토믹 기둥·외부 환경 연출 코드는 업로드한 2.30.3을 유지했습니다. Veil 설치만으로 기존 스킬이 자동으로 새 연출로 바뀌는 것은 아닙니다. 실제 스킬을 Veil로 재제작하기 위한 API와 코드 예제를 사용할 수 있는 상태입니다.

## 게임 내 명령어

- `/botandtools camera`: 6초 JSON 카메라 예제.
- `/botandtools veil`: 8초 보라색 후처리 예제. 카메라 예제와 동시에 실행할 수 있습니다.
- `/botandtools animation`: JSON 팔 동작 예제.
- `/botandtools stop`: 모든 제작 예제 종료.
- `/botandtools status`: Veil 로딩/렌더 프레임, 카메라 점유, 애니메이션과 더미 처리 상태.

제작 예제는 로컬 클라이언트 전용입니다. 기존 스킬은 서버 패킷과 기존 시전 시간축을 사용합니다. 기존 H 컷씬이 시작되면 카메라 미리보기를 중단하고, Veil 미리보기도 다음 클라이언트 틱에 해제합니다.

## AI가 수정할 파일

- 카메라: `src/main/resources/assets/celestialfall/cinematics/camera_demo.json`
- Veil 후처리 구성: `src/main/resources/assets/celestialfall/pinwheel/post/veil_preview.json`
- Veil 셰이더: `src/main/resources/assets/celestialfall/pinwheel/shaders/program/veil_preview.fsh`
- Veil 연동/수명 관리: `client/integration/VeilBridge.java`
- G 기둥 빛·근거리 압도 화면(Veil): `pinwheel/post/atomic_overwhelm.json`, `pinwheel/shaders/program/atomic_bright.fsh`·`atomic_blur_h.fsh`·`atomic_blur_v.fsh`·`atomic_rays.fsh`·`atomic_overwhelm.fsh`, `client/integration/AtomicOverwhelm.java`
- G 기둥 재질·에너지 줄기: `assets/celestialfall/shaders/core/vfx_energy.vsh/.fsh`, `client/atomic/AtomicPillar.java`, 시간·거리 함수 `client/atomic/AtomicStage.java`
- G 기둥이 넓어지는 속도와 서버 피해 반경(같은 함수): `AtomicTimeline.java`(`OPEN_SPEED`, `openingRadius`, `damageRadius`)
- 플레이어 동작: `assets/celestialfall/player_animation/botand_preview.json`
- 기존 스킬 모션: `client/kageno/` 아래 기존 코드.

GUI 편집기 없이 JSON·GLSL·Java를 수정할 수 있습니다. 새 게임플레이 연출을 만들 때는 기존 시전 시계와 연결하고 피해 판정은 서버에 유지하세요. 현재 예제를 플레이어 모두에게 전파하는 네트워크 기능은 추가하지 않았습니다.

## 호환 처리

Immersive Cinematics 0.3.5의 공통 초기화가 전용 서버에서 클라이언트 클래스에 접근하는 문제는 `ImmersiveNetworkServerMixin`으로 보정했습니다. 서버에서는 원래 서버 수신 채널 6개를 등록하고, 클라이언트에서는 원본 초기화를 사용합니다. 외부 모드 JAR은 수정하지 않았습니다. 카메라 종료/접속 종료의 청크 정리를 서버 스레드에서 수행하도록 `ImmersiveDisconnectMixin`도 추가했습니다. 접속 종료 직후 남은 청크 사전 로딩/확인 응답 틱이 닫힌 연결에 패킷을 보내지 않도록 클라이언트 전송 가드도 연결했습니다.

Veil 후처리 예제는 정점/프래그먼트 인터페이스가 일치하는 자체 셰이더를 사용합니다. 셰이더 링크가 실패하면 미리보기를 시작하지 않습니다.

Veil의 중첩 라이브러리는 개발 실행을 위해 `libs/veil-nested`에 노출했습니다. 설치 ZIP에서는 원본 Veil JAR에 내장된 파일을 사용합니다.

## 빌드와 검증

Java 17, Gradle 8.6, Fabric Loom 1.1.14. `gradlew build` 또는 Windows의 `gradlew.bat build`. 최초 빌드는 Minecraft/Fabric 의존성 다운로드가 필요합니다.

기존 JUnit 테스트 **206개**, 실제 게임 실행 검사 **34개**를 통과했습니다. R 흑백, P 취소, E/T/홀드 T/평타/H/G, Photon·LDLib 미로딩, Veil 셰이더의 실제 픽셀 출력, 카메라 동시 재생과 자동/수동 종료, 재생 중 접속 종료 후 정리, 2.30.3 아토믹 외부 시점의 하늘 어둠과 복귀를 확인했습니다.

전용 서버는 모든 모드 공통 초기화 후 EULA 확인 단계까지 정상 진행했습니다. 전용 서버 월드 실행·외부 클라이언트 접속은 검사하지 않았습니다. (2.31.3에서 실제 배포 구성의 전용 서버 + 클라이언트 2개로 월드 실행·접속·전 스킬·접속 종료까지 확인했습니다. [RELEASE_2.31.3_KO.md](RELEASE_2.31.3_KO.md) 참고.)

실행 검증 결과는 `validation/2.31.2/`에 포함됩니다. 자동 촬영은 `tools/kageno/run_integration_capture.py`로 재현할 수 있으며 검증용 모드는 배포 JAR에 포함되지 않습니다. 소프트웨어 OpenGL 환경의 검사이며 실제 GPU 성능이나 Iris/Sodium 병용을 보증하는 검사는 아닙니다.

기본 업로드의 `RELEASE_2.30.3_KO.md`는 아토믹 개선 내역입니다. 현재 설치·의존성 안내는 이 문서와 `DEPENDENCIES.lock.json`을 따릅니다. `MERGE_2.31.2.json` 및 검증 폴더의 변경 파일 목록으로 병합 범위를 확인할 수 있습니다.
