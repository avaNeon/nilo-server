package model

// VideoPage 分页查询视频的结果
type VideoPage struct {
	PageNo    int `json:"pageNo"`
	PageTotal int `json:"pageTotal"`
	List      []struct {
		VideoID string `json:"videoId"` // 后端把 Long 序列化成字符串，避免前端精度丢失
	} `json:"list"`
}
