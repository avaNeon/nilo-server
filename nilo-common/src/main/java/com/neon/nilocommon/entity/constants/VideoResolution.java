package com.neon.nilocommon.entity.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VideoResolution
{
    RES_720P("720P", 720), RES_480P("480P", 480);

    private final String folderName;
    private final int resolution;

    public static VideoResolution fromFolderName(String folderName)
    {
        if (folderName == null)
        {
            return null;
        }
        for (VideoResolution value : values())
        {
            if (value.folderName.equalsIgnoreCase(folderName))
            {
                return value;
            }
        }
        return null;
    }
}
