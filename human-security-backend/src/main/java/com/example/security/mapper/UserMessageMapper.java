package com.example.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.security.entity.UserMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
    @Select("SELECT * FROM user_message WHERE receiver_id=#{userId} AND deleted_by_receiver=0 ORDER BY create_time DESC")
    List<UserMessage> selectInbox(@Param("userId") Long userId);

    @Select("SELECT * FROM user_message WHERE sender_id=#{userId} AND deleted_by_sender=0 ORDER BY create_time DESC")
    List<UserMessage> selectOutbox(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM user_message WHERE receiver_id=#{userId} AND read_status=0 AND deleted_by_receiver=0")
    int countUnread(@Param("userId") Long userId);

    @Update("UPDATE user_message SET read_status=1, read_time=NOW() WHERE id=#{id}")
    int markRead(@Param("id") Long id);

    @Update("UPDATE user_message SET deleted_by_sender=1 WHERE id=#{id}")
    int deleteBySender(@Param("id") Long id);

    @Update("UPDATE user_message SET deleted_by_receiver=1 WHERE id=#{id}")
    int deleteByReceiver(@Param("id") Long id);

    @Insert("INSERT INTO user_message(sender_id,sender_name,receiver_id,receiver_name,title,content,message_type,is_emergency,create_time) VALUES(#{senderId},#{senderName},#{receiverId},#{receiverName},#{title},#{content},#{messageType},#{isEmergency},NOW())")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insertMessage(UserMessage msg);

    /** 获取两个用户之间的对话记录 */
    @Select("SELECT * FROM user_message WHERE " +
            "((sender_id=#{userId1} AND receiver_id=#{userId2} AND deleted_by_sender=0) OR " +
            "(sender_id=#{userId2} AND receiver_id=#{userId1} AND deleted_by_receiver=0)) " +
            "ORDER BY create_time ASC")
    List<UserMessage> selectConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /** 获取用户的会话列表（按最近消息分组） */
    @Select("SELECT m.* FROM user_message m INNER JOIN (" +
            "SELECT CASE WHEN sender_id=#{userId} THEN receiver_id ELSE sender_id END AS contact_id, " +
            "MAX(create_time) AS last_time FROM user_message " +
            "WHERE (sender_id=#{userId} AND deleted_by_sender=0) OR (receiver_id=#{userId} AND deleted_by_receiver=0) " +
            "GROUP BY contact_id ORDER BY last_time DESC" +
            ") t ON ((m.sender_id=#{userId} AND m.receiver_id=t.contact_id) OR (m.receiver_id=#{userId} AND m.sender_id=t.contact_id)) " +
            "AND m.create_time=t.last_time ORDER BY m.create_time DESC")
    List<UserMessage> selectConversationList(@Param("userId") Long userId);

    /** 批量标记来自某用户的消息为已读 */
    @Update("UPDATE user_message SET read_status=1, read_time=NOW() WHERE receiver_id=#{receiverId} AND sender_id=#{senderId} AND read_status=0")
    int markReadBySender(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId);

    /** 批量标记消息为已读 */
    @Update("<script>UPDATE user_message SET read_status=1, read_time=NOW() WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int batchMarkRead(@Param("ids") java.util.List<Long> ids);
}
