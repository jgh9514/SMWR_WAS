# 멀티 모듈 구조

## STS(Eclipse) 로컬 실행

1. **프로젝트 임포트**: `pom.xml` (부모) 기준 Maven 프로젝트로 Import (Existing Maven Projects)
2. **런치 설정**: 아래 `.launch` 파일 사용
   - `smwr-api.launch` → API 서버 (포트 8080)
   - `smwr-admin.launch` → Admin 서버 (포트 8081)
3. **실행**: Run As → 각 런치 선택 후 실행

> 프로젝트 이름이 다르면 `.launch` 파일의 `PROJECT_ATTR` 값을 워크스페이스 프로젝트명에 맞게 수정하세요.

## 모듈 구성

```
SMWR_WAS/
├── pom.xml                 # 부모 POM (멀티 모듈)
├── smwr-common/            # 공통 (이벤트, 상수, 유틸)
├── smwr-monster/           # 몬스터 도메인 (배치/API 공통)
├── smwr-admin/             # 관리자 + 배치 서버
├── smwr-api/               # 사용자 API 서버
└── pom-legacy.xml          # 기존 단일 모듈 백업 (참고용)
```

## 빌드 및 실행

> **중요**: `smwr-admin`이나 `smwr-api`만 따로 실행하면 `smwr-common`, `smwr-monster`를 찾지 못해 실패합니다. **반드시 부모에서 먼저 빌드**하세요.

```bash
# 1. 부모(SMWR_WAS)에서 전체 빌드 (필수 - common, monster를 로컬 저장소에 설치)
cd c:\project\서머너즈워 프로젝트\SMWR_WAS
mvnw clean install -DskipTests

# 2. API 서버 실행
cd smwr-api
mvnw spring-boot:run

# 3. Admin 서버 (다른 터미널에서)
cd smwr-admin
mvnw spring-boot:run
```

**STS**: 부모 프로젝트에서 **Maven** → **Update Project** 후, Boot Dashboard에서 실행

## 모듈별 역할

| 모듈 | 역할 | 패키지 |
|------|------|--------|
| smwr-common | Kafka 이벤트, 상수, S3Util | com.smw.common |
| smwr-monster | 몬스터 서비스, mapper, dto | com.smw.monster |
| smwr-admin | 시스템 관리, 배치, Swarfarm 동기화 | com.admin, com.smw.monster.batch |
| smwr-api | 길드, 시즈, 몬스터 API, Kafka, Redis | com.smw, com.cf, com.sysconf |

## 마이그레이션 완료

- 기존 `pom.xml` → `pom-legacy.xml` (백업)
- 루트 `src/` 제거 (모듈별 src만 사용)
- Docker/CI는 `smwr-api` 모듈 빌드
