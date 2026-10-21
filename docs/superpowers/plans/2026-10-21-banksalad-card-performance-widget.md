# 뱅크샐러드 카드 실적 위젯 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 뱅크샐러드 '실적 관리' 화면의 카드별 월 실적을 접근성 화면 읽기로 수집해 홈 위젯에 표시하는 별도 안드로이드 앱을 만든다.

**Architecture:** 상시 켜진 `AccessibilityService`(`PerfService`)가 모든 갱신을 주관한다. 서비스가 `ACTION_USER_PRESENT`를 동적으로 수신하고, 딥링크로 뱅크샐러드 실적 화면을 띄우고, 덮개를 올리고, 노드 텍스트를 읽어 저장한 뒤 위젯을 다시 그린다. 파싱·정책·표시 문구는 순수 Kotlin 객체로 분리해 JVM 단위 테스트로 검증하고, 안드로이드 결합 부분은 실기에서 검증한다.

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.2, Gradle 8.11.1(wrapper), compileSdk 35, minSdk 31, targetSdk 34, JUnit 4.13.2, org.json(테스트용 `org.json:json:20240303`).

**Spec:** `docs/superpowers/specs/2026-10-21-banksalad-card-performance-widget-design.md`

## 계획 수립 중 실기에서 확정한 사실 (스펙 7절의 미확정 사항)

- 기기: Android 12, SDK 31 → `minSdk = 31`.
- **실적 화면 직행 딥링크가 존재한다:** `banksalad://webview?url=https%3A%2F%2Fwebview.banksalad.com%2Fcard%3Ftab%3Dbenefit` → `OpenWebViewActivity`가 '카드 혜택' 탭으로 열리고 `실적 관리`, `실적 기간 : 10.1~10.31` 노드가 노출된다. (`?tab=` 없이 `/card`만 열면 다른 탭이 선택될 수 있다.)
- WebView 접근성 트리는 화면 밖 노드까지 전부 노출한다(페이지 하단 안내문까지 읽혔음). 따라서 스크롤 없이 전체 트리를 한 번에 순회한다. 카드가 늘어도 같은 방식으로 수집된다.
- 카드 노드 실측 텍스트:
  - `샐러드 신용카드 412,300원 30만원 실적 달성 완료 축하` (완료 뒤에 꼬리 문구가 붙는다)
  - `그린 체크카드 258,500원 30만원 실적 달성 41,500원 남음`
  - `여행 체크카드 0원 30만원 실적 달성 300,000원 남음`
  - 미연결 카드: 이름은 별도 노드(`미연결은행`), 상태 노드는 `실적 달성 ??원 남음` → 정규식에 걸리지 않으므로 자연스럽게 제외된다.

## 스펙과 다르게 정한 구현 결정 (근거 포함)

- **덮개는 `TYPE_ACCESSIBILITY_OVERLAY`로 띄운다.** 접근성 서비스가 직접 띄우는 덮개는 `SYSTEM_ALERT_WINDOW` 권한이 필요 없어서 사용자가 켤 권한이 하나 줄어든다. 동작(불투명, 탭하면 중단)은 스펙과 같다.
- **`ACTION_USER_PRESENT`는 서비스 안에서 동적 등록한다.** 이 브로드캐스트는 매니페스트 리시버로 받을 수 없는 암시적 브로드캐스트다. 또한 시스템이 바인딩한 접근성 서비스는 백그라운드 액티비티 실행 제한의 예외이므로, 뱅크샐러드 실행도 서비스에서 한다(스펙 8절 리스크 대응).
- **수동 갱신(위젯 탭)은 덮개 없이 진행한다.** 사용자가 실적 화면을 보려고 누른 것이므로 화면을 가리지 않고, 값을 읽은 뒤에도 화면을 그대로 둔다.
- **쿨다운(6시간)은 마지막 '시도' 시각 기준이다.** 실패·중단 뒤 매 잠금 해제마다 화면을 점유하지 않게 하기 위함이다. 표시용 '마지막 성공 시각'은 따로 저장한다.
- **화면 이동 방식:** 딥링크만 사용한다. 딥링크로 열었는데 `실적 기간`이 없으면 '카드 혜택' 텍스트 노드(또는 그 부모 중 clickable)를 한 번 누르고 다시 기다린다.
- **warm 스타트:** 갱신 종료 시 `GLOBAL_ACTION_BACK` 1회(웹뷰 액티비티 닫기) 후 `GLOBAL_ACTION_HOME`. 뱅크샐러드 프로세스는 최근 앱에 남겨 다음 갱신이 빨라지게 한다.

