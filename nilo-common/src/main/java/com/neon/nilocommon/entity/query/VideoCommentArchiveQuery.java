package com.neon.nilocommon.entity.query;

import java.util.Date;


/**
 * 评论存档参数
 */
public class VideoCommentArchiveQuery extends BaseQuery {


	/**
	 * 评论ID【对外展示】
	 */
	private Long commentId;

	/**
	 * 父级评论ID（为0表示顶层评论）
	 */
	private Long parentCommentId;

	/**
	 * 视频ID
	 */
	private Long videoId;

	/**
	 * 视频用户ID
	 */
	private Long videoUserId;

	/**
	 * 回复内容
	 */
	private String content;

	private String contentFuzzy;

	/**
	 * 图片路径
	 */
	private String imgPaths;

	private String imgPathsFuzzy;

	/**
	 * 用户ID
	 */
	private Long userId;

	/**
	 * 回复人ID
	 */
	private Long replyUserId;

	/**
	 * 0:未置顶 1:置顶
	 */
	private Integer topType;

	/**
	 * 发布时间
	 */
	private String postTime;

	private String postTimeStart;

	private String postTimeEnd;

	/**
	 * 点赞数量
	 */
	private Integer upvoteCount;

	/**
	 * 点踩数量
	 */
	private Integer downvoteCount;

	/**
	 * （下一层）回复数量
	 */
	private Integer replyCount;

	/**
	 * 删除标记（0：未删除，1：已删除）
	 */
	private Integer deleted;

	/**
	 * 评论者昵称（冗余）
	 */
	private String nickName;

	private String nickNameFuzzy;

	/**
	 * 评论者头像（冗余）
	 */
	private String avatar;

	private String avatarFuzzy;

	/**
	 * 被回复者昵称（冗余）
	 */
	private String replyNickName;

	private String replyNickNameFuzzy;

	/**
	 * 视频标题（冗余）
	 */
	private String videoName;

	private String videoNameFuzzy;

	/**
	 * 视频封面（冗余）
	 */
	private String videoCover;

	private String videoCoverFuzzy;


	public void setCommentId(Long commentId){
		this.commentId = commentId;
	}

	public Long getCommentId(){
		return this.commentId;
	}

	public void setParentCommentId(Long parentCommentId){
		this.parentCommentId = parentCommentId;
	}

	public Long getParentCommentId(){
		return this.parentCommentId;
	}

	public void setVideoId(Long videoId){
		this.videoId = videoId;
	}

	public Long getVideoId(){
		return this.videoId;
	}

	public void setVideoUserId(Long videoUserId){
		this.videoUserId = videoUserId;
	}

	public Long getVideoUserId(){
		return this.videoUserId;
	}

	public void setContent(String content){
		this.content = content;
	}

	public String getContent(){
		return this.content;
	}

	public void setContentFuzzy(String contentFuzzy){
		this.contentFuzzy = contentFuzzy;
	}

	public String getContentFuzzy(){
		return this.contentFuzzy;
	}

	public void setImgPaths(String imgPaths){
		this.imgPaths = imgPaths;
	}

	public String getImgPaths(){
		return this.imgPaths;
	}

	public void setImgPathsFuzzy(String imgPathsFuzzy){
		this.imgPathsFuzzy = imgPathsFuzzy;
	}

	public String getImgPathsFuzzy(){
		return this.imgPathsFuzzy;
	}

	public void setUserId(Long userId){
		this.userId = userId;
	}

	public Long getUserId(){
		return this.userId;
	}

	public void setReplyUserId(Long replyUserId){
		this.replyUserId = replyUserId;
	}

	public Long getReplyUserId(){
		return this.replyUserId;
	}

	public void setTopType(Integer topType){
		this.topType = topType;
	}

	public Integer getTopType(){
		return this.topType;
	}

	public void setPostTime(String postTime){
		this.postTime = postTime;
	}

	public String getPostTime(){
		return this.postTime;
	}

	public void setPostTimeStart(String postTimeStart){
		this.postTimeStart = postTimeStart;
	}

	public String getPostTimeStart(){
		return this.postTimeStart;
	}
	public void setPostTimeEnd(String postTimeEnd){
		this.postTimeEnd = postTimeEnd;
	}

	public String getPostTimeEnd(){
		return this.postTimeEnd;
	}

	public void setUpvoteCount(Integer upvoteCount){
		this.upvoteCount = upvoteCount;
	}

	public Integer getUpvoteCount(){
		return this.upvoteCount;
	}

	public void setDownvoteCount(Integer downvoteCount){
		this.downvoteCount = downvoteCount;
	}

	public Integer getDownvoteCount(){
		return this.downvoteCount;
	}

	public void setReplyCount(Integer replyCount){
		this.replyCount = replyCount;
	}

	public Integer getReplyCount(){
		return this.replyCount;
	}

	public void setDeleted(Integer deleted){
		this.deleted = deleted;
	}

	public Integer getDeleted(){
		return this.deleted;
	}

	public void setNickName(String nickName){
		this.nickName = nickName;
	}

	public String getNickName(){
		return this.nickName;
	}

	public void setNickNameFuzzy(String nickNameFuzzy){
		this.nickNameFuzzy = nickNameFuzzy;
	}

	public String getNickNameFuzzy(){
		return this.nickNameFuzzy;
	}

	public void setAvatar(String avatar){
		this.avatar = avatar;
	}

	public String getAvatar(){
		return this.avatar;
	}

	public void setAvatarFuzzy(String avatarFuzzy){
		this.avatarFuzzy = avatarFuzzy;
	}

	public String getAvatarFuzzy(){
		return this.avatarFuzzy;
	}

	public void setReplyNickName(String replyNickName){
		this.replyNickName = replyNickName;
	}

	public String getReplyNickName(){
		return this.replyNickName;
	}

	public void setReplyNickNameFuzzy(String replyNickNameFuzzy){
		this.replyNickNameFuzzy = replyNickNameFuzzy;
	}

	public String getReplyNickNameFuzzy(){
		return this.replyNickNameFuzzy;
	}

	public void setVideoName(String videoName){
		this.videoName = videoName;
	}

	public String getVideoName(){
		return this.videoName;
	}

	public void setVideoNameFuzzy(String videoNameFuzzy){
		this.videoNameFuzzy = videoNameFuzzy;
	}

	public String getVideoNameFuzzy(){
		return this.videoNameFuzzy;
	}

	public void setVideoCover(String videoCover){
		this.videoCover = videoCover;
	}

	public String getVideoCover(){
		return this.videoCover;
	}

	public void setVideoCoverFuzzy(String videoCoverFuzzy){
		this.videoCoverFuzzy = videoCoverFuzzy;
	}

	public String getVideoCoverFuzzy(){
		return this.videoCoverFuzzy;
	}

}
