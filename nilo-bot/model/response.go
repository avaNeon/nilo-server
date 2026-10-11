// Package model 是 nilo-web 接口返回的数据结构，只取 bot 用到的字段
package model

// Response 后端所有接口统一的返回格式：status 为 success 时 data 才有意义，否则错误原因在 info
type Response[T any] struct {
	Status string `json:"status"`
	Info   string `json:"info"`
	Data   T      `json:"data"`
}
