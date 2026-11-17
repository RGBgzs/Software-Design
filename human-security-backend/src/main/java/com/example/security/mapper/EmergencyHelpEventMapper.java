package com.example.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.security.entity.EmergencyHelpEvent;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EmergencyHelpEventMapper extends BaseMapper<EmergencyHelpEvent> {

    @Insert("INSERT INTO emergency_help_event(initiator_id, event_type, urgency, status, location, notes, linked_pir_alarm_id, linked_device_code, created_at) " +
            "VALUES(#{initiatorId}, #{eventType}, #{urgency}, #{status}, #{location}, #{notes}, #{linkedPirAlarmId}, #{linkedDeviceCode}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEvent(EmergencyHelpEvent event);

    @Select("SELECT * FROM emergency_help_event WHERE initiator_id=#{userId} ORDER BY created_at DESC")
    List<EmergencyHelpEvent> findByInitiator(@Param("userId") Long userId);

    @Select("SELECT DISTINCT e.* FROM emergency_help_event e " +
            "INNER JOIN emergency_help_receiver r ON e.id = r.event_id " +
            "WHERE r.receiver_id=#{userId} ORDER BY e.created_at DESC")
    List<EmergencyHelpEvent> findByReceiver(@Param("userId") Long userId);

    @Select("SELECT * FROM emergency_help_event WHERE id=#{id}")
    EmergencyHelpEvent selectById(@Param("id") Long id);

    @Update("UPDATE emergency_help_event SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
