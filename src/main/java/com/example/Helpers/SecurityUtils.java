package com.example.Helpers;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


public class SecurityUtils {
    public static boolean isAdmin(HttpServletRequest request) {
        Object rolesAttr = request.getAttribute("userRoles");

        if (rolesAttr instanceof List) {
            List<String> roles = (List<String>) rolesAttr;
            return roles != null && roles.contains(Roles.ADMIN);
        }
        return false;
    }

    public static boolean hasRole(HttpServletRequest request, String roleName) {
        Object rolesAttr = request.getAttribute("userRoles");

        if (rolesAttr instanceof List) {
            List<String> roles = (List<String>) rolesAttr;
            return roles != null && roles.contains(roleName);
        }
        return false;
    }

    public static Integer getCurrentUserId(HttpServletRequest request) {
        Object idAttr = request.getAttribute("userId");
        if (idAttr instanceof Integer) {
            return (Integer) idAttr;
        }
        return null;
    }
}