package com.neon.nilocommon;

import com.neon.nilocommon.entity.enums.userVideoAction.VideoActionType;
import com.neon.nilocommon.util.EnumFieldChecker;
import org.junit.jupiter.api.Test;

public class EnumFieldCheckerTest
{
    @Test
    public void testContainsFieldValue()
    {
        System.out.println("1:" + EnumFieldChecker.containsFieldValue(VideoActionType.class, "value", 1));
        System.out.println("2:" + EnumFieldChecker.containsFieldValue(VideoActionType.class, "value", 2));
        System.out.println("3:" + EnumFieldChecker.containsFieldValue(VideoActionType.class, "value", 3));
        System.out.println("4:" + EnumFieldChecker.containsFieldValue(VideoActionType.class, "value", 4));
        System.out.println("\"Hello\":" + EnumFieldChecker.containsFieldValue(VideoActionType.class, "value", "Hello"));

    }

}
