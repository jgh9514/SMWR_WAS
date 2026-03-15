# 멀티 모듈 구조

## 모듈 구성

```
SMWR_WAS/
├── pom-parent.xml          # 부모 POM (멀티 모듈)
├── smwr-common/            # 공통 (이벤트, 상수, 유틸)
├── smwr-monster/           # 몬스터 도메인 (배치/API 공통)
├── smwr-admin/             # 관리자 + 배치 서버
├── smwr-api/               # 사용자 API 서버
└── (기존) src/ pom.xml     # 기존 단일 모듈 (유지)
```

## 빌드 및 실행

```bash
# 멀티 모듈 전체 빌드
mvn -f pom-parent.xml clean install

# API 서버 실행
cd smwr-api && mvn spring-boot:run

# Admin 서버 실행

cd smwr-admin && mvn spring-boot:run
```

## 모듈별 역할

| 모듈 | 역할 | 패키지 |
|------|------|--------|
| smwr-common | Kafka 이벤트, 상수, S3Util | com.smw.common |
| smwr-monster | 몬스터 서비스, mapper, dto | com.smw.monster |
| smwr-admin | 시스템 관리, 배치, Swarfarm 동기화 | com.admin, com.smw.monster.batch |
| smwr-api | 길드, 시즈, 몬스터 API, Kafka, Redis | com.smw, com.cf, com.sysconf |

## 전환 방법

1. **기존 방식 유지**: `pom.xml` + `mvn clean install` 사용
2. **멀티 모듈 사용**: `pom-parent.xml` + `mvn -f pom-parent.xml clean install`

## 마이그레이션 완료 후

- 기존 `pom.xml`을 `pom-legacy.xml`로 백업
- `pom-parent.xml`을 `pom.xml`로 변경
- GitHub Actions 등 CI에서 빌드 스크립트 수정