## Global Constraints

- 패키지/applicationId: `com.wonhy.cardperf`, 앱 이름 `카드 실적 위젯`.
- 대상 패키지: `com.rainist.banksalad2`. 접근성 설정의 `packageNames`도 이 값으로 제한한다.
- 뱅크샐러드 APK 변조, 서버 API 호출, 토큰 추출, root, 보안 잠금 해제 시도는 하지 않는다(스펙 5절 안티목표).
- 자동 갱신 쿨다운 기본 6시간, 오래됨 기준 기본 24시간, 갱신 타임아웃 45초(콜드 스타트 약 9.5초 + 웹뷰 로딩 여유).
- 위젯 A안: 다크 배경, 초록 진행 막대, 완료 강조, 카드마다 `카드명 / 사용액 / 목표 / 진행 막대 / 완료 또는 N원 남음`, 상단에 실적 기간, 구석에 갱신 상태.
- 빌드는 `widget/` 디렉터리의 Gradle wrapper로, 설치는 `adb install -r`로 한다.

## Review Focus

1. **값이 잠깐 덜 그려진 화면을 읽는 경우** → 카드 3개 중 1개만 읽고 성공 처리하면 안 된다. 같은 결과가 연속 2회 폴링(500ms 간격)에서 동일할 때만 확정한다. (Task 4 `ReadStabilizer` 테스트)
2. **목표 금액 표기 변형**(`30만원`, `100만원`, `1,000,000원`, `1.5만원`) → 진행률 계산이 깨지면 안 된다. 해석 불가 시 `사용액/(사용액+남은 금액)`으로 대체한다. (Task 2 테스트)
3. **사용액이 목표를 넘은 완료 카드**(412,300 / 300,000) → 진행 막대는 100%에서 멈추고 '완료'로 표시된다. (Task 2 테스트)
4. **달이 바뀐 뒤 지난달 값만 남은 경우** → 기간 표시 옆에 '지난달'이 드러나야 한다. 12월→1월 경계도 동일하게 판정한다. (Task 3 테스트)
5. **덮개가 뜬 채로 갱신이 멈추는 경우**(뱅크샐러드가 안 뜸, 예외 발생) → 타임아웃·예외 어느 경로에서도 덮개가 내려가고 실패가 기록된다. (Task 5 `finish()` 단일 종료 경로 + Task 7 실기 검증)

---

## File Structure

```
widget/                                   Gradle 루트 (settings, wrapper, local.properties)
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/wonhy/cardperf/
    model/Performance.kt          CardPerf, PerfSnapshot 데이터 클래스 + JSON 직렬화
    parse/PerfParser.kt           노드 텍스트 목록 → PerfSnapshot (순수)
    parse/Money.kt                "30만원"/"1,000,000원" → Long (순수)
    policy/RefreshPolicy.kt       쿨다운 판정 (순수)
    policy/WidgetStatus.kt        갱신 상태 문구·오래됨·지난달 판정 (순수)
    policy/ReadStabilizer.kt      연속 동일 결과 확정 (순수)
    store/PerfStore.kt            SharedPreferences 저장/로드
    service/PerfService.kt        AccessibilityService: 트리거 수신, 갱신 실행
    service/RefreshSession.kt     한 번의 갱신 상태 머신 (실행·폴링·타임아웃·종료)
    service/Overlay.kt            덮개 뷰 표시/제거
    widget/PerfWidgetProvider.kt  AppWidgetProvider, RemoteViews 구성
    widget/RefreshTrampolineActivity.kt  위젯 탭 → 수동 갱신 요청
    MainActivity.kt               권한 안내 + 접근성 설정 열기 + 현재 상태 표시
  app/src/main/res/ (layout/widget_perf.xml, layout/widget_row.xml 포함 행 6개, drawable/*, xml/perf_service.xml, xml/perf_widget_info.xml, values/strings.xml)
  app/src/test/java/com/wonhy/cardperf/ (각 순수 객체 테스트)
```

