package com.sysconf.config;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

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

