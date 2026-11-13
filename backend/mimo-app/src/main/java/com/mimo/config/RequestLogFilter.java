package com.mimo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/** 临时调试：打印所有请求 method + URI（定位 405 后删除） */
@Component
@Slf4j
public class RequestLogFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws java.io.IOException, javax.servlet.ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        log.info("[ReqLog] {} {}", req.getMethod(), req.getRequestURI());
        chain.doFilter(request, response);
    }
}
