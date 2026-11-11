# ToolKit Keeper

Quest ToolKit(`com.sdh.metavision`)을 보조하는 Quest용 앱. ToolKit 자체는 수정하지 않는다.

- **부팅 시**: ADBActivator가 `127.0.0.1:5555`를 열고 안정될 때까지(12초) 기다린 뒤, ToolKit이 5555에 연결돼 있지 않으면 강제 종료 후 재실행(최대 3회).
- **상시**: 2초마다 `am stack list`로 화면에 보이는 앱을 확인. Meta/시스템 앱이 아닌 앱이 보이다가 사라졌는데 ToolKit도 안 보이면 ToolKit을 앞으로 띄운다. 앱 화면의 스위치로 끌 수 있다.
- ADB 명령은 앱에 내장한 [dadb](https://github.com/mobile-dev-inc/dadb)로 헤드셋 자신의 adbd에 접속해 실행한다. 최초 1회 헤드셋에서 디버깅 허용(항상 허용)이 필요하다.

## 빌드 / 설치

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
