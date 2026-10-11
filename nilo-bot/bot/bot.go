// Package bot 是 bot 模块的入口：保存跨轮次的状态，每一轮按顺序调用 service 层
package bot

import (
	"fmt"
	"math/rand/v2"

	"nilo-bot/service"
	"nilo-bot/util"
)

// Bot 定义Bot的行为，保存跨轮次的状态，具体怎么挑视频、怎么搜索交给 BotService
type Bot struct {
	service   *service.BotService
	sessionID string // 每次启动生成一个，以 bot- 开头，和真实用户区分开
	pageTotal int    // 视频列表的总页数，每次分页查询都会带回最新值，新发布的视频会自动算进来
}

// NewBot 创建 bot 模块：生成 sessionID
func NewBot(baseURL string) *Bot {
	sessionID := fmt.Sprintf("bot-%016x", rand.Uint64())
	// 创建本模块专用的日志器，再把日志器交给 BotService 共用
	log := util.NewCallLogger(sessionID)
	return &Bot{
		service:   service.NewBotService(baseURL, log),
		sessionID: sessionID,
		pageTotal: 1,
	}
}

// RunRound 一轮：挑一个视频 → 加一次播放 → 搜它的一个标签。挑不到视频就跳过这一轮；加播放和搜索互不影响
func (b *Bot) RunRound() {
	videoID, pageTotal, ok := b.service.PickVideo(b.pageTotal)
	if !ok {
		return
	}
	b.pageTotal = pageTotal
	b.service.Play(videoID, b.sessionID)
	b.service.SearchRandomTag(videoID)
}
