# STS Boot Dashboard 설정

## 1. 멀티 모듈 프로젝트 임포트

Boot Dashboard가 서버를 인식하려면 **각 모듈이 별도 프로젝트**로 있어야 합니다.

### 방법 A: 부모 POM으로 한 번에 임포트

1. **File** → **Import** → **Maven** → **Existing Maven Projects**
2. **Root Directory**에서 `SMWR_WAS` 폴더 선택
3. **pom.xml** (부모) 선택 후 하위 모듈(smwr-common, smwr-monster, smwr-admin, smwr-api)이 모두 체크되는지 확인
4. **Finish**

### 방법 B: 모듈별 개별 임포트

1. **File** → **Import** → **Maven** → **Existing Maven Projects**
2. **Root Directory**에서 `SMWR_WAS/smwr-api` 선택 → Finish
3. 같은 방식으로 `SMWR_WAS/smwr-admin` 임포트
4. (의존성) `smwr-common`, `smwr-monster`도 임포트

---

## 2. Boot Dashboard에서 실행

1. **Window** → **Show View** → **Other** → **Spring** → **Boot Dashboard**
2. `smwr-api`(8080), `smwr-admin`(8081) **▶ Run** 클릭

---

## 3. Boot Dashboard에 안 보일 때

- **↻ Refresh** 클릭
- **Maven** → **Update Project** (Alt+F5)
