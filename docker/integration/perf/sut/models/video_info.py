"""VideoInfo：video_info 表的映射，相当于 JPA 的 @Entity。
只映射压测用到的列：灌种子数据要写的列和播放量。表结构以 docker/integration/mysql/init/02-nilo-schema.sql 为准。
"""
# 标准库
from datetime import datetime

# 第三方库
from sqlalchemy import BigInteger
from sqlalchemy.orm import Mapped, mapped_column

# 本项目
from models.base import Base


class VideoInfo(Base):
    """视频信息表"""
    __tablename__ = 'video_info'

    # Mapped[类型] 声明列的 Python 类型，SQLAlchemy 据此推断数据库类型；X | None 表示这一列可以为 NULL。
    # 推断不出来的（bigint）用 mapped_column(...) 补充，相当于 @Column
    video_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    video_cover: Mapped[str]
    video_name: Mapped[str]
    user_id: Mapped[int] = mapped_column(BigInteger)
    create_time: Mapped[datetime]
    last_update_time: Mapped[datetime]
    p_category_id: Mapped[int]
    category_id: Mapped[int | None]
    post_type: Mapped[int]  # 0：自制 1：转载
    tags: Mapped[str | None]  # 用逗号分隔的标签
    introduction: Mapped[str | None]
    duration: Mapped[int | None]  # 时长（秒）
    play_count: Mapped[int | None]
