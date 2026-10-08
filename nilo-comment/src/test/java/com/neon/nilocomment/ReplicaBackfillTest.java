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
                                                SELECT *
                                                FROM (SELECT user_id, nick_name AS src_nick_name, avatar AS src_avatar
                                                      FROM nilo.user_info) AS src
                                                ON DUPLICATE KEY UPDATE
                                                    nick_name = src_nick_name,
                                                    avatar = src_avatar
                                                """);
        int videos = jdbcTemplate.update("""
                INSERT INTO video_info_replica (video_id, user_id, video_name, video_cover, interaction)
                SELECT *
                FROM (SELECT video_id,
                             user_id     AS src_user_id,
                             video_name  AS src_video_name,
                             video_cover AS src_video_cover,
                             interaction AS src_interaction
                      FROM nilo.video_info) AS src
                ON DUPLICATE KEY UPDATE
                    user_id = src_user_id,
                    video_name = src_video_name,
                    video_cover = src_video_cover,
                    interaction = src_interaction
                """);
        System.out.printf("user_info_replica affected=%d, video_info_replica affected=%d%n", users, videos);
    }
}
