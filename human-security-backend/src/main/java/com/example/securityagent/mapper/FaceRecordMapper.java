package com.example.securityagent.mapper;

import com.example.securityagent.entity.FaceRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface FaceRecordMapper {

    @Insert("INSERT INTO face_record (user_id, person_id, person_name, person_type, matched, similarity, threshold_value, " +
            "device_code, location, risk_level, suggestion, result_type, reason, alarm_id, capture_time, created_at) " +
            "VALUES (#{userId}, #{personId}, #{personName}, #{personType}, #{matched}, #{similarity}, #{thresholdValue}, " +
            "#{deviceCode}, #{location}, #{riskLevel}, #{suggestion}, #{alarmId}, #{captureTime}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FaceRecord faceRecord);

    @Select("SELECT id, person_name, person_type, matched, similarity, threshold_value, " +
            "device_code, location, risk_level, suggestion, alarm_id, capture_time, created_at " +
            "FROM face_record ORDER BY created_at DESC")
    List<FaceRecord> findAll();

    @Select("SELECT id, person_name, person_type, matched, similarity, threshold_value, " +
            "device_code, location, risk_level, suggestion, alarm_id, capture_time, created_at " +
            "FROM face_record WHERE id = #{id}")
    FaceRecord findById(Long id);
    @Select("SELECT id, user_id, person_id, person_name, person_type, matched, similarity, threshold_value, " +
            "device_code, location, risk_level, suggestion, result_type, reason, alarm_id, capture_time, created_at " +
            "FROM face_record WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<FaceRecord> findByUserId(Long userId);
}
