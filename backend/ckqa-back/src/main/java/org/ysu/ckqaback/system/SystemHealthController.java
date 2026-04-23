package org.ysu.ckqaback.system;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.system.dto.SystemHealthResponse;

/**
 * 系统健康检查控制器。
 */
@RestController
@RequiredArgsConstructor
public class SystemHealthController {

    private final SystemHealthService systemHealthService;

    @GetMapping(ApiPaths.SYSTEM_HEALTH)
    public ApiResponse<SystemHealthResponse> health() {
        return ApiResponseUtils.success(systemHealthService.check());
    }
}
