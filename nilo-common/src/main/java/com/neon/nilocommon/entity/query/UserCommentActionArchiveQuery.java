package com.neon.nilocommon.entity.query;

import java.util.Date;


/**
 * 用户评论行为存档 点赞、点踩参数
 */
public class UserCommentActionArchiveQuery extends BaseQuery {


	/**
	 * 自增ID
	 */
	private Long actionId;

	/**
	 * 视频ID
	 */
	private Long videoId;

	/**
	 * 评论ID
	 */
	private Long commentId;

	/**
	 * 0:评论点赞 1:评论点踩
	 */
	private Integer actionType;

	/**
	 * 用户ID
	 */
	private Long userId;

	/**
	 * 操作时间
	 */
	private String actionTime;

	private String actionTimeStart;

	private String actionTimeEnd;


	public void setActionId(Long actionId){
		this.actionId = actionId;
	}

	public Long getActionId(){
		return this.actionId;
	}

	public void setVideoId(Long videoId){
		this.videoId = videoId;
	}

	public Long getVideoId(){
		return this.videoId;
	}

	public void setCommentId(Long commentId){
		this.commentId = commentId;
	}

	public Long getCommentId(){
		return this.commentId;
	}

	public void setActionType(Integer actionType){
		this.actionType = actionType;
	}

	public Integer getActionType(){
		return this.actionType;
	}

	public void setUserId(Long userId){
		this.userId = userId;
	}

	public Long getUserId(){
		return this.userId;
	}

	public void setActionTime(String actionTime){
		this.actionTime = actionTime;
	}

	public String getActionTime(){
		return this.actionTime;
	}

	public void setActionTimeStart(String actionTimeStart){
		this.actionTimeStart = actionTimeStart;
	}

	public String getActionTimeStart(){
		return this.actionTimeStart;
	}
	public void setActionTimeEnd(String actionTimeEnd){
		this.actionTimeEnd = actionTimeEnd;
	}

	public String getActionTimeEnd(){
		return this.actionTimeEnd;
	}

}
