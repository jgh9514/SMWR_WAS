package com.sysconf.config;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.springframework.beans.factory.annotation.Value;

public class SessionListener implements HttpSessionListener {
	
	@Value("${server.servlet.session.timeout}")
	private int sessionTime;
	
	public void sessionCreated(HttpSessionEvent se) {
		se.getSession().setMaxInactiveInterval(sessionTime);
	}
	
	public void sessionDestroyed(HttpSessionEvent se) {
	}
}

