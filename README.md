# tabcontrol
galaxy tab control

## APK 다운로드

### 자동 빌드된 APK 다운로드
매번 main 브랜치에 푸시될 때마다 자동으로 APK가 빌드되고 GitHub Releases에 업로드됩니다.

**다운로드 방법:**
1. [Releases 페이지](../../releases)로 이동
2. 최신 릴리스 선택
3. Assets 섹션에서 APK 파일 다운로드:
   - `app-debug.apk` - 디버그 버전 (테스트용)
   - `app-release.apk` - 릴리스 버전 (배포용)

### 로컬에서 빌드
```bash
# 디버그 APK 빌드
./gradlew assembleDebug

# 릴리스 APK 빌드
./gradlew assembleRelease
```

빌드된 APK 위치:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`
