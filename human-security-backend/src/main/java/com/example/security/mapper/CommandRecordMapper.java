package com.example.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.security.entity.CommandRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommandRecordMapper extends BaseMapper<CommandRecord> {
}
