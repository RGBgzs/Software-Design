package com.example.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.security.entity.ChatRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {

    @Insert("INSERT INTO chat_record(user_id, user_message, bot_answer, matched_knowledge_id, category) " +
            "VALUES(#{userId}, #{userMessage}, #{botAnswer}, #{matchedKnowledgeId}, #{category})")
    int insertRecord(@Param("userId") Long userId,
                     @Param("userMessage") String userMessage,
                     @Param("botAnswer") String botAnswer,
                     @Param("matchedKnowledgeId") Long matchedKnowledgeId,
                     @Param("category") String category);

    /** 按关键词搜索聊天记录（用户问题和系统回答） */
    @Select("SELECT * FROM chat_record WHERE user_message LIKE CONCAT('%',#{keyword},'%') OR bot_answer LIKE CONCAT('%',#{keyword},'%') ORDER BY created_at DESC")
    java.util.List<ChatRecord> selectByKeyword(@Param("keyword") String keyword);

    /** 查询所有聊天记录（按时间倒序） */
    @Select("SELECT * FROM chat_record ORDER BY created_at DESC")
    java.util.List<ChatRecord> selectAll();
}
