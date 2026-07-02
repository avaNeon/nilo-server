package com.neon.niloweb.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UploadQuotaType
{
    IMAGE("image"), VIDEO("video");

    private final String keyPart;
}