---

### Task 1: 프로젝트 골격과 빌드

**Files:**
- Create: `widget/settings.gradle.kts`, `widget/build.gradle.kts`, `widget/gradle.properties`, `widget/local.properties`, `widget/app/build.gradle.kts`, `widget/app/src/main/AndroidManifest.xml`, `widget/app/src/main/java/com/wonhy/cardperf/MainActivity.kt`, `.gitignore`

**Interfaces:**
- Produces: 빌드 가능한 `:app` 모듈, `./gradlew :app:testDebugUnitTest`와 `:app:assembleDebug`가 동작하는 환경.

- [ ] **Step 1:** 프로젝트 루트(`C:\Projects\banksalad`)에서 `git init`. `.gitignore`에 `re/`, `.superpowers/`, `widget/.gradle/`, `widget/**/build/`, `widget/local.properties`를 넣는다.
- [ ] **Step 2:** 스파이크 구성(AGP 8.5.2, Kotlin 1.9.24, compileSdk 35, minSdk 31, targetSdk 34, Java 17)을 그대로 따라 Gradle 파일을 만든다. 의존성: `androidx.core:core-ktx:1.13.1`, `testImplementation("junit:junit:4.13.2")`, `testImplementation("org.json:json:20240303")`. `android.testOptions.unitTests.isReturnDefaultValues = true`.
- [ ] **Step 3:** 캐시된 Gradle 8.11.1(`~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle`)로 `gradle wrapper --gradle-version 8.11.1`을 실행해 wrapper를 만든다.
- [ ] **Step 4:** 빈 `MainActivity`로 `./gradlew :app:assembleDebug` 실행. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5:** Commit `chore: scaffold card performance widget project`.

### Task 2: 금액 해석과 실적 파서

**Files:**
- Create: `model/Performance.kt`, `parse/Money.kt`, `parse/PerfParser.kt`
- Test: `test/.../parse/MoneyTest.kt`, `test/.../parse/PerfParserTest.kt`

**Interfaces:**
- Produces:
  - `data class CardPerf(val name: String, val used: Long, val goalText: String, val goal: Long?, val done: Boolean, val remaining: Long)` + `val progress: Float` (완료면 1f; `goal`이 있으면 `used/goal`; 없으면 `used/(used+remaining)`; 0..1로 고정)
  - `data class PerfSnapshot(val period: String?, val cards: List<CardPerf>, val readAt: Long)` + `fun toJson(): String`, `companion fun fromJson(s: String): PerfSnapshot?`
  - `object Money { fun parse(text: String): Long? }`
  - `object PerfParser { fun parse(texts: List<String>, now: Long): PerfSnapshot; fun isPerfScreen(texts: List<String>): Boolean }`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// MoneyTest
assertEquals(300_000L, Money.parse("30만원"))
assertEquals(1_000_000L, Money.parse("100만원"))
assertEquals(1_000_000L, Money.parse("1,000,000원"))
assertEquals(15_000L, Money.parse("1.5만원"))
assertNull(Money.parse("??원"))

// PerfParserTest — 실측 노드 텍스트를 그대로 사용
val texts = listOf("실적 관리", "실적 기간 : 10.1~10.31", "설정",
  "샐러드 신용카드 412,300원 30만원 실적 달성 완료 축하",
  "그린 체크카드 258,500원 30만원 실적 달성 41,500원 남음",
  "여행 체크카드 0원 30만원 실적 달성 300,000원 남음",
  "미연결은행", "실적 달성 ??원 남음", "카드 연결")
