package com.tradingx.config;

import com.tradingx.model.RequireAdmin;
import com.tradingx.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod method = (HandlerMethod) handler;

        RequireAdmin classAnnotation = method.getBeanType().getAnnotation(RequireAdmin.class);
        RequireAdmin methodAnnotation = method.getMethodAnnotation(RequireAdmin.class);

        if (classAnnotation == null && methodAnnotation == null) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":-1,\"msg\":\"未登录或会话已过期\"}");
            return false;
        }

        User user = (User) session.getAttribute("user");
        if (!"root".equals(user.getRole())) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(403);
            response.getWriter().write("{\"code\":-1,\"msg\":\"权限不足，需要管理员权限\"}");
            return false;
        }

        return true;
    }
}
