// nilo-bot 模拟用户访问：每分钟随机挑一个已发布的视频，给它加一次播放，再搜一次它的某个标签。
// 只调用 nilo-web 的公开接口，不登录、不连数据库。每次接口调用打一行日志，之后可以据此统计 bot 贡献了多少播放和搜索。
package main

import (
	"fmt"
	"nilo-bot/bot"
	"os"
	"time"
)

// 每轮之间的间隔。播放统计对同一 sessionId 有 30 秒的限流，1 分钟足够宽裕
const interval = time.Minute / 2

func main() {
	baseURL := os.Getenv("NILO_WEB_URL")
	if baseURL == "" {
		fmt.Fprintln(os.Stderr, "缺少环境变量 NILO_WEB_URL（nilo-web 的地址）")
		os.Exit(1)
	}

	b := bot.NewBot(baseURL)
	for {
		b.RunRound()
		time.Sleep(interval)
	}
}
