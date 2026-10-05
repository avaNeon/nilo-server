"""灌种子数据：向 video_info 插入 N 条压测用的视频。工作流的 "Seed videos" 步骤运行它。
用法：python seed_videos.py --count N

视频 ID 从 VIDEO_ID_BASE 开始连续编号，压测请求只访问这些视频。
"""
# 标准库
import argparse
from datetime import datetime

# 第三方库
from sqlalchemy import insert

# 本项目
from core.config import VIDEO_ID_BASE
from core.database import engine
from core.log import log
from models.video_info import VideoInfo

CHUNK = 5000  # 每批插入的条数


def main() -> None:
    """主流程：每批生成 5000 条视频数据并插入，直到插够 N 条"""
    p = argparse.ArgumentParser()
    p.add_argument('--count', type=int, required=True)
    count = p.parse_args().count

    # engine.begin()：取一个连接并开启事务，代码块正常结束时自动提交，出异常时回滚
    with engine.begin() as conn:
        # range(0, count, CHUNK) 依次产生 0, 5000, 10000, ...（不含 count）
        for start in range(0, count, CHUNK):
            n = min(CHUNK, count - start)  # 最后一批可能不足 5000
            # 传入一个列表时，SQLAlchemy 会把它们合并成多行的 INSERT 批量执行
            conn.execute(insert(VideoInfo), build_videos(start, n))
            log(f'seeded {start + n}/{count}')


def build_videos(start: int, n: int) -> list[dict]:
    """main() 每批调用一次：生成第 start ~ start+n-1 号视频的数据，每条是一个 {列名: 值} 的字典。
    video_id = VIDEO_ID_BASE + 序号，名称为"压测视频 <序号>"，播放量从 0 开始；标题、标签、简介是固定值，压测期间只更新播放量"""
    now = datetime.now()
    videos = []
    for i in range(start, start + n):
        videos.append({
            'video_id': VIDEO_ID_BASE + i,
            'video_cover': 'cover/perf.webp',
            'video_name': f'压测视频 {i}',
            'user_id': 1,
            'create_time': now,
            'last_update_time': now,
            'p_category_id': 1,
            'category_id': 1,
            'post_type': 0,
            'tags': '压测,播放统计',
            'introduction': '播放统计链路压测的种子数据',
            'duration': 60,
            'play_count': 0,
        })
    return videos


if __name__ == '__main__':
    main()
