package com.sysconf.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sysconf.constants.Constant;

import jakarta.annotation.PostConstruct;

/**
 * 관리자 여부 판별: {@code smw.admin-user-ids} 목록 + 세션/응답 {@code roles} 내 RL0001 (SYS_USER_ROLE·SYS_ROLE 연동).
 */
@Component
public class AdminPrivilegeResolver {

	@Value("${smw.admin-user-ids:}")
	private String adminUserIdsRaw;

	private Set<String> configuredAdminIds = Collections.emptySet();

	@PostConstruct
	public void init() {
		Set<String> s = new HashSet<>();
		if (adminUserIdsRaw != null && !adminUserIdsRaw.isBlank()) {
			for (String part : adminUserIdsRaw.split(",")) {
				String t = part.trim();
				if (!t.isEmpty()) {
					s.add(t);
				}
			}
		}
		configuredAdminIds = Collections.unmodifiableSet(s);
	}

	public boolean isConfiguredAdmin(String userId) {
		return userId != null && configuredAdminIds.contains(userId);
	}

	/**
	 * 세션 userInfo(인터셉터 주입 맵) 기준 관리자 여부.
	 */
	public boolean isAdminUser(Map<String, Object> userInfo) {
		if (userInfo == null) {
			return false;
		}
		Object uidObj = userInfo.get("sess_user_id");
		if (uidObj == null) {
			uidObj = userInfo.get("user_id");
		}
		String uid = uidObj != null ? uidObj.toString() : null;
		if (isConfiguredAdmin(uid)) {
			return true;
		}
		Object rolesObj = userInfo.get("roles");
		if (rolesObj == null) {
			rolesObj = userInfo.get("sess_role");
		}
		if (!(rolesObj instanceof List)) {
			return false;
		}
		List<?> roles = (List<?>) rolesObj;
		for (Object r : roles) {
			if (!(r instanceof Map)) {
				continue;
			}
			Map<?, ?> role = (Map<?, ?>) r;
			Object roleId = role.get("role_id");
			Object usgYn = role.get("usg_yn");
			String roleIdStr = String.valueOf(roleId);
			boolean enabled = (usgYn == null) || "Y".equalsIgnoreCase(String.valueOf(usgYn));
			if (enabled && Constant.ROLE_ADMIN.equals(roleIdStr)) {
				return true;
			}
		}
		return false;
	}

	public List<String> listConfiguredAdminUserIds() {
		return new ArrayList<>(configuredAdminIds);
	}
}
