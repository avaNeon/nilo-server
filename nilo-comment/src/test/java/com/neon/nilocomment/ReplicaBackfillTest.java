package com.neon.nilocomment;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 把主库 nilo 的用户、视频灌进评论库的复制表。
 * 打了 manual 标签，mvn test 默认不跑。
 */
@Tag("manual")
@SpringBootTest
class ReplicaBackfillTest
{
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void copyFromMain()
    {
        int users = jdbcTemplate.update("""
                                                INSERT INTO user_info_replica (user_id, nick_name, avatar)
                                                SELECT user_id, nick_name, avatar
                                                FROM nilo.user_info
                                                ON DUPLICATE KEY UPDATE
                                                    nick_name = VALUES(nick_name),
                                                    avatar = VALUES(avatar)
                                                """);
        int videos = jdbcTemplate.update("""
                INSERT INTO video_info_replica (video_id, user_id, video_name, video_cover, interaction)
                SELECT video_id, user_id, video_name, video_cover, interaction
                FROM nilo.video_info
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    video_name = VALUES(video_name),
                    video_cover = VALUES(video_cover),
                    interaction = VALUES(interaction)
                """);
        System.out.printf("user_info_replica affected=%d, video_info_replica affected=%d%n", users, videos);
    }
}
