package com.neon.nilocommon.autoconfigure.loginState;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "common.login-state")
public class LoginStateProperties
{
    private boolean enabled = true;
}
