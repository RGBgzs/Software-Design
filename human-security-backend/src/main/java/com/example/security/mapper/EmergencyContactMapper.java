package com.example.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.security.entity.EmergencyContact;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EmergencyContactMapper extends BaseMapper<EmergencyContact> {

    @Select("SELECT * FROM emergency_contact WHERE user_id=#{userId} AND status=1 ORDER BY created_at DESC")
    List<EmergencyContact> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM emergency_contact WHERE user_id=#{userId} AND contact_user_id=#{contactUserId} AND status=1")
    EmergencyContact findByUserAndContact(@Param("userId") Long userId, @Param("contactUserId") Long contactUserId);

    @Insert("INSERT INTO emergency_contact(user_id, contact_user_id, contact_username, contact_nickname, status, created_at) VALUES(#{userId}, #{contactUserId}, #{contactUsername}, #{contactNickname}, 1, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertContact(EmergencyContact contact);

    @Update("UPDATE emergency_contact SET status=0, updated_at=NOW() WHERE id=#{id}")
    int softDelete(@Param("id") Long id);

    @Select("SELECT * FROM emergency_contact WHERE contact_user_id=#{contactUserId} AND status=1")
    List<EmergencyContact> findContactsOf(@Param("contactUserId") Long contactUserId);
}