val s = PerfParser.parse(texts, now = 1L)
assertEquals("10.1~10.31", s.period)
assertEquals(listOf("샐러드 신용카드", "그린 체크카드", "여행 체크카드"), s.cards.map { it.name })
assertTrue(s.cards[0].done); assertEquals(1f, s.cards[0].progress)   // 초과 사용도 100%
assertEquals(412_300L, s.cards[0].used); assertEquals("30만원", s.cards[0].goalText)
assertEquals(41_500L, s.cards[1].remaining); assertFalse(s.cards[1].done)
assertEquals(258_500f / 300_000f, s.cards[1].progress, 0.0001f)
assertEquals(0f, s.cards[2].progress)
assertTrue(PerfParser.isPerfScreen(texts)); assertFalse(PerfParser.isPerfScreen(listOf("카드값")))
// 같은 카드 텍스트가 두 번 나오면 한 번만
assertEquals(1, PerfParser.parse(listOf(texts[3], texts[3]), 1L).cards.size)
// goal 해석 불가 → used/(used+remaining)
val odd = PerfParser.parse(listOf("A카드 100원 목표미정원 실적 달성 300원 남음"), 1L).cards.single()
assertNull(odd.goal); assertEquals(0.25f, odd.progress, 0.0001f)
// JSON 왕복
assertEquals(s, PerfSnapshot.fromJson(s.toJson()))
```

- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest` → 컴파일 실패(FAIL) 확인.
- [ ] **Step 3: 구현.** 카드 정규식은 `^(.+?)\s+([\d,]+)원\s+(\S+원)\s+실적 달성\s+(완료|([\d,]+)원 남음)`(꼬리 문구를 허용하도록 `matchEntire` 대신 `^`로 고정한 `find`를 쓴다). 기간은 `실적 기간\s*:\s*(\S+)`. `isPerfScreen`은 `실적 기간`을 포함한 텍스트 존재 여부. `Money.parse`는 `^([\d,.]+)(만)?원$`.
- [ ] **Step 4:** 테스트 PASS 확인.
- [ ] **Step 5:** Commit `feat: parse banksalad performance texts`.

### Task 3: 갱신 정책과 위젯 상태 문구

**Files:**
- Create: `policy/RefreshPolicy.kt`, `policy/WidgetStatus.kt`
- Test: `test/.../policy/RefreshPolicyTest.kt`, `test/.../policy/WidgetStatusTest.kt`

**Interfaces:**
- Produces:
  - `object RefreshPolicy { const val COOLDOWN_MS = 6 * 3600_000L; fun shouldAutoRefresh(now: Long, lastAttemptAt: Long?): Boolean }`
  - `enum class LastResult { SUCCESS, FAILED, CANCELED }`
  - `object WidgetStatus { const val STALE_MS = 24 * 3600_000L; fun statusLine(now: Long, lastSuccessAt: Long?, lastResult: LastResult?, failReason: String?): String; fun isStale(now: Long, lastSuccessAt: Long?): Boolean; fun isPreviousPeriod(period: String?, now: Long, zone: java.time.ZoneId): Boolean }`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
val h = 3600_000L
assertTrue(RefreshPolicy.shouldAutoRefresh(10 * h, null))
assertFalse(RefreshPolicy.shouldAutoRefresh(10 * h, 5 * h))
assertTrue(RefreshPolicy.shouldAutoRefresh(11 * h, 5 * h))          // 정확히 6시간이면 갱신
assertTrue(RefreshPolicy.shouldAutoRefresh(1 * h, 5 * h))           // 시계가 뒤로 가면 갱신 허용

