package com.example.security.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.security.entity.HumanRecord;
import com.example.security.mapper.HumanRecordMapper;
import org.springframework.stereotype.Service;

@Service
public class HumanRecordService extends ServiceImpl<HumanRecordMapper, HumanRecord> {
}
