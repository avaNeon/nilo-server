package com.neon.nilocommon.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 包含常用的分页行数大小
 */
@Getter
@AllArgsConstructor
public enum PageSize
{
    SIZE15(15), SIZE20(20), SIZE30(30), SIZE40(40), SIZE50(50);

    final int size;

}
