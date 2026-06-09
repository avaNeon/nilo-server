package com.neon.nilocommon.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserStatVO
{
    private Integer count;

    private LocalDate date;
}
