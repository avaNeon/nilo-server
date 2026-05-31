package com.neon.nilocommon.entity.po.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;
import java.util.List;

@Document(indexName = "video_info_doc")
@Setting(replicas = 0)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class VideoInfoDoc
{
    @Id
    private Long videoId;

    @Field(type = FieldType.Keyword, index = false)
    private String videoCover;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String videoName;

    @Field(type = FieldType.Integer, index = false)
    private Integer duration;

    @Field(type = FieldType.Long, index = false)
    private Long userId;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss", index = false)
    private LocalDateTime lastUpdateTime;

    @Field(type = FieldType.Integer, index = false)
    private Integer categoryId;

    @Field(type = FieldType.Keyword)
    private List <String> tags;

    @Field(type = FieldType.Integer, index = false)
    private Integer playCount;

    @Field(type = FieldType.Integer, index = false)
    private Integer danmakuCount;

    @Field(type = FieldType.Integer, index = false)
    private Integer collectCount;
}
