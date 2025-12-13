package com.sysconf.filter;

import java.io.IOException;
import java.util.Set;

import com.sysconf.servlet.CachedBodyHttpServletWrapper;
import com.sysconf.util.StringUtil;

import org.springframework.beans.factory.annotation.Value;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

@Component
public class SimpleCorsFilter implements Filter {

	@Value("${smw.service-domain}")
	private Set<String> service_domain;

	private StringUtil StringUtil = new StringUtil();

	@SuppressWarnings("static-access")
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		String originHeader = StringUtil.nvl(request.getHeader("Origin"));

		boolean isMatch = service_domain.stream().anyMatch(s -> 
			originHeader.equals("http://" + s) || 
			originHeader.equals("https://" + s) ||
			originHeader.contains(s)
		);
		response.setHeader("Access-Control-Allow-Origin", isMatch ? originHeader : " ");
		response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
		response.setHeader("Access-Control-Max-Age", "3600");
//		response.setHeader("Access-Control-Allow-Headers", "content-type, x-xsrf-token");
		response.setHeader("Access-Control-Allow-Headers", "x-pageid, x-dataserviceid, x-portalid, x-siteid, x-skiperrorhandler, x-requested-with, content-type, accept, x-xsrf-token, x-isdp-token, x-ISDP-System-Token, X-PINGOTHER, Authorization, real-path");
		response.setHeader("Access-Control-Allow-Credentials", "true");
		response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
		response.setHeader("Cache-Control" , "no-cache, no-store, max-age=0, must-revalidate");

		// OPTIONS ?�청(Preflight)???�??처리
		if ("OPTIONS".equals(request.getMethod())) {
			response.setStatus(HttpServletResponse.SC_OK);
			return;
		}

		String contentType = request.getContentType();
		if (contentType != null && contentType.contains("multipart/form-data")) {
		  chain.doFilter(req, res);
		} else {
		  try {
		    CachedBodyHttpServletWrapper cachedBodyHttpServletWrapper = new CachedBodyHttpServletWrapper(request);
		    chain.doFilter(cachedBodyHttpServletWrapper, res);
		  } catch (IOException e) {
		    // IOException 처리
		    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		  }
		}
	}

	public void init(FilterConfig filterConfig) {
	}

	public void destroy() {
	}
}
