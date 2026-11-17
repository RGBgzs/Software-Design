package com.example.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.security.entity.AlarmRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {

    /** 按处理状态查询告警列表 (handle_status: 0=未处理/1=已处理/2=误报) */
    @Select("SELECT * FROM alarm_record WHERE handle_status = #{status} ORDER BY alarm_time DESC")
    List<AlarmRecord> selectByHandleStatus(@Param("status") Integer status);

    /** 按等级查询告警列表 */
    @Select("SELECT * FROM alarm_record WHERE alarm_level = #{level} ORDER BY alarm_time DESC")
    List<AlarmRecord> selectByLevel(@Param("level") String level);

    /** 按状态+等级查询告警列表 */
    @Select("SELECT * FROM alarm_record WHERE handle_status = #{status} AND alarm_level = #{level} ORDER BY alarm_time DESC")
    List<AlarmRecord> selectByStatusAndLevel(@Param("status") Integer status,
                                              @Param("level") String level);

    /** 查询最新一条未处理告警 (handle_status = 0) */
    @Select("SELECT * FROM alarm_record WHERE handle_status = 0 ORDER BY alarm_time DESC LIMIT 1")
    AlarmRecord selectLatestUnhandled();

    /** 查询所有告警（按时间倒序） */
    @Select("SELECT * FROM alarm_record ORDER BY alarm_time DESC")
    List<AlarmRecord> selectAll();

    /** 将告警标记为已处理 */
    @Update("UPDATE alarm_record SET handle_status = 1, handle_time = #{handleTime} WHERE id = #{id}")
    int handleAlarm(@Param("id") Long id,
                    @Param("handleTime") LocalDateTime handleTime);

    /** 查询所有未处理告警 (handle_status = 0) */
    @Select("SELECT * FROM alarm_record WHERE handle_status = 0 ORDER BY alarm_time DESC")
    List<AlarmRecord> selectUnhandled();
}
