package com.neon.niloweb.task;

import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class FileTask
{
    private final FileService fileService;

    private final WebConfig webConfig;

    /**
     * 定期清理过期所属权
     */
    @Scheduled(fixedRateString = "#{@webConfig.ownershipExpireClearIntervalHour * 60 * 60 * 1000}")
    public void clearExpireOwnership()
    {
        fileService.clearExpiredOwnership(webConfig.getOwnershipExpireTime());
    }
}
