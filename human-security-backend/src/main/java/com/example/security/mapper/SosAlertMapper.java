package com.example.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.security.entity.SosAlert;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SosAlertMapper extends BaseMapper<SosAlert> {

    @Select("SELECT * FROM sos_alert WHERE guard_id = #{guardId} ORDER BY create_time DESC LIMIT 100")
    List<SosAlert> findByGuardId(@Param("guardId") Long guardId);

    @Select("SELECT * FROM sos_alert WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT 50")
    List<SosAlert> findByUserId(@Param("userId") Long userId);

    @Update("UPDATE sos_alert SET status = #{status}, handle_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
