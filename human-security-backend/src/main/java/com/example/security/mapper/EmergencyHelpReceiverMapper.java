package com.example.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.security.entity.EmergencyHelpReceiver;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EmergencyHelpReceiverMapper extends BaseMapper<EmergencyHelpReceiver> {

    @Insert("INSERT INTO emergency_help_receiver(event_id, receiver_id, receiver_name, status, created_at) " +
            "VALUES(#{eventId}, #{receiverId}, #{receiverName}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertReceiver(EmergencyHelpReceiver receiver);

    @Select("SELECT * FROM emergency_help_receiver WHERE event_id=#{eventId} ORDER BY created_at ASC")
    List<EmergencyHelpReceiver> findByEventId(@Param("eventId") Long eventId);

    @Select("SELECT * FROM emergency_help_receiver WHERE event_id=#{eventId} AND receiver_id=#{receiverId}")
    EmergencyHelpReceiver findByEventAndReceiver(@Param("eventId") Long eventId, @Param("receiverId") Long receiverId);

    @Update("UPDATE emergency_help_receiver SET status=#{status}, acknowledged_at=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