assertEquals("방금 갱신", WidgetStatus.statusLine(now = 30_000, lastSuccessAt = 0, LastResult.SUCCESS, null))
assertEquals("3시간 전 갱신", WidgetStatus.statusLine(3 * h + 1, 0, LastResult.SUCCESS, null))
assertEquals("15분 전 갱신", WidgetStatus.statusLine(15 * 60_000L, 0, LastResult.SUCCESS, null))
assertEquals("2일 전 갱신 · 오래됨", WidgetStatus.statusLine(49 * h, 0, LastResult.SUCCESS, null))
assertEquals("3시간 전 갱신 · 최근 갱신 실패", WidgetStatus.statusLine(3 * h, 0, LastResult.FAILED, "timeout"))
assertEquals("3시간 전 갱신 · 갱신 중단됨", WidgetStatus.statusLine(3 * h, 0, LastResult.CANCELED, null))
assertEquals("아직 갱신 안 됨", WidgetStatus.statusLine(h, null, null, null))
val kst = java.time.ZoneId.of("Asia/Seoul")
fun at(y: Int, m: Int, d: Int) = java.time.ZonedDateTime.of(y, m, d, 9, 0, 0, 0, kst).toInstant().toEpochMilli()
assertFalse(WidgetStatus.isPreviousPeriod("10.1~10.31", at(2026, 10, 21), kst))
assertTrue(WidgetStatus.isPreviousPeriod("10.1~10.31", at(2026, 11, 1), kst))
assertTrue(WidgetStatus.isPreviousPeriod("12.1~12.31", at(2027, 1, 2), kst))
assertFalse(WidgetStatus.isPreviousPeriod(null, at(2026, 11, 1), kst))
```

- [ ] **Step 2:** 테스트 FAIL 확인.
- [ ] **Step 3: 구현.** 상대 시각은 1분 미만 '방금', 60분 미만 'N분', 24시간 미만 'N시간', 이상 'N일'. 접미사 우선순위: 실패/중단 표시가 오래됨보다 앞선다(둘 다면 `· 최근 갱신 실패 · 오래됨`). `isPreviousPeriod`는 기간 끝 날짜의 월(`~` 뒤 `M.d`)이 현재 월과 다르면 true(연도 정보가 없으므로 월 비교만 한다).
- [ ] **Step 4:** 테스트 PASS 확인.
- [ ] **Step 5:** Commit `feat: add refresh cooldown and widget status rules`.

### Task 4: 읽기 안정화

**Files:**
- Create: `policy/ReadStabilizer.kt`
- Test: `test/.../policy/ReadStabilizerTest.kt`

**Interfaces:**
- Produces: `class ReadStabilizer(private val required: Int = 2) { fun offer(cards: List<CardPerf>): Boolean }` — 비어 있지 않은 동일 목록이 `required`회 연속 들어오면 true.

- [ ] **Step 1: 실패하는 테스트:** `[a]`→false, `[a,b]`→false, `[a,b]`→true; `[]`,`[]`→false; `[a]`,`[a,b]`,`[a]`→false.
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** PASS 확인.
- [ ] **Step 5:** Commit `feat: require stable reads before accepting`.

### Task 5: 저장소와 접근성 서비스 갱신 흐름

**Files:**
- Create: `store/PerfStore.kt`, `service/Overlay.kt`, `service/RefreshSession.kt`, `service/PerfService.kt`, `res/xml/perf_service.xml`, `res/values/strings.xml`
- Modify: `AndroidManifest.xml` (서비스 등록, `BIND_ACCESSIBILITY_SERVICE`)

**Interfaces:**
- Consumes: Task 2–4 전부.
- Produces:
  - `class PerfStore(ctx: Context) { var snapshot: PerfSnapshot?; var lastSuccessAt: Long?; var lastAttemptAt: Long?; var lastResult: LastResult?; var failReason: String? }` (prefs 이름 `perf_store`)
  - `class PerfService : AccessibilityService { companion object { @Volatile var instance: PerfService? }; fun requestRefresh(manual: Boolean) }`
  - `PerfWidgetProvider.updateAll(ctx: Context)` 호출(Task 6에서 정의, 이 Task에서는 호출부만 둔다. Task 6 전까지는 빈 함수로 먼저 만든다).

- [ ] **Step 1:** `perf_service.xml`: `typeWindowStateChanged|typeWindowContentChanged`, `canRetrieveWindowContent=true`, `flagRetrieveInteractiveWindows|flagReportViewIds`, `packageNames="com.rainist.banksalad2"`, `notificationTimeout=300`, description 문자열.
- [ ] **Step 2:** `PerfService.onServiceConnected`에서 `instance` 설정 및 `ACTION_USER_PRESENT` 리시버 동적 등록 → 수신 시 `RefreshPolicy.shouldAutoRefresh(now, store.lastAttemptAt)`이면 `requestRefresh(manual=false)`. `onDestroy`/`onUnbind`에서 해제.
- [ ] **Step 3:** `RefreshSession` 구현. 한 번에 하나의 세션만 허용(진행 중이면 무시).
  1. `lastAttemptAt = now` 기록. 자동이면 `Overlay.show(onTap = { finish(CANCELED) })`.
  2. 딥링크 인텐트 `ACTION_VIEW`, data `banksalad://webview?url=https%3A%2F%2Fwebview.banksalad.com%2Fcard%3Ftab%3Dbenefit`, package `com.rainist.banksalad2`, `FLAG_ACTIVITY_NEW_TASK`로 `startActivity`. 예외 시 `finish(FAILED, "launch")`.
  3. `Handler` 500ms 폴링: `windows`에서 `root.packageName == com.rainist.banksalad2`인 창의 루트를 찾아 텍스트 전체를 DFS 수집(덮개 창 때문에 `rootInActiveWindow`를 쓰지 않는다). `isPerfScreen`이 아니고 진입 후 8초가 지났으면 '카드 혜택' 노드(또는 clickable 조상)를 1회 클릭.
  4. `PerfParser.parse` → `ReadStabilizer.offer`가 true면 저장 후 `finish(SUCCESS)`.
  5. 45초 경과 시 `finish(FAILED, if (perf screen seen) "parse" else "timeout")`.
  6. `finish(result)` 단일 종료 경로: 폴링 중지, 실패면 `lastResult/failReason`만 갱신(스냅샷 유지), 성공이면 `snapshot/lastSuccessAt` 갱신. 자동 또는 취소면 `GLOBAL_ACTION_BACK` 후 300ms 뒤 `GLOBAL_ACTION_HOME`; 수동 성공이면 화면 유지. `Overlay.hide()`를 `try/finally`로 보장. `PerfWidgetProvider.updateAll` 호출.
