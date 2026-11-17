package com.example.securityagent.mapper;

import com.example.securityagent.entity.FacePerson;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface FacePersonMapper {

    @Insert("INSERT INTO face_person (user_id, person_id, name, person_type, face_id, quality_score, device_code, image_path, register_source, created_by, status, created_at) " +
            "VALUES (#{userId}, #{personId}, #{name}, #{personType}, #{faceId}, #{qualityScore}, #{deviceCode}, #{imagePath}, #{registerSource}, #{createdBy}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FacePerson facePerson);

    @Select("SELECT id, user_id, person_id, name, person_type, face_id, quality_score, device_code, image_path, register_source, created_by, status, created_at, updated_at " +
            "FROM face_person WHERE person_id = #{personId} AND status = 1")
    FacePerson findByPersonId(String personId);

    @Select("SELECT id, user_id, person_id, name, person_type, face_id, quality_score, device_code, image_path, register_source, created_by, status, created_at, updated_at " +
            "FROM face_person WHERE status = 1 ORDER BY created_at DESC")
    List<FacePerson> findAll();
    @Select("SELECT id, user_id, person_id, name, face_id, quality_score, device_code, image_path, register_source, created_by, status, created_at, updated_at FROM face_person WHERE user_id = #{userId} AND status = 1 ORDER BY created_at DESC")
    List<FacePerson> findByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM face_person WHERE user_id = #{userId} AND status = 1")
    int countByUserId(Long userId);

    @Select("SELECT id, user_id, person_id, name, person_type, face_id, quality_score, device_code, image_path, register_source, created_by, status, created_at, updated_at " +
            "FROM face_person WHERE name = #{name} AND status = 1")
    FacePerson findByName(String name);

    @org.apache.ibatis.annotations.Delete("DELETE FROM face_person WHERE name = #{name}")
    int deleteByName(String name);

    @org.apache.ibatis.annotations.Delete("DELETE FROM face_person WHERE name = #{name} AND user_id = #{userId}")
    int deleteByNameAndUserId(@org.apache.ibatis.annotations.Param("name") String name, @org.apache.ibatis.annotations.Param("userId") Long userId);

    @org.apache.ibatis.annotations.Update("UPDATE face_person SET status = 0, updated_at = NOW() WHERE name = #{name}")
    int softDeleteByName(String name);
}
