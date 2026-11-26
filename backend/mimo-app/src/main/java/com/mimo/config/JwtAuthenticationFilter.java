package com.mimo.config;

import com.mimo.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 单设备登录：默认**关**。
     *
     * 这段逻辑原来靠"`StringRedisTemplate` 永远是 null"来跳过（`RedisAutoConfiguration`
     * 在 `MimoApplication` 里被 exclude 了）。KNOWN-002 接上 Redis 之后这句话就不成立了——
     * 如果不管，同一个账号在两台浏览器同时登录会互相踢掉，开发调试和 e2e 立刻全崩。
     * 所以把开关显式化：`mimo.security.single-device-login=true` 才生效。
     */
    @Value("${mimo.security.single-device-login:false}")
    private boolean singleDeviceLogin;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserId(token);

                    // 单设备登录校验：开关打开且 Redis 可用时才检查
                    if (singleDeviceLogin && redisTemplate != null) {
                        String storedToken = redisTemplate.opsForValue().get("mimo:token:" + userId);
                        if (storedToken != null && !storedToken.equals(token)) {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":401,\"message\":\"账号已在其他设备登录\"}");
                            return;
                        }
                    }

                    String username = jwtUtil.getUsername(token);
                    String role = jwtUtil.getRole(token);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId, null,
                                    Collections.singletonList(new SimpleGrantedAuthority(role)));
                    authentication.setDetails(username);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\"}");
                    return;
                }
            } catch (Exception e) {
                log.warn("JWT 解析异常: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"Token无效，请重新登录\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