- [ ] **Step 4:** `Overlay`: `TYPE_ACCESSIBILITY_OVERLAY`, `MATCH_PARENT`, 불투명 다크 배경, 가운데 '실적 갱신 중…' + 작게 '누르면 중단', `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`, 클릭 시 콜백.
- [ ] **Step 5:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` → 둘 다 성공.
- [ ] **Step 6:** Commit `feat: accessibility service refresh flow`.

### Task 6: 홈 위젯과 수동 갱신, 안내 화면

**Files:**
- Create: `widget/PerfWidgetProvider.kt`, `widget/RefreshTrampolineActivity.kt`, `res/layout/widget_perf.xml`, `res/drawable/widget_bg.xml`, `res/drawable/progress_green.xml`, `res/xml/perf_widget_info.xml`
- Modify: `MainActivity.kt`, `AndroidManifest.xml`

**Interfaces:**
- Consumes: `PerfStore`, `WidgetStatus`, `PerfService.instance?.requestRefresh(manual = true)`.
- Produces: `object PerfWidgetProvider.Companion.updateAll(ctx: Context)` (실제로는 `companion object`의 `fun updateAll`).

- [ ] **Step 1: 레이아웃(A안).** 다크 배경(`#1E1F24`, 모서리 16dp). 헤더: 왼쪽 `카드 실적`, 오른쪽 기간(`10.1~10.31`, 지난달이면 `10.1~10.31 · 지난달`을 주황색 `#FFB74D`). 본문: 행 6개를 미리 두고(`row0..row5`) 카드 수만큼 `VISIBLE`. 각 행: 1줄째 `카드명`(흰색) + 오른쪽 상태(`완료`는 초록 `#4CD964` 굵게, 아니면 `41,500원 남음` 회색), 2줄째 `ProgressBar`(horizontal, max 1000, `progress_green.xml`) + 오른쪽 `258,500원 / 30만원`(작은 회색). 6개를 넘으면 마지막 줄에 `외 N개`. 하단: 상태 문구(`WidgetStatus.statusLine`), 오래됨/실패면 주황색. 카드가 없으면 `탭해서 실적 불러오기`.
- [ ] **Step 2:** `perf_widget_info.xml`: `minWidth=250dp`, `minHeight=110dp`, `targetCellWidth=4`, `targetCellHeight=2`, `resizeMode=horizontal|vertical`, `updatePeriodMillis=0`(갱신은 앱이 직접 한다), `widgetCategory=home_screen`.
- [ ] **Step 3:** 위젯 전체 클릭 → `PendingIntent.getActivity(RefreshTrampolineActivity, FLAG_IMMUTABLE)`. 트램펄린(투명 테마, `excludeFromRecents`, `noHistory`)은 서비스가 켜져 있으면 `requestRefresh(true)` 후 종료하고, 꺼져 있으면 `MainActivity`를 연다.
- [ ] **Step 4:** `MainActivity`: 접근성 서비스 켜짐 여부, 마지막 상태 문구, 버튼 2개(`접근성 설정 열기`, `지금 갱신`). 코드로 UI 구성(레이아웃 파일 불필요).
- [ ] **Step 5:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` 성공.
- [ ] **Step 6:** Commit `feat: home widget with manual refresh`.

### Task 7: 실기 설치와 종단 검증

**Files:** 없음(필요 시 수정 사항만 반영).

- [ ] **Step 1:** `adb install -r widget/app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2:** 접근성 켜기. 사용자가 설정에서 켜는 것이 원칙이며, 개발 검증 중에는 `adb shell settings put secure enabled_accessibility_services com.wonhy.cardperf/com.wonhy.cardperf.service.PerfService` + `accessibility_enabled 1`로 켤 수 있다(기존 값이 `null`임을 확인했으므로 덮어써도 다른 서비스를 끄지 않는다).
- [ ] **Step 3:** 위젯을 홈 화면에 추가하도록 사용자에게 요청한다(런처 조작은 adb로 확정적으로 할 수 없다). 그 전에도 `MainActivity`의 `지금 갱신`으로 흐름을 검증한다.
- [ ] **Step 4: 수동 갱신 검증.** `지금 갱신` → `adb logcat -s CardPerf`에서 `SUCCESS cards=3` 확인, 저장된 값이 실측 값(412,300/258,500/0)과 일치.
- [ ] **Step 5: 자동 갱신 검증.** `adb shell input keyevent KEYCODE_POWER`로 화면을 끄고 사용자가 잠금 해제 → 덮개 표시, 홈 복귀, 성공 로그. 검증을 위해 `lastAttemptAt`을 지우는 디버그 경로는 `adb shell pm clear`가 아니라 `MainActivity`의 `지금 갱신`을 쓰고, 쿨다운은 로그로 확인한다(`skip: cooldown`).
- [ ] **Step 6: 중단 검증.** 자동 갱신 중 덮개 탭 → 홈 복귀, 덮개 사라짐, 위젯 값 유지, 상태 `갱신 중단됨`.
- [ ] **Step 7: 실패 검증.** 뱅크샐러드를 `am force-stop` 후 비행기 모드 등 없이, 타임아웃을 유발하기 어렵다면 로그로 45초 타임아웃 경로의 `finish(FAILED)` 이후 덮개가 내려가는지만 확인한다.
- [ ] **Step 8:** Commit `chore: verified on device`(수정이 있었다면 해당 내용으로).
