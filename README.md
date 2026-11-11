# ToolKit Keeper

Quest ToolKit(`com.sdh.metavision`)을 보조하는 Quest용 앱. ToolKit 자체는 수정하지 않는다.

- **부팅 시**: ADBActivator가 `127.0.0.1:5555`를 열고 안정될 때까지(12초) 기다린 뒤, ToolKit이 5555에 연결돼 있지 않으면 강제 종료 후 재실행(최대 3회).
- **상시**: 2초마다 `am stack list`로 화면에 보이는 앱을 확인. Meta/시스템 앱이 아닌 앱이 보이다가 사라졌는데 ToolKit도 안 보이면 ToolKit을 앞으로 띄운다. 앱 화면의 스위치로 끌 수 있다.
- ADB 명령은 앱에 내장한 [dadb](https://github.com/mobile-dev-inc/dadb)로 헤드셋 자신의 adbd에 접속해 실행한다. 최초 1회 헤드셋에서 디버깅 허용(항상 허용)이 필요하다.

## 설치 방법

### 준비

- Quest 개발자 모드가 켜져 있어야 한다 (Meta Horizon 앱 → 기기 → 개발자 모드).
- Quest ToolKit(개발자 배포처에서 구매)과 [ADBActivator](https://github.com/qtkit/ADBActivator/releases)가 설치돼 있고, ADBActivator README대로 최초 1회 `adb tcpip 5555` + "항상 허용"까지 마친 상태여야 한다. Keeper는 ADBActivator가 여는 5555 포트를 사용한다.

### 1. APK 받기

[Releases](https://github.com/hanjungwoo3/ToolKitKeeper/releases/latest)에서 `ToolKitKeeper-<버전>.apk`를 받는다.

### 2. Quest에 설치

PC에 USB로 연결한 뒤:

```sh
adb install -r ToolKitKeeper-<버전>.apk
```

SideQuest 등 다른 사이드로드 도구로 설치해도 된다. 설치된 앱은 라이브러리의 **알 수 없는 출처**에 `ToolKit Keeper`로 보인다.

### 3. 최초 실행 (1회)

1. 헤드셋에서 `ToolKit Keeper`를 실행한다.
2. 알림 권한 창이 뜨면 **허용**한다.
3. "USB 디버깅을 허용하시겠습니까?" 창이 뜨면 **이 컴퓨터에서 항상 허용**에 체크하고 **허용**한다. 이 키는 PC가 아니라 Keeper 앱의 키다.
4. 앱 화면 상태가 `ADB 연결됨`으로 바뀌면 끝이다. 이후로는 부팅할 때마다 자동으로 동작한다.

허용 창이 뜨지 않으면 아래 [디버깅](#디버깅)을 참고한다.

### 업데이트 / 삭제

- 업데이트: 새 버전 APK를 같은 명령(`adb install -r`)으로 덮어 설치한다. Release APK는 같은 키로 서명되므로 지우지 않아도 되고, 디버깅 허용도 유지된다.
- 삭제: `adb uninstall com.questhelper.tkkeeper` 또는 헤드셋의 앱 설정에서 삭제.

## 빌드

```sh
JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home ./gradlew assembleRelease
adb -d install -r app/build/outputs/apk/release/app-release.apk
```

release 빌드는 debug 키로 서명한다(개인 사이드로드용).

## 디버깅

```sh
adb logcat | grep TKKeeper
```

허용 창이 안 뜨고 adbd 로그에 `prompt currently pending, skipping`이 보이면 `adb -d tcpip 5555`로 adbd를 재시작하면 새 창이 뜬다.
